/*
* PhoneGap is available under *either* the terms of the modified BSD license *or* the
* MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
*
* Copyright (c) 2005-2010, Nitobi Software Inc.
* Copyright (c) 2010-2011, IBM Corporation
*/
package com.phonegap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.phonegap.api.IActivityEventsDispatcher;
import com.phonegap.api.IPlugin;
import com.phonegap.api.LOG;

public class GapView extends WebView {

	public static String TAG = "GAP_" + GapView.class.getSimpleName();

	/**
	 * XML config read helper.
	 */
	private GapConfig config = new GapConfig();
	// The webview for our app
	WebView appView;
	private WebViewClient webViewClient;
	ArrayList<Pattern> whiteList = new ArrayList<Pattern>();
	/**
	 * White-list check URL cache.
	 */
	private HashMap<String, Boolean> whiteListCache = new HashMap<String, Boolean>();

	/**
	 * If overridden, when the back button is pressed, the "backKeyDown" JavaScript event will be fired.
	 */
	public boolean bound = false;
	public CallbackServer callbackServer;
	PluginManager pluginManager;
	private boolean cancelLoadUrl = false;
	boolean clearHistory = false;
	private ProgressDialog spinnerDialog = null;

	/**
	 * The initial URL for our app
	 * ie http://server/path/index.html#abc?query
	 */
	private String url;
	private boolean firstPage = true;

	// The base of the initial URL for our app.
	// Does not include file name.  Ends with /
	// ie http://server/path/
	String baseUrl = null;

	// Plugin to call when activity result is received
	private IPlugin activityResultCallback = null;
	private boolean activityResultKeepRunning;
	private static int PG_REQUEST_CODE = 99;

	// Flag indicates that a loadUrl timeout occurred
	int loadUrlTimeout = 0;

	// Default background color for activity
	// (this is not the color for the webview, which is set in HTML)
	private int backgroundColor = Color.BLACK;

	/*
			  * The variables below are used to cache some of the activity properties.
			  */

	/**
	 * Flag indicates that a URL navigated to from PhoneGap app should be loaded into same webview
	 * instead of being loaded into the web browser.
	 */
	boolean loadInWebView = false;

	// Draw a splash screen using an image located in the drawable resource directory.
	// This is not the same as calling super.loadSplashscreen(url)
	private int splashscreen = 0;

	// LoadUrl timeout value in msec (default of 20 sec)
	private int loadUrlTimeoutValue = 20000;

	// Keep app running when pause is received. (default = true)
	// If true, then the JavaScript and native code continue to run in the background
	// when another application (activity) is started.
	private boolean keepRunning = true;

	Context context;
	private Activity activity;
	private IActivityEventsDispatcher activityEventsDispatcher = new IActivityEventsDispatcher() {
		@Override
		public void onNewIntent(Intent intent) {
			GapView.this.onNewIntent(intent);
		}

		@Override
		public void onResume() {
			GapView.this.onResume();
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent intent) {
			GapView.this.onActivityResult(requestCode, resultCode, intent);
		}
	};

	public GapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		appView = this;
		if (context instanceof Activity)
			activity = (Activity) context;
		else
			throw new IllegalArgumentException("Phone Gap view cannot be embeded.");

		// Load PhoneGap configuration:
		//      white list of allowed URLs
		//      debug setting
		whiteList = GapConfig.loadConfiguration(context);

		this.webViewClient = new GapWebViewClient(this);
		setWebChromeClient(new GapWebChromeClient(context, this));
		setWebViewClient(this.webViewClient);


		this.setInitialScale(100);
		this.setVerticalScrollBarEnabled(false);
		this.requestFocusFromTouch();

		// Enable JavaScript
		WebSettings settings = this.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setJavaScriptCanOpenWindowsAutomatically(true);
		settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

		//Set the nav dump for HTC
		//settings.setNavDump(true);

		// Enable database
		settings.setDatabaseEnabled(true);
		String databasePath = context.getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
		settings.setDatabasePath(databasePath);

		// Enable DOM storage
		WebViewReflect.setDomStorage(settings);

		// Enable built-in geolocation
		WebViewReflect.setGeolocationEnabled(settings, true);

		// Create callback server and plugin manager
		this.callbackServer = new CallbackServer();
		this.pluginManager = new PluginManager(context, this, this);

