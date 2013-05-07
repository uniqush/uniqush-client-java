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

import java.io.StreamCorruptedException;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class KeySet {
	private final int IV_LENGTH = 16;
	private final Charset UTF_8 = Charset.forName("UTF-8");
	public byte[] serverEncrKey;
	public byte[] serverAuthKey;
	public byte[] clientEncrKey;
	public byte[] clientAuthKey;
	
	private Cipher encryptCipher;
	private Cipher decryptCipher;
	
	private Mac clientHmac;
	private Mac serverHmac;

	public int getEncryptHmacSize() {
		return clientHmac.getMacLength();
	}

	public int getDecryptHmacSize() {
		return serverHmac.getMacLength();
	}
	
	public int getEncryptedSize(int length) {
		return encryptCipher.getOutputSize(length);
	}
	
	public int getDecryptedSize(int length) {
		return decryptCipher.getOutputSize(length);
	}
	
	public boolean bytesEq(byte[] a, int aOffset, byte[] b, int bOffset, int length) {
		for (int i = 0; i < length; i++) {
			if (a[i + aOffset] != b[i + bOffset]) {
				return false;
			}
		}
		return true;
	}
	
	public void decrypt(byte[] input, int inputOffset, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, StreamCorruptedException {
		int hmacSz = getDecryptHmacSize();
		int len = decryptCipher.update(input, inputOffset, input.length - inputOffset - hmacSz, output, outputOffset);
		byte[] hmac = new byte[hmacSz];
		serverHmac.reset();
		
		byte[] prefix = new byte[2];
		int datalen = input.length - inputOffset - hmacSz;
		prefix[0] = (byte)(datalen & 0xFF);
		prefix[1] = (byte)((datalen >> 8) & 0xFF);
		serverHmac.update(prefix);
		serverHmac.update(input, inputOffset, getEncryptedSize(len));
		serverHmac.doFinal(hmac, 0);
		if (!bytesEq(hmac, 0, input, inputOffset + getEncryptedSize(len), hmacSz)) {
			throw new StreamCorruptedException("unmached hmac");
		}
		return;
	}
	
	public void encrypt(byte[] input, int inputOffset, byte[] output, int outputOffset) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException {
		// encrypt-then-hmac
		int inputLen = input.length - inputOffset;
		int len = encryptCipher.update(input, inputOffset, inputLen, output, outputOffset);
		System.out.printf("encrypted len=%d; input length=%d\n", len, inputLen);

		byte[] prefix = new byte[2];
		prefix[0] = (byte)(inputLen & 0xFF);
		prefix[1] = (byte)((inputLen >> 8) & 0xFF);
		clientHmac.reset();
		clientHmac.update(prefix);
		clientHmac.update(output, outputOffset, len);
		clientHmac.doFinal(output, outputOffset + len);
	}
	
	public byte[] clientHmac(byte[] data, int offset, int length) throws InvalidKeyException, NoSuchAlgorithmException {
		Mac h = null;
		h = Mac.getInstance("HmacSHA256");
		SecretKey hmacKey = new SecretKeySpec(clientAuthKey, h.getAlgorithm());
		h.init(hmacKey);
		h.update(data, offset, length);
		return h.doFinal();
	}
	
	public KeySet(byte[] key, byte[] nonce) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		MaskGenerationFunction mgf = null;
		
		mgf = new MaskGenerationFunction(MessageDigest.getInstance("SHA256"));
		byte[] seed = new byte[key.length + nonce.length];
		System.arraycopy(key, 0, seed, 0, key.length);
		System.arraycopy(nonce, 0, seed, key.length, nonce.length);
		byte[] mkey = mgf.generateMask(seed, 48);
		
		Mac h = Mac.getInstance("HmacSHA256");
		SecretKey hmacKey = new SecretKeySpec(mkey, h.getAlgorithm());
		h.init(hmacKey);
		this.serverEncrKey = h.doFinal("ServerEncr".getBytes(UTF_8));
		h.reset();
		this.serverAuthKey = h.doFinal("ServerAuth".getBytes(UTF_8));
		h.reset();
		this.clientAuthKey = h.doFinal("ClientAuth".getBytes(UTF_8));   
		h.reset();           
		this.clientEncrKey = h.doFinal("ClientEncr".getBytes(UTF_8));   
		h.reset();
		

		this.encryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
		this.decryptCipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
		
		byte[] iv = new byte[IV_LENGTH];
		for (int i = 0; i < iv.length; i++) {
			iv[i] = 0;
		}
		
		IvParameterSpec ivspec = new IvParameterSpec(iv);

		SecretKeySpec clik = new SecretKeySpec(clientEncrKey, "AES");
		this.encryptCipher.init(Cipher.ENCRYPT_MODE, clik, ivspec);
		
		SecretKeySpec srvk = new SecretKeySpec(serverEncrKey, "AES");
		this.decryptCipher.init(Cipher.DECRYPT_MODE, srvk, ivspec);
		

		this.clientHmac = Mac.getInstance("HmacSHA256");
		SecretKey cliAuthK = new SecretKeySpec(clientAuthKey, this.clientHmac.getAlgorithm());
		clientHmac.init(cliAuthK);
		

		this.serverHmac = Mac.getInstance("HmacSHA256");
		SecretKey srvAuthK = new SecretKeySpec(serverAuthKey, this.serverHmac.getAlgorithm());
		serverHmac.init(srvAuthK);
	}
}
