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

import java.util.ArrayList;

abstract class State {
	protected MessageHandler handler;
	protected String service;
	protected String username;
	
	public State(MessageHandler handler, String service, String username) {
		this.handler = handler;
		this.service = service;
		this.username = username;
	}
	
	abstract public int chunkSize();
	abstract public State transit(byte[] data, ArrayList<byte[]> reply);

	public void onError(Exception e) {
		if (this.handler != null) {
			this.handler.onError(e);
		}
	}

	public void onCloseStart() {
		if (this.handler != null) {
			this.handler.onCloseStart();
		}
	}

	public void onClosed() {
		if (this.handler != null) {
			this.handler.onClosed();
		}
	}
}