		// Add web view but make it invisible while loading URL
		this.appView.setVisibility(View.INVISIBLE);

		// Clear cancel flag
		this.cancelLoadUrl = false;
	}

	/**
	 * Look at activity parameters and process them.
	 * This must be called from the main UI thread.
	 */
	private void handleActivityParameters() {

		// Init web view if not already done
//		if (this.appView == null) {
//			this.init();
//		}

		// If backgroundColor
		this.backgroundColor = config.getIntegerProperty("backgroundColor", Color.BLACK, activity);
//todo		this.root.setBackgroundColor(this.backgroundColor);

		// If spashscreen
		this.splashscreen = config.getIntegerProperty("splashscreen", 0, activity);
		if (this.firstPage && (this.splashscreen != 0)) {
//todo			root.setBackgroundResource(this.splashscreen);
		}

		// If loadInWebView
		this.loadInWebView = config.getBooleanProperty("loadInWebView", false, activity);

		// If loadUrlTimeoutValue
		int timeout = config.getIntegerProperty("loadUrlTimeoutValue", 0, activity);
		if (timeout > 0) {
			this.loadUrlTimeoutValue = timeout;
		}

		// If keepRunning
		this.keepRunning = config.getBooleanProperty("keepRunning", true, activity);
	}

	private static volatile int times;

	/**
	 * Load the url into the webview.
	 * Use it instead of {@link WebView#loadUrl(String)} always.
	 *
	 * @param url url
	 */
	public void loadGapUrl(String url) {
		Log.i(TAG, ++times + " load URL: " + url);

		// If first page of app, then set URL to load to be the one passed in
		if (this.firstPage) {
			this.loadUrlIntoView(url);
		}
		// Otherwise use the URL specified in the activity's extras bundle
		else {
			this.loadUrlIntoView(this.url);
		}
	}

	/**
	 * Load the url into the webview.
	 *
	 * @param url
	 */
	private void loadUrlIntoView(final String url) {
		this.url = url;
		if (this.baseUrl == null) {
			int i = url.lastIndexOf('/');
			if (i > 0) {
				this.baseUrl = url.substring(0, i + 1);
			} else {
				this.baseUrl = this.url + "/";
			}
		}
		if (!url.startsWith("javascript:")) {
			LOG.d(TAG, "URL into view: url=%s baseUrl=%s", url, baseUrl);
		}

		// Load URL on UI thread
		activity.runOnUiThread(new Runnable() {
			final GapView me = GapView.this;

			@Override
			public void run() {

				// Handle activity parameters
				me.handleActivityParameters();

				// Initialize callback server
				me.callbackServer.init(url);

				// Loading dialog.
				// If loadingDialog property, then show the App loading dialog for first page of app.
				String loading;
				if (me.firstPage) {
					loading = GapConfig.getStringProperty("loadingDialog", null, activity);
				} else {
					loading = GapConfig.getStringProperty("loadingPageDialog", null, activity);
				}
				if (loading != null) {
					String title = "";
					String message = "Loading Application...";

					if (loading.length() > 0) {
						int comma = loading.indexOf(',');
						if (comma > 0) {
							title = loading.substring(0, comma);
							message = loading.substring(comma + 1);
						} else {
							title = "";
							message = loading;
						}
					}
					me.spinnerStart(title, message);
				}

				// Create a timeout timer for loadUrl
				final int currentLoadUrlTimeout = me.loadUrlTimeout;
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						Log.e(TAG, "Wait server to start.");
						try {
							synchronized (GapView.this) {
								GapView.this.wait(me.loadUrlTimeoutValue);
							}
						} catch (InterruptedException e) {
							Log.e(TAG, "LoadUrlTimeout interrupted", e);
						}

						// If timeout, then stop loading and handle error
						if (me.loadUrlTimeout == currentLoadUrlTimeout) {
							String msg = "The connection to the server was unsuccessful.";
							Log.e(TAG, msg);
							me.appView.stopLoading();
							me.webViewClient.onReceivedError(me.appView, -6, msg, url);
						}
					}
				};
				Thread thread = new Thread(runnable);
				thread.start();
				me.appView.loadUrl(url);
			}
		});
	}

