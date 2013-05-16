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

package org.uniqush.rsa;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

import org.junit.Test;

public class RSASSAPSSVerifierTest {

	@Test
	public void testVerifyByteArray() {
		BigInteger modulus = new BigInteger("116169470677379136880601217863818349262568478442010864168681707939294733382772665493767578000070487762965366690492174549705615145343554039166153099065910102106103352414659723745586610195947338062846761745176087441561202625599418142717700813833733236525095798695465414527889778229651859962852432925649431830839");
		BigInteger exp = new BigInteger("65537");
		KeySpec keyspec = new RSAPublicKeySpec(modulus, exp);
		KeyFactory kf = null;
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		RSAPublicKey pub = null;
		try {
			pub = (RSAPublicKey) kf.generatePublic(keyspec);
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		byte[] msg = {(byte) 205, (byte)200, (byte)125, (byte)162, (byte)35, (byte)215, (byte)134, (byte)223, (byte)59, (byte)69, (byte)224, (byte)187, (byte)188, (byte)114, (byte)19, (byte)38, (byte)209, (byte)238, (byte)42, (byte)248, (byte)6, (byte)204, (byte)49, (byte)84, (byte)117, (byte)204, (byte)111, (byte)13, (byte)156, (byte)102, (byte)225, (byte)182, (byte)35, (byte)113, (byte)212, (byte)92, (byte)226, (byte)57, (byte)46, (byte)26, (byte)201, (byte)40, (byte)68, (byte)195, (byte)16, (byte)16, (byte)47, (byte)21, (byte)106, (byte)13, (byte)141, (byte)82, (byte)193, (byte)244, (byte)196, (byte)11, (byte)163, (byte)170, (byte)101, (byte)9, (byte)87, (byte)134, (byte)203, (byte)118, (byte)151, (byte)87, (byte)166, (byte)86, (byte)59, (byte)169, (byte)88, (byte)254, (byte)208, (byte)188, (byte)201, (byte)132, (byte)232, (byte)181, (byte)23, (byte)163, (byte)213, (byte)245, (byte)21, (byte)178, (byte)59, (byte)138, (byte)65, (byte)231, (byte)74, (byte)168, (byte)103, (byte)105, (byte)63, (byte)144, (byte)223, (byte)176, (byte)97, (byte)166, (byte)232, (byte)109, (byte)250, (byte)174, (byte)230, (byte)68, (byte)114, (byte)192, (byte)14, (byte)95, (byte)32, (byte)148, (byte)87, (byte)41, (byte)203, (byte)235, (byte)231, (byte)127, (byte)6, (byte)206, (byte)120, (byte)224, (byte)143, (byte)64, (byte)152, (byte)251, (byte)164, (byte)31, (byte)157, (byte)97, (byte)147, (byte)192, (byte)49, (byte)126, (byte)139, (byte)96, (byte)212, (byte)182, (byte)8, (byte)74, (byte)203, (byte)66, (byte)210, (byte)158, (byte)56, (byte)8, (byte)163, (byte)188, (byte)55, (byte)45, (byte)133, (byte)227, (byte)49, (byte)23, (byte)15, (byte)203, (byte)247, (byte)204, (byte)114, (byte)208, (byte)183, (byte)28, (byte)41, (byte)102, (byte)72, (byte)179, (byte)164, (byte)209, (byte)15, (byte)65, (byte)98, (byte)149, (byte)208, (byte)128, (byte)122, (byte)166, (byte)37, (byte)202, (byte)178, (byte)116, (byte)79, (byte)217, (byte)234, (byte)143, (byte)210, (byte)35, (byte)196, (byte)37, (byte)55, (byte)2, (byte)152, (byte)40, (byte)189, (byte)22, (byte)190, (byte)2, (byte)84, (byte)111, (byte)19, (byte)15, (byte)210, (byte)227, (byte)59, (byte)147, (byte)109, (byte)38, (byte)118, (byte)224, (byte)138, (byte)237, (byte)27, (byte)115, (byte)49, (byte)139, (byte)117, (byte)10, (byte)1, (byte)103, (byte)208};
		byte[] signature = {(byte) 144, (byte)116, (byte)48, (byte)143, (byte)181, (byte)152, (byte)233, (byte)112, (byte)27, (byte)34, (byte)148, (byte)56, (byte)142, (byte)82, (byte)249, (byte)113, (byte)250, (byte)172, (byte)43, (byte)96, (byte)165, (byte)20, (byte)90, (byte)241, (byte)133, (byte)223, (byte)82, (byte)135, (byte)181, (byte)237, (byte)40, (byte)135, (byte)229, (byte)124, (byte)231, (byte)253, (byte)68, (byte)220, (byte)134, (byte)52, (byte)228, (byte)7, (byte)200, (byte)224, (byte)228, (byte)54, (byte)11, (byte)194, (byte)38, (byte)243, (byte)236, (byte)34, (byte)127, (byte)157, (byte)158, (byte)84, (byte)99, (byte)142, (byte)141, (byte)49, (byte)245, (byte)5, (byte)18, (byte)21, (byte)223, (byte)110, (byte)187, (byte)156, (byte)47, (byte)149, (byte)121, (byte)170, (byte)119, (byte)89, (byte)138, (byte)56, (byte)249, (byte)20, (byte)181, (byte)185, (byte)193, (byte)189, (byte)131, (byte)196, (byte)226, (byte)249, (byte)243, (byte)130, (byte)160, (byte)208, (byte)170, (byte)53, (byte)66, (byte)255, (byte)238, (byte)101, (byte)152, (byte)74, (byte)96, (byte)27, (byte)198, (byte)158, (byte)178, (byte)141, (byte)235, (byte)39, (byte)220, (byte)161, (byte)44, (byte)130, (byte)194, (byte)212, (byte)195, (byte)246, (byte)108, (byte)213, (byte)0, (byte)241, (byte)255, (byte)43, (byte)153, (byte)77, (byte)138, (byte)78, (byte)48, (byte)203, (byte)179, (byte)60};
		try {
			Signature verifier = new RSASSAPSSVerifier("SHA1");
			verifier.initVerify(pub);
			verifier.update(msg);
			boolean good = verifier.verify(signature);
			if (!good) {
				fail("should be a valid signature");
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

//	
//	private void printBytes(String prefix, byte[] data) {
//		System.out.printf(prefix);
//		for (int i = 0; i < data.length; i++) {
//			System.out.printf("%d,", 0xFF & data[i]);
//		}
//		System.out.println();
//	}
	

}
