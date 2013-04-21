package org.uniqush.client;

public class BufferedChunkReader implements ChunkReader, ReadEventHandler {
	private int expDataLen;
	private byte[] buffer;
	private ReadEventHandler handler;
	
	public BufferedChunkReader(ReadEventHandler handler, int nextBufferSize) {
		expDataLen = nextBufferSize;
		if (expDataLen <= 0) {
			expDataLen = 0;
		} else {
			buffer = new byte[expDataLen];
		}
		
		this.handler = handler;
	}

	@Override
	public int onDataArrive(byte[] buf) {
		if (expDataLen <= 0) {
			int ret = this.handler.onDataArrive(buf);
		}
		int copyLen = Math.min(buf.length, expDataLen);
		System.arraycopy(buf, 0, buffer, buffer.length - expDataLen, copyLen);
		expDataLen = expDataLen - copyLen;
		
		if (expDataLen <= 0) {
			this.handler.onDataArrive(buf);
		}
		
		if (copyLen < buf.length) {
			int newBufLen = buf.length - copyLen;
			this.buffer = new byte[newBufLen];
			System.arraycopy(this.buffer, copyLen, this.buffer, 0, newBufLen);
		}
		return 0;
	}

	@Override
	public void onConnectionFail() {
		this.handler.onConnectionFail();
	}

	@Override
	public void onCloseStart() {
		this.handler.onCloseStart();
	}

	@Override
	public void onClosed() {
		this.handler.onClosed();
	}

	@Override
	public void setWriter(Writer writer) {
		this.handler.setWriter(writer);
	}

	@Override
	public void setNextBufferSize(int size) {
	}

}
