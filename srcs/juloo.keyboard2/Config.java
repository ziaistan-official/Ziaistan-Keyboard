package juloo.keyboard2;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import juloo.keyboard2.prefs.CustomExtraKeysPreference;
import juloo.keyboard2.prefs.ExtraKeysPreference;
import juloo.keyboard2.prefs.LayoutsPreference;

public final class Config
{

  public static final int WIDE_DEVICE_THRESHOLD = 600;

  private final SharedPreferences _prefs;


  public final float marginTop;
  public final float keyPadding;

  public final float labelTextSize;
  public final float sublabelTextSize;



  public List<KeyboardData> layouts;
  public boolean show_numpad = false;

  public boolean inverse_numpad = false;
  public boolean add_number_row;
  public boolean number_row_symbols;
  public float swipe_dist_px;
  public float slide_step_px;

  public boolean vibrate_custom;

  public long vibrate_duration;
  public long longPressTimeout;
  public long longPressInterval;
  public boolean keyrepeat_enabled;
  public float margin_bottom;
  public int keyboardHeightPercent;
  public int screenHeightPixels;
  public float horizontal_margin;
  public float key_vertical_margin;
  public float key_horizontal_margin;
  public int labelBrightness;
  public int keyboardOpacity;
  public float customBorderRadius;
  public float customBorderLineWidth;
  public int keyOpacity;
  public int keyActivatedOpacity;
  public boolean double_tap_lock_shift;
  public float characterSize;
  public int theme;
  public String themeName;
  public boolean autocapitalisation;
  public boolean enable_suggestions;
  public boolean suggestionStripOnTop;
  public boolean switch_input_immediate;
  public NumberLayout selected_number_layout;
  public boolean borderConfig;
  public int circle_sensitivity;
  public boolean clipboard_history_enabled;
  public int clipboard_history_duration;
  public boolean clipboardActionsOnTop;
  public boolean enable_typing_history;
  public boolean popup_on_keypress;
  public boolean circle_gestures;
  public boolean application_integrations;
  public boolean encapsulation;
  public boolean case_conversion_and_formatting;
  public boolean enable_typing_hud;
  public int typing_hud_duration;
  public int typing_hud_bg_color;
  public int typing_hud_txt_color;
  public int typing_hud_txt_size;
  public int typing_hud_x;
  public int typing_hud_y;

  public String suggestion_source_priority;
  public String suggestion_search_priority;
  public Map<String, Integer> suggestion_category_colors;


  public boolean double_space_period;
  public boolean auto_correct_space;
  public boolean revert_on_backspace;
  public boolean space_after_suggestion;
  public boolean learn_new_words;
  public boolean key_text_bold;
  public boolean show_sublabels;
  public boolean use_system_font;
  public boolean shortcut_learn_d;
  public boolean shortcut_translate_t;
  public boolean shortcut_keep_k;
  public boolean shortcut_obsidian_o;
  public boolean shortcut_chrome_c;
  public boolean format_bold_b;
  public boolean format_italic_i;
  public boolean format_upper_u;
  public boolean format_lower_l;
  public boolean format_sentence_s;
  public boolean vibrate_on_correction;
  public boolean sound_on_keypress;
  public int sound_volume;


  public boolean vibrate_on_space;
  public boolean vibrate_on_delete;
  public boolean vibrate_on_action;
  public boolean sound_on_space;
  public boolean sound_on_delete;
  public boolean sound_on_action;
  public boolean show_gesture_trail;
  public boolean swipe_space_cursor;
  public boolean swipe_delete_word;
  public boolean show_emoji_key;
  public boolean show_language_key;
  public boolean auto_switch_back_emoji;
  public boolean block_offensive_words;
  public boolean incognito_mode;
  public boolean show_number_row_password;
  public boolean disable_animations;
  public boolean long_press_space_cursor;
  public boolean delete_swallows_space;
  public boolean smart_punctuation;
  public boolean show_clipboard_suggestion;
  public boolean auto_add_user_words;
  public boolean force_landscape_fullscreen;
  public boolean colored_sublabels;
  public boolean draw_key_borders;


