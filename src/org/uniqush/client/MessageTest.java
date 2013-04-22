package org.uniqush.client;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Test;

public class MessageTest {

	@Test
	public void testIsEmpty() {
		Message msg = new Message(null, null);
		if (!msg.isEmpty()) {
			fail("should be empty");
		}
		
		HashMap<String, String> header = new HashMap<String, String>(10);
		msg = new Message(header, null);
		if (!msg.isEmpty()) {
			fail("should be empty");
		}
		
		byte[] data = {};
		msg = new Message(header, data);
		if (!msg.isEmpty()) {
			fail("should be empty");
		}
		
		byte[] d = {1,2,3};
		msg = new Message(header, d);
		if (msg.isEmpty()) {
			fail("should not be empty");
		}
	}

	@Test
	public void testEqualsMessage() {
		HashMap<String, String> header = new HashMap<String, String>(10);
		header.put("hello", "world");
		byte[] d = {1,2,3};
		Message msg = new Message(header, d);
		
		if (!msg.equals(msg)) {
			fail("should equal");
		}
	}

}
