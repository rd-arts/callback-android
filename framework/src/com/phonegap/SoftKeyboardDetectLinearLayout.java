package com.phonegap;

import android.content.Context;
import android.widget.LinearLayout;
import com.phonegap.api.LOG;

/**
 * We are providing this class to detect when the soft keyboard is shown
 * and hidden in the web view.
 */
class SoftKeyboardDetectLinearLayout extends LinearLayout {

	private static final String TAG = "GAP_" + "SoftKeyboardDetect";

	private int oldHeight = 0;  // Need to save the old height as not to send redundant events
	private int oldWidth = 0; // Need to save old width for orientation change
	private int screenWidth = 0;
	private int screenHeight = 0;
	private GapView gapView;

	public SoftKeyboardDetectLinearLayout(GapView gapView, Context context, int width, int height) {
		super(context);
		this.gapView = gapView;
		screenWidth = width;
		screenHeight = height;
	}

	@Override
	/**
	 * Start listening to new measurement events.  Fire events when the height
	 * gets smaller fire a show keyboard event and when height gets bigger fire
	 * a hide keyboard event.
	 *
	 * Note: We are using callbackServer.sendJavascript() instead of
	 * this.appView.loadUrl() as changing the URL of the app would cause the
	 * soft keyboard to go away.
	 *
	 * @param widthMeasureSpec
	 * @param heightMeasureSpec
	 */
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		LOG.v(TAG, "We are in our onMeasure method");

		// Get the current height of the visible part of the screen.
		// This height will not included the status bar.
		int height = MeasureSpec.getSize(heightMeasureSpec);
		int width = MeasureSpec.getSize(widthMeasureSpec);

		LOG.v(TAG, "Old Height = %d", oldHeight);
		LOG.v(TAG, "Height = %d", height);
		LOG.v(TAG, "Old Width = %d", oldWidth);
		LOG.v(TAG, "Width = %d", width);

		// If the oldHeight = 0 then this is the first measure event as the app starts up.
		// If oldHeight == height then we got a measurement change that doesn't affect us.
		if (oldHeight == 0 || oldHeight == height) {
			LOG.d(TAG, "Ignore this event");
		}
		// Account for orientation change and ignore this event/Fire orientation change
		else if (screenHeight == width) {
			int tmp_var = screenHeight;
			screenHeight = screenWidth;
			screenWidth = tmp_var;
			LOG.v(TAG, "Orientation Change");
		}
		// If the height as gotten bigger then we will assume the soft keyboard has
		// gone away.
		else if (height > oldHeight) {
			LOG.v(TAG, "Throw hide keyboard event");
			gapView.callbackServer.sendJavascript("PhoneGap.fireDocumentEvent('hidekeyboard');");
		}
		// If the height as gotten smaller then we will assume the soft keyboard has
		// been displayed.
		else if (height < oldHeight) {
			LOG.v(TAG, "Throw show keyboard event");
			gapView.callbackServer.sendJavascript("PhoneGap.fireDocumentEvent('showkeyboard');");
		}

		// Update the old height for the next event
		oldHeight = height;
		oldWidth = width;
	}
}
