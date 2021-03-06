/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap;

import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

/**
 * This class provides a way for Java to run JavaScript in the web page that has loaded PhoneGap.
 * The CallbackServer class implements an XHR server and a polling server with a list of JavaScript
 * statements that are to be executed on the web page.
 * <p/>
 * The process flow for XHR is:
 * 1. JavaScript makes an async XHR call.
 * 2. The server holds the connection open until data is available.
 * 3. The server writes the data to the client and closes the connection.
 * 4. The server immediately starts listening for the next XHR call.
 * 5. The client receives this XHR response, processes it.
 * 6. The client sends a new async XHR request.
 * <p/>
 * The CallbackServer class requires the following permission in Android manifest file
 * <uses-permission android:name="android.permission.INTERNET" />
 * <p/>
 * If the device has a proxy set, then XHR cannot be used, so polling must be used instead.
 * This can be determined by the client by calling CallbackServer.usePolling().
 * <p/>
 * The process flow for polling is:
 * 1. The client calls CallbackServer.getJavascript() to retrieve next statement.
 * 2. If statement available, then client processes it.
 * 3. The client repeats #1 in loop.
 */
class CallbackServer implements Runnable {

	private static final String TAG = "GAP_" + CallbackServer.class.getSimpleName();

	/**
	 * The list of JavaScript statements to be sent to JavaScript.
	 */
	private LinkedList<String> javascript;

	/**
	 * The port to listen on.
	 */
	private int port;

	/**
	 * The server thread.
	 */
	private Thread serverThread;

	/**
	 * Indicates the server is running.
	 */
	private boolean active;

	/**
	 * Indicates that the JavaScript statements list is empty
	 */
	private volatile boolean empty;

	/**
	 * Indicates that polling should be used instead of XHR.
	 */
	private boolean usePolling = true;

	/**
	 * Security token to prevent other apps from accessing this callback server via XHR
	 */
	private String token;

	/**
	 * Constructor.
	 */
	public CallbackServer() {
		Log.d(TAG, "CallbackServer()");
		this.active = false;
		this.empty = true;
		this.port = 0;
		this.javascript = new LinkedList<String>();
	}

	/**
	 * Init callback server and start XHR if running local app.
	 * <p/>
	 * If PhoneGap app is loaded from file://, then we can use XHR
	 * otherwise we have to use polling due to cross-domain security restrictions.
	 *
	 * @param url The URL of the PhoneGap app being loaded
	 */
	public void init(String url) {
		Log.d(TAG, "CallbackServer.start(" + url + ")");

		// Determine if XHR or polling is to be used
		if ((url != null) && !url.startsWith("file://")) {
			this.usePolling = true;
			this.stopServer();
		} else if (android.net.Proxy.getDefaultHost() != null) {
			this.usePolling = true;
			this.stopServer();
		} else {
			this.usePolling = false;
			this.startServer();
		}
	}

	/**
	 * Return if polling is being used instead of XHR.
	 *
	 * @return
	 */
	public boolean usePolling() {
		return this.usePolling;
	}

	/**
	 * Get the port that this server is running on.
	 *
	 * @return
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Get the security token that this server requires when calling getJavascript().
	 *
	 * @return
	 */
	public String getToken() {
		return this.token;
	}

	/**
	 * Start the server on a new thread.
	 */
	private void startServer() {
		Log.d(TAG, "CallbackServer.startServer()");
		this.active = false;

		// Start server on new thread
		this.serverThread = new Thread(this);
		this.serverThread.start();
	}

	/**
	 * Restart the server on a new thread.
	 */
	public void restartServer() {

		// Stop server
		this.stopServer();

		// Start server again
		this.startServer();
	}

	/**
	 * Start running the server.
	 * This is called automatically when the server thread is started.
	 */
	@Override
	public void run() {

		// Start server
		try {
			this.active = true;
			String request;
			ServerSocket waitSocket = new ServerSocket(0);
			this.port = waitSocket.getLocalPort();
			Log.d(TAG, "CallbackServer -- using port " + this.port);
			this.token = java.util.UUID.randomUUID().toString();
			Log.d(TAG, "CallbackServer -- using token " + this.token);

			while (this.active) {
				//Log.v(TAG, "CallbackServer: Waiting for data on socket");
				Socket connection = waitSocket.accept();
				BufferedReader xhrReader = new BufferedReader(new InputStreamReader(connection.getInputStream()), 40);
				DataOutputStream output = new DataOutputStream(connection.getOutputStream());
				request = xhrReader.readLine();
				String response = "";
				//Log.d(TAG, "Request=" + request);
				if (this.active && (request != null)) {
					if (request.contains("GET")) {

						// Get requested file
						String[] requestParts = request.split(" ");

						// Must have security token
						if ((requestParts.length == 3) && (requestParts[1].substring(1).equals(this.token))) {
							//Log.v(TAG, "Processing GET request");

							// Wait until there is some data to send, or send empty data every 10 sec
							// to prevent XHR timeout on the client
							synchronized (this) {
								while (this.empty) {
									try {
										this.wait(10000); // prevent timeout from happening
										//Log.v(TAG, ">>> break <<< Look-ahead: " + ((javascript != null) ? javascript.peek() : ""));
										break;
									} catch (InterruptedException e) {
										Log.i(TAG, "wait empty interrupted");
									}
								}
							}

							// If server is still running
							if (this.active) {

								// If no data, then send 404 back to client before it times out
								if (this.empty) {
									//Log.v(TAG, "Sending zero data 404.");
									response = "HTTP/1.1 404 NO DATA\r\n\r\n "; // need to send content otherwise some Android devices fail, so send space
								} else {
									//Log.v(TAG, "Sending JavaScript");
									response = "HTTP/1.1 200 OK\r\n\r\n";
									String js = this.getJavascript();
									if (js != null) {
										response += encode(js, "UTF-8");
									}
								}
							} else {
								response = "HTTP/1.1 503 Service Unavailable\r\n\r\n ";
							}
						} else {
							response = "HTTP/1.1 403 Forbidden\r\n\r\n ";
						}
					} else {
						response = "HTTP/1.1 400 Bad Request\r\n\r\n ";
					}
					if (!this.empty) Log.d(TAG, "Closing output. Resp=" + response);
					output.writeBytes(response);
					output.flush();
				}
				output.close();
				xhrReader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "IO", e);
		}
		this.active = false;
		Log.d(TAG, "CallbackServer.startServer() - EXIT");
	}

