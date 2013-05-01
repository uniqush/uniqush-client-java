package org.uniqush.client;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class MessagePrinter implements MessageHandler {
	public MessagePrinter() {
		
	}
	
	private void printMessage(Message msg) {
		Map<String, String> header = msg.getHeader();
		Iterator<Entry<String,String>> iter = header.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, String> entry = iter.next();
			System.out.printf("[%s=%s]", entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void onMessageFromServer(String id, Message msg) {
		System.out.printf("Message Received from server with id %s: ", id);
		printMessage(msg);
		System.out.println();
	}

	@Override
	public void onMessageFromUser(String service, String username, String id,
			Message msg) {
		System.out.printf("Message Received from %s:%s with id %s:", service, username, id);
		printMessage(msg);
		System.out.println();
	}

	@Override
	public void onMessageDigestFromServer(int size, String id,
			Map<String, String> parameters) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMessageDigestFromUser(String service, String username,
			int size, String id, Map<String, String> parameters) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCloseStart() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onClosed() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onError(Exception e) {
		e.printStackTrace();
	}

}
