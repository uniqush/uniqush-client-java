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
import java.util.Iterator;
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
	
	private CommandMarshaler marshaler;
	
	private State currentState;
	
	private int compressThreshold;
	
	private void printBytes(String name, byte[] buf, int offset, int length) {
		System.out.print(name + " ");
		for (int i = offset; i < offset + length; i++) {
			System.out.printf("%d ", (int)(buf[i] & 0xFF));
		}
		System.out.println();
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
	
	protected byte[] marshalCommand(Command cmd) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		int size = cmd.marshal().length;
		boolean compress = false;
		if (size > this.compressThreshold) {
			compress = true;
		}
		return this.marshaler.marshalCommand(cmd, compress);
	}
	
	/**
	 * Set the parameters and tell the server to set to the same value
	 * @param digestThreshold When a message is larger than the threshold,
	 * 	then server should send the digest instead of the message it self.
	 *  <=0 means always receive the digest first.
	 * @param compressThreshold When a message is larger than the threshold,
	 * 	the peer (both server and client) should compress the message before
	 * 	encrypting it. <= 0 means always compress.
	 * @param digestFields When server sends a digest of a message, it should
	 * 	examine the header of the message and put the specified fields in
	 * 	the digest.
	 * @return
	 * @throws IOException 
	 * @throws BadPaddingException 
	 * @throws ShortBufferException 
	 * @throws IllegalBlockSizeException 
	 */
	public byte[] marshalConfigCommand(int digestThreshold, int compressThreshold, List<String> digestFields) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		this.compressThreshold = compressThreshold;
		Command cmd = new Command(Command.CMD_SETTING, null);
		
		cmd.AppendParameter((new Integer(digestThreshold)).toString());
		cmd.AppendParameter((new Integer(compressThreshold)).toString());
		if (digestFields != null) {
			Iterator<String> iter = digestFields.iterator();
			while (iter.hasNext()) {
				cmd.AppendParameter(iter.next());
			}
		}
		return marshalCommand(cmd);
	}
	
	public byte[] marshalMessageToUser(String service, String username, Message msg, int ttl) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		Command cmd = null;
		cmd = new Command(Command.CMD_FWD_REQ, msg);
		
		// TTL (in second)
		cmd.AppendParameter((new Integer(ttl)).toString() + "s");
		cmd.AppendParameter(username);
		if (service != this.service) {
			cmd.AppendParameter(service);
		}
		return marshalCommand(cmd);
	}
	
	public byte[] marshalMessageToServer(Message msg) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		Command cmd = null;
		if (msg == null) {
			cmd = new Command(Command.CMD_EMPTY, null);
		}
		cmd = new Command(Command.CMD_DATA, msg);
		return marshalCommand(cmd);
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
			KeySet keySet = new KeySet(masterKey, nonce);
			byte[] clienthmac = keySet.clientHmac(keyExReply, 0, DH_PUBLIC_KEY_LENGTH + 1);		
			System.arraycopy(clienthmac, 0, keyExReply, DH_PUBLIC_KEY_LENGTH + 1, AUTH_KEY_LENGTH);
			ostream.write(keyExReply);
			
			this.marshaler = new CommandMarshaler(keySet);
			
			Command authCmd = new Command(Command.CMD_AUTH, null);
			authCmd.AppendParameter(service);
			authCmd.AppendParameter(username);
			authCmd.AppendParameter(token);
			
			byte[] authData = marshaler.marshalCommand(authCmd, false);
			ostream.write(authData);
			
			// reuse the authData as prefix
			int prefixLen = marshaler.prefixLength();
			n = readFull(istream, authData, prefixLen);
			if (n != prefixLen) {
				throw new LoginException("no enough data");
			}
			
			n = marshaler.chunkSize(authData);
			byte[] chunk = new byte[n];
			int len = readFull(istream, chunk, n);
			if (len != n) {
				throw new LoginException("no enough data");
			}
			Command cmd = marshaler.unmarshalCommand(chunk);
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
		this.currentState = new ReadingChunkSizeState(this.handler, this.marshaler, service, service);
	}
}
