package org.uniqush.client;

import java.io.OutputStream;
import java.security.interfaces.RSAPublicKey;

class KeyExchangingState implements State {
	private CommandHandler handler;
	private int nextLen;
	private RSAPublicKey rsaPub;
	
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
		
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		this.nextLen = DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1;
	}
	
	private void printBytes(byte[] data) {
		if (data == null) {
			System.out.printf("null");
			return;
		}
		for (int i = 0; i < data.length; i++) {
			System.out.printf("%d ", data[i]);
		}
	}

	@Override
	public State transit(byte[] data, OutputStream ostream) {
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		if (data.length != DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1) {
			this.nextLen = 0;
			return this;
		}
		if (data[0] != CURRENT_PROTOCOL_VERSION) {
			this.nextLen = 0;
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
		System.out.println();
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
			this.handler.OnKeyExchangeError();
		}
	}

	@Override
	public int nextChunkLength() {
		return this.nextLen;
	}

}
