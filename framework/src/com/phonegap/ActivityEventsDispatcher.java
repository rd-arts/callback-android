package com.phonegap;

import android.content.Intent;

public interface ActivityEventsDispatcher {
	public void onNewIntent(Intent intent);

	public void onResume();

	public void onActivityResult(int requestCode, int resultCode, Intent intent);
}
