package org.uniqush.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import javax.security.auth.login.LoginException;

import org.uniqush.diffiehellman.DHGroup;
import org.uniqush.diffiehellman.DHPrivateKey;
import org.uniqush.diffiehellman.DHPublicKey;

/*
interface ConnectionHandler {
	public int handshake(InputStream istream, OutputStream ostream, String service, String username, String token);
	public int onData(byte[] input, ArrayList<byte[]> replies);
	public byte[] encodeMessageToUsers(String service, String receiver, Message msg);
	public byte[] encodeMessageToServer(Message msg);
}
*/

class ConnectionHandler {
	final static int ENCR_KEY_LENGTH = 32;
	final static int AUTH_KEY_LENGTH = 32;
	final static int IV_LENGTH = 32;
	final static int HMAC_LENGTH = 32;
	final static int PSS_SALT_LENGTH = 32;
	final static int DH_GROUP_ID = 14;
	final static int DH_PUBLIC_KEY_LENGTH = 256;
	final static int NONCE_LENGTH = 32;
	final static byte CURRENT_PROTOCOL_VERSION = 1;
	private CommandHandler handler;
	private String service;
	private String username;
	private String token;
	private RSAPublicKey rsaPub;
	
	public ConnectionHandler(CommandHandler handler,
			String service,
			String username,
			String token,
			RSAPublicKey pub) {
		this.handler = handler;
		this.service = service;
		this.username = username;
		this.token = token;
		this.rsaPub = pub;
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
	
	public void handshake(InputStream istream,
			OutputStream ostream) throws LoginException {
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		byte[] data = new byte[DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1];
		int n = readFull(istream, data);
		if (n != data.length) {
			throw new LoginException("no enough data");
		}
		if (data[0] != CURRENT_PROTOCOL_VERSION) {
			throw new LoginException("imcompatible version");
		}

		byte[] dhpub = new byte[DH_PUBLIC_KEY_LENGTH];
		byte[] sig = new byte[siglen];
		byte[] nonce = new byte[NONCE_LENGTH];
		System.arraycopy(data, 1, dhpub, 0, DH_PUBLIC_KEY_LENGTH);
		System.arraycopy(data, DH_PUBLIC_KEY_LENGTH + 1, sig, 0, siglen);
		System.arraycopy(data, DH_PUBLIC_KEY_LENGTH + siglen + 1, nonce, 0, NONCE_LENGTH);

		DHPrivateKey dhpriv = null;
		DHGroup group = null;
		Signature sign = null;
		try {
			sign = Signature.getInstance("SHA256WITHRSA/PSS", "BC");
			sign.initVerify(this.rsaPub);
			sign.update(dhpub);
			boolean goodsign = sign.verify(data, DH_PUBLIC_KEY_LENGTH + 1, siglen);
			
			if (!goodsign) {
				throw new LoginException("bad signature");
			}
			group = DHGroup.getGroup(DH_GROUP_ID);
			dhpriv = group.generatePrivateKey(new SecureRandom());
			DHPublicKey mypub = dhpriv.getPublicKey();
			DHPublicKey serverpub = new DHPublicKey(dhpub);
			byte[] masterKey = group.computeKey(serverpub, dhpriv);
			
			byte[] keyExReply = new byte[DH_PUBLIC_KEY_LENGTH + AUTH_KEY_LENGTH + 1];
			keyExReply[0] = CURRENT_PROTOCOL_VERSION;
			byte[] mydhpubBytes = mypub.toByteArray();
			System.arraycopy(mydhpubBytes, 0, keyExReply, 1, DH_PUBLIC_KEY_LENGTH);
			
			KeySet ks = new KeySet(masterKey, nonce);
			byte[] clienthmac = ks.clientHmac(mydhpubBytes);			
			System.arraycopy(clienthmac, 0, keyExReply, DH_PUBLIC_KEY_LENGTH + 1, AUTH_KEY_LENGTH);
			ostream.write(keyExReply);
			
		} catch (NoSuchAlgorithmException e) {
			throw new LoginException("cannot find the algorithm: " + e.getMessage());
		} catch (NoSuchProviderException e) {
			throw new LoginException("cannot find the provider: " + e.getMessage());
		} catch (InvalidKeyException e) {
			throw new LoginException("invalid rsa key: " + e.getMessage());
		} catch (SignatureException e) {
			throw new LoginException("bad signature: " + e.getMessage());
		} catch (IOException e) {
			throw new LoginException("io error: " + e.getMessage());
		}
	}
}
