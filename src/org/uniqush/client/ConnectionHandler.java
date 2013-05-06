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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import org.xerial.snappy.Snappy;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.security.auth.login.LoginException;

import org.uniqush.diffiehellman.DHGroup;
import org.uniqush.diffiehellman.DHPrivateKey;
import org.uniqush.diffiehellman.DHPublicKey;

class ConnectionHandler {
	final static int ENCR_KEY_LENGTH = 32;
	final static int AUTH_KEY_LENGTH = 32;
	final static int IV_LENGTH = 32;
	final static int HMAC_LENGTH = 32;
	final static int PSS_SALT_LENGTH = 32;
	final static int DH_GROUP_ID = 14;
	final static int DH_PUBLIC_KEY_LENGTH = 256;
	final static int NONCE_LENGTH = 32;
	final static byte CURRENT_PROTOCOL_VERSION = 1;

	private MessageHandler handler;
	private String service;
	private String username;
	private String token;
	private RSAPublicKey rsaPub;
	private KeySet keySet;
	
	private State currentState;
	
	private int compressThreshold;
	
	
	protected void setPrefix(byte[] prefix, int length) {
		if (prefix.length < 2) {
			return;
		}
		
		// Command Length: little endian
		prefix[0] = (byte)(length & 0xFF);
		prefix[1] = (byte)(length & 0xFF00);
	}
	
	protected int chunkSize(byte[] prefix) {
		int length = 0;
		length = prefix[1];
		length = length << 8;
		length |= prefix[0];
		
		length += keySet.getDecryptHmacSize();
		return length;
	}
	
	private void printBytes(String name, byte[] buf, int offset, int length) {
		System.out.print(name + " ");
		for (int i = offset; i < offset + length; i++) {
			System.out.printf("%d ", (int)(buf[i] & 0xFF));
		}
		System.out.println();
	}
	
	protected Command unmarshalCommand(byte[] encrypted) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, IOException {
		int hmaclen = keySet.getDecryptHmacSize();
		int len = keySet.getDecryptedSize(encrypted.length - hmaclen);
		byte[] encoded = new byte[len];
		keySet.decrypt(encrypted, 0, encoded, 0);
		
		byte[] data = new byte[encoded.length - 1];
		System.arraycopy(encoded, 1, data, 0, encoded.length - 1);
		if ((encoded[0] & Command.CMDFLAG_COMPRESS) != (byte) 0) {
			data = Snappy.uncompress(data);
		}
		Command cmd = new Command(data);
		return cmd;
	}
	
	protected byte[] marshalCommand(Command cmd, boolean compress) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		byte[] data = cmd.marshal();
		byte[] compressed = data;

		if (compress) {
			compressed = Snappy.compress(data);
		}
		
		byte[] encoded = new byte[compressed.length + 1];
		// clear the flag field.
		encoded[0] = 0;
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
	
	public ConnectionHandler(MessageHandler handler,
			String service,
			String username,
			String token,
			RSAPublicKey pub) {
		this.handler = handler;
		this.service = service;
		this.username = username;
		this.token = token;
		this.rsaPub = pub;
		this.compressThreshold = 512;
		
		this.currentState = new ErrorState(this.handler, this.service, this.username);
	}
	
	private int readFull(InputStream istream, byte[] buf, int length) {
		int n = 0;
		while (n < length) {
			try {
				int i = istream.read(buf, n, length - n);
				if (i < 0) {
					return n;
				}
				n += i;
			} catch (IOException e) {
				return n;
			}
		}
		return n;
	}
	
	public void onError(Exception e) {
		this.currentState.onError(e);
	}
	
	public void onCloseStart() {
		this.currentState.onCloseStart();
	}
	
	public void onClosed() {
		this.currentState.onClosed();
	}
	
	public int nextChunkSize() {
		return this.currentState.chunkSize();
	}
	
	public void onData(byte[] data, List<byte[]> reply) {
		this.currentState = this.currentState.transit(data, reply);
	}
	
