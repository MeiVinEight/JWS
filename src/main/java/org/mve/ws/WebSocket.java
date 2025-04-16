package org.mve.ws;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

public class WebSocket
{
	private final boolean secure;
	private final String host;
	private final int port;
	private final String uri;
	private final Map<String, String> header = new HashMap<>();

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
			this.uri = uri;
		}
		catch (MalformedURLException e)
		{
			throw new IllegalArgumentException(e);
		}
	}
}
