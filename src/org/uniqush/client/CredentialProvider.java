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

import java.security.interfaces.RSAPublicKey;

/**
 * The implementation should provide credential information like
 * the public key of the server, the token for some user.
 * @author monnand
 */
public interface CredentialProvider {
	String getToken(String service, String username);
	RSAPublicKey getPublicKey(String addr, int port);
}
