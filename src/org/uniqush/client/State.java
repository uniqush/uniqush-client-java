package org.uniqush.client;

import java.io.OutputStream;

interface State {
	public State transit(byte[] data, OutputStream ostrea);
	public int nextChunkLength();
	public void onConnectionFail();
	public void onCloseStart();
	public void onClosed();
}