//    /**
//     * Load the url into the webview after waiting for period of time.
//     * This is used to display the splashscreen for certain amount of time.
//     *
//     * @param url
//     * @param time The number of ms to wait before loading webview
//     */
//    public void loadUrl(final String url, int time) {
//
//        // If first page of app, then set URL to load to be the one passed in
//        if (this.firstPage) {
//            this.loadUrlIntoView(url, time);
//        }
//        // Otherwise use the URL specified in the activity's extras bundle
//        else {
//            this.loadUrlIntoView(this.url);
//        }
//    }

//    /**
//     * Load the url into the webview after waiting for period of time.
//     * This is used to display the splashscreen for certain amount of time.
//     *
//     * @param url
//     * @param time The number of ms to wait before loading webview
//     */
//    private void loadUrlIntoView(final String url, final int time) {
//
//        // If not first page of app, then load immediately
//        if (!this.firstPage) {
//            this.loadUrl(url);
//        }
//
//        if (!url.startsWith("javascript:")) {
//            LOG.d(TAG, "DroidGap.loadUrl(%s, %d)", url, time);
//        }
//        final GapView me = this;
//
//        // Handle activity parameters
//        activity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                me.handleActivityParameters();
//            }
//        });
//
//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    synchronized (this) {
//                        this.wait(time);
//                    }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                if (!me.cancelLoadUrl) {
//                    me.loadUrl(url);
//                } else {
//                    me.cancelLoadUrl = false;
//                    LOG.d(TAG, "Aborting loadUrl(%s): Another URL was loaded before timer expired.", url);
//                }
//            }
//        };
//        Thread thread = new Thread(runnable);
//        thread.start();
//    }

	/**
	 * Cancel loadUrl before it has been loaded.
	 */
	public void cancelLoadUrl() {
		this.cancelLoadUrl = true;
	}

	/**
	 * Clear the resource cache.
	 */
	public void clearCache() {
		this.appView.clearCache(true);
	}

	/**
	 * Clear web history in this web view.
	 */
	@Override
	public void clearHistory() {
		this.clearHistory = true;
		if (this.appView != null) {
			this.appView.clearHistory();
		}
	}

