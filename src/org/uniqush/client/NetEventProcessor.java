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
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

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
		BigInteger modulus = new BigInteger("17388413383649711290254825310339272211319767525363227404074762677568514182143042061437868796167591657550511002079944736281939887225873458139996723826487428401781326377898963252196351565707602724215593767581359596805150972910866410145077679809190513515791700412131297152963158161216863933118912237764396054712362877219981113432778260759907285938711897115187091296093563327334234754294283390985106557355671509013541665508032935881514071942658154269549566084778139622274561728679270315969220599430415442568612476273211334694395646067337896362196452349792104972786584396268761019495750809374141828098785614861562903854521");
		BigInteger exp = new BigInteger("65537");
		KeySpec keyspec = new RSAPublicKeySpec(modulus, exp);
		KeyFactory kf = null;
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		PublicKey pub = null;
		try {
			pub = kf.generatePublic(keyspec);
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(pub.toString());
		
		CommandReader cmdrd = new CommandReader(null, (RSAPublicKey) pub);
		NetEventProcessor proc = null;
		try {
			 proc = new NetEventProcessor("127.0.0.1", 8964, cmdrd);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		proc.run();
	}

}
