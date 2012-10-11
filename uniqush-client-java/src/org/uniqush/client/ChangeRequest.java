package org.uniqush.client;

import java.nio.channels.SocketChannel;

class ChangeRequest {
	public static final int REGISTER = 1;
	public static final int CHANGEOPS = 2;
	
	public SocketChannel socket;
	public int type;
	public int ops;
	public String name;
	public ChangeRequest(SocketChannel socket, int type, int ops) {
		this.socket = socket;
		this.type = type;
		this.ops = ops;
		
		this.name = this.socket.socket().getRemoteSocketAddress().toString();
	}
	public ChangeRequest(SocketChannel socket, int type, int ops, String name) {
		this.socket = socket;
		this.type = type;
		this.ops = ops;
		
		this.name = name;
	}
}