  public boolean emoji_show_recent;
  public boolean emoji_show_kaomoji;
  public float emoji_size_factor;
  public int emoji_columns_portrait;
  public int emoji_columns_landscape;
  public boolean emoji_vibrate;
  public boolean emoji_sound;
  public boolean emoji_long_press_name;
  public boolean emoji_favorites_enabled;
  public boolean emoji_long_press_add_favorite;
  public int emoji_history_size;
  public boolean emoji_favorites_first;
  public float emoji_kaomoji_size_factor;
  public boolean emoji_show_tab_labels;


  public int gesture_trail_style;
  public float gesture_trail_width_factor;
  public float gesture_trail_length_factor;


  public String app_theme;


  public boolean shouldOfferVoiceTyping;
  public String actionLabel;
  public int actionId;
  public boolean swapEnterActionKey;
  public ExtraKeys extra_keys_subtype;
  public Map<KeyValue, KeyboardData.PreferredPos> extra_keys_param;
  public Map<KeyValue, KeyboardData.PreferredPos> extra_keys_custom;

  public IKeyEventHandler handler;
  public boolean orientation_landscape = false;
  public boolean foldable_unfolded = false;
  public boolean wide_screen = false;

  int current_layout_narrow;
  int current_layout_wide;

  private Config(SharedPreferences prefs, Resources res, IKeyEventHandler h, Boolean foldableUnfolded)
  {
    _prefs = prefs;

    marginTop = res.getDimension(R.dimen.margin_top);
    keyPadding = res.getDimension(R.dimen.key_padding);
    labelTextSize = 0.33f;
    sublabelTextSize = 0.22f;

    refresh(res, foldableUnfolded);

    shouldOfferVoiceTyping = false;
    actionLabel = null;
    actionId = 0;
    swapEnterActionKey = false;
    extra_keys_subtype = null;
    handler = h;
  }


