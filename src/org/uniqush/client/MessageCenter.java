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
import java.util.concurrent.Semaphore;

import javax.security.auth.login.LoginException;

public class MessageCenter implements Runnable {
	private Socket serverSocket;
	private ConnectionHandler handler;
	private Semaphore writeLock;
	
	public MessageCenter(String address,
			int port,
			String service,
			String username,
			String token,
			RSAPublicKey pub) throws UnknownHostException, IOException, LoginException {
		this.serverSocket = new Socket(address, port);
		ConnectionHandler handler = new ConnectionHandler(null, service, username, token, pub);
		handler.handshake(this.serverSocket.getInputStream(), this.serverSocket.getOutputStream());
		this.handler = handler;
		this.writeLock = new Semaphore(1);
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
		do {
			int len = this.handler.nextChunkLength();
			if (len <= 0) {
				break;
			}
			byte[] data = new byte[len];
			int n = readFull(istream, data, len);
			if (n != len) {
				break;
			}
			byte[] reply = this.handler.onData(data);
			if (reply != null && reply.length > 0) {
				try {
					this.writeLock.acquire();
					ostream.write(reply);
					this.writeLock.release();
				} catch (Exception e) {
					this.writeLock.release();
					this.handler.onError(e);
					break;
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
			MessageCenter center = new MessageCenter("127.0.0.1", 8964, "service", "monnand", "token", (RSAPublicKey)pub);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
}
