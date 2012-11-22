package org.uniqush.client;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class Session implements ReadEventHandler {
	
	private class NullWriter implements Writer {
		@Override
		public void write(byte[] buf) {
		}
		
		public NullWriter() {
		}
	}

	private Writer writer = new NullWriter();
	private PublicKey pubKey;
	
	public Session(PublicKey pub) {
		this.pubKey = pub;
	}
	
	private int writeBytes(byte[] data) {
		int length = data.length;
		ByteBuffer buf = ByteBuffer.allocate(2 + length);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short)length);
		buf.put(data);
		this.writer.write(buf.array());
		return 0;
	}
	
	@Override
	public void onDataArrive(byte[] buf) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setWriter(Writer writer) {
		this.writer = writer;
		String key = "hello world\n";
		byte[] input = key.getBytes();
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("RSA/None/OAEPWithSHA256AndMGF1Padding", "BC");
			cipher.init(Cipher.ENCRYPT_MODE, this.pubKey);
			byte[] secret = cipher.doFinal(input);
			this.writeBytes(secret);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onConnectionFail() {
		// TODO Auto-generated method stub
		
	}
	public static void main(String[] argv) {
		BigInteger modulus = new BigInteger("17388413383649711290254825310339272211319767525363227404074762677568514182143042061437868796167591657550511002079944736281939887225873458139996723826487428401781326377898963252196351565707602724215593767581359596805150972910866410145077679809190513515791700412131297152963158161216863933118912237764396054712362877219981113432778260759907285938711897115187091296093563327334234754294283390985106557355671509013541665508032935881514071942658154269549566084778139622274561728679270315969220599430415442568612476273211334694395646067337896362196452349792104972786584396268761019495750809374141828098785614861562903854521");
		BigInteger exp = new BigInteger("65537");
		KeySpec keyspec = new RSAPublicKeySpec(modulus, exp);
		KeyFactory kf;
		try {
			kf = KeyFactory.getInstance("RSA");
			PublicKey pub = kf.generatePublic(keyspec);
			Session session = new Session(pub);
			NetEventProcessor evtproc = new NetEventProcessor();
			Thread th = new Thread(evtproc);
			th.start();
			evtproc.connect(InetAddress.getLocalHost(), 8964, "server", session);
			Thread.sleep(4000);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onCloseStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onClosed() {
		// TODO Auto-generated method stub
		
	}


}