	public void handshake(InputStream istream,
			OutputStream ostream) throws LoginException {
		int siglen = (rsaPub.getModulus().bitLength() + 7)/8;
		byte[] data = new byte[DH_PUBLIC_KEY_LENGTH + siglen + NONCE_LENGTH + 1];
		int n = readFull(istream, data, data.length);
		if (n != data.length) {
			throw new LoginException("no enough data");
		}
		if (data[0] != CURRENT_PROTOCOL_VERSION) {
			throw new LoginException("imcompatible version");
		}

		byte[] dhpub = new byte[DH_PUBLIC_KEY_LENGTH];
		byte[] nonce = new byte[NONCE_LENGTH];
		System.arraycopy(data, 1, dhpub, 0, DH_PUBLIC_KEY_LENGTH);
		System.arraycopy(data, DH_PUBLIC_KEY_LENGTH + siglen + 1, nonce, 0, NONCE_LENGTH);

		try {
			
			// Verify the signature from the server. Make sure there is no MITM attack.
			Signature sign = Signature.getInstance("SHA256WITHRSA/PSS", "BC");
			sign.initVerify(this.rsaPub);
			sign.update(data, 0, DH_PUBLIC_KEY_LENGTH + 1);
			boolean goodsign = sign.verify(data, DH_PUBLIC_KEY_LENGTH + 1, siglen);
			
			if (!goodsign) {
				throw new LoginException("bad signature");
			}
			
			// Generate a DH key.
			DHGroup group = DHGroup.getGroup(DH_GROUP_ID);
			DHPrivateKey dhpriv = group.generatePrivateKey(new SecureRandom());
			DHPublicKey mypub = dhpriv.getPublicKey();
			DHPublicKey serverpub = new DHPublicKey(dhpub);
			byte[] masterKey = group.computeKey(serverpub, dhpriv);
			
			byte[] keyExReply = new byte[DH_PUBLIC_KEY_LENGTH + AUTH_KEY_LENGTH + 1];
			keyExReply[0] = CURRENT_PROTOCOL_VERSION;
			byte[] mydhpubBytes = mypub.toByteArray();
			System.arraycopy(mydhpubBytes, 0, keyExReply, 1, DH_PUBLIC_KEY_LENGTH);
			
			// Calculate keys and send the message back;
			keySet = new KeySet(masterKey, nonce);
			byte[] clienthmac = keySet.clientHmac(keyExReply, 0, DH_PUBLIC_KEY_LENGTH + 1);		
			System.arraycopy(clienthmac, 0, keyExReply, DH_PUBLIC_KEY_LENGTH + 1, AUTH_KEY_LENGTH);
			ostream.write(keyExReply);
			
			Command authCmd = new Command(Command.CMD_AUTH, null);
			authCmd.AppendParameter(service);
			authCmd.AppendParameter(username);
			authCmd.AppendParameter(token);
			
			byte[] authData = marshalCommand(authCmd, false);
			ostream.write(authData);
			
			// reuse the authData as prefix
			n = readFull(istream, authData, 2);
			if (n != 2) {
				throw new LoginException("no enough data");
			}
			
			n = chunkSize(authData);
			byte[] chunk = new byte[n];
			int len = readFull(istream, chunk, n);
			if (len != n) {
				throw new LoginException("no enough data");
			}
			Command cmd = unmarshalCommand(chunk);
			if (cmd.getType() != Command.CMD_AUTHOK) {
				throw new LoginException("bad server reply");
			}
		} catch (NoSuchAlgorithmException e) {
			throw new LoginException("cannot find the algorithm: " + e.getMessage());
		} catch (NoSuchProviderException e) {
			throw new LoginException("cannot find the provider: " + e.getMessage());
		} catch (InvalidKeyException e) {
			throw new LoginException("invalid key: " + e.getMessage());
		} catch (SignatureException e) {
			throw new LoginException("bad signature: " + e.getMessage());
		} catch (StreamCorruptedException e) {
			throw new LoginException("hmac error: " + e.getMessage());
		} catch (IOException e) {
			throw new LoginException("io error: " + e.getMessage());
		} catch (NoSuchPaddingException e) {
			throw new LoginException("no such padding: " + e.getMessage());
		} catch (IllegalBlockSizeException e) {
			throw new LoginException("encryption error: " + e.getMessage());
		} catch (ShortBufferException e) {
			throw new LoginException("encryption error: " + e.getMessage());
		} catch (BadPaddingException e) {
			throw new LoginException("encryption error: " + e.getMessage());
		} catch (InvalidAlgorithmParameterException e) {
			throw new LoginException("encryption error: " + e.getMessage());
		}
		this.currentState = new ReadingChunkSizeState(this.handler, this.keySet, service, service);
	}
}