	/**
	 * Stop server.
	 * This stops the thread that the server is running on.
	 */
	private void stopServer() {
		Log.d(TAG, "CallbackServer.stopServer()");
		if (this.active) {
			this.active = false;

			// Break out of server wait
			synchronized (this) {
				this.notify();
			}
		}
	}

	/**
	 * Destroy
	 */
	public void destroy() {
		this.stopServer();
	}

	/**
	 * Get the number of JavaScript statements.
	 *
	 * @return int
	 */
	public int getSize() {
		int size = this.javascript.size();
		Log.d(TAG, "getSize() = " + size);
		return size;
	}

	/**
	 * Get the next JavaScript statement and remove from list.
	 *
	 * @return String
	 */
	public String getJavascript() {
		if (this.javascript.size() == 0) {
			// Log.v(TAG, "getJS(): No new JS available");
			return null;
		}
		String statement = this.javascript.remove(0);
		Log.v(TAG, "getJS()=" + statement);
		if (this.javascript.size() == 0) {
			synchronized (this) {
				this.empty = true;
			}
		}
		return statement;
	}

	/**
	 * Add a JavaScript statement to the list.
	 *
	 * @param statement
	 */
	public void sendJavascript(String statement) {
		Log.v(TAG, "sendJS=" + statement);
		this.javascript.add(statement);
		synchronized (this) {
			this.empty = false;
			this.notify();
		}
	}

	/* The Following code has been modified from original implementation of URLEncoder */

	/* start */

	/*
		 *  Licensed to the Apache Software Foundation (ASF) under one or more
		 *  contributor license agreements.  See the NOTICE file distributed with
		 *  this work for additional information regarding copyright ownership.
		 *  The ASF licenses this file to You under the Apache License, Version 2.0
		 *  (the "License"); you may not use this file except in compliance with
		 *  the License.  You may obtain a copy of the License at
		 *
		 *     http://www.apache.org/licenses/LICENSE-2.0
		 *
		 *  Unless required by applicable law or agreed to in writing, software
		 *  distributed under the License is distributed on an "AS IS" BASIS,
		 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		 *  See the License for the specific language governing permissions and
		 *  limitations under the License.
		 */
	private static final String digits = "0123456789ABCDEF";

	/**
	 * This will encode the return value to JavaScript.  We revert the encoding for
	 * common characters that don't require encoding to reduce the size of the string
	 * being passed to JavaScript.
	 *
	 * @param s   to be encoded
	 * @param enc encoding type
	 * @return encoded string
	 */
	private static String encode(String s, String enc) throws UnsupportedEncodingException {
		if (s == null || enc == null) {
			throw new NullPointerException();
		}
		// check for UnsupportedEncodingException
		"".getBytes(enc);

		// Guess a bit bigger for encoded form
		StringBuilder buf = new StringBuilder(s.length() + 16);
		int start = -1;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
					|| (ch >= '0' && ch <= '9')
					|| " .-*_'(),<>=?@[]{}:~\"\\/;!".indexOf(ch) > -1) {
				if (start >= 0) {
					convert(s.substring(start, i), buf, enc);
					start = -1;
				}
				if (ch != ' ') {
					buf.append(ch);
				} else {
					buf.append(' ');
				}
			} else {
				if (start < 0) {
					start = i;
				}
			}
		}
		if (start >= 0) {
			convert(s.substring(start, s.length()), buf, enc);
		}
		return buf.toString();
	}

	private static void convert(String s, StringBuilder buf, String enc) throws UnsupportedEncodingException {
		byte[] bytes = s.getBytes(enc);
		for (int j = 0; j < bytes.length; j++) {
			buf.append('%');
			buf.append(digits.charAt((bytes[j] & 0xf0) >> 4));
			buf.append(digits.charAt(bytes[j] & 0xf));
		}
	}

	/* end */
}
