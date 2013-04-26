package org.uniqush.diffiehellman;

import java.math.BigInteger;

public class DHPublicKey {
	protected BigInteger y;
	
	protected DHPublicKey(BigInteger y) {
		this.y = y;
	}
	
	public DHPublicKey(byte[] data) {
		this.y = new BigInteger(1, data);
	}
	
	public byte[] toByteArray() {
		byte[] b = this.y.toByteArray();
		if (b[0] == 0) {
		    byte[] tmp = new byte[b.length - 1];
		    System.arraycopy(b, 1, tmp, 0, tmp.length);
		    b = tmp;
		}
		return b;
	}
}
