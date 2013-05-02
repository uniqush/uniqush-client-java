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
	
	// Params:
	// 0. [optional] The Id of the message
	private final static int CMD_DATA = 0;

	// Params:
	// 0. [optional] The Id of the message
	private final static int CMD_EMPTY = 1;

	// Sent from client.
	//
	// Params
	// 0. service name
	// 1. username
	private final static int CMD_AUTH = 2;

	private final static int CMD_AUTHOK = 3;
	private final static int CMD_BYE = 4;

	// Sent from client.
	// Telling the server about its perference.
	//
	// Params:
	// 0. Digest threshold: -1 always send message directly; Empty: not change
	// 1. Compression threshold: -1 always compress the data; Empty: not change
	// >2. [optional] Digest fields
	private final static int CMD_SETTING = 5;

	// Sent from server.
	// Telling the client an
	// arrival of a message.
	//
	// Params:
	// 0. Size of the message
	// 1. The id of the message
	//
	// Message.Header:
	// Other digest info
	private final static int CMD_DIGEST = 6;

	// Sent from client.
	// Telling the server which cached
	// message it wants to retrive.
	//
	// Params:
	// 0. The message id
	private final static int CMD_MSG_RETRIEVE = 7;

	// Sent from client.
	// Telling the server to forward a
	// message to another user.
	//
	// Params:
	// 0. TTL
	// 1. Reciever's name
	// 2. [optional] Reciever's service name.
	//	    If empty, then same service as the client
	private final static int CMD_FWD_REQ = 8;

	// Sent from server.
	// Telling the client the mssage
	// is originally from another user.
	//
	// Params:
	// 0. Sender's name
	// 1. [optional] Sender's service name.
	//	    If empty, then same service as the client
	// 2. [optional] The Id of the message in the cache.
	private final static int CMD_FWD = 9;

	// Sent from client.
	//
	// Params:
	// 0. 1: visible; 0: invisible;
	//
	// If a client if invisible to the server,
	// then sending any message to this client will
	// not be considered as a message.
	//
	// Well... Imagine a scenario:
	//
	// Alice has two devices.
	//
	// If the app on any device is online, then any message
	// will be delivered to the device and no notification
	// will be pushed to other devices.
	//
	// However, if the app is "invisible" to the server,
	// then it will be considered as off online even if
	// there is a connection between the server and the client.
	//
	// (It is only considered as off line when we want to know if
	// we should push a notification. But it counts for other purpose,
	// say, number of connections under the user.)
	private final static int CMD_SET_VISIBILITY = 10;

	// Sent from client
	//
	// Params:
	//   0. "1" (as ASCII character, not integer) means subscribe; "0" means unsubscribe. No change on others.
	// Message:
	//   Header: parameters
	private final static int CMD_SUBSCRIPTION = 11;

	private MessageHandler handler;
	private String service;
	private String username;
	private String token;
	private RSAPublicKey rsaPub;
	private KeySet keySet;
	
	private int compressThreshold;
	
	private final static int CMDFLAG_COMPRESS = 1;
	
	protected void setPrefix(byte[] prefix, int length, boolean compress) {
		if (prefix.length < 4) {
			return;
		}
		
		// Command Length: little endian
		prefix[0] = (byte)(length & 0xFF);
		prefix[1] = (byte)(length & 0xFF00);
		
		// Flag: little endian
		prefix[2] = 0;
		prefix[3] = 0;
		if (compress) {
			prefix[2] |= CMDFLAG_COMPRESS;
		}
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
	
	protected Command unmarshalCommand(byte[] encrypted, byte[] prefix) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException, IOException {
		byte[] encoded = encrypted;

		int hmaclen = keySet.getDecryptHmacSize();
		int len = keySet.getDecryptedSize(encrypted.length - hmaclen);
		encoded = new byte[len];
		keySet.decrypt(encrypted, 0, encoded, 0);
		
		byte[] data = encoded;
		if ((prefix[2] & CMDFLAG_COMPRESS) != (byte) 0) {
			data = Snappy.uncompress(encoded);
		}
		Command cmd = new Command(data);
		return cmd;
	}
	
	protected byte[] marshalCommand(Command cmd, boolean compress, boolean encrypt) throws IllegalBlockSizeException, ShortBufferException, BadPaddingException, IOException {
		byte[] data = cmd.marshal();
		byte[] encoded = data;

		if (compress) {
			encoded = Snappy.compress(data);
		}
		int n = encoded.length;
		int prefixSz = 4;
		byte[] encrypted = encoded;
		
		if (encrypt) {
			n = keySet.getEncryptedSize(n);
			int hmacSz = keySet.getEncryptHmacSize();
			encrypted = new byte[n + hmacSz + prefixSz];
			keySet.encrypt(encoded, 0, encrypted, prefixSz);
			printBytes("encrypted:", encrypted, 0, encrypted.length);
		} else {
			encrypted = new byte[n + prefixSz];
			System.arraycopy(encoded, 0, encrypted, prefixSz, n);
		}
		
		setPrefix(encrypted, n, compress);
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
		if (this.handler != null) {
			this.handler.onError(e);
		}
	}
	
	public void onCloseStart() {
		if (this.handler != null) {
			this.handler.onCloseStart();
		}
	}
	
	public void onClosed() {
		if (this.handler != null) {
			this.handler.onClosed();
		}
	}
	
	private int expectedLen = 0;
	private byte[] currentCommandPrefix = null;
	
	protected byte[] processCommand(Command cmd) {
		switch (cmd.getType()) {
		case CMD_DATA:
			if (handler != null) {
				handler.onMessageFromServer(cmd.getParameter(0), cmd.getMessage());
			}
		}
		return null;
	}
	
	public int nextChunkLength() {
		return expectedLen;
	}
	
	public byte[] onData(byte[] data) {
		if (null == data || data.length != expectedLen) {
			onError(new StreamCorruptedException("No enough data"));
			return null;
		}
		
		if (currentCommandPrefix == null) {
			currentCommandPrefix = data.clone();
			expectedLen = chunkSize(data);
			return null;
		}
		try {
			Command cmd = unmarshalCommand(data, currentCommandPrefix);
			currentCommandPrefix = null;
			expectedLen = 4;
			return this.processCommand(cmd);
		} catch (Exception e) {
			if (this.handler != null) {
				this.handler.onError(e);
			}
			expectedLen = 0;
		}
		return null;
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
			sign.update(dhpub);
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
			byte[] clienthmac = keySet.clientHmac(mydhpubBytes);		
			System.arraycopy(clienthmac, 0, keyExReply, DH_PUBLIC_KEY_LENGTH + 1, AUTH_KEY_LENGTH);
			ostream.write(keyExReply);
			
			Command authCmd = new Command(CMD_AUTH, null);
			authCmd.AppendParameter(service);
			authCmd.AppendParameter(username);
			authCmd.AppendParameter(token);
			
			byte[] authData = marshalCommand(authCmd, false, true);
			ostream.write(authData);
			
			// reuse the authData as prefix
			n = readFull(istream, authData, 4);
			if (n != 4) {
				throw new LoginException("no enough data");
			}
			
			n = chunkSize(authData);
			
			byte[] chunk = new byte[n];
			int len = readFull(istream, chunk, n);
			if (len != n) {
				throw new LoginException("no enough data");
			}
			Command cmd = unmarshalCommand(chunk, authData);
			if (cmd.getType() != CMD_AUTHOK) {
				throw new LoginException("bad server reply");
			}
			
			expectedLen = 4;
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
	}
}
