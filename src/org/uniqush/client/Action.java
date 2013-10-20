package org.uniqush.client;

abstract class Action {
	final static int ACTION_NOTHING = 0;
	final static int ACTION_RECONNECT = 1;
	final static int ACTION_CLOSE = 2;
	
	private int actionType;
	public Action(int type) {
		this.actionType = type;
	}
	
	public int type() {
		return this.actionType;
	}
}
