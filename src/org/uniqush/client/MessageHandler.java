package org.uniqush.client;

import java.util.Map;

public interface MessageHandler {
	public void onMessageFromServer(String id, Message msg);
	public void onMessageFromUser(String service, String username, String id, Message msg);
	public void onMessageDigestFromServer(int size, String id, Map<String, String> parameters);
	public void onMessageDigestFromUser(String service, String username, int size, String id, Map<String, String> parameters);
	
	public void onCloseStart();
	public void onClosed();
	public void onError(Exception e);
}
