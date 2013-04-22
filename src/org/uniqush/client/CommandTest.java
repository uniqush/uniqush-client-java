package org.uniqush.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class CommandTest {

	@Test
	public void testMarshal() {
	}

	@Test
	public void testCommand() {
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
