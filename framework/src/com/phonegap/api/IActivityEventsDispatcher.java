package com.phonegap.api;

import android.content.Intent;

public interface IActivityEventsDispatcher {
	public void onNewIntent(Intent intent);

	public void onResume();

	public void onActivityResult(int requestCode, int resultCode, Intent intent);
}
