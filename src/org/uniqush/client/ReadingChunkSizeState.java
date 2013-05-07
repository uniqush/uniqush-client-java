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

import java.io.StreamCorruptedException;
import java.util.List;

class ReadingChunkSizeState extends State {
	private CommandMarshaler marshaler;
	
	public ReadingChunkSizeState(MessageHandler handler, CommandMarshaler marshaler, String service, String username) {
		super(handler, service, username);
		this.marshaler = marshaler;
	}

	@Override
	public int chunkSize() {
		return marshaler.prefixLength();
	}
	
	@Override
	public State transit(byte[] data, List<byte[]> reply) {
		reply.clear();
		if (data == null || data.length != 2) {
			this.onError(new StreamCorruptedException("No enough data"));
			return new ErrorState(this.handler, service, username);
		}
		
		int length = this.marshaler.chunkSize(data);
		return new ReadingChunkState(this.handler, this.marshaler, service, username, length);
	}
}
