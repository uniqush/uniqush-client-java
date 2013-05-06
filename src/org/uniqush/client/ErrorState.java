package org.uniqush.client;

import java.util.List;

class ErrorState extends State {

	public ErrorState(MessageHandler handler, String service, String username) {
		super(handler, service, username);
	}

	@Override
	public int chunkSize() {
		return -1;
	}

	@Override
	public State transit(byte[] data, List<byte[]> reply) {
		return this;
	}

}
