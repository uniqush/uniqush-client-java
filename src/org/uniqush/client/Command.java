package org.uniqush.client;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class Command {
	private byte type;
	private String[] params;
	private Message msg;
	private final Charset UTF_8 = Charset.forName("UTF-8");
	
	private int cutString(byte[] data, int start) {
		for (int i = start; i < data.length; i++) {
			if (data[i] == 0) {
				return i;
			}
		}
		return data.length;
	}
	
	public byte[] Marshal() {
		HashMap<String, String> header = null;
		byte[] body = null;
		
		int nrBytes = 4;
		int nrEntities = 0;
		
		if (this.params != null) {
			nrEntities += this.params.length;
		}
		
		if (this.msg != null) {
			header = this.msg.getHeader();
			if (header != null) {
				nrEntities += header.size() * 2;
			}
			body = this.msg.getBody();
			if (body != null) {
				nrBytes += body.length;
			}
		}
		
		ArrayList<byte[]> list = null;
		if (nrEntities > 0) {
			list = new ArrayList<byte[]>(nrEntities);

			if (this.params != null) {
				for (int i = 0; i < this.params.length; i++) {
					byte[] p = this.params[i].getBytes(UTF_8);
					nrBytes += p.length + 1;
					list.add(p);
				}
			}
			if (header != null) {
				Iterator<Entry<String, String>> iter = header.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<String, String> entry = iter.next();
					String key = entry.getKey();
					String value = entry.getValue();
					
					byte[] keyb = key.getBytes(UTF_8);
					byte[] valueb = value.getBytes(UTF_8);
					
					nrBytes += keyb.length + 1;
					nrBytes += valueb.length + 1;
					
					list.add(keyb);
					list.add(valueb);
				}
			}
		}
		
		byte[] ret = new byte[nrBytes];
		
		ret[0] = this.type;
		if (this.params != null) {
			ret[1] = (byte) (0x0000000F & this.params.length);
			ret[1] = (byte) (ret[1] << 4);
		}
		
		if (header != null) {
			int n = header.size();
			ret[2] = (byte)((0x0000FF00 & n) >> 8);
			ret[3] = (byte) (0x000000FF & n);
		}
		int start = 4;
		Iterator<byte[]> iter = list.iterator();
		while (iter.hasNext()) {
			byte[] data = iter.next();
			System.arraycopy(data, 0, ret, start, data.length);
			start += data.length;
			ret[start] = 0;
			start++;
		}
		if (body != null) {
			System.arraycopy(body, 0, ret, start, body.length);
		}
		return ret;
	}

	public Command(byte[] data) {
		if (data.length < 4) {
			return;
		}
		type = data[0];
		int nrParams = data[1] >> 4;
		int nrHeaders = data[2];
		nrHeaders = nrHeaders << 8;
		nrHeaders |= data[3];
		
		int start = 4;
		
		if (nrParams > 0) {
			this.params = new String[nrParams];
			
			for (int i = 0; i < nrParams; i++) {
				int end = cutString(data, start);
				this.params[i] = new String(data, start, end - start, UTF_8);
				start = end + 1;
			}
		}
		HashMap<String, String> header = null;
		byte[] body = null;
		if (nrHeaders > 0) {
			header = new HashMap<String,String>(nrHeaders);
			
			for (int i = 0; i < nrHeaders; i++) {
				int end = cutString(data, start);
				start = end + 1;
				String key = new String(data, start, end - start, UTF_8);
				
				end = cutString(data, start);
				start = end + 1;
				String value = new String(data, start, end - start, UTF_8);
				
				header.put(key, value);
			}
		}
		if (start < data.length) {
			body = new byte[data.length - start];
			System.arraycopy(data, start, body, 0, data.length - start);
		}
		if (header != null || body != null) {
			this.msg = new Message(header, body);
		}
	}
}
