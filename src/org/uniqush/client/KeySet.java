package org.uniqush.client;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

class KeySet {
	private final Charset UTF_8 = Charset.forName("UTF-8");
	public byte[] serverEncrKey;
	public byte[] serverAuthKey;
	public byte[] clientEncrKey;
	public byte[] clientAuthKey;
	
	public byte[] clientHmac(byte[] data) throws InvalidKeyException, NoSuchAlgorithmException {
		Mac h = null;
		h = Mac.getInstance("HmacSHA256");
		SecretKey hmacKey = new SecretKeySpec(clientAuthKey, h.getAlgorithm());
		h.init(hmacKey);
		return h.doFinal(data);
	}
	
	public KeySet(byte[] key, byte[] nonce) {
		MaskGenerationFunction mgf = null;
		try {
			mgf = new MaskGenerationFunction(MessageDigest.getInstance("SHA256"));
		} catch (NoSuchAlgorithmException e2) {
			// Impossibru!!
		}
		byte[] seed = new byte[key.length + nonce.length];
		System.arraycopy(key, 0, seed, 0, key.length);
		System.arraycopy(nonce, 0, seed, key.length, nonce.length);
		byte[] mkey = mgf.generateMask(seed, 48);
		
		Mac h = null;
		try {
			h = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e1) {
			// Impossibru!!
		}
		SecretKey hmacKey = new SecretKeySpec(mkey, h.getAlgorithm());
		
		try {
			h.init(hmacKey);
		} catch (InvalidKeyException e) {
			// Impossibru!!
		}
		this.serverEncrKey = h.doFinal("ServerEncr".getBytes(UTF_8));
		h.reset();
		this.serverAuthKey = h.doFinal("ServerAuth".getBytes(UTF_8));
		h.reset();
		this.clientAuthKey = h.doFinal("ClientAuth".getBytes(UTF_8));   
		h.reset();           
		this.clientEncrKey = h.doFinal("ClientEncr".getBytes(UTF_8));   
		h.reset();
	}
}