  public void refresh(Resources res, Boolean foldableUnfolded)
  {
    DisplayMetrics dm = res.getDisplayMetrics();
    orientation_landscape = res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    foldable_unfolded = foldableUnfolded;

    float characterSizeScale = 1.f;
    String show_numpad_s = _prefs.getString("show_numpad", "never");
    show_numpad = "always".equals(show_numpad_s);
    if (orientation_landscape)
    {
      if ("landscape".equals(show_numpad_s))
        show_numpad = true;
      keyboardHeightPercent = _prefs.getInt(foldable_unfolded ? "keyboard_height_landscape_unfolded" : "keyboard_height_landscape", 20);
      characterSizeScale = 1.25f;
    }
    else
    {
      keyboardHeightPercent = _prefs.getInt(foldable_unfolded ? "keyboard_height_unfolded" : "keyboard_height", 20);
    }
    layouts = LayoutsPreference.load_from_preferences(res, _prefs);
    inverse_numpad = _prefs.getString("numpad_layout", "default").equals("low_first");
    String number_row = _prefs.getString("number_row", "symbols");
    add_number_row = !number_row.equals("no_number_row");
    number_row_symbols = number_row.equals("symbols");




    float dpi_ratio = Math.max(dm.xdpi, dm.ydpi) / Math.min(dm.xdpi, dm.ydpi);
    float swipe_scaling = Math.min(dm.widthPixels, dm.heightPixels) / 10.f * dpi_ratio;
    float swipe_dist_value = Float.valueOf(_prefs.getString("swipe_dist", "15"));
    swipe_dist_px = swipe_dist_value / 25.f * swipe_scaling;
    float slider_sensitivity = Float.valueOf(_prefs.getString("slider_sensitivity", "15")) / 100.f;
    slide_step_px = slider_sensitivity * swipe_scaling;
    vibrate_custom = _prefs.getBoolean("vibrate_custom", false);
    vibrate_duration = _prefs.getInt("vibrate_duration", 20);
    longPressTimeout = _prefs.getInt("longpress_timeout", 600);
    longPressInterval = _prefs.getInt("longpress_interval", 65);
    keyrepeat_enabled = _prefs.getBoolean("keyrepeat_enabled", true);
    margin_bottom = get_dip_pref_oriented(dm, "margin_bottom", 0, 3);
    key_vertical_margin = get_dip_pref(dm, "key_vertical_margin", 1.5f) / 100;
    key_horizontal_margin = get_dip_pref(dm, "key_horizontal_margin", 2) / 100;

    labelBrightness = _prefs.getInt("label_brightness", 100) * 255 / 100;

    keyboardOpacity = _prefs.getInt("keyboard_opacity", 100) * 255 / 100;
    keyOpacity = _prefs.getInt("key_opacity", 100) * 255 / 100;
    keyActivatedOpacity = _prefs.getInt("key_activated_opacity", 100) * 255 / 100;

    borderConfig = _prefs.getBoolean("border_config", false);
    customBorderRadius = _prefs.getInt("custom_border_radius", 0) / 100.f;
    customBorderLineWidth = get_dip_pref(dm, "custom_border_line_width", 0);
    screenHeightPixels = dm.heightPixels;
    horizontal_margin =
      get_dip_pref_oriented(dm, "horizontal_margin", 15, 28);
    double_tap_lock_shift = _prefs.getBoolean("lock_double_tap", true);
    characterSize =
      _prefs.getFloat("character_size", 1.15f)
      * characterSizeScale;
    themeName = _prefs.getString("theme", "galactic");
    theme = getThemeId(res, themeName);
    autocapitalisation = _prefs.getBoolean("autocapitalisation", true);
    enable_suggestions = _prefs.getBoolean("enable_suggestions", true);
    suggestionStripOnTop = _prefs.getBoolean("suggestion_strip_on_top", true);
    switch_input_immediate = _prefs.getBoolean("switch_input_immediate", false);
    extra_keys_param = ExtraKeysPreference.get_extra_keys(_prefs);
    extra_keys_custom = CustomExtraKeysPreference.get(_prefs);
    selected_number_layout = NumberLayout.of_string(_prefs.getString("number_entry_layout", "pin"));
    current_layout_narrow = _prefs.getInt("current_layout_portrait", 0);
    current_layout_wide = _prefs.getInt("current_layout_landscape", 0);
    circle_sensitivity = Integer.valueOf(_prefs.getString("circle_sensitivity", "2"));
    clipboard_history_enabled = _prefs.getBoolean("clipboard_history_enabled", true);
    clipboard_history_duration = Integer.parseInt(_prefs.getString("clipboard_history_duration", String.valueOf(Integer.MAX_VALUE)));
    clipboardActionsOnTop = _prefs.getBoolean("clipboard_actions_on_top", false);
    enable_typing_history = _prefs.getBoolean("enable_typing_history", true);
    popup_on_keypress = _prefs.getBoolean("popup_on_keypress", true);
    circle_gestures = _prefs.getBoolean("circle_gestures", true);
    application_integrations = _prefs.getBoolean("application_integrations", true);
    encapsulation = _prefs.getBoolean("encapsulation", true);
    case_conversion_and_formatting = _prefs.getBoolean("case_conversion_and_formatting", true);
    enable_typing_hud = _prefs.getBoolean("enable_typing_hud", false);
    typing_hud_duration = _prefs.getInt("typing_hud_duration", 3000);
    typing_hud_bg_color = parseColor(_prefs.getString("typing_hud_bg_color", "CC000000"), 0xCC000000);
    typing_hud_txt_color = parseColor(_prefs.getString("typing_hud_txt_color", "FFFFFFFF"), 0xFFFFFFFF);
    typing_hud_txt_size = _prefs.getInt("typing_hud_txt_size", 18);
    typing_hud_x = _prefs.getInt("typing_hud_x", -1);
    typing_hud_y = _prefs.getInt("typing_hud_y", -1);

    suggestion_source_priority = _prefs.getString("suggestion_source_priority", "typed,filters,next_word,custom,common,wordlist");
    suggestion_search_priority = _prefs.getString("suggestion_search_priority", "prefix,keyboard_aware,deletion,insertion,substitution,transposition,doubling,singling");
    suggestion_category_colors = loadCategoryColors();


    swapEnterActionKey = _prefs.getBoolean("swap_enter_action_key", false);
    double_space_period = _prefs.getBoolean("double_space_period", false);
    auto_correct_space = _prefs.getBoolean("auto_correct_space", true);
    revert_on_backspace = _prefs.getBoolean("revert_on_backspace", true);
    space_after_suggestion = _prefs.getBoolean("space_after_suggestion", true);
    learn_new_words = _prefs.getBoolean("learn_new_words", true);
    key_text_bold = _prefs.getBoolean("key_text_bold", true);
    show_sublabels = _prefs.getBoolean("show_sublabels", true);
    use_system_font = _prefs.getBoolean("use_system_font", false);
    shortcut_learn_d = _prefs.getBoolean("shortcut_learn_d", true);
    shortcut_translate_t = _prefs.getBoolean("shortcut_translate_t", true);
    shortcut_keep_k = _prefs.getBoolean("shortcut_keep_k", true);
    shortcut_obsidian_o = _prefs.getBoolean("shortcut_obsidian_o", true);
    shortcut_chrome_c = _prefs.getBoolean("shortcut_chrome_c", true);
    format_bold_b = _prefs.getBoolean("format_bold_b", true);
    format_italic_i = _prefs.getBoolean("format_italic_i", true);
    format_upper_u = _prefs.getBoolean("format_upper_u", true);
    format_lower_l = _prefs.getBoolean("format_lower_l", true);
    format_sentence_s = _prefs.getBoolean("format_sentence_s", true);
    vibrate_on_correction = _prefs.getBoolean("vibrate_on_correction", false);
    sound_on_keypress = _prefs.getBoolean("sound_on_keypress", false);
    sound_volume = _prefs.getInt("sound_volume", 50);

    vibrate_on_space = _prefs.getBoolean("vibrate_on_space", true);
    vibrate_on_delete = _prefs.getBoolean("vibrate_on_delete", true);
    vibrate_on_action = _prefs.getBoolean("vibrate_on_action", true);
    sound_on_space = _prefs.getBoolean("sound_on_space", true);
    sound_on_delete = _prefs.getBoolean("sound_on_delete", true);
    sound_on_action = _prefs.getBoolean("sound_on_action", true);
    show_gesture_trail = _prefs.getBoolean("show_gesture_trail", true);
    swipe_space_cursor = _prefs.getBoolean("swipe_space_cursor", true);
    swipe_delete_word = _prefs.getBoolean("swipe_delete_word", true);
    show_emoji_key = _prefs.getBoolean("show_emoji_key", true);
    show_language_key = _prefs.getBoolean("show_language_key", true);
    auto_switch_back_emoji = _prefs.getBoolean("auto_switch_back_emoji", true);
    block_offensive_words = _prefs.getBoolean("block_offensive_words", true);
    incognito_mode = _prefs.getBoolean("incognito_mode", false);
    show_number_row_password = _prefs.getBoolean("show_number_row_password", true);
    disable_animations = _prefs.getBoolean("disable_animations", false);
    long_press_space_cursor = _prefs.getBoolean("long_press_space_cursor", true);
    delete_swallows_space = _prefs.getBoolean("delete_swallows_space", false);
    smart_punctuation = _prefs.getBoolean("smart_punctuation", false);
    show_clipboard_suggestion = _prefs.getBoolean("show_clipboard_suggestion", true);
    auto_add_user_words = _prefs.getBoolean("auto_add_user_words", false);
    force_landscape_fullscreen = _prefs.getBoolean("force_landscape_fullscreen", false);
    colored_sublabels = _prefs.getBoolean("colored_sublabels", false);
    draw_key_borders = _prefs.getBoolean("draw_key_borders", false);

    emoji_show_recent = _prefs.getBoolean("emoji_show_recent", true);
    emoji_show_kaomoji = _prefs.getBoolean("emoji_show_kaomoji", true);
    emoji_size_factor = _prefs.getFloat("emoji_size_factor", 1.0f);
    emoji_columns_portrait = _prefs.getInt("emoji_columns_portrait", 7);
    emoji_columns_landscape = _prefs.getInt("emoji_columns_landscape", 10);
    emoji_vibrate = _prefs.getBoolean("emoji_vibrate", true);
    emoji_sound = _prefs.getBoolean("emoji_sound", false);
    emoji_long_press_name = _prefs.getBoolean("emoji_long_press_name", true);
    emoji_favorites_enabled = _prefs.getBoolean("emoji_favorites_enabled", true);
    emoji_long_press_add_favorite = _prefs.getBoolean("emoji_long_press_add_favorite", false);
    emoji_history_size = _prefs.getInt("emoji_history_size", 50);
    emoji_favorites_first = _prefs.getBoolean("emoji_favorites_first", false);
    emoji_kaomoji_size_factor = _prefs.getFloat("emoji_kaomoji_size_factor", 1.0f);
    emoji_show_tab_labels = _prefs.getBoolean("emoji_show_tab_labels", false);

    gesture_trail_style = Integer.parseInt(_prefs.getString("gesture_trail_style", "0"));
    gesture_trail_width_factor = _prefs.getFloat("gesture_trail_width_factor", 1.0f);
    gesture_trail_length_factor = _prefs.getFloat("gesture_trail_length_factor", 1.0f);
    app_theme = _prefs.getString("app_theme", "system");

    float screen_width_dp = dm.widthPixels / dm.density;
    wide_screen = screen_width_dp >= WIDE_DEVICE_THRESHOLD;
  }

