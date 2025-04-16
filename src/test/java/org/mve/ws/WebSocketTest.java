package org.mve.ws;

import org.junit.jupiter.api.Test;

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
}
