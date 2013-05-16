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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {
	private Socket serverSocket;
	protected ConnectionHandler handler;
	private Semaphore writeLock;
	
	public MessageCenter() {
		this.serverSocket = null;
		this.writeLock = new Semaphore(1);
	}
	
	public void connect(
			String address,
			int port,
			String service,
			String username,
			String token,
			RSAPublicKey pub,
			MessageHandler msgHandler) throws UnknownHostException, IOException, LoginException, InterruptedException {
		this.writeLock.acquire();
		if (this.serverSocket != null) {
			this.serverSocket.close();
		}
		this.serverSocket = new Socket(address, port);
		ConnectionHandler handler = new ConnectionHandler(msgHandler, service, username, token, pub);
		handler.handshake(this.serverSocket.getInputStream(), this.serverSocket.getOutputStream());
		this.handler = handler;
		this.writeLock.release();
	}
	
	protected void sendData(byte[] data) throws IOException, InterruptedException {
		this.writeLock.acquire();
		if (this.serverSocket == null) {
			this.writeLock.release();
			throw new IOException("Not ready");
		}
		this.serverSocket.getOutputStream().write(data);
		this.writeLock.release();
	}
	
	public void sendMessageToUser(String service, String username, Message msg, int ttl) throws InterruptedException, IOException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte [] data = this.handler.marshalMessageToUser(service, username, msg, ttl);
		sendData(data);
	}
	
	public void sendMessageToServer(Message msg) throws InterruptedException, IOException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalMessageToServer(msg);
		sendData(data);
	}
	
	public void config(int digestThreshold, int compressThreshold, List<String> digestFields) throws IOException, InterruptedException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalConfigCommand(digestThreshold, compressThreshold, digestFields);
		sendData(data);
	}
	
	public void requestMessage(String id) throws InterruptedException, IOException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalRequestMessageCommand(id);
		sendData(data);
	}
	
	public void subscribe(Map<String, String> params) throws InterruptedException, IOException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalSubscriptionCommand(params, true);
		sendData(data);
	}
	
	public void unsubscribe(Map<String, String> params) throws InterruptedException, IOException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalSubscriptionCommand(params, false);
		sendData(data);
	}

	private int readFull(InputStream istream, byte[] buf, int length) {
		int n = 0;
		while (n < length) {
			try {
				int i = istream.read(buf, n, length - n);
				if (i < 0) {
					return n;
				}
				n += i;
			} catch (IOException e) {
				return n;
			}
		}
		return n;
	}
	
	@Override
	public void run() {
		InputStream istream = null;
		OutputStream ostream = null;
		
		try {
			istream = this.serverSocket.getInputStream();
			ostream = this.serverSocket.getOutputStream();
		} catch (IOException e) {
			this.handler.onError(e);
			return;
		}
		
loop:
		do {
			int len = this.handler.nextChunkSize();
			if (len <= 0) {
				break;
			}
			byte[] data = new byte[len];
			int n = readFull(istream, data, len);
			if (n != len) {
				break;
			}
			
			ArrayList<byte[]> reply = new ArrayList<byte[]>();
			this.handler.onData(data, reply);
			if (reply != null && reply.size() > 0) {
				Iterator<byte[]> iter = reply.iterator();
				while (iter.hasNext()) {
					byte[] r = iter.next();
					try {
						this.writeLock.acquire();
						ostream.write(r);
						this.writeLock.release();
					} catch (Exception e) {
						this.writeLock.release();
						this.handler.onError(e);
						break loop;
					}
				}
			}
		} while (true);
	}
	
	public void stop() {
		this.handler.onCloseStart();
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			// WTF. What do you want me to do?
		}
		this.serverSocket = null;
		this.handler.onClosed();
	}

		
}
