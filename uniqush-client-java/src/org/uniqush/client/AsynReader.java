package org.uniqush.client;

public interface AsynReader {
	void OnDataArrive(String name, byte[] buf);
}
