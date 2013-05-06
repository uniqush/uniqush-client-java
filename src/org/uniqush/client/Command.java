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
import java.util.Map;
import java.util.Map.Entry;

class Command {

	public final static int CMDFLAG_COMPRESS = 1;
	
	// Params:
	// 0. [optional] The Id of the message
	public final static int CMD_DATA = 0;

	// Params:
	// 0. [optional] The Id of the message
	public final static int CMD_EMPTY = 1;

	// Sent from client.
	//
	// Params
	// 0. service name
	// 1. username
	public final static int CMD_AUTH = 2;

	public final static int CMD_AUTHOK = 3;
	public final static int CMD_BYE = 4;

	// Sent from client.
	// Telling the server about its preference.
	//
	// Params:
	// 0. Digest threshold: -1 always send message directly; Empty: not change
	// 1. Compression threshold: -1 always compress the data; Empty: not change
	// >2. [optional] Digest fields
	public final static int CMD_SETTING = 5;

	// Sent from server.
	// Telling the client an
	// arrival of a message.
	//
	// Params:
	// 0. Size of the message
	// 1. The id of the message
	//
	// Message.Header:
	// Other digest info
	public final static int CMD_DIGEST = 6;

	// Sent from client.
	// Telling the server which cached
	// message it wants to retrieve.
	//
	// Params:
	// 0. The message id
	public final static int CMD_MSG_RETRIEVE = 7;

	// Sent from client.
	// Telling the server to forward a
	// message to another user.
	//
	// Params:
	// 0. TTL
	// 1. Reciever's name
	// 2. [optional] Reciever's service name.
	//	    If empty, then same service as the client
	public final static int CMD_FWD_REQ = 8;

	// Sent from server.
	// Telling the client the massage
	// is originally from another user.
	//
	// Params:
	// 0. Sender's name
	// 1. [optional] Sender's service name.
	//	    If empty, then same service as the client
	// 2. [optional] The Id of the message in the cache.
	public final static int CMD_FWD = 9;

	// Sent from client.
	//
	// Params:
	// 0. 1: visible; 0: invisible;
	//
	// If a client if invisible to the server,
	// then sending any message to this client will
	// not be considered as a message.
	//
	// Well... Imagine a scenario:
	//
	// Alice has two devices.
	//
	// If the app on any device is online, then any message
	// will be delivered to the device and no notification
	// will be pushed to other devices.
	//
	// However, if the app is "invisible" to the server,
	// then it will be considered as off online even if
	// there is a connection between the server and the client.
	//
	// (It is only considered as off line when we want to know if
	// we should push a notification. But it counts for other purpose,
	// say, number of connections under the user.)
	public final static int CMD_SET_VISIBILITY = 10;

	// Sent from client
	//
	// Params:
	//   0. "1" (as ASCII character, not integer) means subscribe;
	//      "0" means unsubscribe. No change on others.
	// Message:
	//   Header: parameters
	public final static int CMD_SUBSCRIPTION = 11;

	public final static int CMD_NR_CMDS = 12;
	
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
		Map<String, String> header = null;
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
