/*
 * Copyright 2013 Nan Deng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
	
	public boolean decode(byte[] hashed, byte[] em, int emBits, int saltLen) {
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
		
		if ((em[em.length - 1] & 0xFF) != 0xBC) {
			return false;
		}
		if ((em[0] & (0xFF << (8 - (8 * emLen - emBits)))) != 0) {
			return false;
		}
		
		byte[] DB = new byte[emLen - hLen - 1];
		byte[] H = new byte[hLen];
		
		System.arraycopy(em, 0, DB, 0, emLen - hLen - 1);
		System.arraycopy(em, emLen - hLen - 1, H, 0, hLen);
		
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
