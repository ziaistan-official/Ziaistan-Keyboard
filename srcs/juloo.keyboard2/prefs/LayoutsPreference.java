package juloo.keyboard2.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import android.widget.Toast;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import juloo.keyboard2.*;
import org.json.JSONException;
import org.json.JSONObject;

public class LayoutsPreference extends ListGroupPreference<LayoutsPreference.Layout>
{
  static final String KEY = "layouts";
  static final List<Layout> DEFAULT =
    Arrays.asList(new NamedLayout("latn_qwerty_us"), new NamedLayout("urdu_ziaistani_ur"), new NamedLayout("urdu_phonetic_ur"));
  static final ListGroupPreference.Serializer<Layout> SERIALIZER =
    new Serializer();


  String[] _layout_display_names;

  public LayoutsPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setKey(KEY);
    Resources res = ctx.getResources();
    _layout_display_names = res.getStringArray(R.array.pref_layout_entries);
  }


  static List<String> _unsafe_layout_ids_str = null;
  static TypedArray _unsafe_layout_ids_res = null;


  public static List<String> get_layout_names(Resources res)
  {
    if (_unsafe_layout_ids_str == null)
      _unsafe_layout_ids_str = Arrays.asList(
          res.getStringArray(R.array.pref_layout_values));
    return _unsafe_layout_ids_str;
  }


  public static int layout_id_of_name(Resources res, String name)
  {
    if (_unsafe_layout_ids_res == null)
      _unsafe_layout_ids_res = res.obtainTypedArray(R.array.layout_ids);
    int i = get_layout_names(res).indexOf(name);
    if (i >= 0)
      return _unsafe_layout_ids_res.getResourceId(i, 0);
    return -1;
  }


  public static List<Layout> load_layouts_from_preferences(SharedPreferences prefs)
  {
    return load_from_preferences(KEY, prefs, DEFAULT, SERIALIZER);
  }

  public static List<KeyboardData> load_from_preferences(Resources res, SharedPreferences prefs)
  {
    List<KeyboardData> layouts = new ArrayList<KeyboardData>();
    for (Layout l : load_from_preferences(KEY, prefs, DEFAULT, SERIALIZER))
    {
      if (l instanceof NamedLayout)
        layouts.add(layout_of_string(res, ((NamedLayout)l).name));
      else if (l instanceof CustomLayout)
        layouts.add(((CustomLayout)l).parsed);
      else
        layouts.add(null);
    }
    return layouts;
  }


  public static void save_to_preferences(SharedPreferences.Editor prefs, List<Layout> items)
  {
    save_to_preferences(KEY, prefs, items, SERIALIZER);
  }

  public static KeyboardData layout_of_string(Resources res, String name)
  {
    int id = layout_id_of_name(res, name);
    if (id > 0)
      return KeyboardData.load(res, id);

    return null;
  }

  @Override
  protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
  {
    super.onSetInitialValue(restoreValue, defaultValue);
    if (_values.size() == 0)
      set_values(new ArrayList<Layout>(DEFAULT), false);
  }

  String label_of_layout(Layout l)
  {
    if (l instanceof NamedLayout)
    {
      String lname = ((NamedLayout)l).name;
      int value_i = get_layout_names(getContext().getResources()).indexOf(lname);
      return value_i < 0 ? lname : _layout_display_names[value_i];
    }
    else if (l instanceof CustomLayout)
    {

      CustomLayout cl = (CustomLayout)l;
      if (cl.parsed != null && cl.parsed.name != null
          && !cl.parsed.name.equals(""))
        return cl.parsed.name;
      else
        return getContext().getString(R.string.pref_layout_e_custom);
    }
    else
      return getContext().getString(R.string.pref_layout_e_system);
  }

  @Override
  String label_of_value(Layout value, int i)
  {
    return getContext().getString(R.string.pref_layouts_item, i + 1,
        label_of_layout(value));
  }

  @Override
  AddButton on_attach_add_button(AddButton prev_btn)
  {
    if (prev_btn == null)
      return new LayoutsAddButton(getContext());
    return prev_btn;
  }

  @Override
  boolean should_allow_remove_item(Layout value)
  {
    return (_values.size() > 1 && !(value instanceof CustomLayout));
  }

  @Override
  ListGroupPreference.Serializer<Layout> get_serializer() { return SERIALIZER; }

  void select_dialog(final SelectionCallback callback)
  {
    List<String> displayNames = new ArrayList<>(Arrays.asList(_layout_display_names));
    List<String> valueNames = new ArrayList<>(get_layout_names(getContext().getResources()));

    // Add external layouts from backup
    File backupDir = new File("/storage/emulated/0/Download/ziaistan_keyboard_backup/");
    if (backupDir.exists()) {
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".xml"));
        if (files != null) {
            for (File f : files) {
                displayNames.add("Backup: " + f.getName());
                valueNames.add("external:" + f.getAbsolutePath());
            }
        }
    }

    ArrayAdapter layouts = new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, displayNames);
    new AlertDialog.Builder(getContext())
      .setView(View.inflate(getContext(), R.layout.dialog_edit_text, null))
      .setAdapter(layouts, new DialogInterface.OnClickListener(){
        public void onClick(DialogInterface _dialog, int which)
        {
          String name = valueNames.get(which);
          if (name.startsWith("external:")) {
              try {
                  String xml = Utils.read_all_utf8(new java.io.FileInputStream(name.substring(9)));
                  callback.select(CustomLayout.parse(xml));
              } catch (Exception e) {
                  Toast.makeText(getContext(), "Failed to load backup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
              }
              return;
          }
          switch (name)
          {
            case "system":
              callback.select(new SystemLayout());
              break;
            case "custom":
              select_custom(callback, read_initial_custom_layout());
              break;
            default:
              callback.select(new NamedLayout(name));
              break;
          }
        }
      })
      .show();
  }


  void select_custom(final SelectionCallback callback, String initial_text)
  {
    boolean allow_remove = callback.allow_remove() && _values.size() > 1;
    CustomLayoutEditDialog.show(getContext(), initial_text, allow_remove,
        new CustomLayoutEditDialog.Callback()
        {
          public void select(String text)
          {
            if (text == null)
              callback.select(null);
            else
              callback.select(CustomLayout.parse(text));
          }

          public String validate(String text)
          {
            try
            {
              KeyboardData.load_string_exn(text);
              return null;
            }
            catch (Exception e)
            {
              return e.getMessage();
            }
          }
        });
  }


  @Override
  void select(final SelectionCallback callback, Layout prev_layout)
  {
    if (prev_layout != null) {
      String xml = null;
      if (prev_layout instanceof CustomLayout) xml = ((CustomLayout)prev_layout).xml;
      else if (prev_layout instanceof NamedLayout) {
          KeyboardData data = layout_of_string(getContext().getResources(), ((NamedLayout)prev_layout).name);
          if (data != null) xml = KeyboardData.serialize_to_unified_xml(data);
      }

      if (xml != null) {
          final String finalXml = xml;
          new AlertDialog.Builder(getContext())
              .setTitle("Edit Layout")
              .setItems(new String[]{"Visual Editor (Live)", "Source Editor (XML)"}, (dialog, which) -> {
                  if (which == 0) {
                      // Save to a temp pref for the activity to pick up
                      Config.globalPrefs().edit().putString("layout_customizer_input", finalXml).apply();
                      Intent intent = new Intent(getContext(), LiveLayoutCustomizationActivity.class);
                      getContext().startActivity(intent);

                      // Listen for results (simulated via polling or standard activity lifecycle)
                      Toast.makeText(getContext(), "Apply changes in the visual editor and come back.", Toast.LENGTH_LONG).show();
                  } else {
                      select_custom(callback, finalXml);
                  }
              })
              .show();
          return;
      }
    }
    select_dialog(callback);
  }


  String read_initial_custom_layout()
  {
    try
    {
      Resources res = getContext().getResources();
      return Utils.read_all_utf8(res.openRawResource(R.raw.latn_qwerty_us));
    }
    catch (Exception _e)
    {
      return "";
    }
  }

  class LayoutsAddButton extends AddButton
  {
    public LayoutsAddButton(Context ctx)
    {
      super(ctx);
      setLayoutResource(R.layout.pref_layouts_add_btn);
    }
  }


  public interface Layout {}

  public static final class SystemLayout implements Layout
  {
    public SystemLayout() {}
  }


  public static final class NamedLayout implements Layout
  {
    public final String name;
    public NamedLayout(String n) { name = n; }
  }


  public static final class CustomLayout implements Layout
  {
    public final String xml;

    public final KeyboardData parsed;
    public CustomLayout(String xml_, KeyboardData k) { xml = xml_; parsed = k; }
    public static CustomLayout parse(String xml)
    {
      KeyboardData parsed = null;
      try { parsed = KeyboardData.load_string_exn(xml); }
      catch (Exception e) {}
      return new CustomLayout(xml, parsed);
    }
  }


  public static class Serializer implements ListGroupPreference.Serializer<Layout>
  {
    public Layout load_item(Object obj) throws JSONException
    {
      if (obj instanceof String)
      {
        String name = (String)obj;
        if (name.equals("system"))
          return new SystemLayout();
        return new NamedLayout(name);
      }
      JSONObject obj_ = (JSONObject)obj;
      switch (obj_.getString("kind"))
      {
        case "custom": return CustomLayout.parse(obj_.getString("xml"));
        case "system": default: return new SystemLayout();
      }
    }

    public Object save_item(Layout v) throws JSONException
    {
      if (v instanceof NamedLayout)
        return ((NamedLayout)v).name;
      if (v instanceof CustomLayout)
        return new JSONObject().put("kind", "custom")
          .put("xml", ((CustomLayout)v).xml);
      return new JSONObject().put("kind", "system");
    }
  }
}
