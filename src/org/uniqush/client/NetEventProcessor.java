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

		public SocketChannel socket;
		public int type;
		public int ops;
		public String name;
		
		public ChangeRequest(SocketChannel socket, int type, int ops, String name) {
			this.socket = socket;
			this.type = type;
			this.ops = ops;
			
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
							
							synchronized (change) {
								change.notifyAll();
							}
							break;
						}
					}
					this.pendingChanges.clear();
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
						try {
							socketChannel.finishConnect();
						} catch (IOException e){
							key.cancel();
						}
						key.interestOps(SelectionKey.OP_READ);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 
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
	
	public void registerReader(String name, ReadEventHandler handler) {
		ConnectionWriter writer = null;
		synchronized (this.connNameMap) {
			Connection conn = this.connNameMap.get(name);
			if (conn == null) {
				return;
			}
		}

		writer = new ConnectionWriter(this, name);
		synchronized (this.readHandlers) {
			this.readHandlers.put(name, handler);
		}
		
		handler.setWriter(writer);
	}

	public String connect(InetAddress server, int port) throws IOException {
		return this.connect(server, port, "");
	}
	
	public String connect(InetAddress server, int port, String name) throws IOException {
		SocketChannel sockChann = SocketChannel.open();
		sockChann.configureBlocking(false);
		sockChann.connect(new InetSocketAddress(server, port));
		ChangeRequest change = new ChangeRequest(sockChann, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT, name);
		
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
	
	public static void main(String[] args) {
		try {
			NetEventProcessor evtproc = new NetEventProcessor();
			Thread th = new Thread(evtproc);
			th.start();
			byte[] data = {0, 1, 15, 0, 104, 101, 108, 108, 111, 0, 119, 111, 114, 108, 100, 0, 1, 2, 3};
			evtproc.connect(InetAddress.getLocalHost(), 8964, "server");
			evtproc.send("server", data);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
