package org.uniqush.rsa;

import java.security.MessageDigest;
import java.util.Arrays;

class EMSADecoder {
	private MessageDigest hash;
	private MaskGenerationFunction mgf;
	
	public EMSADecoder(MessageDigest hash) {
		try {
			this.hash = (MessageDigest) hash.clone();
			this.mgf = new MaskGenerationFunction((MessageDigest) hash.clone());
		} catch (CloneNotSupportedException e) {
			throw new IllegalArgumentException("Impossible");
		}
	}
	
	public boolean decode(byte[] hashed, byte[] EM, int emBits, int saltLen) {
		if (saltLen < 0) {
	    	throw new IllegalArgumentException("salt length should not less than zero");
		}
		this.hash.reset();
		int hLen = hash.getDigestLength();
		if (hLen != hashed.length) {
			throw new IllegalArgumentException("invalid message hash length");
		}
		if (emBits < (8 * hLen + 8 * saltLen + 9)) {
			throw new IllegalArgumentException("decoding error");
		}
		int emLen = (emBits + 7) / 8;
		
		if ((EM[EM.length - 1] & 0xFF) != 0xBC) {
			return false;
		}
		if ((EM[0] & (0xFF << (8 - (8 * emLen - emBits)))) != 0) {
			return false;
		}
		
		byte[] DB = new byte[emLen - hLen - 1];
		byte[] H = new byte[hLen];
		
		System.arraycopy(EM, 0, DB, 0, emLen - hLen - 1);
		System.arraycopy(EM, emLen - hLen - 1, H, 0, hLen);
		
		byte[] dbMask = mgf.generateMask(H, emLen - hLen - 1);
		
		for (int i = 0; i < DB.length; i++) {
			DB[i] ^= dbMask[i];
		}
		
		DB[0] &= (0xFF >>> (8 * emLen - emBits));
		
		for (int i = 0; i < emLen - hLen - hLen - 2; i++) {
			if (DB[i] != 0) {
				return false;
			}
		}
		
		if (DB[emLen - hLen - hLen - 2] != (byte)1) {
			return false;
		}
		
		byte[] salt = new byte[saltLen];
		System.arraycopy(DB, DB.length - saltLen, salt, 0, saltLen);
		
		for (int i = 0; i < 8; i++) {
			this.hash.update((byte)0);
		}
		
		hash.update(hashed, 0, hLen);
		hash.update(salt, 0, saltLen);
		byte[] H0 = hash.digest();
		return Arrays.equals(H, H0);
	}
}
