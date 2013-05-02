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
