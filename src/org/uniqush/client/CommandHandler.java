package org.uniqush.client;

interface CommandHandler {
	public void OnCommand(Command cmd);
	public void OnKeyExchangeError();
	public void OnAuthenticationError();
}
