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
