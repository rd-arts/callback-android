/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class PluginResult {

	public enum Status {
		NO_RESULT,
		OK,
		CLASS_NOT_FOUND_EXCEPTION,
		ILLEGAL_ACCESS_EXCEPTION,
		INSTANTIATION_EXCEPTION,
		MALFORMED_URL_EXCEPTION,
		IO_EXCEPTION,
		INVALID_ACTION,
		JSON_EXCEPTION,
		ERROR
	}

	private static String[] StatusMessages = new String[]{
			"No result",
			"OK",
			"Class not found",
			"Illegal access",
			"Instantiation error",
			"Malformed url",
			"IO error",
			"Invalid action",
			"JSON error",
			"Error"
	};

	private final int status;
	private final String message;
	private boolean keepCallback = false;
	private String cast = null;

	public PluginResult(Status status) {
		this.status = status.ordinal();
		this.message = "'" + PluginResult.StatusMessages[this.status] + "'";
	}

	public PluginResult(Status status, String message) {
		this.status = status.ordinal();
		this.message = JSONObject.quote(message);
	}

	public PluginResult(Status status, JSONArray message, String cast) {
		this.status = status.ordinal();
		this.message = message.toString();
		this.cast = cast;
	}

	public PluginResult(Status status, JSONObject message, String cast) {
		this.status = status.ordinal();
		this.message = message.toString();
		this.cast = cast;
	}

	public PluginResult(Status status, JSONArray message) {
		this.status = status.ordinal();
		this.message = message.toString();
	}

	public PluginResult(Status status, JSONObject message) {
		this.status = status.ordinal();
		this.message = message.toString();
	}

	public PluginResult(Status status, int i) {
		this.status = status.ordinal();
		this.message = "" + i;
	}

	public PluginResult(Status status, float f) {
		this.status = status.ordinal();
		this.message = "" + f;
	}

	public PluginResult(Status status, boolean b) {
		this.status = status.ordinal();
		this.message = "" + b;
	}

	public void setKeepCallback(boolean b) {
		this.keepCallback = b;
	}

	public int getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public boolean getKeepCallback() {
		return this.keepCallback;
	}

	public String getJSONString() {
		return "{status:" + this.status + ",message:" + this.message + ",keepCallback:" + this.keepCallback + "}";
	}

	public String toSuccessCallbackString(String callbackId) {
		StringBuilder buf = new StringBuilder();
		if (cast != null) {
			buf.append("var temp = ").append(cast).append("(").append(this.getJSONString()).append(");\n");
			buf.append("PhoneGap.callbackSuccess('").append(callbackId).append("',temp);");
		} else {
			buf.append("PhoneGap.callbackSuccess('").append(callbackId).append("',").append(this.getJSONString()).append(");");
		}
		return buf.toString();
	}

	public String toErrorCallbackString(String callbackId) {
		return "PhoneGap.callbackError('" + callbackId + "', " + this.getJSONString() + ");";
	}

	@Override
	public String toString() {
		return "Res{" +
				"status=" + status +
				", message='" + message + '\'' +
				", keepCallback=" + keepCallback +
				", cast='" + cast + '\'' +
				'}';
	}
}
