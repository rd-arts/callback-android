/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010-2011, IBM Corporation
 */
package com.phonegap;

import android.util.Log;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * This class exposes methods in DroidGap that can be called from JavaScript.
 */
public class ControlViewPlugin extends Plugin {

	private static final String TAG = "GAP_AppGap";

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action	 The action to execute.
	 * @param args	   JSONArry of arguments for the plugin.
	 * @param callbackId The callback id used when calling back into JavaScript.
	 * @return A PluginResult object with a status and message.
	 */
	@Override
	public PluginResult execute(String action, JSONArray args, String callbackId) {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";

		try {
			if (action.equals("clearCache")) {
				this.clearCache();
			} else if (action.equals("loadUrl")) {
				this.loadUrl(args.getString(0), args.optJSONObject(1));
			} else if (action.equals("cancelLoadUrl")) {
				this.cancelLoadUrl();
			} else if (action.equals("clearHistory")) {
				this.clearHistory();
			} else if (action.equals("backHistory")) {
				this.backHistory();
			} else if (action.equals("overrideBackbutton")) {
				this.overrideBackbutton(args.getBoolean(0));
			} else if (action.equals("isBackbuttonOverridden")) {
				boolean b = this.isBackbuttonOverridden();
				return new PluginResult(status, b);
			} else if (action.equals("exitApp")) {
				this.exitApp();
			} else if (action.equals("addWhiteListEntry")) {
				this.addWhiteListEntry(args.getString(0), args.optBoolean(1));
			}
			return new PluginResult(status, result);
		} catch (JSONException e) {
			return new PluginResult(PluginResult.Status.JSON_EXCEPTION);
		}
	}

	//--------------------------------------------------------------------------
	// LOCAL METHODS
	//--------------------------------------------------------------------------

	/**
	 * Clear the resource cache.
	 */
	private void clearCache() {
		this.ctx.clearCache();
	}

	/**
	 * Load the url into the webview.
	 *
	 * @param url
	 * @param props Properties that can be passed in to the DroidGap activity (i.e. loadingDialog, wait, ...)
	 * @throws JSONException
	 */
	private void loadUrl(String url, JSONObject props) throws JSONException {
		Log.d(TAG, String.format("Load URL=%s,json=%s", url, props));
		int wait = 0;
		boolean openExternal = false;
		boolean clearHistory = false;

		// If there are properties, then set them on the Activity
		HashMap<String, Object> params = new HashMap<String, Object>();
		if (props != null) {
			JSONArray keys = props.names();
			for (int i = 0; i < keys.length(); i++) {
				String key = keys.getString(i);
				if (key.equals("wait")) {
					wait = props.getInt(key);
				} else if (key.equalsIgnoreCase("openexternal")) {
					openExternal = props.getBoolean(key);
				} else if (key.equalsIgnoreCase("clearhistory")) {
					clearHistory = props.getBoolean(key);
				} else {
					Object value = props.get(key);
					if (value == null) {

					} else if (value instanceof String) {
						params.put(key, value);
					} else if (value instanceof Boolean) {
						params.put(key, value);
					} else if (value instanceof Integer) {
						params.put(key, value);
					}
				}
			}
		}

		// If wait property, then delay loading

		if (wait > 0) {
			try {
				synchronized (this) {
					this.wait(wait);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.ctx.showWebPage(url, openExternal, clearHistory, params);
	}

	/**
	 * Cancel loadUrl before it has been loaded.
	 */
	private void cancelLoadUrl() {
		this.ctx.cancelLoadUrl();
	}

	/**
	 * Clear page history for the app.
	 */
	private void clearHistory() {
		this.ctx.clearHistory();
	}

	/**
	 * Go to previous page displayed.
	 * This is the same as pressing the backbutton on Android device.
	 */
	private void backHistory() {
		this.ctx.endActivity();
	}

	/**
	 * Override the default behavior of the Android back button.
	 * If overridden, when the back button is pressed, the "backKeyDown" JavaScript event will be fired.
	 *
	 * @param override T=override, F=cancel override
	 */
	private void overrideBackbutton(boolean override) {
		Log.d(TAG, "WARNING: Back Button Default Behaviour will be overridden.  The backbutton event will be fired!");
		this.ctx.bound = override;
	}

	/**
	 * Return whether the Android back button is overridden by the user.
	 *
	 * @return boolean
	 */
	private boolean isBackbuttonOverridden() {
		return this.ctx.bound;
	}

	/**
	 * Exit the Android application.
	 */
	private void exitApp() {
		this.ctx.endActivity();
	}

	/**
	 * Add entry to approved list of URLs (whitelist)
	 *
	 * @param origin	 URL regular expression to allow
	 * @param subdomains T=include all subdomains under origin
	 */
	private void addWhiteListEntry(String origin, boolean subdomains) {
		GapConfig.addWhiteListEntry(this.ctx.whiteList, origin, subdomains);
	}
}
