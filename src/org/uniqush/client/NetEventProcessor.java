/*
 * Copyright 2013 Nan Deng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.uniqush.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.List;

public class NetEventProcessor implements Runnable {
	public NetEventProcessor() throws IOException {
		selector = SelectorProvider.provider().openSelector();
	}
	
	private class Event {
		public static final int NEW_CONNECTION = 1;
		public static final int CHANGE_OPERATION = 2;
		public static final int STOP = 3;
		public static final int DISCONNECT = 4;

		public int type;
	}

	private List<Event> pendingEvents;
	private Selector selector;
	
	private int processEvents() {
		synchronized(pendingEvents) {
			Iterator<Event> iter = this.pendingEvents.iterator();
			while (iter.hasNext()) {
				Event evt = iter.next();
				switch (evt.type) {
				case Event.STOP:
					return -1;
				}
			}
		}
		return 0;
	}
	
	private int processSelector() {
		Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
		while (selectedKeys.hasNext()) {
			SelectionKey key = (SelectionKey) selectedKeys.next();
			selectedKeys.remove();
			if (key.isValid()) {
				continue;
			}
			if (key.isReadable()) {
				
			}
		}
		return 0;
	}
	
	@Override
	public void run() {
		while (true) {
			if (processEvents() < 0) {
				break;
			}
			try {
				this.selector.select();
				processSelector();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
