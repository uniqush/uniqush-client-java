/*
 * Copyright 2012 Nan Deng
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
