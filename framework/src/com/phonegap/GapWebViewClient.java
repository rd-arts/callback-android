package com.phonegap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.util.Log;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.phonegap.api.LOG;

import java.util.HashMap;

/**
 * The webview client receives notifications about appView
 */
public class GapWebViewClient extends WebViewClient {

	private static final String TAG = "GAP_" + GapWebViewClient.class.getSimpleName();

	private GapView view;
	private Activity activity;

	/**
	 * Constructor.
	 *
	 * @param view
	 */
	public GapWebViewClient(GapView view) {
		this.view = view;
		activity = view.getActivity();
	}

	/**
	 * Give the host application a chance to take over the control when a new url
	 * is about to be loaded in the current WebView.
	 *
	 * @param view The WebView that is initiating the callback.
	 * @param url  The url to be loaded.
	 * @return true to override, false for default behavior
	 */
	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		Log.v(TAG, "Ask URL: " + url);

		// new activity
		String prefixTargetNew = "_LOADINTERNAL_";
		if (url.contains(prefixTargetNew)) {
			// in-control loading
			view.loadUrl(url);
			return true;
		}
		// correct URL: remove marker
		// url = url.substring(url.lastIndexOf(prefixTargetNew) + prefixTargetNew.length());
		url = url.replace(prefixTargetNew, "");

		// First give any plugins the chance to handle the url themselves
		if (this.view.pluginManager.onOverrideUrlLoading(url)) {
		}

		// If dialing phone (tel:5551212)
		else if (url.startsWith(WebView.SCHEME_TEL)) {
			try {
				Intent intent = new Intent(Intent.ACTION_DIAL);
				intent.setData(Uri.parse(url));
				activity.startActivity(intent);
			} catch (android.content.ActivityNotFoundException e) {
				LOG.e(GapView.TAG, "Error dialing " + url + ": " + e.toString());
			}
		}

		// If displaying map (geo:0,0?q=address)
		else if (url.startsWith("geo:")) {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse(url));
				activity.startActivity(intent);
			} catch (android.content.ActivityNotFoundException e) {
				LOG.e(GapView.TAG, "Error showing map " + url + ": " + e.toString());
			}
		}

		// If sending email (mailto:abc@corp.com)
		else if (url.startsWith(WebView.SCHEME_MAILTO)) {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse(url));
				activity.startActivity(intent);
			} catch (android.content.ActivityNotFoundException e) {
				LOG.e(GapView.TAG, "Error sending email " + url + ": " + e.toString());
			}
		}

		// If sms:5551212?body=This is the message
		else if (url.startsWith("sms:")) {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW);

				// Get address
				String address = null;
				int parmIndex = url.indexOf('?');
				if (parmIndex == -1) {
					address = url.substring(4);
				} else {
					address = url.substring(4, parmIndex);

					// If body, then set sms body
					Uri uri = Uri.parse(url);
					String query = uri.getQuery();
					if (query != null) {
						if (query.startsWith("body=")) {
							intent.putExtra("sms_body", query.substring(5));
						}
					}
				}
				intent.setData(Uri.parse("sms:" + address));
				intent.putExtra("address", address);
				intent.setType("vnd.android-dir/mms-sms");
				activity.startActivity(intent);
			} catch (android.content.ActivityNotFoundException e) {
				LOG.e(GapView.TAG, "Error sending sms " + url + ":" + e.toString());
			}
		}

		// All else
		else {

			// If our app or file:, then load into a new phonegap webview container by starting a new instance of our activity.
			// Our app continues to run.  When BACK is pressed, our app is redisplayed.
			if (this.view.loadInWebView || url.startsWith("file://") || this.view.baseUrl.equals(url) || this.view.isUrlWhiteListed(url)) {
				try {
					// Init parameters to new DroidGap activity and propagate existing parameters
					HashMap<String, Object> params = new HashMap<String, Object>();
					this.view.showWebPage(url, true, false, params);
				} catch (android.content.ActivityNotFoundException e) {
					LOG.e(GapView.TAG, "Error loading url into DroidGap - " + url, e);
				}
			}

			// If not our application, let default viewer handle
			else {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					activity.startActivity(intent);
				} catch (android.content.ActivityNotFoundException e) {
					LOG.e(GapView.TAG, "Error loading url " + url, e);
				}
			}
		}
		return true;
	}

	/**
	 * Notify the host application that a page has finished loading.
	 *
	 * @param view The webview initiating the callback.
	 * @param url  The url of the page.
	 */
	@Override
	public void onPageFinished(WebView view, String url) {
		Log.v(TAG, "onPageFinished URL: " + url);
		super.onPageFinished(view, url);

		// Clear timeout flag
		this.view.loadUrlTimeout++;

		// Try firing the onNativeReady event in JS. If it fails because the JS is
		// not loaded yet then just set a flag so that the onNativeReady can be fired
		// from the JS side when the JS gets to that code.
		if (!url.equals("about:blank")) {
			this.view.loadUrl("javascript:try{ PhoneGap.onNativeReady.fire();}catch(e){_nativeReady = true;}");
		}

		// Make app visible after 2 sec in case there was a JS error and PhoneGap JS never initialized correctly
		if (this.view.getVisibility() == View.INVISIBLE) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(2000);
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								GapWebViewClient.this.view.setVisibility(View.VISIBLE);
								GapWebViewClient.this.view.spinnerStop();
							}
						});
					} catch (InterruptedException e) {
						Log.i(TAG, "interrupted:193");
					}
				}
			});
			t.start();
		}

		// Clear history, so that previous screen isn't there when Back button is pressed
		if (this.view.clearHistory) {
			this.view.clearHistory = false;
			this.view.appView.clearHistory();
		}

		// Shutdown if blank loaded
		if (url.equals("about:blank")) {
			if (this.view.callbackServer != null) {
				this.view.callbackServer.destroy();
			}
			this.view.endActivity();
		}
	}

	/**
	 * Report an error to the host application. These errors are unrecoverable (i.e. the main resource is unavailable).
	 * The errorCode parameter corresponds to one of the ERROR_* constants.
	 *
	 * @param view		The WebView that is initiating the callback.
	 * @param errorCode   The error code corresponding to an ERROR_* value.
	 * @param description A String describing the error.
	 * @param failingUrl  The url that failed to load.
	 */
	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
		LOG.d(GapView.TAG, "onReceivedError: Error code=%s Description=%s URL=%s",
				errorCode, description, failingUrl);

		// Clear timeout flag
		this.view.loadUrlTimeout++;

		// Stop "app loading" spinner if showing
		this.view.spinnerStop();

		// Handle error
		this.view.onReceivedError(errorCode, description, failingUrl);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		LOG.d(GapView.TAG, "onReceivedSslError: getPrimaryError=%d error=%s",
				error.getPrimaryError(), error);

		final String packageName = this.view.context.getPackageName();
		final PackageManager pm = this.view.context.getPackageManager();
		ApplicationInfo appInfo;
		try {
			appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
			if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
				// debug = true
				handler.proceed();
			} else {
				// debug = false
				super.onReceivedSslError(view, handler, error);
			}
		} catch (PackageManager.NameNotFoundException e) {
			// When it doubt, lock it out!
			super.onReceivedSslError(view, handler, error);
		}
	}
}
