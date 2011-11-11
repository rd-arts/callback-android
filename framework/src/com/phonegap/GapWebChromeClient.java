package com.phonegap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.EditText;
import com.phonegap.api.LOG;
import org.json.JSONArray;
import org.json.JSONException;

import java.text.MessageFormat;

/**
 * Set the chrome handler.
 */
public class GapWebChromeClient extends WebChromeClient {

	private String TAG = "GAP_" + "PhoneGapLog";
	@SuppressWarnings({"FieldCanBeLocal"})
	private long MAX_QUOTA = 100 * 1024 * 1024;

	private Context ctx;
	private GapView gapView;

	/**
	 * Constructor.
	 *
	 * @param ctx
	 */
	public GapWebChromeClient(Context ctx, GapView gapView) {
		this.ctx = ctx;
		this.gapView = gapView;
	}

	/**
	 * Tell the client to display a javascript alert dialog.
	 *
	 * @param view
	 * @param url
	 * @param message
	 * @param result
	 */
	@Override
	public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
		Log.d(TAG, MessageFormat.format("onJsAlert url={0}\nmsg={1}\n", url, message));

		AlertDialog.Builder dlg = new AlertDialog.Builder(this.ctx);
		dlg.setMessage(message);
		dlg.setTitle("Alert");
		//Don't let alerts break the back button
		dlg.setCancelable(true);
		dlg.setPositiveButton(android.R.string.ok,
				new AlertDialog.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				});
		dlg.setOnCancelListener(
				new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						result.confirm();
					}
				});
		dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
			//DO NOTHING
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					result.confirm();
					return false;
				} else
					return true;
			}
		});
		dlg.create();
		dlg.show();
		return true;
	}

	/**
	 * Tell the client to display a confirm dialog to the user.
	 *
	 * @param view
	 * @param url
	 * @param message
	 * @param result
	 */
	@Override
	public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {

		Log.d(TAG, MessageFormat.format("onJsConfirm url={0}\nmsg={1}\n", url, message));

		AlertDialog.Builder dlg = new AlertDialog.Builder(this.ctx);
		dlg.setMessage(message);
		dlg.setTitle("Confirm");
		dlg.setCancelable(true);
		dlg.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				});
		dlg.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						result.cancel();
					}
				});
		dlg.setOnCancelListener(
				new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						result.cancel();
					}
				});
		dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
			//DO NOTHING
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					result.cancel();
					return false;
				} else
					return true;
			}
		});
		dlg.create();
		dlg.show();
		return true;
	}

	/**
	 * Tell the client to display a prompt dialog to the user.
	 * If the client returns true, WebView will assume that the client will
	 * handle the prompt dialog and call the appropriate JsPromptResult method.
	 *
	 * @param webView
	 * @param url
	 * @param message
	 * @param defaultValue
	 * @param result
	 */
	@Override
	public boolean onJsPrompt(WebView webView, String url, String message, String defaultValue, JsPromptResult result) {

		Log.d(TAG, MessageFormat.format("onJsPro url={0}\t msg={1} def={2}", url, message, defaultValue));

		// Security check to make sure any requests are coming from the page initially
		// loaded in webview and not another loaded in an iframe.
		boolean reqOk = false;
		if (url.indexOf(this.gapView.baseUrl) == 0 || this.gapView.isUrlWhiteListed(url)) {
			reqOk = true;
		}

		// Calling PluginManager.exec() to call a native service using
		// prompt(this.stringify(args), "gap:"+this.stringify([service, action, callbackId, true]));
		if (reqOk && defaultValue != null && defaultValue.length() > 3 && defaultValue.substring(0, 4).equals("gap:")) {
			JSONArray array;
			try {
				array = new JSONArray(defaultValue.substring(4));
				String service = array.getString(0);
				String action = array.getString(1);
				String callbackId = array.getString(2);
				boolean async = array.getBoolean(3);
				String r = this.gapView.pluginManager.exec(service, action, callbackId, message, async);
				result.confirm(r);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		// Polling for JavaScript messages
		else if (reqOk && defaultValue != null && defaultValue.equals("gap_poll:")) {
			String r = this.gapView.callbackServer.getJavascript();
			result.confirm(r);
		}

		// Calling into CallbackServer
		else if (reqOk && defaultValue != null && defaultValue.equals("gap_callbackServer:")) {
			String r = "";
			if (message.equals("usePolling")) {
				r = "" + this.gapView.callbackServer.usePolling();
			} else if (message.equals("restartServer")) {
				this.gapView.callbackServer.restartServer();
			} else if (message.equals("getPort")) {
				r = Integer.toString(this.gapView.callbackServer.getPort());
			} else if (message.equals("getToken")) {
				r = this.gapView.callbackServer.getToken();
			}
			result.confirm(r);
		}

		// PhoneGap JS has initialized, so show webview
		// (This solves white flash seen when rendering HTML)
		else if (reqOk && defaultValue != null && defaultValue.equals("gap_init:")) {
			this.gapView.appView.setVisibility(View.VISIBLE);
			this.gapView.spinnerStop();
			result.confirm("OK");
		}

		// Show dialog
		else {
			final JsPromptResult res = result;
			AlertDialog.Builder dlg = new AlertDialog.Builder(this.ctx);
			dlg.setMessage(message);
			final EditText input = new EditText(this.ctx);
			if (defaultValue != null) {
				input.setText(defaultValue);
			}
			dlg.setView(input);
			dlg.setCancelable(false);
			dlg.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String usertext = input.getText().toString();
							res.confirm(usertext);
						}
					});
			dlg.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							res.cancel();
						}
					});
			dlg.create();
			dlg.show();
		}
		return true;
	}

	/**
	 * Handle database quota exceeded notification.
	 *
	 * @param url
	 * @param databaseIdentifier
	 * @param currentQuota
	 * @param estimatedSize
	 * @param totalUsedQuota
	 * @param quotaUpdater
	 */
	@Override
	public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
										long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
		LOG.d(TAG, "DroidGap:  onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d",
				estimatedSize, currentQuota, totalUsedQuota);

		if (estimatedSize < MAX_QUOTA) {
			//increase for 1Mb
			long newQuota = estimatedSize;
			LOG.d(TAG, "calling quotaUpdater.updateQuota newQuota: %d", newQuota);
			quotaUpdater.updateQuota(newQuota);
		} else {
			// Set the quota to whatever it is and force an error
			// TODO: get docs on how to handle this properly
			quotaUpdater.updateQuota(currentQuota);
		}
	}

	// console.log in api level 7: http://developer.android.com/guide/developing/debug-tasks.html
	@Override
	public void onConsoleMessage(String message, int lineNumber, String sourceID) {
		LOG.d(TAG, "%s: Line %d : %s", sourceID, lineNumber, message);
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
		LOG.d(TAG, consoleMessage.message());
		return true;
	}

	@Override
	/**
	 * Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
	 *
	 * @param origin
	 * @param callback
	 */
	public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
		Log.d(TAG, MessageFormat.format("onGeolocationPermissionsShowPrompt origin={0}", origin));
		super.onGeolocationPermissionsShowPrompt(origin, callback);
		callback.invoke(origin, true, false);
	}

}
