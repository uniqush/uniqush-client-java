package org.uniqush.client;

import java.util.ArrayList;

class ErrorState extends State {

	public ErrorState(MessageHandler handler, String service, String username) {
		super(handler, service, username);
	}

	@Override
	public int chunkSize() {
		return -1;
	}

	@Override
	public State transit(byte[] data, ArrayList<byte[]> reply) {
		return this;
	}

}
