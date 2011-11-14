/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 */
package com.phonegap;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class DemoCallbackViewActivity extends Activity {

	private static final String TAG = "GAP_" + DemoCallbackViewActivity.class.getSimpleName();

	private GapView gapView;
	private ActivityEventsDispatcher activityEventsDispatcher;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		setContentView(R.layout.screen_demo);
		gapView = (GapView) findViewById(R.id.gapView);

		gapView.loadGapUrl("file:///android_asset/www/demo_index.html");
//		gapView.loadGapUrl("file:///android_asset/www/demo_index2.html");
//		gapView.loadGapUrl("file:///android_asset/js/demo_index3.html");

		activityEventsDispatcher = gapView.getActivityEventsDispatcher();

		findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(DemoCallbackViewActivity.this, "I am just a native button.\n" +
						"Web-based view below me can do lots of mobile magic. While it is still HTML5.",
						Toast.LENGTH_SHORT).show();
			}
		});
		findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(DemoCallbackViewActivity.this, "I am button too.", Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// You can hijack all input to PhoneGap WebView "return gapView.onKeyDown(keyCode, event);".
		// Or filter for example only KeyEvent.KEYCODE_BACK.
		//return gapView.onKeyDown(keyCode, event);

		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onResume() {
		super.onResume();
		activityEventsDispatcher.onResume();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		activityEventsDispatcher.onNewIntent(intent);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		activityEventsDispatcher.onActivityResult(requestCode, resultCode, data);
	}
}
