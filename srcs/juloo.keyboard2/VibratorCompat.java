package juloo.keyboard2;

import android.content.Context;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class VibratorCompat
{
  public static void vibrate(View v, Config config)
  {
    if (config.vibrate_custom)
    {
      if (config.vibrate_duration > 0)
        vibrator_vibrate(v, config.vibrate_duration);
    }
    else
    {
      v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
          HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }
  }


  static void vibrator_vibrate(View v, long duration)
  {
    try
    {
      get_vibrator(v.getContext()).vibrate(duration);
    }
    catch (Exception e) {}
  }

  public static void vibrate(Context context, long duration)
  {
    try
    {
      if (duration > 0)
        get_vibrator(context).vibrate(duration);
    }
    catch (Exception e) {}
  }

  static Vibrator vibrator_service = null;

  static Vibrator get_vibrator(Context context)
  {
    if (vibrator_service == null)
    {
      vibrator_service =
        (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    }
    return vibrator_service;
  }
}
