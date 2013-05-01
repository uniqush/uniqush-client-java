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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

class Command {
	private byte type;
	private ArrayList<String> params;
	private Message msg;
	private final Charset UTF_8 = Charset.forName("UTF-8");
	
	public boolean equals(Command cmd) {
		if (cmd.type != this.type) {
			return false;
		}
		if (cmd.params != null && this.params != null) {
			if (!cmd.params.equals(this.params)) {
				return false;
			}
		} else {
			return false;
		}
		if (cmd.msg != null && this.msg != null) {
			return cmd.msg.equals(this.msg);
		} else if (cmd.msg == null && this.msg == null) {
			return true;
		} else if (cmd.msg == null) {
			return this.msg.isEmpty();
		} else if (this.msg == null) {
			return cmd.msg.isEmpty();
		}
		return true;
	}
	
	public Message getMessage() {
		return this.msg;
	}
	
	public Command(int type, Message msg) {
		this.type = (byte) type;
		this.msg = msg;
	}
	
	public String getParameter(int i) {
		if (this.params == null) {
			return null;
		}
		if (i >= this.params.size()) {
			return null;
		}
		return this.params.get(i);
	}
	
	public int getType() {
		return (int)this.type;
	}
	
	public void AppendParameter(String p) {
		if (this.params == null) {
			this.params = new ArrayList<String>(4);
		}
		this.params.add(p);
	}
	
	private int cutString(byte[] data, int start) {
		for (int i = start; i < data.length; i++) {
			if (data[i] == 0) {
				return i;
			}
		}
		return data.length;
	}
	
	public int nrParameters() {
		if (this.params == null) {
			return 0;
		}
		return this.params.size();
	}
	
	public byte[] marshal() {
		HashMap<String, String> header = null;
		byte[] body = null;
		
		int nrBytes = 4;
		int nrEntities = 0;
		
		if (this.params != null) {
			nrEntities += this.params.size();
		}
		
		if (this.msg != null) {
			header = this.msg.getHeader();
			if (header != null) {
				nrEntities += header.size() * 2;
			}
			body = this.msg.getBody();
			if (body != null) {
				nrBytes += body.length;
			}
		}
		
		ArrayList<byte[]> list = null;
		if (nrEntities > 0) {
			list = new ArrayList<byte[]>(nrEntities);

			if (this.params != null) {
				Iterator<String> iter = this.params.iterator();
				while(iter.hasNext()) {
					String param = iter.next();
					byte[] p = param.getBytes(UTF_8);
					nrBytes += p.length + 1;
					list.add(p);
				}
			}
			if (header != null) {
				Iterator<Entry<String, String>> iter = header.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<String, String> entry = iter.next();
					String key = entry.getKey();
					String value = entry.getValue();
					
					byte[] keyb = key.getBytes(UTF_8);
					byte[] valueb = value.getBytes(UTF_8);
					
					nrBytes += keyb.length + 1;
					nrBytes += valueb.length + 1;
					
					list.add(keyb);
					list.add(valueb);
				}
			}
		}
		
		byte[] ret = new byte[nrBytes];
		
		ret[0] = this.type;
		if (this.params != null) {
			ret[1] = (byte) (0x0000000F & this.params.size());
			ret[1] = (byte) (ret[1] << 4);
		}
		
		if (header != null) {
			int n = header.size();
			ret[2] = (byte)((0x0000FF00 & n) >> 8);
			ret[3] = (byte) (0x000000FF & n);
		}
		int start = 4;
		
		if (list != null) {
			Iterator<byte[]> iter = list.iterator();
			while (iter.hasNext()) {
				byte[] data = iter.next();
				System.arraycopy(data, 0, ret, start, data.length);
				start += data.length;
				ret[start] = 0;
				start++;
			}
		}
		if (body != null) {
			System.arraycopy(body, 0, ret, start, body.length);
		}
		return ret;
	}

	public Command(byte[] data) {
		if (data.length < 4) {
			return;
		}
		type = data[0];
		int nrParams = data[1] >> 4;
		int nrHeaders = data[2];
		nrHeaders = nrHeaders << 8;
		nrHeaders |= data[3];
		
		int start = 4;
		
		if (nrParams > 0) {
			this.params = new ArrayList<String>(nrParams);
			
			for (int i = 0; i < nrParams; i++) {
				int end = cutString(data, start);
				int length = end - start;
				if (length < 0 || length > data.length - start) {
					break;
				}
				String p = new String(data, start, length, UTF_8);
				this.params.add(p);
				start = end + 1;
			}
		}
		HashMap<String, String> header = null;
		byte[] body = null;
		if (nrHeaders > 0) {
			header = new HashMap<String,String>(nrHeaders);
			
			for (int i = 0; i < nrHeaders; i++) {
				int end = cutString(data, start);
				int length = end - start;
				if (length < 0 || length > data.length - start) {
					break;
				}
				String key = new String(data, start, length, UTF_8);
				start = end + 1;
				end = cutString(data, start);
				length = end - start;
				if (length < 0 || length > data.length - start) {
					break;
				}
				String value = new String(data, start, length, UTF_8);
				start = end + 1;
				header.put(key, value);
			}
		}
		if (start < data.length) {
			body = new byte[data.length - start];
			System.arraycopy(data, start, body, 0, data.length - start);
		}
		if (header != null || body != null) {
			this.msg = new Message(header, body);
		}
	}
}
