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
	public void onMessageFromServer(String dstService, String dstUser,
			String id, Message msg);

	public void onMessageFromUser(String dstService, String dstUser,
			String srcService, String srcUser, String id, Message msg);

	/**
	 * 
	 * @param online
	 *            true if there exists a connection with server under the
	 *            service and the user. false if the digest is received from a
	 *            push notification provider, like GCM, ADM.
	 * @param dstService
	 *            The service name of the receiver.
	 * @param dstUser
	 *            The user name of the receiver.
	 * @param size
	 *            size of the referred message.
	 * @param id
	 *            id of the message.
	 * @param parameters
	 *            other parameters specified by the application server.
	 */
	public void onMessageDigestFromServer(boolean online, String dstService,
			String dstUser, int size, String id, Map<String, String> parameters);

	public void onMessageDigestFromUser(boolean online, String dstService,
			String dstUser, String srcService, String srcUser, int size,
			String id, Map<String, String> parameters);

	public void onCloseStart();

	public void onClosed();

	public void onError(Exception e);
}
