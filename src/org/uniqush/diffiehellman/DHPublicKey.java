package org.uniqush.diffiehellman;

import java.math.BigInteger;

public class DHPublicKey {
	protected BigInteger y;
	
	protected DHPublicKey(BigInteger y) {
		this.y = y;
	}
	
	public DHPublicKey(byte[] data) {
		this.y = new BigInteger(data);
	}
	
	public byte[] toByteArray() {
		return this.y.toByteArray();
	}
}
