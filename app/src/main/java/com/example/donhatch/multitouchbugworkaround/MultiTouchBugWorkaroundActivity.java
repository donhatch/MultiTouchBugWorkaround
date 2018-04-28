// To build and run:
/*
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.example.donhatch.multitouchbugworkaround/com.example.donhatch.multitouchbugworkaround.MultiTouchBugWorkaroundActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
*/

package com.example.donhatch.multitouchbugworkaround;

import android.content.res.Configuration;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.os.Handler;


public class MultiTouchBugWorkaroundActivity extends AppCompatActivity {
  private static final String TAG = MultiTouchBugWorkaroundActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    final int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "on onCreate");
    super.onCreate(savedInstanceState);
    setContentView(new PaintView(this) {{
      setBackgroundColor(0xffc0c0c0);
    }});

    if (true) {
      new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          FixedOnTouchListener.unitTest();
        }
      }, 1500);  // after a delay, to wait for other noise to clear in logcat
    }

    makeFullScreen();

    if (verboseLevel >= 1) Log.i(TAG, "out onCreate");
  }

  private void makeFullScreen() {
    // https://developer.android.com/training/system-ui/immersive.html#sticky
    // TODO: this apparently isn't adequate!  The top bar appears when rotation lock pops up its dialog.
    // We can make it go away by having onWindowFocusChanged(false) call us,
    // but it's annoying that the bar appears momentarily-- it would be better if it didn't.  How?
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(0
          | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  // not sure what this is
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  // hides navigation (bottom) bar
          | View.SYSTEM_UI_FLAG_FULLSCREEN  // hides top bar
          | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);  // other app I copied from did this, but I don't think we need it
  }
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    // This will get called on, e.g., orientation change,
    // if the manifest specifies that we handle config changes
    makeFullScreen();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "            in onWindowFocusChanged");

    if (true || hasFocus) {
      makeFullScreen();
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out onWindowFocusChanged");
  }
}
