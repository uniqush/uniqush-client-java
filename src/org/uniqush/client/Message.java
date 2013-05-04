/*
 * Copyright 2013 Nan Deng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.uniqush.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class Message {
	private Map<String, String> header;
	private byte[] body;
	
	public Message() {
	}
	
	public Message(Map<String, String> header, byte[] body) {
		this.header = header;
		this.body = body;
	}
	
	public Map<String, String> getHeader() {
		return this.header;
	}
	
	public String get(String key) {
		return this.header.get(key);
	}
	
	public void put(String key, String value) {
		if (this.header == null) {
			this.header = new HashMap<String, String>(3);
		}
		this.header.put(key, value);
	}
	
	public void put(byte[] body) {
		this.body = body;
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
