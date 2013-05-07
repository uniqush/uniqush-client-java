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

import java.io.IOException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import org.xerial.snappy.Snappy;

class CommandMarshaler {
	private KeySet keySet;
	
	public CommandMarshaler(KeySet ks) {
		this.keySet = ks;
	}
	
	public int prefixLength() {
		return 2;
	}
	
	public int chunkSize(byte[] prefix) {
		int length = 0;
		length = prefix[1];
		length = length << 8;
		length |= prefix[0];
		
		length += keySet.getDecryptHmacSize();
		return length;
	}
	
	public Command unmarshalCommand(byte[] encrypted) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, IOException {
		int hmaclen = keySet.getDecryptHmacSize();
		int len = keySet.getDecryptedSize(encrypted.length - hmaclen);
		byte[] encoded = new byte[len];
		keySet.decrypt(encrypted, 0, encoded, 0);
		
		int paddingLen = encoded[0] >> 3;
		
		byte[] data = new byte[encoded.length - 1 - paddingLen];
		System.arraycopy(encoded, 1, data, 0, data.length);
		if ((encoded[0] & Command.CMDFLAG_COMPRESS) != (byte) 0) {
			data = Snappy.uncompress(data);
		}
		Command cmd = new Command(data);
		return cmd;
	}
	
	protected void setPrefix(byte[] prefix, int length) {
		if (prefix.length < 2) {
			return;
		}
		
		// Command Length: little endian
		prefix[0] = (byte)(length & 0xFF);
		prefix[1] = (byte)(length & 0xFF00);
	}
	
	public byte[] marshalCommand(Command cmd, boolean compress) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		byte[] data = cmd.marshal();
		byte[] compressed = data;

		if (compress) {
			compressed = Snappy.compress(data);
		}
		
		int nrBlk = (compressed.length + 16) / 16;
		int paddingLen = (nrBlk * 16) - (compressed.length + 1);
		
		byte[] encoded = new byte[compressed.length + 1 + paddingLen];
		// clear the flag field.
		encoded[0] = 0;
		encoded[0] = (byte) (paddingLen << 3);
		if (compress) {
			encoded[0] |= Command.CMDFLAG_COMPRESS;
		}
		System.arraycopy(compressed, 0, encoded, 1, compressed.length);
		int n = encoded.length;
		int prefixSz = 2;
		
		n = keySet.getEncryptedSize(n);
		int hmacSz = keySet.getEncryptHmacSize();
		byte[] encrypted = new byte[n + hmacSz + prefixSz];
		
		keySet.encrypt(encoded, 0, encrypted, prefixSz);
		setPrefix(encrypted, n);
		return encrypted;
	}
}
