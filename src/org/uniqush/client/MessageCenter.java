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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {
	
	private CredentialProvider credentialProvider;
	private Socket serverSocket;
	private Semaphore sockSem;

	private ConnectionHandler handler;
	private ReadWriteLock connHandlerLock;
	
	private AtomicInteger nrSockets;
	
	public MessageCenter(CredentialProvider cp) {
		this.serverSocket = null;
		
		// At first, the socket is not ready,
		// so there's no resource.
		this.sockSem = new Semaphore(0);
		this.connHandlerLock = new ReentrantReadWriteLock();
		this.connHandlerLock.writeLock().lock();
		this.nrSockets = new AtomicInteger(0);
		this.credentialProvider = cp;
	}
	
	public void connect(
			String address,
			int port,
			String service,
			String username,
			MessageHandler msgHandler) throws UnknownHostException, IOException, LoginException, InterruptedException {
		synchronized (this) {
			if (this.serverSocket != null) {
				this.serverSocket.close();
			}
			this.serverSocket = new Socket(address, port);
			ConnectionHandler handler = new ConnectionHandler(msgHandler, address, port, service, username, this.credentialProvider);
			handler.handshake(this.serverSocket.getInputStream(), this.serverSocket.getOutputStream());
			this.handler = handler;
			
			this.sockSem.release();
			this.nrSockets.set(1);
			this.connHandlerLock.writeLock().unlock();
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
	
	interface DataMarshaler {
		public byte[] marshal() throws InterruptedException, IOException;
	}
	
	protected void marshalThenSend(DataMarshaler m)  throws InterruptedException, IOException {
		this.connHandlerLock.readLock().lock();
		if (this.handler == null) {
			this.connHandlerLock.readLock().unlock();
			throw new IOException("Not ready");
		}
		byte [] data = m.marshal();
		this.connHandlerLock.readLock().unlock();
		sendData(data);
	}
	
	public void sendMessageToUser(final String service, final String username, final Message msg, final int ttl) throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalMessageToUser(service, username, msg, ttl);
			}
		});
	}
	
	public void sendMessageToServer(final Message msg) throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalMessageToServer(msg);
			}
		});
	}
	
	public void requestAllSince(final Date since) throws IOException, InterruptedException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalRequestAllSince(since);
			}
		});
	}
	
	public void config(final int digestThreshold, final int compressThreshold, final List<String> digestFields) throws IOException, InterruptedException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalConfigCommand(digestThreshold, compressThreshold, digestFields);
			}
		});
	}
	
	public void requestMessage(final String id) throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalRequestMessageCommand(id);
			}
		});
	}
	
	public void subscribe(final Map<String, String> params) throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalSubscriptionCommand(params, true);
			}
		});
	}
	
	public void unsubscribe(final Map<String, String> params) throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalSubscriptionCommand(params, false);
			}
		});
	}
	
	public void setVisibility(final boolean visible) throws IOException, InterruptedException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalSetVisibilityCommand(visible);
			}
		});
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
				this.connHandlerLock.writeLock().lock();
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
