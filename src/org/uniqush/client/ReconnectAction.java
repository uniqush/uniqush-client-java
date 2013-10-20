package org.uniqush.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ReconnectAction extends Action {
	private String host;
	private int port;
	
	private String[] splitHostPort(String addr) {
		String ipPattern = "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+)";
		String ipV6Pattern = "\\[([a-zA-Z0-9:]+)\\]:(\\d+)";
		String hostPattern = "([\\w\\.\\-]+):(\\d+)";
		Pattern p = Pattern.compile( ipPattern + "|" + ipV6Pattern + "|" + hostPattern );
		Matcher m = p.matcher( addr );
		String[] ret = new String[2];
		if( m.matches() ) {
		    if( m.group(1) != null ) {
		        // group(1) IP address, group(2) is port
		    	ret[0] = m.group(1);
		    	ret[1] = m.group(2);
		    } else if( m.group(3) != null ) {
		        // group(3) is IPv6 address, group(4) is port
		    	ret[0] = m.group(3);
		    	ret[1] = m.group(4);
		    } else if( m.group(5) != null ) {
		        // group(5) is hostname, group(6) is port
		    	ret[0] = m.group(5);
		    	ret[1] = m.group(6);
		    } else {
		        // Not a valid address
		    	return null;
		    }
		} else {
			return null;
		}
		return ret;
	}
	
	public ReconnectAction(String addr) {
		super(Action.ACTION_RECONNECT);
		host = null;
		port = -1;
		
		String[] parts = this.splitHostPort(addr);
		
		System.out.println("I was to to reconnect to " + addr);
		
		if (parts == null || parts.length != 2) {
			return;
		}
		
		host = parts[0];
		port = Integer.parseInt(parts[1]);
	}
	
	public int getPort() {
		return port;
	}
	
	public String getHost() {
		return host;
	}
}
