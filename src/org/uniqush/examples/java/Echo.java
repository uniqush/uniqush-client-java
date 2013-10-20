package org.uniqush.examples.java;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;

import javax.security.auth.login.LoginException;
import org.uniqush.client.CredentialProvider;
import org.uniqush.client.MessageCenter;

public class Echo {
	public static void main(String[] argv) {
		CredentialProvider cp = new UserCredentialProvider();
		try {
			MessageCenter center = new MessageCenter(cp);
			MessageEcho msgHandler = new MessageEcho(center);
			center.connect("127.0.0.1", 8964, "service", "monnand", msgHandler);
			//center.config(0, 32, null);
			center.setVisibility(false);
			Thread th = new Thread(center);
			th.start();
			//Thread.sleep(6 * 1000);
			//center.stop();
			
			// We want to retrieve all messages 10 min ago.
			long now = new Date().getTime();
			Date since = new Date(now - 10 * 60 * 1000);
			center.requestAllSince(since);
			
			th.join();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (LoginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
