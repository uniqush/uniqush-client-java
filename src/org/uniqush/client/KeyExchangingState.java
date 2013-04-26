package org.uniqush.client;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;

import org.uniqush.diffiehellman.DHGroup;
import org.uniqush.diffiehellman.DHPrivateKey;
import org.uniqush.diffiehellman.DHPublicKey;

class KeyExchangingState implements State {
	private CommandHandler handler;
	private int nextLen;
	private RSAPublicKey rsaPub;
	private String failReason;
	
	final static int ENCR_KEY_LENGTH = 32;
	final static int AUTH_KEY_LENGTH = 32;
	final static int IV_LENGTH = 32;
	final static int HMAC_LENGTH = 32;
	final static int PSS_SALT_LENGTH = 32;
	final static int DH_GROUP_ID = 14;
	final static int DH_PUBLIC_KEY_LENGTH = 256;
	final static int NONCE_LENGTH = 32;
	final static byte CURRENT_PROTOCOL_VERSION = 1;
	
	public KeyExchangingState(CommandHandler handler, RSAPublicKey rsaPub) {
		this.handler = handler;
		this.rsaPub = rsaPub;
		this.failReason = "";
		
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		this.nextLen = DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1;
	}
	
	private void printBytes(byte[] data) {
		if (data == null) {
			System.out.printf("null");
			return;
		}
		for (int i = 0; i < data.length; i++) {
			int d = data[i] & 0xFF;
			System.out.printf("%d ", d);
		}
	}
	
	@Override
	public State transit(byte[] data, OutputStream ostream) {
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		if (data.length != DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1) {
			this.nextLen = 0;
			this.failReason = "bad data chunk";
			return this;
		}
		if (data[0] != CURRENT_PROTOCOL_VERSION) {
			this.nextLen = 0;
			this.failReason = "incompatible protocol version";
			return this;
		}
		
		byte[] dhpub = new byte[DH_PUBLIC_KEY_LENGTH];
		byte[] sig = new byte[siglen];
		byte[] nonce = new byte[NONCE_LENGTH];
		System.arraycopy(data, 1, dhpub, 0, DH_PUBLIC_KEY_LENGTH);
		System.arraycopy(data, DH_PUBLIC_KEY_LENGTH + 1, sig, 0, siglen);
		System.arraycopy(data, DH_PUBLIC_KEY_LENGTH + siglen + 1, nonce, 0, NONCE_LENGTH);
		
		System.out.printf("dh public key: ");
		printBytes(dhpub);
		System.out.printf("\nsig: ");
		printBytes(sig);
		System.out.printf("\nnonce: ");
		printBytes(nonce);
		DHPrivateKey dhpriv = null;
		DHGroup group = null;
		try {
			Signature sign = Signature.getInstance("SHA256WITHRSA/PSS", "BC");
			sign.initVerify(this.rsaPub);
			sign.update(dhpub);
			boolean goodsign = sign.verify(data, DH_PUBLIC_KEY_LENGTH + 1, siglen);
			
			if (!goodsign) {
				this.nextLen = 0;
				this.failReason = "bad signature from the server";
				return this;
			}
			group = DHGroup.getGroup(DH_GROUP_ID);
			dhpriv = group.generatePrivateKey(new SecureRandom());
		} catch (NoSuchAlgorithmException e) {
			this.nextLen = 0;
			this.failReason = e.getLocalizedMessage();
			return this;
		} catch (NoSuchProviderException e) {
			this.nextLen = 0;
			this.failReason = e.getLocalizedMessage();
			return this;
		} catch (InvalidKeyException e) {
			this.nextLen = 0;
			this.failReason = e.getLocalizedMessage();
			return this;
		} catch (SignatureException e) {
			this.nextLen = 0;
			this.failReason = e.getLocalizedMessage();
			return this;
		}
		DHPublicKey mypub = dhpriv.getPublicKey();
		System.out.printf("\nmy dh pub: ");
		printBytes(mypub.toByteArray());
		System.out.println();
		DHPublicKey serverpub = new DHPublicKey(dhpub);
		byte[] masterKey = group.computeKey(serverpub, dhpriv);
		System.out.printf("\nmasterkey: ");
		printBytes(masterKey);
		System.out.println();
		
		byte[] keyExReply = new byte[DH_PUBLIC_KEY_LENGTH + AUTH_KEY_LENGTH + 1];
		keyExReply[0] = CURRENT_PROTOCOL_VERSION;
		byte[] mydhpubBytes = mypub.toByteArray();
		System.arraycopy(mydhpubBytes, 0, keyExReply, 1, DH_PUBLIC_KEY_LENGTH);
		
		KeySet ks = new KeySet(masterKey, nonce);
		
		try {
			byte[] clienthmac = ks.clientHmac(mydhpubBytes);
			System.out.printf("\nclienthmac: ");
			printBytes(clienthmac);
			System.out.println();
			
			System.arraycopy(clienthmac, 0, keyExReply, DH_PUBLIC_KEY_LENGTH + 1, AUTH_KEY_LENGTH);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			ostream.write(keyExReply);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void onConnectionFail() {
	}

	@Override
	public void onCloseStart() {
	}

	@Override
	public void onClosed() {
		if (this.handler != null) {
			this.handler.OnKeyExchangeError(this.failReason);
		}
	}

	@Override
	public int nextChunkLength() {
		return this.nextLen;
	}

}
