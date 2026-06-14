package juloo.keyboard2;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.preference.PreferenceManager;
import java.util.Map;
import java.util.Set;

@TargetApi(24)
public final class DirectBootAwarePreferences
{

  public static SharedPreferences get_shared_preferences(Context context)
  {
    if (VERSION.SDK_INT < 24)
      return PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences prefs = get_protected_prefs(context);
    check_need_migration(context, prefs);
    return prefs;
  }


  public static void copy_preferences_to_protected_storage(Context context,
      SharedPreferences src)
  {
    if (VERSION.SDK_INT >= 24)
      copy_shared_preferences(src, get_protected_prefs(context));
  }

  static SharedPreferences get_protected_prefs(Context context)
  {
    String pref_name =
      PreferenceManager.getDefaultSharedPreferencesName(context);
    return context.createDeviceProtectedStorageContext()
      .getSharedPreferences(pref_name, Context.MODE_PRIVATE);
  }

  static void check_need_migration(Context app_context,
      SharedPreferences protected_prefs)
  {
    if (!protected_prefs.getBoolean("need_migration", true))
      return;
    SharedPreferences prefs;
    try
    {
      prefs = PreferenceManager.getDefaultSharedPreferences(app_context);
    }
    catch (Exception e)
    {

      return;
    }
    prefs.edit().putBoolean("need_migration", false).apply();
    copy_shared_preferences(prefs, protected_prefs);
  }

  static void copy_shared_preferences(SharedPreferences src, SharedPreferences dst)
  {
    SharedPreferences.Editor e = dst.edit();
    Map<String, ?> entries = src.getAll();
    for (String k : entries.keySet())
    {
      Object v = entries.get(k);
      if (v instanceof Boolean)
        e.putBoolean(k, (Boolean)v);
      else if (v instanceof Float)
        e.putFloat(k, (Float)v);
      else if (v instanceof Integer)
        e.putInt(k, (Integer)v);
      else if (v instanceof Long)
        e.putLong(k, (Long)v);
      else if (v instanceof String)
        e.putString(k, (String)v);
      else if (v instanceof Set)
        e.putStringSet(k, (Set<String>)v);
    }
    e.apply();
  }
}
