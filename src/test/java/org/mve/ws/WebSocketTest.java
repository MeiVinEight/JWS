package org.mve.ws;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.opentest4j.AssertionFailedError;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;

public class WebSocketTest
{
	@Test
	public void test0()
	{
		// Check constructor
		WebSocket ws1 = new WebSocket("ws://127.0.0.1");
		WebSocket ws2 = new WebSocket("ws://127.0.0.1/ws");
		WebSocket ws3 = new WebSocket("ws://127.0.0.1:80");
		WebSocket ws4 = new WebSocket("ws://127.0.0.1:80/ws");
		ws1.close();
		ws2.close();
		ws3.close();
		ws4.close();
	}

	@Test
	public void test1()
	{
		WebSocket ws = new WebSocket("ws://broadcastlv.chat.bilibili.com:2244/sub");
		while (!ws.finish())
		{
			Thread.yield();
		}
		ws.close();
	}

	@Test
	public void test2()
	{
	}

	@Test
	public void test3() throws Throwable
	{
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress("0.0.0.0", 2244));
		server.configureBlocking(false);
		WebSocket ws = new WebSocket("ws://127.0.0.1:2244");
		boolean finish = ws.finish(5000);
		server.close();
		Assertions.assertFalse(finish);
	}

	@Test
	public void test4() throws Throwable
	{
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress("0.0.0.0", 2244));
		server.configureBlocking(false);
		WebSocket ws = new WebSocket("ws://127.0.0.1:2244");
		Assertions.assertThrowsExactly(AssertionFailedError.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> ws.finish()));
		server.close();
	}

	@Test
	public void test5() throws Throwable
	{
		// H4zkKfpnizOa7DAdcolAeQ==
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress("0.0.0.0", 2244));
		server.configureBlocking(false);
		WebSocket ws = new WebSocket("ws://127.0.0.1:2244");
		ws.header(WebSocket.HEADER_SEC_WS_KEY, "H4zkKfpnizOa7DAdcolAeQ==");
		ws.blocking(false);
		boolean finish = ws.finish();
		Assertions.assertFalse(finish);
		SocketChannel client = server.accept();
		Assertions.assertNotNull(client);
		String str = "HTTP/1.1 101 Switching Protocols\r\n" +
			"Upgrade: websocket\r\n" +
			"Connection: Upgrade\r\n" +
			"Sec-WebSocket-Accept: +iT4jCD8ClKUZWP3snmTS9I+4Vw=\r\n\r\n";
		ByteBuffer buf = ByteBuffer.wrap(str.getBytes());
		while (buf.hasRemaining())
			client.write(buf);
		ws.blocking(true);
		finish = Assertions.assertTimeout(Duration.ofSeconds(2), (ThrowingSupplier<Boolean>) ws::finish);
		Assertions.assertTrue(finish);
		client.close();
		ws.reset();
		server.close();
	}

	@Test
	public void test6() throws Throwable
	{
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress("0.0.0.0", 2244));
		server.configureBlocking(false);
		WebSocket ws = new WebSocket("ws://127.0.0.1:2244");
		ws.header(WebSocket.HEADER_SEC_WS_KEY, "H4zkKfpnizOa7DAdcolAeQ==");
		Assertions.assertTrue(ws.blocking());
		ws.blocking(false);
		Assertions.assertFalse(ws.blocking());
		boolean finish = ws.finish();
		Assertions.assertFalse(finish);
		SocketChannel client = server.accept();
		Assertions.assertNotNull(client);
		ByteBuffer buf = ByteBuffer.allocate(1);
		int a = 0;
		int b = 0;
		int c = 0;
		int d = 0;
		while (a != '\r' || b != '\n' || c != '\r' || d != '\n')
		{
			a = b;
			b = c;
			c = d;
			buf.clear();
			while (buf.hasRemaining())
				client.read(buf);
			buf.flip();
			d = buf.get();
		}
		String str = "HTTP/1.1 101 Switching Protocols\r\n" +
			"Upgrade: websocket\r\n" +
			"Connection: Upgrade\r\n" +
			"Sec-WebSocket-Accept: +iT4jCD8ClKUZWP3snmTS9I+4Vw=\r\n\r\n";
		buf = ByteBuffer.wrap(str.getBytes());
		while (buf.hasRemaining())
			client.write(buf);
		ws.blocking(true);
		Assertions.assertTrue(ws.blocking());
		finish = Assertions.assertTimeout(Duration.ofSeconds(2), (ThrowingSupplier<Boolean>) ws::finish);
		Assertions.assertTrue(finish);
		Assertions.assertThrows(AssertionFailedError.class, () -> Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ws.close(3000)));
		Assertions.assertEquals(WebSocket.STAT_CLOSING, ws.status());
		Assertions.assertFalse(ws.writing());
		Assertions.assertTrue(ws.reading());
		buf.clear();
		buf.limit(8);
		while (buf.hasRemaining())
			client.read(buf);
		buf.clear();
		buf.put((byte) (WebSocket.MASK_FIN | WebSocket.OPC_CLOSE));
		buf.put((byte) 2);
		buf.putShort((short) 1000);
		buf.flip();
		while (buf.hasRemaining())
			client.write(buf);
		Assertions.assertTimeout(Duration.ofSeconds(2), () -> ws.close());
		Assertions.assertEquals(WebSocket.STAT_CLOSED, ws.status());
		buf.clear();
		Assertions.assertEquals(-1, client.read(buf));
		client.close();
		server.close();
	}
}
