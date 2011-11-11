/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap.api;

import android.content.Intent;

/**
 * The Phonegap activity abstract class that is extended by DroidGap.
 * It is used to isolate plugin development, and remove dependency on entire Phonegap library.
 */
public interface PhonegapActivity {

	/**
	 * Add a class that implements a service.
	 *
	 * @param serviceType
	 * @param className
	 */
	public void addService(String serviceType, String className);

	/**
	 * Send JavaScript statement back to JavaScript.
	 *
	 * @param message
	 */
	public void sendJavascript(String statement);

	/**
	 * Launch an activity for which you would like a result when it finished. When this activity exits,
	 * your onActivityResult() method will be called.
	 *
	 * @param command	 The command object
	 * @param intent	  The intent to start
	 * @param requestCode The request code that is passed to callback to identify the activity
	 */
	public void startActivityForResult(IPlugin command, Intent intent, int requestCode);

	/**
	 * Load the specified URL in the PhoneGap webview.
	 *
	 * @param url The URL to load.
	 */
	public void loadUrl(String url);
}
