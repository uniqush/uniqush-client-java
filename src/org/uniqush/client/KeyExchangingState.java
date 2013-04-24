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
	final static int CURRENT_PROTOCOL_VERSION = 1;
	
	public KeyExchangingState(CommandHandler handler, RSAPublicKey rsaPub) {
		this.handler = handler;
		this.rsaPub = rsaPub;
		
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		this.nextLen = DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1;
	}

	@Override
	public State transit(byte[] data, OutputStream ostrea) {
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
		this.handler.OnKeyExchangeError();
	}

	@Override
	public int nextChunkLength() {
		return this.nextLen;
	}

}
