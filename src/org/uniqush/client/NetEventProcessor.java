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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Map.Entry;

public class NetEventProcessor implements Runnable {
	private class ConnectionWriter implements Writer {
		
		private String name;
		private NetEventProcessor proc;

		public ConnectionWriter(NetEventProcessor proc, String name) {
			this.name = name;
			this.proc = proc;
		}
		
		@Override
		public void write(byte[] buf) {
			this.proc.send(this.name, buf);
		}
	}
	
	private class Connection {
		public SocketChannel sock;
		public String name;
		
		private List<ByteBuffer> pendingData = new LinkedList<ByteBuffer>();
		public Connection(SocketChannel sock, String name) {
			this.sock = sock;
			this.name = name;
		}
		
		public void write(byte[] buf) {
			synchronized (this.pendingData) {
				ByteBuffer buffer = ByteBuffer.wrap(buf);
				this.pendingData.add(buffer);
			}
		}
		
		public boolean isEmpty() {
			return pendingData.isEmpty();
		}
		
		public void flush() throws IOException {
			synchronized (this.pendingData) {
				while (!this.pendingData.isEmpty()) {
					ByteBuffer buf = this.pendingData.get(0);
					while (buf.hasRemaining()) {
						this.sock.write(buf);
					}
					this.pendingData.remove(0);
				}
			}
		}
	}

	private class ChangeRequest {
		public static final int REGISTER = 1;
		public static final int CHANGEOPS = 2;
		public static final int STOP = 3;

		public SocketChannel socket;
		public int type;
		public int ops;
		public String name;
		public ReadEventHandler handler;

		public ChangeRequest(SocketChannel socket, int type, int ops, String name) {
			this.socket = socket;
			this.type = type;
			this.ops = ops;
			if (name.length() == 0) {
				this.socket.socket().getRemoteSocketAddress().toString();
			}
			this.name = name;
		}
		
		public ChangeRequest(SocketChannel socket, int type, int ops, String name, ReadEventHandler handler) {
			this.socket = socket;
			this.type = type;
			this.ops = ops;
			this.handler = handler;
			if (name.length() == 0) {
				this.socket.socket().getRemoteSocketAddress().toString();
			}
			this.name = name;
		}
	}

	private Selector selector;
	private List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();

	private Map<String, ReadEventHandler> readHandlers = new HashMap<String, ReadEventHandler>();
	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
	private Map<SocketChannel, Connection> conns = new HashMap<SocketChannel, Connection>();
	private Map<String, Connection> connNameMap = new HashMap<String, Connection>();
	
	public NetEventProcessor() throws IOException {
		selector = SelectorProvider.provider().openSelector();
	}
	
	public void send(String name, byte[] data) {
		Connection conn = null;
		synchronized (this.connNameMap) {
			conn = this.connNameMap.get(name);
		}
		
		if (conn == null) {
			return;
		}
		conn.write(data);
		synchronized (this.pendingChanges) {
			ChangeRequest change = new ChangeRequest(conn.sock, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE, name);
			this.pendingChanges.add(change);
		}
		
		this.selector.wakeup();
	}
	
