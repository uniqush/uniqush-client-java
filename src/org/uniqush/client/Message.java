package org.uniqush.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

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
	
	public boolean isEmpty() {
		if (this.header != null) {
			if (this.header.size() > 0) {
				return false;
			}
		}
		if (this.body != null) {
			if (this.body.length > 0) {
				return false;
			}
		}
		return true;
	}
	
	public boolean equals(Message msg) {
		if (msg.header == null && this.header != null) {
			return false;
		} else if (msg.header != null && this.header == null) {
			return false;
		} else if (msg.header != null && this.header != null) {
			if (msg.header.size() != this.header.size()) {
				return false;
			}
			Iterator<Entry<String, String>> iter = this.header.entrySet().iterator();
			
			while (iter.hasNext()) {
				Entry<String, String> entry = iter.next();
				String key = entry.getKey();
				String value = entry.getValue();
				
				if (!value.equals(msg.header.get(key))) {
					return false;
				}
			}
		}
		
		if (msg.body == null && this.body != null) {
			return false;
		} else if (msg.body != null && this.body == null) {
			return false;
		} else if (msg.body != null && this.body != null) {
			if (!msg.body.equals(this.body)) {
				return false;
			}
		}
		return true;
	}

}
