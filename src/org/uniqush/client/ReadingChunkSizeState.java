package org.uniqush.client;

import java.util.ArrayList;

class ReadingChunkSizeState implements State {
	
	MessageHandler handler;
	
	public ReadingChunkSizeState(MessageHandler handler) {
		this.handler = handler;
	}

	@Override
	public int chunkSize() {
		return 2;
	}

	@Override
	public State transit(byte[] data, ArrayList<byte[]> reply) {
		return null;
	}

	@Override
	public void onError(Exception e) {
		if (this.handler != null) {
			this.handler.onError(e);
		}
	}

	@Override
	public void onCloseStart() {
		if (this.handler != null) {
			this.handler.onCloseStart();
		}
	}

	@Override
	public void onClosed() {
		// TODO Auto-generated method stub

	}

}
