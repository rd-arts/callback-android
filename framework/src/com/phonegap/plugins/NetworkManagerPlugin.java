/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010-2011, IBM Corporation
 */
package com.phonegap.plugins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import org.json.JSONArray;

public class NetworkManagerPlugin extends Plugin {

	private static final String TAG = "GAP_" + NetworkManagerPlugin.class.getSimpleName();

	public static int NOT_REACHABLE = 0;
	public static int REACHABLE_VIA_CARRIER_DATA_NETWORK = 1;
	public static int REACHABLE_VIA_WIFI_NETWORK = 2;

	private static final String WIFI = "wifi";
	public static final String WIMAX = "wimax";
	// mobile
	private static final String MOBILE = "mobile";
	// 2G network types
	private static final String GSM = "gsm";
	private static final String GPRS = "gprs";
	private static final String EDGE = "edge";
	// 3G network types
	private static final String CDMA = "cdma";
	private static final String UMTS = "umts";
	private static final String HSPA = "hspa";
	private static final String HSUPA = "hsupa";
	private static final String HSDPA = "hsdpa";
	private static final String ONEXRTT = "1xrtt";
	private static final String EHRPD = "ehrpd";
	// 4G network types
	private static final String LTE = "lte";
	private static final String UMB = "umb";
	private static final String HSPA_PLUS = "hspa+";
	// return types
	private static final String TYPE_UNKNOWN = "unknown";
	public static final String TYPE_ETHERNET = "ethernet";
	private static final String TYPE_WIFI = "wifi";
	private static final String TYPE_2G = "2g";
	private static final String TYPE_3G = "3g";
	private static final String TYPE_4G = "4g";
	private static final String TYPE_NONE = "none";

	private String connectionCallbackId;

	private ConnectivityManager sockMan;
	private BroadcastReceiver receiver;

	/**
	 * Constructor.
	 */
	public NetworkManagerPlugin() {
		this.receiver = null;
	}

	/**
	 * Sets the context of the Command. This can then be used to do things like
	 * get file paths associated with the Activity.
	 *
	 * @param ctx The context of the main Activity.
	 */
	@Override
	public void setContext(Context context) {
		super.setContext(context);
		this.sockMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		this.connectionCallbackId = null;

		// We need to listen to connectivity events to update navigator.connection
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		if (this.receiver == null) {
			this.receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					updateConnectionInfo((NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO));
				}
			};
			context.registerReceiver(this.receiver, intentFilter);
		}

	}

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
		PluginResult.Status status = PluginResult.Status.INVALID_ACTION;
		String result = "Unsupported Operation: " + action;

		if (action.equals("getConnectionInfo")) {
			this.connectionCallbackId = callbackId;
			NetworkInfo info = sockMan.getActiveNetworkInfo();
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, this.getConnectionInfo(info));
			pluginResult.setKeepCallback(true);
			return pluginResult;
		}

		return new PluginResult(status, result);
	}

	/**
	 * Identifies if action to be executed returns a value and should be run synchronously.
	 *
	 * @param action The action to execute
	 * @return T=returns value
	 */
	@Override
	public boolean isSynch(String action) {
		// All methods take a while, so always use async
		return false;
	}

	/**
	 * Stop network receiver.
	 */
	@Override
	public void onDestroy() {
		if (this.receiver != null) {
			try {
				this.context.unregisterReceiver(this.receiver);
			} catch (Exception e) {
				Log.e(TAG, "unregisterReceiver error onDestroy.", e);
			}
		}
	}

	//--------------------------------------------------------------------------
	// LOCAL METHODS
	//--------------------------------------------------------------------------


	/**
	 * Updates the JavaScript side whenever the connection changes
	 *
	 * @param info the current active network info
	 * @return
	 */
	private void updateConnectionInfo(NetworkInfo info) {
		// send update to javascript "navigator.network.connection"
		sendUpdate(this.getConnectionInfo(info));
	}

	/**
	 * Get the latest network connection information
	 *
	 * @param info the current active network info
	 * @return a JSONObject that represents the network info
	 */
	private String getConnectionInfo(NetworkInfo info) {
		String type = TYPE_NONE;
		if (info != null) {
			// If we are not connected to any network set type to none
			if (!info.isConnected()) {
				type = TYPE_NONE;
			} else {
				type = getType(info);
			}
		}
		return type;
	}

	/**
	 * Create a new plugin result and send it back to JavaScript
	 *
	 * @param connection the network info to set as navigator.connection
	 */
	private void sendUpdate(String type) {
		PluginResult result = new PluginResult(PluginResult.Status.OK, type);
		result.setKeepCallback(true);
		this.success(result, this.connectionCallbackId);
	}

	/**
	 * Determine the type of connection
	 *
	 * @param info the network info so we can determine connection type.
	 * @return the type of mobile network we are on
	 */
	private String getType(NetworkInfo info) {
		if (info != null) {
			String type = info.getTypeName();

			if (type.toLowerCase().equals(WIFI)) {
				return TYPE_WIFI;
			} else if (type.toLowerCase().equals(MOBILE)) {
				type = info.getSubtypeName();
				if (type.toLowerCase().equals(GSM) ||
						type.toLowerCase().equals(GPRS) ||
						type.toLowerCase().equals(EDGE)) {
					return TYPE_2G;
				} else if (type.toLowerCase().startsWith(CDMA) ||
						type.toLowerCase().equals(UMTS) ||
						type.toLowerCase().equals(ONEXRTT) ||
						type.toLowerCase().equals(EHRPD) ||
						type.toLowerCase().equals(HSUPA) ||
						type.toLowerCase().equals(HSDPA) ||
						type.toLowerCase().equals(HSPA)) {
					return TYPE_3G;
				} else if (type.toLowerCase().equals(LTE) ||
						type.toLowerCase().equals(UMB) ||
						type.toLowerCase().equals(HSPA_PLUS)) {
					return TYPE_4G;
				}
			}
		} else {
			return TYPE_NONE;
		}
		return TYPE_UNKNOWN;
	}
}
