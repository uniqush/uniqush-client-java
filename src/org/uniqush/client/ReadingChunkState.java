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

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

public class ReadingChunkState extends State {
	
	private int size;
	private CommandMarshaler marshaler;
	
	public ReadingChunkState(MessageHandler handler, CommandMarshaler marshaler, String service, String username, int size) {
		super(handler, service, username);
		this.size = size;
		this.marshaler = marshaler;
	}

	@Override
	public int chunkSize() {
		return this.size;
	}
	
	protected State processCommand(Command cmd, List<byte[]> reply) throws StreamCorruptedException {
		Action action = null;
		System.out.println("Received command: " + cmd.getType());
		switch (cmd.getType()) {
		case Command.CMD_DATA:
			if (handler != null) {
				handler.onMessageFromServer(this.service, this.username, cmd.getParameter(0), cmd.getMessage());
			}
			break;
		case Command.CMD_FWD:
			String sender = cmd.getParameter(0);
			if (sender == null) {
				this.onError(new StreamCorruptedException("no sender in forward message"));
				return new ErrorState(this.handler, service, username);
			}
			String service = cmd.getParameter(1);
			if (service == null) {
				service = this.service;
			}
			String id = cmd.getParameter(2);
			if (this.handler != null) {
				this.handler.onMessageFromUser(this.service, this.username, service, sender, id, cmd.getMessage());
			}
			break;
		case Command.CMD_DIGEST:
			if (cmd.nrParameters() < 2) {
				throw new StreamCorruptedException("bad server implementation: too little parameters for digest");
			}
			String szStr = cmd.getParameter(0);
			int size = Integer.parseInt(szStr);
			String msgId = cmd.getParameter(1);
			if (msgId == null || msgId.length() <= 0) {
				throw new StreamCorruptedException("bad server implementation: invalid msgId");
			}
			Message msg = cmd.getMessage();
			Map<String, String> info = null;
			if (msg != null) {
				info = msg.getHeader();
			}
			sender = cmd.getParameter(2);
			if (sender == null) {
				this.handler.onMessageDigestFromServer(true, this.service, this.username, size, msgId, info);
			} else {
				service = cmd.getParameter(3);
				if (service == null) {
					service = this.service;
				}
				this.handler.onMessageDigestFromUser(true, this.service, this.username, service, sender, size, msgId, info);
			}
			break;
		case Command.CMD_REDIRECT:
			
			System.out.println("Received redirect command");
			if (cmd.nrParameters() <= 0) {
				// This is a bad command. Ignore it.
				break;
			}
			
			// randomly choose a server to connect;
			Random r = new Random();
			int idx = r.nextInt(cmd.nrParameters());
			action = new ReconnectAction(cmd.getParameter(idx));
			break;
		case Command.CMD_BYE:
			action = new CloseAction();
			break;
		}
		State ret = new ReadingChunkSizeState(this.handler, this.marshaler, service, service);
		ret.setAction(action);
		return ret;
	}
	
	@Override
	public State transit(byte[] data, List<byte[]> reply) {
		reply.clear();
		if (data == null || data.length != this.size) {
			this.onError(new StreamCorruptedException("No enough data"));
			return new ErrorState(this.handler, service, username);
		}
		try {
			Command cmd = marshaler.unmarshalCommand(data);
			return processCommand(cmd, reply);
		} catch (StreamCorruptedException e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		} catch (ShortBufferException e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		} catch (IllegalBlockSizeException e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		} catch (BadPaddingException e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		} catch (IOException e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		}
	}
}
