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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {
	private Socket serverSocket;
	private ConnectionHandler handler;
	private Semaphore writeLock;
	
	public MessageCenter() {
		this.serverSocket = null;
	}
	
	public void connect(
			String address,
			int port,
			String service,
			String username,
			String token,
			RSAPublicKey pub,
			MessageHandler msgHandler) throws UnknownHostException, IOException, LoginException {
		if (this.serverSocket != null) {
			this.serverSocket.close();
		}
		this.serverSocket = new Socket(address, port);
		ConnectionHandler handler = new ConnectionHandler(msgHandler, service, username, token, pub);
		handler.handshake(this.serverSocket.getInputStream(), this.serverSocket.getOutputStream());
		this.handler = handler;
		this.writeLock = new Semaphore(1);
	}
	
	public void sendMessageToServer(Message msg) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException, InterruptedException {
		byte[] data = this.handler.marshalMessageToServer(msg);

		this.writeLock.acquire();
		this.serverSocket.getOutputStream().write(data);
		this.writeLock.release();
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
		this.handler.onCloseStart();
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			// WTF. What do you want me to do?
		}
		this.handler.onClosed();
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
		try {
			MessageCenter center = new MessageCenter();
			MessageEcho msgHandler = new MessageEcho(center);
			center.connect("127.0.0.1", 8964, "service", "monnand", "token", (RSAPublicKey)pub, msgHandler);
			Thread th = new Thread(center);
			th.start();
			th.join();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
}
