/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.websocket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * Example of a simple POJO defining a WebSocket server endpoint using
 * annotations. When a message is received, it performs a simple
 * braodcast to send the message out to all connected sessions (including back
 * to the sender). It further queues a Runnable with the ManagedExecutorService
 * provided by Java EE7 Concurrency Utilities (that is injected using CDI). The
 * runnable does a simple sleep, and then re-broadcasts the message.
 * 
 * <p>
 * The value is the URI relative to your app’s context root,  e.g. the context
 * root for this application is <code>websocket</code>, which makes the
 * WebSocket URL used to reach this endpoint
 * <code>ws://localhost/websocket/EchoAsyncEndpoint</code>.
 * </p>
 * <p>
 * The methods below are annotated for lifecycle (onOpen, onClose, onError), or
 * message (onMessage) events.
 * </p>
 * <p>
 * By default, a new instance of server endpoint is instantiated for each client
 * connection (section 3.1.7 of JSR 356 specification).
 * </p>
 */
@ServerEndpoint(value = "/EchoAsyncEndpoint")
public class EchoAsyncEndpoint extends EchoCommon {
	
	/** CDI injection of Java EE7 Managed executor service */
	@Resource
	ManagedExecutorService executor;

	/** message id: incremented as messages are received by this endpoint */
	int count = 0;

	/**
	 * @param session Session for established WebSocket
	 * @param ec endpoint configuration
	 * 
	 * @see EchoCommon#endptId
	 */
	@OnOpen
	public void onOpen(Session session, EndpointConfig ec) {
		// (lifecycle) Called when the connection is opened.
		Hello.log(this, "Endpoint " + endptId + " is open!");
		
		// Store the endpoint id in the session so that when we log and push 
		// messages around, we have something more user-friendly to look at.
		session.getUserProperties().put("endptId", endptId);
	}

	@OnClose
	public void onClose(Session session, CloseReason reason) {
		// (lifecycle) Called when the connection is closed
		Hello.log(this, "Endpoint " + endptId + " is closed!");
	}

	@OnMessage
	public void receiveMessage(final String message, final Session session) throws IOException {
		// Called when a message is received.
		// Single endpoint per connection by default --> @OnMessage methods are single threaded!
		// Endpoint/per-connection instances can see each other through sessions.

		if ("stop".equals(message)) {
			Hello.log(this, "Endpoint " + endptId + " was asked to stop");
			session.close();
		} else if (message.startsWith(AnnotatedClientEndpoint.NEW_CLIENT)) {
			AnnotatedClientEndpoint.connect(message);
		} else {
			final int id = count++;
			broadcast(session, id, message); // in EchoCommon

			executor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
					broadcast(session, id, message + " (delayed)"); // in EchoCommon
				}
			});
		}
	}

	@OnError
	public void onError(Throwable t) {
		// (lifecycle) Called if/when an error occurs and the connection is disrupted
		Hello.log(this, "oops: " + t);
		t.printStackTrace();
	}
}