	public void run() {
		System.out.println("Run the thread");
		while (true) {
			try {
				boolean stop = false;
				synchronized(this.pendingChanges) {
					Iterator<ChangeRequest> changes = this.pendingChanges.iterator();
					while (changes.hasNext()) {
						ChangeRequest change = changes.next();
						switch(change.type) {
						case ChangeRequest.CHANGEOPS:
							SelectionKey key = change.socket.keyFor(this.selector);
							key.interestOps(change.ops);
							break;
						case ChangeRequest.REGISTER:
							change.socket.register(this.selector, change.ops);
							Connection conn = new Connection(change.socket, change.name);
							synchronized (this.conns) { 
								this.conns.put(change.socket, conn);
							}
							
							synchronized (this.connNameMap) {
								this.connNameMap.put(change.name, conn);
							}
							if (change.handler != null) {
								synchronized (this.readHandlers) {
									this.readHandlers.put(change.name, change.handler);
								}
							}
							
							synchronized (change) {
								change.notifyAll();
							}
							break;
						case ChangeRequest.STOP:
							stop = true;
						}
					}
					this.pendingChanges.clear();
				}
				if (stop) {
					break;
				}
				
				this.selector.select();

				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey key = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();
					
					if (!key.isValid())
						continue;
					
					if (key.isReadable()) {
						this.read(key);
					} else if (key.isWritable()) {
						this.write(key);
					} else if (key.isConnectable()) {
						SocketChannel socketChannel = (SocketChannel) key.channel();
						Connection conn = this.conns.get(socketChannel);
						ReadEventHandler handler = this.readHandlers.get(conn.name);
						try {
							socketChannel.finishConnect();
						} catch (IOException e){
							key.cancel();
							if (handler != null) {
								handler.onConnectionFail();
							}
						}
						key.interestOps(SelectionKey.OP_READ);
						Writer writer = new ConnectionWriter(this, conn.name);
						handler.setWriter(writer);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
		
		synchronized (this.conns) {
			Iterator<Entry<SocketChannel, Connection>> it = this.conns.entrySet().iterator();
			while (it.hasNext()) {
				Entry<SocketChannel, Connection> pairs = it.next();
				SocketChannel sock = pairs.getKey();
				Connection conn = pairs.getValue();
				ReadEventHandler handler = null;
				synchronized (this.readHandlers) {
					handler = this.readHandlers.get(conn.name);
				}
				if (handler != null) {
					handler.onCloseStart();
				}
				try {
					sock.close();
				} catch (IOException e) {
				}
				
				if (handler != null) {
					handler.onClosed();
				}
			}
		}
	}
	
	public void stop() {
		ChangeRequest change = new ChangeRequest(null, ChangeRequest.STOP, 0, "", null);
		synchronized (this.pendingChanges) {
			this.pendingChanges.add(change);
		}
	}
	
	private void write(SelectionKey key) {
		SocketChannel sockChann = (SocketChannel) key.channel();
		Connection conn = this.conns.get(sockChann);
		try {
			conn.flush();
		} catch (IOException e) {
		}
		if (conn.isEmpty()) {
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	private void read(SelectionKey key) {
		SocketChannel sockChann = (SocketChannel) key.channel();
		this.readBuffer.clear();
		int numRead;
		try {
			numRead = sockChann.read(this.readBuffer);
		} catch (IOException e) {
			// The remote forcibly closed the connection, cancel
			// the selection key and close the channel.
			key.cancel();
			try {
				sockChann.close();
			} catch (IOException e1) {
			}
			
			synchronized (this.conns) {
				this.conns.remove(sockChann);
			}
			return;
		}

		if (numRead < 0) {
			// The remote forcibly closed the connection, cancel
			// the selection key and close the channel.
			key.cancel();
			try {
				sockChann.close();
			} catch (IOException e1) {
				// TODO
			}
			
			synchronized (this.conns) {
				this.conns.remove(sockChann);
			}
			return;
		}
		byte[] buf = new byte[numRead];
		this.readBuffer.get(buf, 0, numRead);
		String name = "";
		
		synchronized (this.conns) {
			Connection conn = this.conns.get(sockChann);
			if (conn == null) {
				return;
			}
			name = conn.name;
		}
		synchronized (this.readHandlers) {
			
			ReadEventHandler h = this.readHandlers.get(name);
			if (h == null) {
				return;
			}
			h.onDataArrive(buf);
		}
		return;
	}
	
	public String connect(InetAddress server, int port, String name, ReadEventHandler handler) throws IOException {
		SocketChannel sockChann = SocketChannel.open();
		sockChann.configureBlocking(false);
		sockChann.connect(new InetSocketAddress(server, port));
		ChangeRequest change = new ChangeRequest(sockChann, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT, name, handler);
		
		synchronized (this.pendingChanges) {
			this.pendingChanges.add(change);
		}
		this.selector.wakeup();
		synchronized (change) {
			try {
				change.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		System.out.println("CONNECTED");
		return change.name;
	}
	
}
