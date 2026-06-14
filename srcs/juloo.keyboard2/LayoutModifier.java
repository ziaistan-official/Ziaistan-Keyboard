package juloo.keyboard2;

import android.content.res.Resources;
import android.view.KeyEvent;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LayoutModifier
{
  static Config globalConfig;
  static KeyboardData.Row bottom_row;
  static KeyboardData.Row number_row_no_symbols;
  static KeyboardData.Row number_row_symbols;
  static KeyboardData num_pad;


  public static KeyboardData modify_layout(KeyboardData kw)
  {


    final TreeMap<KeyValue, KeyboardData.PreferredPos> extra_keys = new TreeMap<KeyValue, KeyboardData.PreferredPos>();
    final Set<KeyValue> remove_keys = new HashSet<KeyValue>();


    extra_keys.put(KeyValue.getKeyByName("config"), KeyboardData.PreferredPos.ANYWHERE);
    extra_keys.putAll(globalConfig.extra_keys_param);
    extra_keys.putAll(globalConfig.extra_keys_custom);


    KeyboardData.Row added_number_row = null;
    KeyboardData added_numpad = null;
    if (globalConfig.show_numpad)
    {
      added_numpad = modify_numpad(num_pad, kw);
      remove_keys.addAll(added_numpad.getKeys().keySet());
    }
    else if (globalConfig.add_number_row && !kw.embedded_number_row)
    {
      added_number_row = modify_number_row(globalConfig.number_row_symbols ? number_row_symbols : number_row_no_symbols, kw);
      remove_keys.addAll(added_number_row.getKeys(0).keySet());
    }

    if (kw.bottom_row)
      kw = kw.insert_row(bottom_row, kw.rows.size());


    Set<KeyValue> extra_keys_keyset = extra_keys.keySet();

    Set<KeyValue> kw_keys = kw.getKeys().keySet();
    if (globalConfig.extra_keys_subtype != null && kw.locale_extra_keys)
    {
      Set<KeyValue> present = new HashSet<KeyValue>(kw_keys);
      present.addAll(extra_keys_keyset);
      globalConfig.extra_keys_subtype.compute(extra_keys,
          new ExtraKeys.Query(kw.script, present));
    }
    kw = kw.mapKeys(new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        if (localized && !extra_keys.containsKey(key))
          return null;
        if (remove_keys.contains(key))
          return null;
        return modify_key(key);
      }
    });
    if (added_numpad != null)
      kw = kw.addNumPad(added_numpad);

    extra_keys_keyset.removeAll(kw_keys);
    if (extra_keys.size() > 0)
      kw = kw.addExtraKeys(extra_keys.entrySet().iterator());

    if (added_number_row != null)
      kw = kw.insert_row(added_number_row, 0);
    return kw;
  }


  public static KeyboardData modify_numpad(KeyboardData kw, KeyboardData main_kw)
  {
    final int map_digit = KeyModifier.modify_numpad_script(main_kw.numpad_script);
    return kw.mapKeys(new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        switch (key.getKind())
        {
          case Char:
            char prev_c = key.getChar();
            char c = prev_c;
            if (globalConfig.inverse_numpad)
              c = inverse_numpad_char(c);
            if (map_digit != -1)
            {
              KeyValue modified = ComposeKey.apply(map_digit, c);
              if (modified != null)
                return modified;
            }
            if (prev_c != c)
              return key.withChar(c);
            return key;
        }
        return modify_key(key);
      }
    });
  }


  public static KeyboardData modify_pinentry(KeyboardData kw, KeyboardData main_kw)
  {
    KeyboardData.MapKeyValues m = numpad_script_map(main_kw.numpad_script);
    return m == null ? kw : kw.mapKeys(m);
  }


  static KeyboardData.Row modify_number_row(KeyboardData.Row row,
      KeyboardData main_kw)
  {
    KeyboardData.MapKeyValues m = numpad_script_map(main_kw.numpad_script);
    return m == null ? row : row.mapKeys(m);
  }

  static KeyboardData.MapKeyValues numpad_script_map(String numpad_script)
  {
    final int map_digit = KeyModifier.modify_numpad_script(numpad_script);
    if (map_digit == -1)
      return null;
    return new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        KeyValue modified = ComposeKey.apply(map_digit, key);
        return (modified != null) ? modified : key;
      }
    };
  }


  static KeyValue modify_key(KeyValue orig)
  {
    switch (orig.getKind())
    {
      case Event:
        switch (orig.getEvent())
        {
          case CHANGE_METHOD_PICKER:
            if (globalConfig.switch_input_immediate)
              return KeyValue.getKeyByName("change_method_prev");
            break;
          case ACTION:
            if (globalConfig.actionLabel == null)
              return null;
            if (globalConfig.swapEnterActionKey)
              return KeyValue.getKeyByName("enter");
            return KeyValue.makeActionKey(globalConfig.actionLabel);
          case SWITCH_FORWARD:
            return (globalConfig.layouts.size() > 1) ? orig : null;
          case SWITCH_BACKWARD:
            return (globalConfig.layouts.size() > 2) ? orig : null;
          case SWITCH_VOICE_TYPING:
          case SWITCH_VOICE_TYPING_CHOOSER:
            return globalConfig.shouldOfferVoiceTyping ? orig : null;
        }
        break;
      case Keyevent:
        switch (orig.getKeyevent())
        {
          case KeyEvent.KEYCODE_ENTER:
            if (globalConfig.swapEnterActionKey && globalConfig.actionLabel != null)
              return KeyValue.makeActionKey(globalConfig.actionLabel);
            break;
        }
        break;
    }
    return orig;
  }

  static char inverse_numpad_char(char c)
  {
    switch (c)
    {
      case '7': return '1';
      case '8': return '2';
      case '9': return '3';
      case '1': return '7';
      case '2': return '8';
      case '3': return '9';
      default: return c;
    }
  }

  public static void init(Config globalConfig_, Resources res)
  {
    globalConfig = globalConfig_;
    try
    {
      number_row_no_symbols = KeyboardData.load_row(res, R.xml.number_row_no_symbols);
      number_row_symbols = KeyboardData.load_row(res, R.xml.number_row);

      String customBottomXml = Config.globalPrefs() == null ? null : Config.globalPrefs().getString("custom_bottom_row_xml", null);
      if (customBottomXml != null) {
          try {
              KeyboardData layout = KeyboardData.load_string_exn(customBottomXml);
              bottom_row = layout.rows.get(0);
          } catch (Exception e) {
              bottom_row = KeyboardData.load_row(res, R.xml.bottom_row);
          }
      } else {
          bottom_row = KeyboardData.load_row(res, R.xml.bottom_row);
      }

      num_pad = KeyboardData.load_num_pad(res);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e.getMessage());
    }
  }
}
