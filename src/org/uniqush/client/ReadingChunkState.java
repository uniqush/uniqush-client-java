package org.uniqush.client;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import org.xerial.snappy.Snappy;

public class ReadingChunkState extends State {
	
	private int size;
	private KeySet keySet;
	
	public ReadingChunkState(MessageHandler handler, KeySet keySet, String service, String username, int size) {
		super(handler, service, username);
		this.size = size;
		this.keySet = keySet;
	}

	@Override
	public int chunkSize() {
		return this.size;
	}

	protected Command unmarshalCommand(byte[] encrypted) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, IOException {
		int hmaclen = keySet.getDecryptHmacSize();
		int len = keySet.getDecryptedSize(encrypted.length - hmaclen);
		byte[] encoded = new byte[len];
		keySet.decrypt(encrypted, 0, encoded, 0);
		
		byte[] data = new byte[encoded.length - 1];
		System.arraycopy(encoded, 1, data, 0, encoded.length - 1);
		if ((encoded[0] & Command.CMDFLAG_COMPRESS) != (byte) 0) {
			data = Snappy.uncompress(data);
		}
		Command cmd = new Command(data);
		return cmd;
	}
	
	protected State processCommand(Command cmd, List<byte[]> reply) {
		switch (cmd.getType()) {
		case Command.CMD_DATA:
			if (handler != null) {
				handler.onMessageFromServer(cmd.getParameter(0), cmd.getMessage());
			}
		case Command.CMD_FWD:
			String sender = cmd.getParameter(0);
			if (sender == null) {
				this.onError(new StreamCorruptedException("no sender in forward message"));
				return new ErrorState(this.handler, service, username);
			}
			String service = cmd.getParameter(1);
			if (service == null) {
				service = this.service;
			}
			String id = cmd.getParameter(2);
			if (this.handler != null) {
				this.handler.onMessageFromUser(service, service, id, cmd.getMessage());
			}
		}
		return new ReadingChunkSizeState(this.handler, this.keySet, service, service);
	}
	
	@Override
	public State transit(byte[] data, List<byte[]> reply) {
		reply.clear();
		if (data == null || data.length != this.size) {
			this.onError(new StreamCorruptedException("No enough data"));
			return new ErrorState(this.handler, service, username);
		}
		try {
			Command cmd = unmarshalCommand(data);
			return processCommand(cmd, reply);
		} catch (Exception e) {
			this.onError(e);
			return new ErrorState(this.handler, service, username);
		}
	}
}
