package org.uniqush.rsa;

import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.Cipher;

public class RSASSAPSSVerifier extends Signature {
	
	private RSAPublicKey pubkey;
	private MessageDigest hash;
	private int saltLen;
	
	public RSASSAPSSVerifier(String hashName) throws NoSuchAlgorithmException {
		this(hashName, -1);
	}
	
	public RSASSAPSSVerifier(String hashName, int saltLen) throws NoSuchAlgorithmException {
		super("RSA/PSS");
		MessageDigest h = MessageDigest.getInstance(hashName);
		this.hash = h;
		this.saltLen = saltLen;
		if (this.saltLen <= 0) {
			this.saltLen = h.getDigestLength();
		}
	}
	
	public RSASSAPSSVerifier(MessageDigest hash) {
		this(hash, hash.getDigestLength());
	}
	
	public RSASSAPSSVerifier(MessageDigest hash, int saltLen) {
		super("RSA/PSS");
		this.saltLen = saltLen;
		try {
			this.hash = (MessageDigest) hash.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalArgumentException("Impossible!");
		}
	}

	@Override
	@Deprecated
	protected Object engineGetParameter(String arg0)
			throws InvalidParameterException {
		throw new InvalidParameterException("not supported");
	}

	@Override
	protected void engineInitSign(PrivateKey arg0) throws InvalidKeyException {
		throw new InvalidKeyException("not supported");
	}

	@Override
	protected void engineInitVerify(PublicKey arg0) throws InvalidKeyException {
		this.pubkey = (RSAPublicKey) arg0;
	}

	@Override
	@Deprecated
	protected void engineSetParameter(String arg0, Object arg1)
			throws InvalidParameterException {
		throw new InvalidParameterException("not supported");

	}

	@Override
	protected byte[] engineSign() throws SignatureException {
		throw new SignatureException("not supported");
	}

	@Override
	protected void engineUpdate(byte arg0) throws SignatureException {
		this.hash.update(arg0);
	}

	@Override
	protected void engineUpdate(byte[] buf, int offset, int len)
			throws SignatureException {
		this.hash.update(buf, offset, len);
	}

	@Override
	protected boolean engineVerify(byte[] sig) throws SignatureException {
		if (this.pubkey == null) {
			throw new SignatureException("public key is not defined");
		}
		byte[] hashed = this.hash.digest();
		this.hash.reset();
		EMSADecoder decoder = new EMSADecoder(this.hash);
		int modBits = this.pubkey.getModulus().bitLength();
		int k = (modBits + 7) / 8;
		if (sig.length != k) {
			return false;
		}
		
		byte[] em = null;
		
		try {
			Cipher rsa = Cipher.getInstance("RSA/NONE/NoPadding");
			rsa.init(Cipher.ENCRYPT_MODE, this.pubkey);	
			em = rsa.doFinal(sig);
		} catch (Exception e) {
			return false;
		}
		
		int emBits = modBits - 1;
		int emLen = (emBits + 7) / 8;
		
		if (em.length > emLen) {
			return false;
		}
		if (em.length < emLen) {
			byte[] tmp = new byte[emLen];
			System.arraycopy(em, 0, tmp, emLen - em.length, em.length);
			em = tmp;
		}
		return decoder.decode(hashed, em, emBits, this.saltLen);
	}

}
