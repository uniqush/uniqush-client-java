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
import java.io.StringReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.openssl.PEMReader;

class NetEventProcessor implements Runnable {	
	private Socket serverSocket;
	private DataHandler handler;
	
	public NetEventProcessor(String address, int port, DataHandler handler) throws UnknownHostException, IOException {
		this.connect(address, port, handler);
	}
	
	private void connect(String address, int port, DataHandler handler) throws UnknownHostException, IOException {
		this.handler = handler;
		this.serverSocket = new Socket(address, port);
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
		OutputStream ostream = this.serverSocket.getOutputStream();
		ostream.write(data);
	}
	
	public void close() throws IOException {
		this.serverSocket.close();
	}

	@Override
	public void run() {
		try {
			OutputStream ostream = this.serverSocket.getOutputStream();
			InputStream istream = this.serverSocket.getInputStream();
			byte[] buf = null;
			do {
				int n = this.handler.onDataArrive(buf, ostream);
				if (n <= 0) {
					break;
				}
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
	
	public static void main(String[] argv) {
		String pubkeyStr = "-----BEGIN PUBLIC KEY-----\n" +
				"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAib4l9JSLFSiPusrk2Cxb\n" +
				"km0oXQeC2wh/QDcnFf8g9PdrPhlKVobof9rNJt6XXjGiytkubmRMFlvC47NJcMvP\n" +
				"z0IvY0amSACUqPE20I4CLhIZQ2kraWBigC36x6SfTlZVDlRsfzVcwb/THo0e6KNx\n" +
				"WbrF3Y3/wvDZvDsT7gBULcz0eV+3/E1Dc4c+OBJnKgY+qXmGbzMXan0v7r6Cgypp\n" +
				"oQLOXVVz/9uRBJobOuys4DpedRSk6KizZ+P1UU5MGpN4vum9L+f/iguDzohU/tLo\n" +
				"XG+LuRwT8rTM32CGSJNoBC2Pd6YuhfxxJVWf1wmvdKoJYsi050eGySoe4rdM+dD9\n" +
				"uQIDAQAB\n" +
				"-----END PUBLIC KEY-----";
		StringReader pubreader = new StringReader(pubkeyStr);
		PEMReader pemReader = new PEMReader(pubreader);
		RSAPublicKey pubkey = null;
		try {
			Object obj = pemReader.readObject();
			pemReader.close();
			if (!(obj instanceof RSAPublicKey)) {
				return;
			}
			pubkey = (RSAPublicKey) obj;
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(pubkey.toString());
	}

}
