package org.uniqush.rsa;

import static org.junit.Assert.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

public class EMSADecoderTest {

	@Test
	public void testDecode() {
		byte[] hashed = {(byte)55, (byte)182, (byte)106, (byte)224, (byte)68, (byte)88, (byte)67, (byte)53, (byte)61, (byte)71, (byte)236, (byte)176, (byte)180, (byte)253, (byte)20, (byte)193, (byte)16, (byte)230, (byte)45, (byte)106};
		byte[] encoded = {(byte)102, (byte)228, (byte)103, (byte)46, (byte)131, (byte)106, (byte)209, (byte)33, (byte)186, (byte)36, (byte)75, (byte)237, (byte)101, (byte)118, (byte)184, (byte)103, (byte)217, (byte)164, (byte)71, (byte)194, (byte)138, (byte)110, (byte)102, (byte)165, (byte)184, (byte)125, (byte)238, (byte)127, (byte)188, (byte)126, (byte)101, (byte)175, (byte)80, (byte)87, (byte)248, (byte)111, (byte)174, (byte)137, (byte)132, (byte)217, (byte)186, (byte)127, (byte)150, (byte)154, (byte)214, (byte)254, (byte)2, (byte)164, (byte)215, (byte)95, (byte)116, (byte)69, (byte)254, (byte)253, (byte)216, (byte)91, (byte)109, (byte)58, (byte)71, (byte)124, (byte)40, (byte)210, (byte)75, (byte)161, (byte)227, (byte)117, (byte)111, (byte)121, (byte)45, (byte)209, (byte)220, (byte)232, (byte)202, (byte)148, (byte)68, (byte)14, (byte)203, (byte)82, (byte)121, (byte)236, (byte)211, (byte)24, (byte)58, (byte)49, (byte)31, (byte)200, (byte)150, (byte)218, (byte)28, (byte)179, (byte)147, (byte)17, (byte)175, (byte)55, (byte)234, (byte)74, (byte)117, (byte)226, (byte)75, (byte)219, (byte)253, (byte)92, (byte)29, (byte)160, (byte)222, (byte)124, (byte)236, (byte)223, (byte)26, (byte)137, (byte)111, (byte)157, (byte)139, (byte)200, (byte)22, (byte)217, (byte)124, (byte)215, (byte)162, (byte)196, (byte)59, (byte)173, (byte)84, (byte)111, (byte)190, (byte)140, (byte)254, (byte)188};

		MessageDigest hash = null;
		try { 
			hash = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		EMSADecoder decoder = new EMSADecoder(hash);
		boolean valid = decoder.decode(hashed, encoded, 1023, 20);
		if (!valid) {
			fail("Should be a valid encoded signature");
		}
	}	

}
