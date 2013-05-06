package org.uniqush.client;

import java.io.StreamCorruptedException;
import java.util.List;

class ReadingChunkSizeState extends State {
	private KeySet keySet;
	
	public ReadingChunkSizeState(MessageHandler handler, KeySet keyset, String service, String username) {
		super(handler, service, service);
		this.keySet = keyset;
	}

	@Override
	public int chunkSize() {
		return 2;
	}
	

	protected int chunkSize(byte[] prefix) {
		int length = 0;
		length = prefix[1];
		length = length << 8;
		length |= prefix[0];
		
		length += keySet.getDecryptHmacSize();
		return length;
	}

	@Override
	public State transit(byte[] data, List<byte[]> reply) {
		reply.clear();
		if (data == null || data.length != 2) {
			this.onError(new StreamCorruptedException("No enough data"));
			return new ErrorState(this.handler, service, username);
		}
		
		int length = this.chunkSize(data);
		return new ReadingChunkState(this.handler, this.keySet, service, username, length);
	}
}
