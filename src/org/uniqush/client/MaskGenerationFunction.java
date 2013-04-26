package org.uniqush.client;

import java.security.MessageDigest;

class MaskGenerationFunction {
	private MessageDigest hash;
	
	public MaskGenerationFunction(MessageDigest hash) {
		this.hash = hash;
	}
	
	private void intToOctetString(int i, byte[] s) {
		s[0] = (byte)(i >>> 24);
		s[1] = (byte)(i >>> 16);
		s[2] = (byte)(i >>> 8);
		s[3] = (byte)(i >>> 0);
	}
	
	public byte[] generateMask(byte[] seed, int length) {
		byte[] mask = new byte[length];
		byte[] c = new byte[4];
		int counter = 0;
		int hLen = hash.getDigestLength();

		hash.reset();

		while (counter < (length / hLen)) {
			intToOctetString(counter, c);
			hash.update(seed);
			hash.update(c);
			System.arraycopy(hash.digest(), 0, mask, counter * hLen, hLen);
			counter++;
		}

		if ((counter * hLen) < length) {
			intToOctetString(counter, c);
			hash.update(seed);
			hash.update(c);
			System.arraycopy(hash.digest(), 0, mask, counter * hLen, mask.length - (counter * hLen));
		}

		return mask;
	}
}
