package org.uniqush.client;

import java.util.Map;

public class MessagePrinter implements MessageHandler {
	public MessagePrinter() {
		
	}

	@Override
	public void onMessageFromServer(String id, Message msg) {
		System.out.printf("Message Received from server with id %s: %s", id, msg.toString());
	}

	@Override
	public void onMessageFromUser(String service, String username, String id,
			Message msg) {
		System.out.printf("Message Received from %s:%s with id %s: %s", service, username, id, msg.toString());

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
