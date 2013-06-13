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
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {
	private Socket serverSocket;
	protected ConnectionHandler handler;
	private Semaphore sockSem;
	
	private AtomicInteger nrSockets;
	
	public MessageCenter() {
		this.serverSocket = null;
		
		// At first, the socket is not ready,
		// so there's no resource.
		this.sockSem = new Semaphore(0);
		
		this.nrSockets = new AtomicInteger(0);
	}
	
	public void connect(
			String address,
			int port,
			String service,
			String username,
			String token,
			RSAPublicKey pub,
			MessageHandler msgHandler) throws UnknownHostException, IOException, LoginException, InterruptedException {
		synchronized (this) {
			if (this.serverSocket != null) {
				this.serverSocket.close();
			}
			this.serverSocket = new Socket(address, port);
			ConnectionHandler handler = new ConnectionHandler(msgHandler, service, username, token, pub);
			handler.handshake(this.serverSocket.getInputStream(), this.serverSocket.getOutputStream());
			this.handler = handler;
			
			this.sockSem.release();
			this.nrSockets.set(1);
		}
	}
	
	protected synchronized void sendData(byte[] data) throws  IOException, InterruptedException {
		if(!this.sockSem.tryAcquire()) {
			throw new IOException("Not ready");
		}
		if (this.serverSocket == null) {
			this.sockSem.release();
			throw new IOException("Not ready");
		}
		try {
			this.serverSocket.getOutputStream().write(data);
		} catch (IOException e) {
			this.sockSem.release();
			throw e;
		}
		this.sockSem.release();
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
	
	public void setVisibility(boolean visible) throws IOException, InterruptedException {
		if (this.handler == null) {
			throw new IOException("Not ready");
		}
		byte[] data = this.handler.marshalSetVisibilityCommand(visible);
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
		try {
			this.sockSem.acquire();
		} catch (InterruptedException e1) {
			return;
		}
		
		try {
			istream = this.serverSocket.getInputStream();
		} catch (IOException e) {
			this.sockSem.release();
			this.handler.onError(e);
			return;
		}
		this.sockSem.release();
		
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
						this.sendData(r);
					} catch (IOException e) {
						this.handler.onError(e);
						break loop;
					} catch (InterruptedException e) {
						break loop;
					}	
				}
			}
		} while (true);
		this.stop();
	}
	
	public void stop() {
		synchronized (this) {

			if (this.nrSockets.get() > 0) {
				// There is no more socket resource.
				// call connect() to get one.
				this.sockSem.acquireUninterruptibly();
				this.handler.onCloseStart();
				try {
					if (this.serverSocket != null) {
						this.serverSocket.close();
					}
				} catch (IOException e) {
					// WTF. What do you want me to do?
				}
				this.serverSocket = null;
				this.handler.onClosed();
			}
			this.nrSockets.set(0);
		}
	}
		
}
