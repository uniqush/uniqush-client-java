package org.uniqush.client;

import java.util.HashMap;

public class Message {
	private HashMap<String, String> header;
	private byte[] body;
	
	
	public Message(HashMap<String, String> header, byte[] body) {
		this.header = header;
		this.body = body;
	}
	
	public HashMap<String, String> getHeader() {
		return this.header;
	}
	
	public String get(String key) {
		return this.header.get(key);
	}
	
	public byte[] getBody() {
		return this.body;
	}

}
