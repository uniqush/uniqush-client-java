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

public class NetEventProcessor {
	
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

	private Selector selector;
	private List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();

	private List<AsynReader> readers = new LinkedList<AsynReader>();
	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
	private Map<SocketChannel, Connection> conns = new HashMap<SocketChannel, Connection>();
	
	public NetEventProcessor() throws IOException {
		selector = SelectorProvider.provider().openSelector();
	}
	
	public void start() {
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
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}
	
	private void write(SelectionKey key) {
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
		synchronized (this.readers) {
			Iterator<AsynReader> iterator = this.readers.iterator();
			
			while (iterator.hasNext()) {
				AsynReader reader = iterator.next();
				reader.OnDataArrive(name, buf);
			}
		}
		return;
	}
	
	public void registerReader(AsynReader reader) {
		synchronized (this.readers) {
			this.readers.add(reader);
		}
	}

	public void connect(InetAddress server, int port) throws IOException {
		SocketChannel sockChann = SocketChannel.open();
		sockChann.configureBlocking(false);
		sockChann.connect(new InetSocketAddress(server, port));
		
		synchronized (this.pendingChanges) {
			this.pendingChanges.add(new ChangeRequest(sockChann, ChangeRequest.REGISTER, SelectionKey.OP_READ));
		}
	}
	
	public void connect(InetAddress server, int port, String name) throws IOException {
		SocketChannel sockChann = SocketChannel.open();
		sockChann.configureBlocking(false);
		sockChann.connect(new InetSocketAddress(server, port));
		
		synchronized (this.pendingChanges) {
			this.pendingChanges.add(new ChangeRequest(sockChann, ChangeRequest.REGISTER, SelectionKey.OP_READ, name));
		}
	}
}
