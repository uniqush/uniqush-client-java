package org.uniqush.diffiehellman;

import java.math.BigInteger;

public class DHPrivateKey {
	protected BigInteger x;
	protected BigInteger y;
	protected DHPrivateKey(BigInteger x, DHGroup group) {
		this.x = x;
		this.y = group.generator.modPow(x, group.modulus);
	}
	
	public DHPublicKey getPublicKey() {
		return new DHPublicKey(y);
	}
}
