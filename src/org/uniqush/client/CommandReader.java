package org.uniqush.client;

import java.io.OutputStream;
import java.security.interfaces.RSAPublicKey;

class CommandReader implements DataHandler {
	private State currentState;
	
	public CommandReader(CommandHandler handler, RSAPublicKey rsaPub) {
		this.currentState = new KeyExchangingState(handler, rsaPub);
	}

	@Override
	public void onConnectionFail() {
		if (currentState != null) {
			currentState.onConnectionFail();
		}

	}

	@Override
	public void onCloseStart() {
		if (currentState != null) {
			currentState.onCloseStart();
		}
	}

	@Override
	public void onClosed() {
		if (currentState != null) {
			currentState.onClosed();
		}
	}

	@Override
	public int onDataArrive(byte[] buf, OutputStream ostream) {
		if (currentState == null) {
			return 0;
		}
		if (buf != null) {
			this.currentState = this.currentState.transit(buf, ostream);
		}
		if (currentState != null) {
			return this.currentState.nextChunkLength();
		}
		return 0;
	}

}
