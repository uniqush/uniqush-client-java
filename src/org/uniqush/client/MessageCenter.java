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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {

	private CredentialProvider credentialProvider;
	
	// serverSocket and currentConnHandlerId are guarded by sockLock
	private Socket serverSocket;
	private int currentConnHandlerId;
	private Lock sockLock;

	private ConnectionHandler handler;
	private ReadWriteLock connHandlerLock;

	public MessageCenter(CredentialProvider cp) {
		this.serverSocket = null;

		this.sockLock = new ReentrantLock();
		this.sockLock.lock();
		this.currentConnHandlerId = -1;
		this.connHandlerLock = new ReentrantReadWriteLock();
		this.connHandlerLock.writeLock().lock();
		this.credentialProvider = cp;
	}

	public void connect(String address, int port, String service,
			String username, MessageHandler msgHandler)
			throws UnknownHostException, IOException, LoginException,
			InterruptedException {
		synchronized (this) {
					
			if (this.serverSocket != null) {
				this.serverSocket.close();
			}
			this.serverSocket = new Socket(address, port);
			ConnectionHandler handler = new ConnectionHandler(msgHandler,
					address, port, service, username, this.credentialProvider);
			handler.handshake(this.serverSocket.getInputStream(),
					this.serverSocket.getOutputStream());
			this.handler = handler;
			this.currentConnHandlerId = handler.getId();

			this.sockLock.unlock();
			this.connHandlerLock.writeLock().unlock();
		}
	}

	protected synchronized void sendData(byte[] data, int connHandlerId) throws IOException,
			InterruptedException {
		this.sockLock.lock();
		if (this.serverSocket == null) {
			this.sockLock.unlock();
			throw new IOException("Not ready");
		}
		if (connHandlerId != this.currentConnHandlerId) {
			this.sockLock.unlock();
			throw new EOFException("connection handler id mismatch");
		}
		try {
			this.serverSocket.getOutputStream().write(data);
		} catch (IOException e) {
			this.sockLock.unlock();
			throw e;
		}
		this.sockLock.unlock();
	}

	interface DataMarshaler {
		public byte[] marshal() throws InterruptedException, IOException;
	}

	protected void marshalThenSend(DataMarshaler m)
			throws InterruptedException, IOException {
		int connHandlerId = -1;
		this.connHandlerLock.readLock().lock();
		if (this.handler == null) {
			this.connHandlerLock.readLock().unlock();
			throw new IOException("Not ready");
		}
		byte[] data = m.marshal();
		connHandlerId = this.handler.getId();
		this.connHandlerLock.readLock().unlock();
		sendData(data, connHandlerId);
	}

	public void sendMessageToUser(final String service, final String username,
			final Message msg, final int ttl) throws InterruptedException,
			IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler
						.marshalMessageToUser(service, username, msg, ttl);
			}
		});
	}

	public void sendMessageToServer(final Message msg)
			throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalMessageToServer(msg);
			}
		});
	}

	public void requestAllSince(final Date since) throws IOException,
			InterruptedException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalRequestAllSince(since);
			}
		});
	}

	public void config(final int digestThreshold, final int compressThreshold,
			final List<String> digestFields) throws IOException,
			InterruptedException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalConfigCommand(digestThreshold,
						compressThreshold, digestFields);
			}
		});
	}

	public void requestMessage(final String id) throws InterruptedException,
			IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalRequestMessageCommand(id);
			}
		});
	}

	public void subscribe(final Map<String, String> params)
			throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalSubscriptionCommand(params, true);
			}
		});
	}

	public void unsubscribe(final Map<String, String> params)
			throws InterruptedException, IOException {
		marshalThenSend(new DataMarshaler() {
			public byte[] marshal() throws InterruptedException, IOException {
				return handler.marshalSubscriptionCommand(params, false);
			}
		});
	}

	public void setVisibility(final boolean visible) throws IOException,
			InterruptedException {
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
		this.sockLock.lock();

		try {
			istream = this.serverSocket.getInputStream();
		} catch (IOException e) {
			this.sockLock.unlock();
			this.handler.onError(e);
			return;
		}
		this.sockLock.unlock();

		loop: do {
			// we don't need to lock the handler,
			// because the current thread is the owner of the handler.
			// There will be no other thread which changes the handler.
			int len = this.handler.nextChunkSize();
			if (len <= 0) {
				break;
			}

			// XXX GC is not free.
			byte[] data = new byte[len];
			int n = readFull(istream, data, len);
			if (n != len) {
				break;
			}

			ArrayList<byte[]> reply = new ArrayList<byte[]>();
			int hid = this.handler.getId();
			Action action = this.handler.onData(data, reply);
			if (reply != null && reply.size() > 0) {
				Iterator<byte[]> iter = reply.iterator();
				while (iter.hasNext()) {
					byte[] r = iter.next();
					try {
						this.sendData(r, hid);
					} catch (IOException e) {
						this.handler.onError(e);
						break loop;
					} catch (InterruptedException e) {
						break loop;
					}
				}
			}

			if (action == null) {
				continue;
			}
			switch (action.type()) {
			case Action.ACTION_RECONNECT:
				ReconnectAction ra = (ReconnectAction) action;
				String host = ra.getHost();
				int port = ra.getPort();
				if (host == null || port <= 0 || host.length() <= 0) {
					break;
				}
				MessageHandler mhandler = this.handler.getHandler();
				String service = this.handler.getService();
				String username = this.handler.getUsername();

				this.connHandlerLock.writeLock().lock();
				this.sockLock.lock();
				try {
					this.serverSocket.close();
				} catch (IOException e1) {
					// error on close?
				}
				try {
					this.connect(host, port, service, username, mhandler);
				} catch (Exception e) {
					mhandler.onError(e);
					break loop;
				}
				break;
			case Action.ACTION_CLOSE:
				break loop;
			}

		} while (true);
		this.stop();
	}

	public void stop() {
		synchronized (this) {
			this.connHandlerLock.writeLock().lock();
			this.sockLock.lock();
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
	}

}
