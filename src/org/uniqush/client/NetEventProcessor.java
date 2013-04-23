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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class NetEventProcessor implements Runnable {	
	private Socket serverSocket;
	private DataHandler handler;
	private SocketWriter writer;
	
	private class SocketWriter implements Writer {
		private OutputStream ostream;
		
		public SocketWriter(OutputStream ostream) {
			this.ostream = ostream;
		}
		
		@Override
		public void write(byte[] data) throws IOException {
			this.ostream.write(data);
		}
		
	}
	
	public NetEventProcessor(String address, int port, DataHandler handler) throws UnknownHostException, IOException {
		this.connect(address, port, handler);
	}
	
	private void connect(String address, int port, DataHandler handler) throws UnknownHostException, IOException {
		this.handler = handler;
		this.serverSocket = new Socket(address, port);
		this.writer = new SocketWriter(this.serverSocket.getOutputStream());
	}
	
	private int readFull(InputStream istream, byte[] buf) {
		int n = 0;
		while (n < buf.length) {
			try {
				int i = istream.read(buf, n, buf.length - n);
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
	
	public void write(byte[] data) throws IOException {
		this.writer.write(data);
	}

	@Override
	public void run() {
		try {
			InputStream istream = this.serverSocket.getInputStream();
			byte[] buf = null;
			do {
				int n = this.handler.onDataArrive(buf, this.writer);
				buf = new byte[n];
				int i = this.readFull(istream, buf);
				if (i != n) {
					break;
				}
			} while (true);
		} catch (IOException e) {
			// Well... We just close the connection and tell the handler
			// that it was closed.
			this.handler.onConnectionFail();
		}
		this.handler.onCloseStart();
		try {
			this.serverSocket.close();
		} catch (IOException e1) {
			// There is actually, nothing we can do.
		}
		this.handler.onClosed();
		this.serverSocket = null;

	}

}
