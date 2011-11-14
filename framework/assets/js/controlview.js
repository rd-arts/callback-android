/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 *
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010-2011, IBM Corporation
 */

/* This file and prototype was App (app.js) before. */

if (!PhoneGap.hasResource("ControlView")) {
PhoneGap.addResource("ControlView");
(function() {

/**
 * Constructor
 * @constructor
 */
var ControlView = function() {};

/**
 * Clear the resource cache.
 */
ControlView.prototype.clearCache = function() {
    PhoneGap.exec(null, null, "ControlViewPlugin", "clearCache", []);
};

/**
 * Load the url into the webview or into new browser instance.
 *
 * @param url           The URL to load
 * @param props         Properties that can be passed in to the activity:
 *      wait: int                           => wait msec before loading URL
 *      loadingDialog: "Title,Message"      => display a native loading dialog
 *      loadUrlTimeoutValue: int            => time in msec to wait before triggering a timeout error
 *      clearHistory: boolean              => clear webview history (default=false)
 *      openExternal: boolean              => open in a new browser (default=false)
 *
 * Example:
 *      navigator.app.loadUrl("http://server/myapp/index.html", {wait:2000, loadingDialog:"Wait,Loading App", loadUrlTimeoutValue: 60000});
 */
ControlView.prototype.loadUrl = function(url, props) {
    PhoneGap.exec(null, null, "ControlViewPlugin", "loadUrl", [url, props]);
};

/**
 * Cancel loadUrl that is waiting to be loaded.
 */
ControlView.prototype.cancelLoadUrl = function() {
    PhoneGap.exec(null, null, "ControlViewPlugin", "cancelLoadUrl", []);
};

/**
 * Clear web history in this web view.
 * Instead of BACK button loading the previous web page, it will exit the app.
 */
ControlView.prototype.clearHistory = function() {
    PhoneGap.exec(null, null, "ControlViewPlugin", "clearHistory", []);
};

/**
 * Go to previous page displayed.
 * This is the same as pressing the backbutton on Android device.
 */
ControlView.prototype.backHistory = function() {
    PhoneGap.exec(null, null, "ControlViewPlugin", "backHistory", []);
};

/**
 * Override the default behavior of the Android back button.
 * If overridden, when the back button is pressed, the "backKeyDown" JavaScript event will be fired.
 *
 * Note: The user should not have to call this method.  Instead, when the user
 *       registers for the "backbutton" event, this is automatically done.
 *
 * @param override		T=override, F=cancel override
 */
ControlView.prototype.overrideBackbutton = function(override) {
    PhoneGap.exec(null, null, "ControlViewPlugin", "overrideBackbutton", [override]);
};

/**
 * Exit and terminate the application.
 */
ControlView.prototype.exitApp = function() {
	return PhoneGap.exec(null, null, "ControlViewPlugin", "exitApp", []);
};

/**
 * Add entry to approved list of URLs (whitelist) that will be loaded into PhoneGap container instead of default browser.
 * 
 * @param origin		URL regular expression to allow
 * @param subdomains	T=include all subdomains under origin
 */
ControlView.prototype.addWhiteListEntry = function(origin, subdomains) {
	return PhoneGap.exec(null, null, "ControlViewPlugin", "addWhiteListEntry", [origin, subdomains]);
};

PhoneGap.addConstructor(function() {
    navigator.app = new ControlView();
});
}());
}