//    @Override
//    /**
//     * Called by the system when the device configuration changes while your activity is running.
//     *
//     * @param Configuration newConfig
//     */
//    public void onConfigurationChanged(Configuration newConfig) {
//        //don't reload the current page when the orientation is changed
//        super.onConfigurationChanged(newConfig);
//    }

	/**
	 * Called when the activity receives a new intent
	 */
	private void onNewIntent(Intent intent) {
		//Forward to plugins
		this.pluginManager.onNewIntent(intent);
	}

	/**
	 * activity onResume.
	 */
	private void onResume() {
		// Send resume event to JavaScript
		this.appView.loadUrl("javascript:try{PhoneGap.onResume.fire();}catch(e){};");

		// Forward to plugins
		this.pluginManager.onResume(this.keepRunning || this.activityResultKeepRunning);

		// If app doesn't want to run in background
		if (!this.keepRunning || this.activityResultKeepRunning) {

			// Restore multitasking state
			if (this.activityResultKeepRunning) {
				this.keepRunning = this.activityResultKeepRunning;
				this.activityResultKeepRunning = false;
			}

			// Resume JavaScript timers (including setInterval)
			this.appView.resumeTimers();
		}
	}

	@Override
	protected void onDetachedFromWindow() {
//		// Make sure pause event is sent if onPause hasn't been called before onDestroy
//		this.appView.loadUrl("javascript:try{PhoneGap.onPause.fire();}catch(e){};");
//
//		// Send destroy event to JavaScript
//		this.appView.loadUrl("javascript:try{PhoneGap.onDestroy.fire();}catch(e){};");
//
//		// Load blank page so that JavaScript onunload is called
//		this.appView.loadUrl("about:blank");
//
//		// Forward to plugins
//		this.pluginManager.onDestroy();
//
		super.onDetachedFromWindow();
	}

	/**
	 * Add a class that implements a service.
	 *
	 * @param serviceType
	 * @param className
	 */
	public void addService(String serviceType, String className) {
		this.pluginManager.addService(serviceType, className);
	}

	/**
	 * Send JavaScript statement back to JavaScript.
	 * (This is a convenience method)
	 *
	 * @param statement
	 */
	public void sendJavascript(String statement) {
		this.callbackServer.sendJavascript(statement);
	}

	/**
	 * Display a new browser with the specified URL.
	 * <p/>
	 * NOTE: If usePhoneGap is set, only trusted PhoneGap URLs should be loaded,
	 * since any PhoneGap API can be called by the loaded HTML page.
	 *
	 * @param url		 The url to load.
	 * @param usePhoneGap Load url in PhoneGap webview.
	 * @param clearPrev   Clear the activity stack, so new app becomes top of stack
	 * @param params	  DroidGap parameters for new app
	 * @throws android.content.ActivityNotFoundException
	 *
	 */
	public void showWebPage(String url, boolean usePhoneGap, boolean clearPrev, HashMap<String, Object> params) throws android.content.ActivityNotFoundException {
		Intent intent = null;
		if (usePhoneGap) {
			Toast.makeText(context, "New activity...", Toast.LENGTH_SHORT);
////			try {
//            // TODO check
//            //intent = new Intent().setClass(context, Class.forName(activity.getComponentName().getClassName()));
//            intent = new Intent().setClass(context, GapViewActivity.class);
//            return;
//            intent.putExtra("url", url);
//
//            // Add parameters
//            if (params != null) {
//                java.util.Set<Entry<String, Object>> s = params.entrySet();
//                Iterator<Entry<String, Object>> it = s.iterator();
//                while (it.hasNext()) {
//                    Entry<String, Object> entry = it.next();
//                    String key = entry.getKey();
//                    Object value = entry.getValue();
//                    if (value == null) {
//                    } else if (value.getClass().equals(String.class)) {
//                        intent.putExtra(key, (String) value);
//                    } else if (value.getClass().equals(Boolean.class)) {
//                        intent.putExtra(key, (Boolean) value);
//                    } else if (value.getClass().equals(Integer.class)) {
//                        intent.putExtra(key, (Integer) value);
//                    }
//                }
//            }
//            activity.startActivityForResult(intent, PG_REQUEST_CODE);
////			} catch (ClassNotFoundException e) {
////				e.printStackTrace();
////				intent = new Intent(Intent.ACTION_VIEW);
////				intent.setData(Uri.parse(url));
////				context.startActivity(intent);
////			}
		} else {
			intent = new Intent(Intent.ACTION_VIEW);
			intent.setData(Uri.parse(url));
			context.startActivity(intent);
		}

		// Finish current activity
		if (clearPrev) {
			this.endActivity();
		}
	}

	/**
	 * Show the spinner.  Must be called from the UI thread.
	 *
	 * @param title   Title of the dialog
	 * @param message The message of the dialog
	 */
	private void spinnerStart(final String title, final String message) {
		if (this.spinnerDialog != null) {
			this.spinnerDialog.dismiss();
			this.spinnerDialog = null;
		}
		final GapView me = this;
		this.spinnerDialog = ProgressDialog.show(context, title, message, true, true,
				new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						me.spinnerDialog = null;
					}
				});
	}

	/**
	 * Stop spinner.
	 */
	public void spinnerStop() {
		if (this.spinnerDialog != null) {
			this.spinnerDialog.dismiss();
			this.spinnerDialog = null;
		}
	}


	/**
	 * End this activity by calling finish for activity
	 */
	public void endActivity() {
		activity.finish();
	}

	/**
	 * Called when a key is pressed.
	 *
	 * @param keyCode
	 * @param event
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (this.appView == null) {
			return super.onKeyDown(keyCode, event);
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:

				// If back key is bound, then send event to JavaScript
				if (this.bound) {
					this.appView.loadUrl("javascript:PhoneGap.fireDocumentEvent('backbutton');");
					return true;
				}

				// If not bound
				else {

					// Go to previous page in webview if it is possible to go back
					if (this.appView.canGoBack()) {
						this.appView.goBack();
						return true;
					}

					// If not, then invoke behavior of super class
					else {
						return super.onKeyDown(keyCode, event);
					}
				}

			case KeyEvent.KEYCODE_MENU:
				this.appView.loadUrl("javascript:PhoneGap.fireDocumentEvent('menubutton');");
				return true;

			case KeyEvent.KEYCODE_SEARCH:
				this.appView.loadUrl("javascript:PhoneGap.fireDocumentEvent('searchbutton');");
				return true;

			default:
				return super.onKeyDown(keyCode, event);
		}


	}

	/**
	 * Any calls to Activity.startActivityForResult must use method below, so
	 * the result can be routed to them correctly.
	 * <p/>
	 * This is done to eliminate the need to modify DroidGap.java to receive activity results.
	 *
	 * @param intent	  The intent to start
	 * @param requestCode Identifies who to send the result to
	 * @throws RuntimeException
	 */
	public void startActivityForResult(Intent intent, int requestCode) throws RuntimeException {
		LOG.d(TAG, "DroidGap.startActivityForResult(intent,%d)", requestCode);
		activity.startActivityForResult(intent, requestCode);
	}

	/**
	 * Launch an activity for which you would like a result when it finished. When this activity exits,
	 * your onActivityResult() method will be called.
	 *
	 * @param command	 The command object
	 * @param intent	  The intent to start
	 * @param requestCode The request code that is passed to callback to identify the activity
	 */
	public void startActivityForResult(IPlugin command, Intent intent, int requestCode) {
		this.activityResultCallback = command;
		this.activityResultKeepRunning = this.keepRunning;

		// If multitasking turned on, then disable it for activities that return results
		if (command != null) {
			this.keepRunning = false;
		}

		// Start activity
		activity.startActivityForResult(intent, requestCode);
	}

	/**
	 * Called when an activity you launched exits, giving you the requestCode you started it with,
	 * the resultCode it returned, and any additional data from it.
	 *
	 * @param requestCode The request code originally supplied to startActivityForResult(),
	 *                    allowing you to identify who this result came from.
	 * @param resultCode  The integer result code returned by the child activity through its setResult().
	 * @param data		An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
	 */
	private void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// If a subsequent DroidGap activity is returning
		if (requestCode == PG_REQUEST_CODE) {
			// If terminating app, then shut down this activity too
			if (resultCode == Activity.RESULT_OK) {
				activity.setResult(Activity.RESULT_OK);
				this.endActivity();
			}
			return;
		}

		IPlugin callback = this.activityResultCallback;
		if (callback != null) {
			callback.onActivityResult(requestCode, resultCode, intent);
		} else {
			Log.w(TAG, String.format("No callback for result!!! req=%d res=%d", requestCode, resultCode));
		}
	}

	/**
	 * Report an error to the host application. These errors are unrecoverable (i.e. the main resource is unavailable).
	 * The errorCode parameter corresponds to one of the ERROR_* constants.
	 *
	 * @param errorCode   The error code corresponding to an ERROR_* value.
	 * @param description A String describing the error.
	 * @param failingUrl  The url that failed to load.
	 */
	public void onReceivedError(int errorCode, String description, String failingUrl) {

		// If errorUrl specified, then load it
		final String errorUrl = GapConfig.getStringProperty("errorUrl", null, activity);
		if ((errorUrl != null) && (errorUrl.startsWith("file://") || errorUrl.indexOf(this.baseUrl) == 0 || isUrlWhiteListed(errorUrl)) && (!failingUrl.equals(errorUrl))) {

			// Load URL on UI thread
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					GapView.this.showWebPage(errorUrl, true, true, null);
				}
			});
		}

		// If not, then display error dialog
		else {
			this.appView.setVisibility(View.GONE);
			this.displayError("Application Error", description + " (" + failingUrl + ")", "OK", true);
		}
	}

	/**
	 * Display an error dialog and optionally exit application.
	 *
	 * @param title
	 * @param message
	 * @param button
	 * @param exit
	 */
	private void displayError(final String title, final String message, final String button, final boolean exit) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder dlg = new AlertDialog.Builder(context);
				dlg.setMessage(message);
				dlg.setTitle(title);
				dlg.setCancelable(false);
				dlg.setPositiveButton(button,
						new AlertDialog.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
								if (exit) {
									endActivity();
								}
							}
						});
				dlg.create();
				dlg.show();
			}
		});
	}


	/**
	 * Determine if URL is in approved list of URLs to load.
	 *
	 * @param url
	 * @return
	 */
	boolean isUrlWhiteListed(String url) {

		// Check to see if we have matched url previously
		if (whiteListCache.containsKey(url)) {
			return true;
		}

		// Look for match in white list
		for (Pattern p : whiteList) {
			Matcher m = p.matcher(url);

			// If match found, then cache it to speed up subsequent comparisons
			if (m.find()) {
				whiteListCache.put(url, true);
				return true;
			}
		}
		return false;
	}

	public IActivityEventsDispatcher getActivityEventsDispatcher() {
		return activityEventsDispatcher;
	}

	public Activity getActivity() {
		return activity;
	}
}
