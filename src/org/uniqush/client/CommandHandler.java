package org.uniqush.client;

interface CommandHandler {
	public void OnCommand(Command cmd);
	public void OnKeyExchangeError(String reason);
	public void OnAuthenticationError();
}
