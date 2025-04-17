package org.mve.ws;

import org.mve.Array;
import org.mve.JavaVM;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class WebSocket
{
	public static final String METHOD_GET = "GET";
	public static final String HTTP_VERSION = "HTTP/1.1";
	public static final String HEADER_HOST = "Host";
	public static final String HEADER_CONNECTION = "Connection";
	public static final String HEADER_UPGRADE = "Upgrade";
	public static final String HEADER_SEC_WS_KEY       = "Sec-WebSocket-Key";
	public static final String HEADER_SEC_WS_VERSION   = "Sec-WebSocket-Version";
	public static final String HEADER_SEC_WS_PROTOCOL  = "Sec-WebSocket-Protocol";
	public static final String HEADER_SEC_WS_EXTENSION = "Sec-WebSocket-Extension";
	public static final String HEADER_SEC_WS_ACCEPT    = "Sec-WebSocket-Accept";

	public static final int STAT_CLOSED     = 0;
	public static final int STAT_CONNECTING = 1;
	public static final int STAT_HANDSHAKE1 = 2;
	public static final int STAT_HANDSHAKE2 = 3;
	public static final int STAT_HANDSHAKE3 = 4;
	public static final int STAT_CONNECTED  = 5;
	public static final int STAT_CLOSING    = 6;

	public static final int RS_OVERED  = 0;
	public static final int RS_OPCODE  = 1;
	public static final int RS_LENGTH  = 2;
	public static final int RS_MASKING = 3;
	public static final int RS_PAYLOAD = 4;

	public static final int MASK_FIN = 0x80;
	public static final int MASK_RSV = 0x70;
	public static final int MASK_OPC = 0x0F;
	public static final int MASK_MSK = 0x80;
	public static final int MASK_LEN = 0x7F;

	public static final int OPC_CONTINUE = 0x0;
	public static final int OPC_TEXT     = 0x1;
	public static final int OPC_BINARY   = 0x2;
	public static final int OPC_CLOSE    = 0x8;
	public static final int OPC_PING     = 0x9;
	public static final int OPC_PONG     = 0xA;

	private static final int READING = 0;
	private static final int WRITING = 1;
	private static final int STATING = 2;

	public boolean secure;
	public String host;
	public int port;
	public String path;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, String> header = new HashMap<>();
	private int status = WebSocket.STAT_CLOSED;
	private boolean reading = false;
	private boolean writing = false;
	private SocketChannel socket = null;
	private boolean blocking = true;
	private final ReentrantLock[] locking = new ReentrantLock[]{new ReentrantLock(), new ReentrantLock(), new ReentrantLock()};

	// Read Write buffer
	private ByteBuffer RB = ByteBuffer.allocate(4096);
	private ByteBuffer WB = ByteBuffer.allocate(4096);
	private int RS = WebSocket.RS_OVERED;

	// Data buffer

	private boolean fin = false;
	private int opcode = 0;
	private long length = 0;
	private final byte[] masking = new byte[5];
	private final Array array = new Array(4096);

	public WebSocket(String url)
	{
		try
		{
			int protoIdx = url.indexOf("://");
			if (protoIdx == -1) throw new MalformedURLException(url + " is not a valid websocket URL");

			String proto = url.substring(0, protoIdx);
			boolean sec = false;
			if ("wss".equals(proto)) sec = true;
			else if (!"ws".equals(proto)) throw new MalformedURLException("Invalid protocol: " + proto);
			this.secure = sec;

			url = url.substring(protoIdx + 3);
			int end = 0;
			while (end < url.length() && url.charAt(end) != '/') end++;

			String hostAndPort = url.substring(0, end);
			String host = hostAndPort;
			int port = this.secure ? 443 : 80;
			int portIdx = hostAndPort.indexOf(':');
			if (portIdx != -1)
			{
				host = hostAndPort.substring(0, portIdx);
				port = Integer.parseInt(hostAndPort.substring(portIdx + 1));
			}
			this.host = host;
			this.port = port;

			String uri = "/";
			if (end < url.length()) uri = url.substring(end);
			this.path = uri;
		}
		catch (MalformedURLException e)
		{
			throw new IllegalArgumentException(e);
		}

		this.reset();
		this.header.clear();
		this.header(WebSocket.HEADER_HOST, this.host);
		this.header(WebSocket.HEADER_CONNECTION, "Upgrade");
		this.header(WebSocket.HEADER_UPGRADE, "websocket");
		this.header(WebSocket.HEADER_SEC_WS_KEY, WebSocket.key(this.random));
		this.header(WebSocket.HEADER_SEC_WS_VERSION, "13");
	}

	public void header(String key, String value)
	{
		if (value == null)
		{
			this.header.remove(key);
		}
		this.header.put(key, value);
	}

	public boolean finish()
	{
		try
		{
			switch (this.status)
			{
				case WebSocket.STAT_CLOSED:
					this.reset();
					this.socket = SocketChannel.open();
					this.socket.configureBlocking(this.blocking);
					SocketAddress address = new InetSocketAddress(this.host, this.port);
					this.socket.connect(address);
					this.status = WebSocket.STAT_CONNECTING;

				case WebSocket.STAT_CONNECTING:
					if (this.socket == null)
					{
						throw new NullPointerException();
					}
					if (!this.socket.finishConnect()) return false;
					this.status = WebSocket.STAT_HANDSHAKE1;

				case WebSocket.STAT_HANDSHAKE1:
					StringBuilder requ = new StringBuilder(WebSocket.METHOD_GET);
					requ.append(" ");
					requ.append(this.path);
					requ.append(" ");
					requ.append(HTTP_VERSION);
					requ.append("\r\n");
					for (Map.Entry<String, String> header : this.header.entrySet())
					{
						requ.append(header.getKey());
						requ.append(": ");
						requ.append(header.getValue());
						requ.append("\r\n");
					}
					requ.append("\r\n");
					ByteBuffer buf = ByteBuffer.wrap(requ.toString().getBytes(StandardCharsets.UTF_8));
					while (buf.hasRemaining())
					{
						this.socket.write(buf);
					}
					this.status = WebSocket.STAT_HANDSHAKE2;

				case WebSocket.STAT_HANDSHAKE2:
					this.RB.clear();
					this.RB.limit(1);
					int read = this.socket.read(this.RB);
					if (read == 0) return false;
					this.RB.flip();
					// for (int i = 0; i < this.RB.limit(); i++)
					while (this.RB.hasRemaining())
					{
						byte b = this.RB.get();
						this.array.put(b);
						int off = this.array.length() - 4;
						if (off < 0) continue;
						int b0 = this.array.get(off);
						int b1 = this.array.get(off + 1);
						int b2 = this.array.get(off + 2);
						int b3 = this.array.get(off + 3);
						if ((b0 == '\r') && (b1 == '\n') && (b2 == '\r') && (b3 == '\n'))
						{
							this.status = WebSocket.STAT_HANDSHAKE3;
							break;
						}
					}
					return false;

				case WebSocket.STAT_HANDSHAKE3:
					StringBuilder builder = new StringBuilder(this.array.length());
					ArrayList<String> response = new ArrayList<>();
					boolean carriage = false;
					while (this.array.length() > 0)
					{
						int b = this.array.get();
						if (b == '\r') carriage = true;
						else if (b == '\n')
						{
							if (!carriage)
								throw new IllegalArgumentException("Wrong HTTP response: Expected CR before LF");
							carriage = false;
							if (builder.length() > 0)
								response.add(builder.toString());
							builder.setLength(0);
						}
						else
						{
							if (carriage) builder.append('\r');
							builder.append((char) b);
						}
					}

					String respLine = response.get(0);
					int code = Integer.parseInt(respLine.substring(9, 12));
					if (code != 101) throw new IllegalStateException(String.valueOf(code));

					Map<String, String> resp = new HashMap<>();
					for (int i = 1; i < response.size(); i++)
					{
						String header = response.get(i);
						int idx = header.indexOf(':');
						String key = header.substring(0, idx).trim();
						String value = header.substring(idx + 1).trim();
						if (resp.containsKey(key))
							resp.put(key, resp.get(key) + ", " + value);
						else
							resp.put(key, value);
					}

					if (!"Upgrade".equalsIgnoreCase(resp.get(WebSocket.HEADER_CONNECTION)))
						throw new IllegalStateException(WebSocket.HEADER_CONNECTION + ": " + resp.get(WebSocket.HEADER_CONNECTION));

					if (!"websocket".equalsIgnoreCase(resp.get(WebSocket.HEADER_UPGRADE)))
						throw new IllegalStateException(WebSocket.HEADER_UPGRADE + ": " + resp.get(WebSocket.HEADER_UPGRADE));

					String wsAccept = resp.get(WebSocket.HEADER_SEC_WS_ACCEPT);
					if (!WebSocket.accept(this.header.get(WebSocket.HEADER_SEC_WS_KEY)).equals(wsAccept))
						throw new IllegalStateException(WebSocket.HEADER_SEC_WS_KEY + ": " + wsAccept);

					// TODO check extensions and protocols
					if (resp.containsKey(WebSocket.HEADER_SEC_WS_PROTOCOL))
						throw new IllegalStateException(resp.get(WebSocket.HEADER_SEC_WS_PROTOCOL + ": " + WebSocket.HEADER_SEC_WS_PROTOCOL));
					if (resp.containsKey(WebSocket.HEADER_SEC_WS_EXTENSION))
						throw new IllegalStateException(resp.get(WebSocket.HEADER_SEC_WS_EXTENSION + ": " + WebSocket.HEADER_SEC_WS_EXTENSION));

					this.reading = true;
					this.writing = true;
					status = WebSocket.STAT_CONNECTED;
					return true;
				default: return true;
			}
		}
		catch (Throwable t)
		{
			this.status = WebSocket.STAT_CLOSED;
			JavaVM.exception(t);
		}
		return false;
	}

	public boolean reading()
	{
		return this.reading;
	}

	public boolean writing()
	{
		return this.writing;
	}

	public void blocking(boolean block)
	{
		this.blocking = block;
		if (this.socket != null)
		{
			try
			{
				this.socket.configureBlocking(this.blocking);
			}
			catch (IOException e)
			{
				JavaVM.exception(e);
			}
		}
	}

	public int read(byte[] buf, int off, int len)
	{
		int retVal = 0;
		try
		{
			this.locking[WebSocket.READING].lock();
			if (!this.reading()) return -1;
			while (this.array.length() < len)
			{
				this.receive();
				if (this.blocking) break;
			}
			int read = this.array.length();
			if (read > len) read = len;
			this.array.get(buf, off, read);
			retVal = read;
		}
		catch (Throwable t)
		{
			this.close();
			JavaVM.exception(t);
		}
		finally
		{
			this.locking[WebSocket.READING].unlock();
		}
		return retVal;
	}

	public void write(byte[] buf, int off, int len)
	{
		try
		{
			this.WB = WebSocket.expand(this.WB, len + 16);
			this.locking[WebSocket.WRITING].lock();
			if (!this.writing()) return;
			this.WB.clear();
			this.WB.put((byte) (WebSocket.MASK_FIN | WebSocket.OPC_BINARY));
			this.WB.put((byte) (WebSocket.MASK_MSK | 127));
			this.WB.putLong(len);
			this.random.nextBytes(this.masking);
			byte[] payload = new byte[len];
			System.arraycopy(buf, off, payload, 0, len);
			WebSocket.masking(this.masking, payload);
			this.WB.put(this.masking, 0, 4);
			this.WB.put(payload);
			this.WB.flip();
			while (this.WB.hasRemaining())
				this.socket.write(this.WB);
		}
		catch (IOException e)
		{
			this.close();
			JavaVM.exception(e);
		}
		finally
		{
			this.locking[WebSocket.WRITING].unlock();
		}
	}

	public void reset()
	{
		this.reading = false;
		this.writing = false;
		if (this.socket != null)
		{
			try
			{
				this.socket.close();
			}
			catch (IOException ignored)
			{
			}
		}
		this.socket = null;
	}

	public void close()
	{
		// TODO Control close
		this.reset();
	}

	private void receive()
	{
		try
		{
			this.locking[WebSocket.READING].lock();
			do
			{
				switch (this.RS)
				{
					case WebSocket.RS_OVERED:
					{
						this.fin = false;
						this.opcode = 0;
						this.length = 0;
						this.masking[0] = 0;
						this.masking[1] = 0;
						this.masking[2] = 0;
						this.masking[3] = 0;
						this.masking[4] = 0;
						this.RB.clear();
						this.RB.limit(1);
						this.RS = WebSocket.RS_OPCODE;
					}
					case WebSocket.RS_OPCODE:
					{
						int read = WebSocket.transfer(this.socket, this.blocking, this.RB);
						if (read == -1)
							this.close();
						if (this.RB.hasRemaining()) break;
						this.RB.flip();
						int b1 = this.RB.get() & 0xFF;
						this.fin = (b1 & WebSocket.MASK_FIN) != 0;
						if ((b1 & WebSocket.MASK_RSV) != 0)
							throw new IllegalStateException("Reserved not zero " + ((b1 & WebSocket.MASK_RSV) >> 4));
						this.opcode = b1 & WebSocket.MASK_OPC;
						this.RB.clear();
						this.RB.limit(1);
						this.RS = WebSocket.RS_LENGTH;
					}
					case WebSocket.RS_LENGTH:
					{
						int read = WebSocket.transfer(this.socket, this.blocking, this.RB);
						if (read == -1)
							this.close();
						if (this.RB.hasRemaining()) break;
						this.RB.flip();
						if (this.RB.limit() == 1)
						{
							int b1 = this.RB.get() & 0xFF;
							if ((b1 & WebSocket.MASK_MSK) != 0) this.masking[4] = 1;
							this.length = b1 & WebSocket.MASK_LEN;
							if (this.length == 126)
							{
								this.length = 0;
								this.RB.clear();
								this.RB.limit(2);
								break;
							}
							else if (this.length == 127)
							{
								this.length = 0;
								this.RB.clear();
								this.RB.limit(8);
								break;
							}
						}
						else
						{
							if (this.RB.limit() == 2) this.length = this.RB.getShort() & 0xFFFF;
							else if (this.RB.limit() == 8) this.length = this.RB.getLong();
						}
						this.RB.clear();
						if (this.masking[4] != 0)
						{
							this.RB.limit(4);
							this.status = WebSocket.RS_MASKING;
						}
						else
						{
							this.RB = WebSocket.expand(this.RB, this.length);
							this.RB.limit((int) this.length);
							this.RS = WebSocket.RS_PAYLOAD;
							break;
						}
					}
					case WebSocket.RS_MASKING:
					{
						int read = WebSocket.transfer(this.socket, this.blocking, this.RB);
						if (read == -1)
							this.close();
						if (this.RB.hasRemaining()) break;
						this.RB.flip();
						this.RB.get(this.masking, 0, 4);
						this.RB = WebSocket.expand(this.RB, this.length);
						this.RB.limit((int) this.length);
						this.RS = WebSocket.RS_PAYLOAD;
					}
					case WebSocket.RS_PAYLOAD:
					{
						int read = WebSocket.transfer(this.socket, this.blocking, this.RB);
						if (read == -1)
							this.close();
						if (this.RB.hasRemaining()) break;
						this.RS = WebSocket.RS_OVERED;
						this.RB.flip();
						byte[] payload = new byte[this.RB.remaining()];
						this.RB.get(payload);
						if (this.masking[4] != 0)
						{
							WebSocket.masking(this.masking, payload);
						}
						switch (this.opcode)
						{
							case WebSocket.OPC_CONTINUE:
							case WebSocket.OPC_TEXT:
							case WebSocket.OPC_BINARY:
								this.array.put(payload);
								break;
							case WebSocket.OPC_CLOSE:
								this.reading = false;
								this.close();
								break;
							case WebSocket.OPC_PING:
								// send pong
								this.RB = WebSocket.expand(this.RB, payload.length + 16);
								this.random.nextBytes(this.masking);
								WebSocket.masking(this.masking, payload);
								this.RB.clear();
								this.RB.put((byte) (WebSocket.MASK_FIN | WebSocket.OPC_PING));
								this.RB.put((byte) (WebSocket.MASK_MSK | 127));
								this.RB.putLong(this.RB.remaining());
								this.RB.put(this.masking, 0, 4);
								this.RB.put(payload);
								this.RB.flip();
								this.locking[WebSocket.WRITING].lock();
								while (this.RB.hasRemaining())
									this.socket.write(this.RB);
								this.locking[WebSocket.WRITING].unlock();
								break;
							case WebSocket.OPC_PONG:
								break;
						}
						return;
					}
				}
			}
			while (this.blocking);
		}
		catch (Throwable e)
		{
			this.close();
			JavaVM.exception(e);
		}
		finally
		{
			this.locking[WebSocket.READING].unlock();
		}
	}

	private static void masking(byte[] masking, byte[] payload)
	{
		for (int i = 0; i < payload.length; i++)
		{
			payload[i] = ((byte) (payload[i] ^ masking[i & 3]));
		}
	}

	private static ByteBuffer expand(ByteBuffer buf, long limit)
	{
		int oldCap = buf.capacity();
		if (oldCap <= limit) return buf;
		while (oldCap < limit)
		{
			oldCap <<= 1;
		}
		return ByteBuffer.allocate(oldCap);
	}

	private static int transfer(SocketChannel socket, boolean blocking, ByteBuffer buffer) throws IOException
	{
		int read = 0;
		if (blocking)
		{
			while (buffer.hasRemaining() && read != -1)
			{
				read = socket.read(buffer);
			}
		}
		else
			read = socket.read(buffer);
		return read;
	}

	public static String key(Random random)
	{
		byte[] buffer = new byte[16];
		random.nextBytes(buffer);
		return Base64.getEncoder().encodeToString(buffer);
	}

	public static String accept(String key)
	{
		key += "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(key.getBytes(StandardCharsets.UTF_8));
			key = Base64.getEncoder().encodeToString(digest.digest());
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
		return key;
	}
}