  public int get_current_layout()
  {
    return (wide_screen)
            ? current_layout_wide : current_layout_narrow;
  }

  public void set_current_layout(int l)
  {
    if (wide_screen)
      current_layout_wide = l;
    else
      current_layout_narrow = l;

    SharedPreferences.Editor e = _prefs.edit();
    e.putInt("current_layout_portrait", current_layout_narrow);
    e.putInt("current_layout_landscape", current_layout_wide);
    e.apply();
  }

  private Map<String, Integer> loadCategoryColors() {
    Map<String, Integer> colors = new java.util.HashMap<>();
    String sourcePriority = _prefs.getString("suggestion_source_priority", "typed,filters,next_word,custom,common,wordlist");
    String searchPriority = _prefs.getString("suggestion_search_priority", "prefix,keyboard_aware,deletion,insertion,substitution,transposition,doubling,singling");

    java.util.Set<String> allCats = new java.util.HashSet<>();
    for (String s : sourcePriority.split(",")) allCats.add(s.trim());
    for (String s : searchPriority.split(",")) allCats.add(s.trim());
    allCats.add("autocorrect");
    allCats.add("keyboard_aware");

    for (String cat : allCats) {
        String defaultHex = cat.equals("typed") ? "FF00FF00" : "FFFFFFFF";
        String hex = _prefs.getString("suggestion_color_" + cat, defaultHex);
        colors.put(cat, parseColor(hex, (int) Long.parseLong(defaultHex, 16)));
    }
    return colors;
  }

