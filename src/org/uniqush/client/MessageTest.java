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
