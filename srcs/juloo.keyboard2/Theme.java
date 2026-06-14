package juloo.keyboard2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class Theme
{

  public final int colorKeyboard;
  public final int colorKey;
  public final int colorKeyActivated;


  public final int lockedColor;
  public final int activatedColor;
  public final int labelColor;
  public final int subLabelColor;
  public final int secondaryLabelColor;
  public final int greyedLabelColor;


  public final float keyBorderRadius;
  public final float keyBorderWidth;
  public final float keyBorderWidthActivated;
  public final int keyBorderColorLeft;
  public final int keyBorderColorTop;
  public final int keyBorderColorRight;
  public final int keyBorderColorBottom;

  public final int colorNavBar;
  public final boolean isLightNavBar;

  public Theme(Context context, AttributeSet attrs)
  {
    getKeyFont(context);
    Config config = Config.globalConfig();
    String currentTheme = config != null ? config.themeName : "galactic";

    if (currentTheme != null && (currentTheme.startsWith("custom_") || currentTheme.startsWith("embedded_"))) {
        android.content.SharedPreferences prefs = Config.globalPrefs();
        colorKeyboard = prefs.getInt("theme_color_kb_" + currentTheme, 0xFF1B1B1B);
        colorKey = prefs.getInt("theme_color_key_" + currentTheme, 0xFF333333);
        colorKeyActivated = colorKeyboard;
        labelColor = prefs.getInt("theme_color_label_" + currentTheme, 0xFFFFFFFF);
        activatedColor = prefs.getInt("theme_color_active_" + currentTheme, 0xFF3399FF);
        lockedColor = 0xFF33CC33;
        subLabelColor = 0xFFCCCCCC;
        keyBorderRadius = prefs.getInt("theme_radius_" + currentTheme, 5) * context.getResources().getDisplayMetrics().density;
        keyBorderWidth = 1.2f * context.getResources().getDisplayMetrics().density;
        keyBorderWidthActivated = 0;
        keyBorderColorLeft = colorKey;
        keyBorderColorTop = colorKey;
        keyBorderColorRight = colorKey;
        keyBorderColorBottom = 0xFF404040;
        colorNavBar = colorKeyboard;
        isLightNavBar = false;
        secondaryLabelColor = adjustLight(labelColor, 0.25f);
        greyedLabelColor = adjustLight(labelColor, 0.5f);
        return;
    }

    TypedArray s = context.getTheme().obtainStyledAttributes(attrs, R.styleable.keyboard, 0, 0);
    colorKeyboard = s.getColor(R.styleable.keyboard_colorKeyboard, 0);
    colorKey = s.getColor(R.styleable.keyboard_colorKey, 0);
    colorKeyActivated = s.getColor(R.styleable.keyboard_colorKeyActivated, 0);

    colorNavBar = s.getColor(R.styleable.keyboard_navigationBarColor, 0);
    isLightNavBar = s.getBoolean(R.styleable.keyboard_windowLightNavigationBar, false);
    labelColor = s.getColor(R.styleable.keyboard_colorLabel, 0);
    activatedColor = s.getColor(R.styleable.keyboard_colorLabelActivated, 0);
    lockedColor = s.getColor(R.styleable.keyboard_colorLabelLocked, 0);
    subLabelColor = s.getColor(R.styleable.keyboard_colorSubLabel, 0);
    secondaryLabelColor = adjustLight(labelColor,
        s.getFloat(R.styleable.keyboard_secondaryDimming, 0.25f));
    greyedLabelColor = adjustLight(labelColor,
        s.getFloat(R.styleable.keyboard_greyedDimming, 0.5f));
    keyBorderRadius = s.getDimension(R.styleable.keyboard_keyBorderRadius, 0);
    keyBorderWidth = s.getDimension(R.styleable.keyboard_keyBorderWidth, 0);
    keyBorderWidthActivated = s.getDimension(R.styleable.keyboard_keyBorderWidthActivated, 0);
    keyBorderColorLeft = s.getColor(R.styleable.keyboard_keyBorderColorLeft, colorKey);
    keyBorderColorTop = s.getColor(R.styleable.keyboard_keyBorderColorTop, colorKey);
    keyBorderColorRight = s.getColor(R.styleable.keyboard_keyBorderColorRight, colorKey);
    keyBorderColorBottom = s.getColor(R.styleable.keyboard_keyBorderColorBottom, colorKey);
    s.recycle();
  }


  int adjustLight(int color, float alpha)
  {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    float v = hsv[2];
    hsv[2] = alpha - (2 * alpha - 1) * v;
    return Color.HSVToColor(hsv);
  }

  Paint initIndicationPaint(Paint.Align align, Typeface font)
  {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextAlign(align);
    if (font != null)
      paint.setTypeface(font);
    return (paint);
  }

  static Typeface _key_font = null;

  static public Typeface getKeyFont(Context context)
  {
    if (_key_font == null)
      _key_font = Typeface.createFromAsset(context.getAssets(), "special_font.ttf");
    return _key_font;
  }

  public static final class Computed
  {
    public final float vertical_margin;
    public final float horizontal_margin;
    public final float margin_top;
    public final float margin_left;
    public final float row_height;
    public final Paint indication_paint;

    public final Key key;
    public final Key key_activated;

    public Computed(Theme theme, Config config, float keyWidth, KeyboardData layout)
    {





      row_height = Math.min(
          config.screenHeightPixels * config.keyboardHeightPercent / 100 / 3.95f,
          config.screenHeightPixels / layout.keysHeight);
      vertical_margin = config.key_vertical_margin * row_height;
      horizontal_margin = config.key_horizontal_margin * keyWidth;


      margin_top = config.marginTop + vertical_margin / 2;
      margin_left = horizontal_margin / 2;
      key = new Key(theme, config, keyWidth, false);
      key_activated = new Key(theme, config, keyWidth, true);
      indication_paint = init_label_paint(config, null);
      indication_paint.setColor(theme.subLabelColor);
    }

    public static final class Key
    {
      public final Paint bg_paint = new Paint();
      public final Paint border_left_paint;
      public final Paint border_top_paint;
      public final Paint border_right_paint;
      public final Paint border_bottom_paint;
      public final float border_width;
      public final float border_radius;
      final Paint _label_paint;
      final Paint _special_label_paint;
      final Paint _sublabel_paint;
      final Paint _special_sublabel_paint;
      final int _label_alpha_bits;

      public Key(Theme theme, Config config, float keyWidth, boolean activated)
      {
        bg_paint.setColor(activated ? theme.colorKeyActivated : theme.colorKey);
        float tempBorderWidth;
        if (config.borderConfig)
        {
          border_radius = config.customBorderRadius * keyWidth;
          tempBorderWidth = config.customBorderLineWidth;
        }
        else
        {
          border_radius = theme.keyBorderRadius;
          tempBorderWidth = activated ? theme.keyBorderWidthActivated : theme.keyBorderWidth;
        }

        if (config.draw_key_borders && tempBorderWidth <= 0) {
             tempBorderWidth = 2.0f;
        }
        border_width = tempBorderWidth;

        bg_paint.setAlpha(activated ? config.keyActivatedOpacity : config.keyOpacity);
        border_left_paint = init_border_paint(config, border_width, theme.keyBorderColorLeft);
        border_top_paint = init_border_paint(config, border_width, theme.keyBorderColorTop);
        border_right_paint = init_border_paint(config, border_width, theme.keyBorderColorRight);
        border_bottom_paint = init_border_paint(config, border_width, theme.keyBorderColorBottom);
        _label_paint = init_label_paint(config, null);
        Typeface specialFont = config.use_system_font ? Typeface.DEFAULT : _key_font;
        _special_label_paint = init_label_paint(config, specialFont);
        _sublabel_paint = init_label_paint(config, null);
        _special_sublabel_paint = init_label_paint(config, specialFont);
        _label_alpha_bits = (config.labelBrightness & 0xFF) << 24;
      }

      public Paint label_paint(boolean special_font, int color, float text_size)
      {
        Paint p = special_font ? _special_label_paint : _label_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        return p;
      }

      public Paint sublabel_paint(boolean special_font, int color, float text_size, Paint.Align align)
      {
        Paint p = special_font ? _special_sublabel_paint : _sublabel_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        p.setTextAlign(align);
        return p;
      }
    }

    static Paint init_border_paint(Config config, float border_width, int color)
    {
      Paint p = new Paint();
      p.setAlpha(config.keyOpacity);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(border_width);
      p.setColor(color);
      return p;
    }

    static Paint init_label_paint(Config config, Typeface font)
    {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setTextAlign(Paint.Align.CENTER);
      if (config.key_text_bold) {
          p.setTypeface(Typeface.DEFAULT_BOLD);
      }
      if (font != null)
        p.setTypeface(font);
      return p;
    }
  }
}
