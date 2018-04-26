// To build and run:
/*
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.example.donhatch.multitouchbugworkaround/com.example.donhatch.multitouchbugworkaround.MultiTouchBugWorkaroundActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
*/

package com.example.donhatch.multitouchbugworkaround;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

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
    if (verboseLevel >= 1) Log.i(TAG, "out onCreate");
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "            in onWindowFocusChanged");

    if (hasFocus) {
      // https://developer.android.com/training/system-ui/immersive.html#sticky
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
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out onWindowFocusChanged");
  }
}
