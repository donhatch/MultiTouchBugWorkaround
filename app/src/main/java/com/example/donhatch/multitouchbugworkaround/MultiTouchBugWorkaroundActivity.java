// To build and run:
/*
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.example.donhatch.multitouchbugworkaround/com.example.donhatch.multitouchbugworkaround.MultiTouchBugWorkaroundActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
*/
// To retrieve log:
// CBB: currently the program re-creates the file each time (I think?) but `tail -f` on android doesn't notice that.  Should I open it in append mode?  But then it will grow without bound?  Hmm.  Is there a shell 1-liner that does the required thing?
// CBB: surely there's a better way than the following obscure recipe?
/*
adb exec-out "run-as com.example.donhatch.multitouchbugworkaround cat /data/user/0/com.example.donhatch.multitouchbugworkaround/files/FixedOnTouchListener.trace.txt"
adb exec-out "run-as com.example.donhatch.multitouchbugworkaround tail -1000000 -f /data/user/0/com.example.donhatch.multitouchbugworkaround/files/FixedOnTouchListener.trace.txt"
I think that only works when it's a debug app, though.

Screenshot:
     adb exec-out "screencap -p" > foo.png
*/

package com.example.donhatch.multitouchbugworkaround;

import android.content.Context;
import android.content.res.Configuration;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;


public class MultiTouchBugWorkaroundActivity extends AppCompatActivity {
  private static final String TAG = FixedOnTouchListener.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    final int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "on onCreate");
    super.onCreate(savedInstanceState);

    final Context context = getApplicationContext();
    if (false) {
      setContentView(new PaintView(context) {{
        setBackgroundColor(0xffc0c0c0);
      }});
    } else {
      // I want to make it so it doesn't take up the whole screen, to see whether I can get an ACTION_OUTSIDE
      setContentView(new LinearLayout(context) {{
        setBackgroundColor(0xffc0c0c0);
        addView(new PaintView(context) {{
          setBackgroundColor(0xffc0c0c0);
        }});
      }});
    }

    if (true) {
      new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          FixedOnTouchListenerInstrumented.unitTest();
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
    super.onConfigurationChanged(newConfig);
    makeFullScreen();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    int verboseLevel = 1;
    if (verboseLevel >= 1) Log.i(TAG, "            in onWindowFocusChanged");
    super.onWindowFocusChanged(hasFocus);
    if (true || hasFocus) {
      makeFullScreen();
    }
    if (verboseLevel >= 1) Log.i(TAG, "            out onWindowFocusChanged");
  }
}
