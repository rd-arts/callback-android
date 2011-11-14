package com.phonegap;

import android.app.Activity;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import com.phonegap.api.LOG;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

class GapConfig {
	private static final String TAG = "GAP_" + GapConfig.class.getSimpleName();

	/**
	 * Load PhoneGap configuration from res/xml/phonegap.xml.
	 * Approved list of URLs that can be loaded into DroidGap
	 * <access origin="http://server regexp" subdomains="true" />
	 * Log level: ERROR, WARN, INFO, DEBUG, VERBOSE (default=ERROR)
	 * <log level="DEBUG" />
	 */
	public static ArrayList<Pattern> loadConfiguration(Context context) {
		ArrayList<Pattern> whiteList = new ArrayList<Pattern>();

		int id = context.getResources().getIdentifier("phonegap", "xml", context.getPackageName());
		if (id == 0) {
			LOG.i("PhoneGapLog", "phonegap.xml missing. Ignoring...");
			return whiteList;
		}
		XmlResourceParser xml = context.getResources().getXml(id);
		int eventType = -1;
		while (eventType != XmlResourceParser.END_DOCUMENT) {
			if (eventType == XmlResourceParser.START_TAG) {
				String strNode = xml.getName();
				if (strNode.equals("access")) {
					String origin = xml.getAttributeValue(null, "origin");
					String subdomains = xml.getAttributeValue(null, "subdomains");
					if (origin != null) {
						addWhiteListEntry(whiteList, origin, (subdomains != null) && (subdomains.compareToIgnoreCase("true") == 0));
					}
				} else if (strNode.equals("log")) {
					String level = xml.getAttributeValue(null, "level");
					LOG.i("PhoneGapLog", "Found log level %s", level);
					if (level != null) {
						LOG.setLogLevel(level);
					}
				}
			}
			try {
				eventType = xml.next();
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return whiteList;
	}

	/**
	 * Add entry to approved list of URLs (whitelist)
	 *
	 * @param whiteList
	 * @param origin	 URL regular expression to allow
	 * @param subdomains T=include all subdomains under origin
	 */
	public static void addWhiteListEntry(ArrayList<Pattern> whiteList, String origin, boolean subdomains) {
		try {
			// Unlimited access to network resources
			if ("*".equals(origin)) {
				LOG.d(TAG, "Unlimited access to network resources");
				whiteList.add(Pattern.compile(".*"));
			} else { // specific access
				// check if subdomains should be included
				// TODO: we should not to add more domains if * has already been added
				if (subdomains) {
					// XXX making it stupid friendly for people who forget to include protocol/SSL
					if (origin.startsWith("http")) {
						whiteList.add(Pattern.compile(origin.replaceFirst("https?://", "^https?://.*")));
					} else {
						whiteList.add(Pattern.compile("^https?://.*" + origin));
					}
					LOG.d(TAG, "Origin to allow with subdomains: %s", origin);
				} else {
					// XXX making it stupid friendly for people who forget to include protocol/SSL
					if (origin.startsWith("http")) {
						whiteList.add(Pattern.compile(origin.replaceFirst("https?://", "^https?://")));
					} else {
						whiteList.add(Pattern.compile("^https?://" + origin));
					}
					LOG.d(TAG, "Origin to allow: %s", origin);
				}
			}
		} catch (Exception e) {
			LOG.d(TAG, "Failed to add origin %s", origin);
		}
	}


	/**
	 * Get boolean property for activity.
	 *
	 * @param name
	 * @param defaultValue
	 * @param activity
	 * @return
	 */
	public boolean getBooleanProperty(String name, boolean defaultValue, Activity activity) {
		Bundle bundle = activity.getIntent().getExtras();
		if (bundle == null) {
			return defaultValue;
		}
		Boolean p = (Boolean) bundle.get(name);
		if (p == null) {
			return defaultValue;
		}
		return p.booleanValue();
	}

	/**
	 * Get int property for activity.
	 *
	 * @param name
	 * @param defaultValue
	 * @param activity
	 * @return
	 */
	public int getIntegerProperty(String name, int defaultValue, Activity activity) {
		Bundle bundle = activity.getIntent().getExtras();
		if (bundle == null) {
			return defaultValue;
		}
		Integer p = (Integer) bundle.get(name);
		if (p == null) {
			return defaultValue;
		}
		return p.intValue();
	}

	/**
	 * Get string property for activity.
	 *
	 * @param name
	 * @param defaultValue
	 * @param activity
	 * @return
	 */
	@Nullable
	public static String getStringProperty(String name, @Nullable String defaultValue, Activity activity) {
		Bundle bundle = activity.getIntent().getExtras();
		if (bundle == null) {
			return defaultValue;
		}
		String p = bundle.getString(name);
		if (p == null) {
			return defaultValue;
		}
		return p;
	}

	/**
	 * Get double property for activity.
	 *
	 * @param name
	 * @param defaultValue
	 * @param activity
	 * @return
	 */
	public double getDoubleProperty(String name, double defaultValue, Activity activity) {
		Bundle bundle = activity.getIntent().getExtras();
		if (bundle == null) {
			return defaultValue;
		}
		Double p = (Double) bundle.get(name);
		if (p == null) {
			return defaultValue;
		}
		return p.doubleValue();
	}

	/**
	 * Set boolean property on activity.
	 *
	 * @param name
	 * @param value
	 * @param activity
	 */
	public void setBooleanProperty(String name, boolean value, Activity activity) {
		activity.getIntent().putExtra(name, value);
	}

	/**
	 * Set int property on activity.
	 *
	 * @param name
	 * @param value
	 * @param activity
	 */
	public void setIntegerProperty(String name, int value, Activity activity) {
		activity.getIntent().putExtra(name, value);
	}

	/**
	 * Set string property on activity.
	 *
	 * @param name
	 * @param value
	 * @param activity
	 */
	public void setStringProperty(String name, String value, Activity activity) {
		activity.getIntent().putExtra(name, value);
	}

	/**
	 * Set double property on activity.
	 *
	 * @param name
	 * @param value
	 * @param activity
	 */
	public void setDoubleProperty(String name, double value, Activity activity) {
		activity.getIntent().putExtra(name, value);
	}
}
