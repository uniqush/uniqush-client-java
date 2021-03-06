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

import java.util.Arrays;

import org.junit.Test;

public class CommandTest {

	@Test
	public void testEqualsNoMessage() {
		Command cmd1 = new Command(1, null);
		cmd1.AppendParameter("hello");
		cmd1.AppendParameter("");
		
		Command cmd2 = new Command(1, null);
		cmd2.AppendParameter("hello");
		cmd2.AppendParameter("");
		if (!cmd1.equals(cmd2)) {
			fail("should equal");
		}
		if (!cmd2.equals(cmd1)) {
			fail("should equal");
		}
	}
	
	@Test
	public void testNotEqualEmptyParamNoMessage() {
		Command cmd1 = new Command(1, null);
		
		Command cmd2 = new Command(1, null);
		cmd2.AppendParameter("hello");
		cmd2.AppendParameter("");
		if (cmd1.equals(cmd2)) {
			fail("should not equal");
		}
		if (cmd2.equals(cmd1)) {
			fail("should not equal");
		}
	}

	
	@Test
	public void testNotEqualsNoMessage() {
		Command cmd1 = new Command(1, null);
		cmd1.AppendParameter("hello");
		cmd1.AppendParameter("aa");
		
		Command cmd2 = new Command(1, null);
		cmd2.AppendParameter("hello");
		cmd2.AppendParameter("");
		if (cmd1.equals(cmd2)) {
			fail("should not equal");
		}
	}

	@Test
	public void testMarshalNoMessage() {
		Command cmd = new Command(1, null);
		cmd.AppendParameter("hello");
		cmd.AppendParameter("");
		byte[] correct = {1,32,0,0,104,101,108,108,111,0,0};
		byte[] data = cmd.marshal();
		if (!Arrays.equals(data, correct)) {
			fail("bad marshal");
		}
	}

	@Test
	public void testCommandNoMessage() {
		byte[] data = {1,32,0,0,104,101,108,108,111,0,0};
		Command cmd = new Command(data);
		if (cmd.nrParameters() != 2) {
			fail("invalid number of parameters ");
		}
		String p = cmd.getParameter(0);
		if (!p.equals("hello")) {
			fail("first parameter is wrong");
		}
		p = cmd.getParameter(1);
		if (p.length() > 0) {
			fail("second parameter is wrong");
		}
	}

}