  private int parseColor(String colorString, int defaultColor) {
      if (colorString == null || colorString.isEmpty()) return defaultColor;
      try {
          if (colorString.startsWith("#")) colorString = colorString.substring(1);
          if (colorString.length() == 6) colorString = "FF" + colorString;
          return (int) Long.parseLong(colorString, 16);
      } catch (Exception e) {
          return defaultColor;
      }
  }

  public void set_clipboard_history_enabled(boolean e)
  {
    clipboard_history_enabled = e;
    _prefs.edit().putBoolean("clipboard_history_enabled", e).commit();
  }

  private float get_dip_pref(DisplayMetrics dm, String pref_name, float def)
  {
    float value;
    try { value = _prefs.getInt(pref_name, -1); }
    catch (Exception e) { value = _prefs.getFloat(pref_name, -1f); }
    if (value < 0f)
      value = def;
    return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm));
  }


  float get_dip_pref_oriented(DisplayMetrics dm, String pref_base_name, float def_port, float def_land)
  {
    final String suffix;
    if (foldable_unfolded) {
      suffix = orientation_landscape ? "_landscape_unfolded" : "_portrait_unfolded";
    } else {
      suffix = orientation_landscape ? "_landscape" : "_portrait";
    }

    float def = orientation_landscape ? def_land : def_port;
    return get_dip_pref(dm, pref_base_name + suffix, def);
  }

  private int getThemeId(Resources res, String theme_name)
  {
    int night_mode = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    switch (theme_name)
    {
      case "light": return R.style.Light;
      case "black": return R.style.Black;
      case "altblack": return R.style.AltBlack;
      case "dark": return R.style.Dark;
      case "white": return R.style.White;
      case "epaper": return R.style.ePaper;
      case "desert": return R.style.Desert;
      case "jungle": return R.style.Jungle;
      case "monetlight": return R.style.MonetLight;
      case "monetdark": return R.style.MonetDark;
      case "monet":
        if ((night_mode & Configuration.UI_MODE_NIGHT_NO) != 0)
          return R.style.MonetLight;
        return R.style.MonetDark;
      case "rosepine": return R.style.RosePine;
      case "everforestlight": return R.style.EverforestLight;
      case "cobalt": return R.style.Cobalt;
      case "pine": return R.style.Pine;
      case "epaperblack": return R.style.ePaperBlack;
      case "goldenpearl": return R.style.GoldenPearl;
      case "cyberdream": return R.style.CyberDream;
      case "holographic": return R.style.Holographic;
      case "cosmic": return R.style.Cosmic;
      case "neonpunk": return R.style.NeonPunk;
      case "galactic": return R.style.Galactic;
      case "quantum": return R.style.Quantum;
      case "waterdrop": return R.style.WaterDrop;
      case "sponge": return R.style.Sponge;
      case "metal": return R.style.Metal;
      case "wood": return R.style.Wood;
      case "glass": return R.style.Glass;
      case "plastic": return R.style.Plastic;
      case "leather": return R.style.Leather;
      case "denim": return R.style.Denim;
      case "stone": return R.style.Stone;
      case "brick": return R.style.Brick;
      case "marble": return R.style.Marble;
      case "carbonfiber": return R.style.CarbonFiber;
      case "circuit": return R.style.Circuit;
      case "grid": return R.style.Grid;
      case "paper": return R.style.Paper;
      case "cork": return R.style.Cork;
      case "fabric": return R.style.Fabric;
      case "knitted": return R.style.Knitted;
      case "ice": return R.style.Ice;
      case "fire": return R.style.Fire;
      case "sky": return R.style.Sky;
      case "sand": return R.style.Sand;
      case "forestcamo": return R.style.ForestCamo;
      case "chalkboard": return R.style.Chalkboard;
      case "retro": return R.style.Retro;
      case "neongrid": return R.style.NeonGrid;
      case "cybercity": return R.style.CyberCity;
      case "laser": return R.style.Laser;
      case "plasma": return R.style.Plasma;
      case "hacker": return R.style.Hacker;
      case "synthwave": return R.style.Synthwave;
      case "matrix": return R.style.Matrix;
      case "terminal": return R.style.Terminal;
      case "glitch": return R.style.Glitch;
      case "future": return R.style.Future;
      case "hexagon": return R.style.Hexagon;
      case "circle": return R.style.Circle;
      case "leaf": return R.style.Leaf;
      case "gem": return R.style.Gem;
      case "shield": return R.style.Shield;
      case "star": return R.style.Star;
      case "bubble": return R.style.Bubble;
      case "tile": return R.style.Tile;
      case "puzzle": return R.style.Puzzle;
      case "diamond": return R.style.Diamond;

      case "cyberpunk": return R.style.NeonPunk;
      case "liquid_glass": return R.style.Glass;
      case "mechanical_rgb": return R.style.Dark;
      case "magma_ember": return R.style.Fire;
      case "ink_parchment": return R.style.Paper;
      case "cosmic_nebula": return R.style.Cosmic;
      case "sakura_garden": return R.style.Light;
      case "retro_8bit": return R.style.Retro;
      case "golden_era": return R.style.GoldenPearl;
      case "deep_ocean": return R.style.AppTheme_Ocean;
      case "neon_rain": return R.style.NeonPunk;
      case "candy_crush": return R.style.Light;
      case "steampunk": return R.style.Wood;

      case "spirit_realm": return R.style.Dark;
      case "golden_luxury": return R.style.GoldenPearl;
      case "sakura_breeze": return R.style.Light;
      case "bioluminescence": return R.style.AppTheme_Ocean;
      case "retro_arcade": return R.style.Retro;
      case "crystal_prism": return R.style.Holographic;
      case "vaporwave": return R.style.Synthwave;
      case "noir_rain": return R.style.Dark;
      case "paper_cutout": return R.style.Paper;
      case "star_field": return R.style.Cosmic;
      case "gears": return R.style.Metal;
      default:
      case "system":
        if ((night_mode & Configuration.UI_MODE_NIGHT_NO) != 0)
          return R.style.Light;
        return R.style.Dark;
    }
  }

  private static Config _globalConfig = null;

  public static void initGlobalConfig(SharedPreferences prefs, Resources res,
      IKeyEventHandler handler, Boolean foldableUnfolded)
  {
    migrate(prefs);
    if (_globalConfig == null) {
        _globalConfig = new Config(prefs, res, handler, foldableUnfolded);
    } else {
        _globalConfig.handler = handler;
        _globalConfig.refresh(res, foldableUnfolded);
    }
    LayoutModifier.init(_globalConfig, res);
  }

  public static Config globalConfig()
  {
    return _globalConfig;
  }

  public static SharedPreferences globalPrefs()
  {
    if (_globalConfig == null) return null;
    return _globalConfig._prefs;
  }

  public static interface IKeyEventHandler
  {
    public void key_down(KeyValue value, boolean is_swipe);
    public void key_up(KeyValue value, Pointers.Modifiers mods);
    public void mods_changed(Pointers.Modifiers mods);
    public void onGestureFinished(List<android.graphics.PointF> path);
  }



  private static int CONFIG_VERSION = 4;

  public static void migrate(SharedPreferences prefs)
  {
    int saved_version = prefs.getInt("version", 0);
    Logs.debug_config_migration(saved_version, CONFIG_VERSION);
    if (saved_version == CONFIG_VERSION)
      return;
    SharedPreferences.Editor e = prefs.edit();
    e.putInt("version", CONFIG_VERSION);


    switch (saved_version)
    {
      case 0:


        if (prefs.contains("layout")) {


            List<LayoutsPreference.Layout> l = new ArrayList<LayoutsPreference.Layout>();
            l.add(migrate_layout(prefs.getString("layout", null)));
            String snd_layout = prefs.getString("second_layout", null);
            if (snd_layout != null && !snd_layout.equals("none"))
              l.add(migrate_layout(snd_layout));
            String custom_layout = prefs.getString("custom_layout", "");
            if (custom_layout != null && !custom_layout.equals(""))
              l.add(LayoutsPreference.CustomLayout.parse(custom_layout));
            LayoutsPreference.save_to_preferences(e, l);
        }

      case 1:
        Object numberRowPref = prefs.getAll().get("number_row");
        if (numberRowPref instanceof Boolean) {
            boolean add_number_row = (Boolean) numberRowPref;
            e.putString("number_row", add_number_row ? "no_symbols" : "no_number_row");
        }

      case 2:
        if (!prefs.contains("number_entry_layout")) {
          e.putString("number_entry_layout", prefs.getBoolean("pin_entry_enabled", true) ? "pin" : "number");
        }

      case 3:
        e.putBoolean("autocapitalisation", false);
        e.putBoolean("auto_add_user_words", false);
        e.putString("gesture_trail_style", "1");
        e.putFloat("gesture_trail_width_factor", 3.0f);
        e.putFloat("gesture_trail_length_factor", 3.0f);
        e.putString("app_theme", "ocean");

      case 4:
      default: break;
    }
    e.apply();
  }

  private static LayoutsPreference.Layout migrate_layout(String name)
  {
    if (name == null || name.equals("system"))
      return new LayoutsPreference.SystemLayout();
    return new LayoutsPreference.NamedLayout(name);
  }
}
