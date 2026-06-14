package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.Arrays;

public class Keyboard2View extends View
  implements View.OnTouchListener, Pointers.IPointerEventHandler
{
  private KeyboardData _keyboard;


  private KeyValue _shift_kv;
  private KeyboardData.Key _shift_key;


  private KeyValue _compose_kv;
  private KeyboardData.Key _compose_key;

  private Pointers _pointers;
  private boolean _editMode = false;
  private EditCallback _editCallback;
  private final java.util.Set<KeyboardData.Key> _selectedKeys = new java.util.HashSet<>();
  private final java.util.Set<KeyboardData.Row> _selectedRows = new java.util.HashSet<>();

  public interface EditCallback {
      void onKeyClick(KeyboardData.Key key);
      void onKeyDoubleClick(KeyboardData.Key key);
      void onKeyLongPress(KeyboardData.Key key);
      void onRowPinch(KeyboardData.Row row, float scale);
      void onRowDrag(KeyboardData.Row row, float dx, float dy);
      void onSelectionChanged(java.util.Set<KeyboardData.Key> keys, java.util.Set<KeyboardData.Row> rows);
  }

  public void setEditMode(boolean enabled, EditCallback callback) {
      this._editMode = enabled;
      this._editCallback = callback;
  }

  private Pointers.Modifiers _mods;

  private static int _currentWhat = 0;

  private Config _config;
  private Config.IKeyEventHandler _customHandler = null;

  private float _keyWidth;
  private float _mainLabelSize;
  private float _subLabelSize;
  private float _marginRight;
  private float _marginLeft;
  private float _marginBottom;
  private int _insets_left = 0;
  private int _insets_right = 0;
  private int _insets_bottom = 0;

  private Theme _theme;
  private Theme.Computed _tc;

  private static RectF _tmpRect = new RectF();

  private KeyValue popupKeyValue = null;
  private float popupX = 0;
  private float popupY = 0;
  private final Handler popupHandler = new Handler();
  private final Runnable dismissPopupRunnable = () -> {
      popupKeyValue = null;
      invalidate();
  };
  private final Paint popupBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final java.util.List<android.graphics.PointF> _trailPoints = new java.util.ArrayList<>();
  private final android.graphics.Path _trailPath = new android.graphics.Path();
  private final Paint _trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private android.graphics.BlurMaskFilter _cachedNeonFilter;
  private float _cachedNeonWidth = -1;
  private android.graphics.LinearGradient _cachedRainbowShader;
  private final android.graphics.Matrix _rainbowMatrix = new android.graphics.Matrix();


  private float lastTouchX = -1;
  private float lastTouchY = -1;

  enum Vertical
  {
    TOP,
    CENTER,
    BOTTOM
  }

  public Keyboard2View(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _config = Config.globalConfig();
    popupTextPaint.setTextAlign(Paint.Align.CENTER);
    if (_config.use_system_font) {
      popupTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    } else {
      popupTextPaint.setTypeface(Theme.getKeyFont(context));
    }
    _trailPaint.setStyle(Paint.Style.STROKE);
    _trailPaint.setStrokeWidth(10f);
    _trailPaint.setStrokeCap(Paint.Cap.ROUND);
    _trailPaint.setStrokeJoin(Paint.Join.ROUND);
    _trailPaint.setColor(0x80FF0000);

    _pointers = new Pointers(this, _config);
    refresh_navigation_bar(context);
    setOnTouchListener(this);
    int layout_id = (attrs == null) ? 0 :
      attrs.getAttributeResourceValue(null, "layout", 0);
    if (layout_id == 0)
      reset();
    else
      setKeyboard(KeyboardData.load(getResources(), layout_id));
  }

  private Window getParentWindow(Context context)
  {
    if (context instanceof Activity)
      return ((Activity)context).getWindow();
    if (context instanceof InputMethodService)
      return ((InputMethodService)context).getWindow().getWindow();
    if (context instanceof ContextWrapper)
      return getParentWindow(((ContextWrapper)context).getBaseContext());
    return null;
  }

  public void refresh_navigation_bar(Context context)
  {
    if (VERSION.SDK_INT < 21)
      return;

    Window w = getParentWindow(context);
    if (w == null) return;
    w.setNavigationBarColor(_theme.colorNavBar);
    if (VERSION.SDK_INT < 26)
      return;
    int uiFlags = getSystemUiVisibility();
    if (_theme.isLightNavBar)
      uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    else
      uiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    setSystemUiVisibility(uiFlags);
  }

  public void setKeyEventHandler(Config.IKeyEventHandler handler) {
      _customHandler = handler;
  }

  public KeyboardData getKeyboard() {
    return _keyboard;
  }

  public void setKeyboard(KeyboardData kw)
  {
    _keyboard = kw;
    _shift_kv = KeyValue.getKeyByName("shift");
    _shift_key = _keyboard.findKeyWithValue(_shift_kv);
    _compose_kv = KeyValue.getKeyByName("compose");
    _compose_key = _keyboard.findKeyWithValue(_compose_kv);
    KeyModifier.set_modmap(_keyboard.modmap);
    reset();
  }

  public void reset()
  {
    _mods = Pointers.Modifiers.EMPTY;
    _pointers.clear();
    requestLayout();
    invalidate();
  }

  void set_fake_ptr_latched(KeyboardData.Key key, KeyValue kv, boolean latched,
      boolean lock)
  {
    if (_keyboard == null || key == null)
      return;
    _pointers.set_fake_pointer_state(key, kv, latched, lock);
  }


  public void set_shift_state(boolean latched, boolean lock)
  {
    set_fake_ptr_latched(_shift_key, _shift_kv, latched, lock);
  }


  public void set_compose_pending(boolean pending)
  {
    set_fake_ptr_latched(_compose_key, _compose_kv, pending, false);
  }


  public void set_selection_state(boolean selection_state)
  {
    set_fake_ptr_latched(KeyboardData.Key.EMPTY,
        KeyValue.getKeyByName("selection_mode"), selection_state, true);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
  {
    return KeyModifier.modify(k, mods);
  }

  @Override
  public void onPointerDown(KeyValue k, boolean isSwipe)
  {
    updateFlags();
    getKeyEventHandler().key_down(k, isSwipe);
    invalidate();
    vibrate();
  }

  @Override
  public void onShowPopup(KeyValue kv, KeyboardData.Key key) {
    showPopup(kv);
  }

  public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
  {


    getKeyEventHandler().key_up(k, mods);
    updateFlags();
    invalidate();
  }

  public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
  {
    getKeyEventHandler().key_up(k, mods);
    updateFlags();
  }

  public void onPointerFlagsChanged(boolean shouldVibrate)
  {
    updateFlags();
    invalidate();
    if (shouldVibrate)
      vibrate();
  }

  private void updateFlags()
  {
    _mods = _pointers.getModifiers();
    getKeyEventHandler().mods_changed(_mods);
  }

  private Config.IKeyEventHandler getKeyEventHandler() {
      return _customHandler != null ? _customHandler : _config.handler;
  }

  private long lastClickTime = 0;
  private KeyboardData.Key lastClickedKey = null;
  private float initialPinchDist = 0;
  private float initialRowHeight = 0;
  private KeyboardData.Row activeRow = null;
  private KeyboardData.Key draggingKey = null;
  private float dragX, dragY;
  private boolean isPinching = false;
  private boolean isDragging = false;

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    if (_editMode && _editCallback != null) {
        return handleEditTouch(event);
    }
    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        _pointers.onTouchUp(event.getPointerId(event.getActionIndex()));
        if (_trailPoints.size() > 0) {
             _trailPoints.clear();
             invalidate();
        }
        lastTouchX = -1;
        lastTouchY = -1;
        break;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        p = event.getActionIndex();
        float tx = event.getX(p);
        float ty = event.getY(p);
        lastTouchX = tx;
        lastTouchY = ty;
        if (_config.show_gesture_trail) {
            _trailPoints.clear();
            _trailPoints.add(new android.graphics.PointF(tx, ty));
        }
        KeyboardData.Key key = getKeyAtPosition(tx, ty);
        if (key != null)
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++) {
          float mx = event.getX(p);
          float my = event.getY(p);
          _pointers.onTouchMove(mx, my, event.getPointerId(p));
          if (p == event.getActionIndex()) {
              lastTouchX = mx;
              lastTouchY = my;
          }
          if (_config.show_gesture_trail && p == event.getActionIndex()) {
              _trailPoints.add(new android.graphics.PointF(mx, my));
              int maxPoints = (int)(20 * _config.gesture_trail_length_factor);
              if (_trailPoints.size() > maxPoints) {
                  _trailPoints.remove(0);
              }
              invalidate();
          }
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        _pointers.onTouchCancel();
        if (_trailPoints.size() > 0) {
             _trailPoints.clear();
             invalidate();
        }
        break;
      default:
        return (false);
    }
    return (true);
  }

  private KeyboardData.Row getRowAtPosition(float ty)
  {
    float y = _config.marginTop;
    if (ty < y)
      return null;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += (row.shift + row.height) * _tc.row_height;
      if (ty < y)
        return row;
    }
    return null;
  }

  private KeyboardData.Key getKeyAtPosition(float tx, float ty)
  {
    KeyboardData.Row row = getRowAtPosition(ty);
    float x = _marginLeft;
    if (row == null || tx < x)
      return null;
    for (KeyboardData.Key key : row.keys)
    {
      float xLeft = x + key.shift * _keyWidth;
      float xRight = xLeft + key.width * _keyWidth;
      if (tx < xLeft)
        return null;
      if (tx < xRight)
        return key;
      x = xRight;
    }
    return null;
  }

  private void vibrate()
  {
    VibratorCompat.vibrate(this, _config);
  }

  private String getKeyPopupText(KeyValue key) {
    if (key == null) return null;
    switch (key.getKind()) {
        case ModifiedChar: {
            char baseChar = (char) key.getChar();
            int meta = key.getMetaState();
            StringBuilder sb = new StringBuilder();
            if ((meta & KeyEvent.META_CTRL_ON) != 0) sb.append("Ctrl+");
            if ((meta & KeyEvent.META_ALT_ON) != 0) sb.append("Alt+");
            if ((meta & KeyEvent.META_SHIFT_ON) != 0) sb.append("Shift+");
            if ((meta & KeyEvent.META_SHIFT_ON) != 0) {
                sb.append(Character.toUpperCase(baseChar));
            } else {
                sb.append(baseChar);
            }
            return sb.toString();
        }
        case Char:
            return String.valueOf((char) key.getChar());
        case String:
        default:
            return key.getString();
    }
  }

  private void showPopup(KeyValue key) {
    popupKeyValue = key;
    popupX = getWidth() / 2f;
    popupY = _tc.row_height;

    popupHandler.removeCallbacks(dismissPopupRunnable);
    invalidate();
    popupHandler.postDelayed(dismissPopupRunnable, 200);
  }

  @Override
  public void onMeasure(int wSpec, int hSpec)
  {
    int width;
    DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
    width = dm.widthPixels;
    _marginLeft = Math.max(_config.horizontal_margin, _insets_left);
    _marginRight = Math.max(_config.horizontal_margin, _insets_right);
    _marginBottom = _config.margin_bottom + _insets_bottom;
    _keyWidth = (width - _marginLeft - _marginRight) / _keyboard.keysWidth;
    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard);




    float labelBaseSize = Math.min(
        _tc.row_height - _tc.vertical_margin,
        (width / 10 - _tc.horizontal_margin) * 3/2
        ) * _config.characterSize;
    _mainLabelSize = labelBaseSize * _config.labelTextSize;
    _subLabelSize = labelBaseSize * _config.sublabelTextSize;
    int height =
      (int)(_tc.row_height * _keyboard.keysHeight
          + _config.marginTop + _marginBottom);
    setMeasuredDimension(width, height);
  }

  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    if (!changed)
      return;
    if (VERSION.SDK_INT >= 29)
    {

      Rect keyboard_area = new Rect(
          left + (int)_marginLeft,
          top + (int)_config.marginTop,
          right - (int)_marginRight,
          bottom - (int)_marginBottom);
      setSystemGestureExclusionRects(Arrays.asList(keyboard_area));
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets wi)
  {

    if (VERSION.SDK_INT < 35)
      return wi;
    int insets_types =
      WindowInsets.Type.systemBars()
      | WindowInsets.Type.displayCutout();
    Insets insets = wi.getInsets(insets_types);
    _insets_left = insets.left;
    _insets_right = insets.right;
    _insets_bottom = insets.bottom;
    return WindowInsets.CONSUMED;
  }


  static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
    Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
    Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
    Paint.Align.CENTER, Paint.Align.CENTER
  };

  static final Vertical[] LABEL_POSITION_V = new Vertical[]{
    Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
    Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
    Vertical.BOTTOM
  };

  @Override
  protected void onDraw(Canvas canvas)
  {

    getBackground().setAlpha(_config.keyboardOpacity);
    Paint selectionPaint = new Paint();
    selectionPaint.setStyle(Paint.Style.STROKE);
    selectionPaint.setStrokeWidth(4f);
    selectionPaint.setColor(0x8000FF00);
    float y = _tc.margin_top;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (KeyboardData.Key k : row.keys)
      {
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        boolean isKeyDown = _pointers.isKeyDown(k);
        Theme.Computed.Key tc_key = isKeyDown ? _tc.key_activated : _tc.key;


        if (drawCustomThemeKey(canvas, x, y, keyW, keyH, tc_key, isKeyDown, k)) {

        } else {
            drawKeyFrame(canvas, x, y, keyW, keyH, tc_key, k);
        }

        if (_editMode) {
            if (_selectedKeys.contains(k) || _selectedRows.contains(row)) {
                canvas.drawRect(x, y, x + keyW, y + keyH, selectionPaint);
            }
        }

        if (k.keys[0] != null) {
          String colorString = _theme.isLightNavBar ? k.colorLight[0] : k.colorDark[0];
          int colorOverride = parseColor(colorString, -1);
          drawLabel(canvas, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, tc_key, colorOverride, k);
        }
        if (_config.show_sublabels) {
          for (int i = 1; i < 9; i++)
          {
            if (k.keys[i] != null) {
              String colorString = _theme.isLightNavBar ? k.colorLight[i] : k.colorDark[i];
              int colorOverride = -1;
              if (colorString != null) {
                colorOverride = parseColor(colorString, -1);
              }
              drawSubLabel(canvas, k.keys[i], x, y, keyW, keyH, i, isKeyDown, tc_key, colorOverride, k);
            }
          }
        }
        // drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }

    if (_config.show_gesture_trail && !_trailPoints.isEmpty()) {
        float baseWidth = 10f * _config.gesture_trail_width_factor;
        _trailPaint.setStrokeWidth(baseWidth);
        int color = _theme.activatedColor;
        _trailPaint.setColor(color);
        _trailPaint.setAlpha(128);
        _trailPaint.setMaskFilter(null);
        _trailPaint.setShader(null);

        switch (_config.gesture_trail_style) {
            case 1:
                if (Math.abs(_cachedNeonWidth - baseWidth) > 0.1f || _cachedNeonFilter == null) {
                    _cachedNeonFilter = new android.graphics.BlurMaskFilter(baseWidth, android.graphics.BlurMaskFilter.Blur.NORMAL);
                    _cachedNeonWidth = baseWidth;
                }
                _trailPaint.setMaskFilter(_cachedNeonFilter);
                _trailPath.reset();
                _trailPath.moveTo(_trailPoints.get(0).x, _trailPoints.get(0).y);
                for (int i = 1; i < _trailPoints.size(); i++) {
                    _trailPath.lineTo(_trailPoints.get(i).x, _trailPoints.get(i).y);
                }
                canvas.drawPath(_trailPath, _trailPaint);

                _trailPaint.setMaskFilter(null);
                _trailPaint.setColor(android.graphics.Color.WHITE);
                _trailPaint.setStrokeWidth(baseWidth / 3);
                canvas.drawPath(_trailPath, _trailPaint);
                break;
            case 2:
                if (_trailPoints.size() > 1) {
                    if (_cachedRainbowShader == null) {
                        _cachedRainbowShader = new android.graphics.LinearGradient(0, 0, 1, 0,
                            new int[]{android.graphics.Color.RED, android.graphics.Color.YELLOW, android.graphics.Color.GREEN, android.graphics.Color.BLUE, android.graphics.Color.MAGENTA},
                            null, android.graphics.Shader.TileMode.CLAMP);
                    }
                    android.graphics.PointF start = _trailPoints.get(0);
                    android.graphics.PointF end = _trailPoints.get(_trailPoints.size() - 1);
                    float dx = end.x - start.x;
                    float dy = end.y - start.y;
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    float angle = (float)Math.toDegrees(Math.atan2(dy, dx));
                    _rainbowMatrix.reset();
                    _rainbowMatrix.preScale(dist, 1);
                    _rainbowMatrix.postRotate(angle);
                    _rainbowMatrix.postTranslate(start.x, start.y);
                    _cachedRainbowShader.setLocalMatrix(_rainbowMatrix);
                    _trailPaint.setShader(_cachedRainbowShader);
                }
                _trailPath.reset();
                _trailPath.moveTo(_trailPoints.get(0).x, _trailPoints.get(0).y);
                for (int i = 1; i < _trailPoints.size(); i++) {
                    _trailPath.lineTo(_trailPoints.get(i).x, _trailPoints.get(i).y);
                }
                canvas.drawPath(_trailPath, _trailPaint);
                break;
            case 3:
                _trailPaint.setStyle(Paint.Style.FILL);
                for (android.graphics.PointF p : _trailPoints) {
                    canvas.drawCircle(p.x, p.y, baseWidth / 2, _trailPaint);
                }
                _trailPaint.setStyle(Paint.Style.STROKE);
                break;
            case 4:
                _trailPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < _trailPoints.size(); i++) {
                    float factor = (float) i / _trailPoints.size();
                    float r = (baseWidth / 2) * factor;
                    canvas.drawCircle(_trailPoints.get(i).x, _trailPoints.get(i).y, r, _trailPaint);
                }
                _trailPaint.setStyle(Paint.Style.STROKE);
                break;
            default:
                _trailPath.reset();
                _trailPath.moveTo(_trailPoints.get(0).x, _trailPoints.get(0).y);
                for (int i = 1; i < _trailPoints.size(); i++) {
                    _trailPath.lineTo(_trailPoints.get(i).x, _trailPoints.get(i).y);
                }
                canvas.drawPath(_trailPath, _trailPaint);
                break;
        }
    }


    if (popupKeyValue != null) {
      float bubbleSize = _keyWidth * 2.5f;
      float bubbleRadius = bubbleSize / 2f;


      int baseColor = _tc.key_activated.bg_paint.getColor();
      int textColor = labelColor(popupKeyValue, true, false);


      popupBubblePaint.setShadowLayer(12.0f, 0, 8.0f, 0x60000000);
      popupBubblePaint.setColor(baseColor);
      canvas.drawCircle(popupX, popupY, bubbleRadius, popupBubblePaint);
      popupBubblePaint.clearShadowLayer();


      popupHighlightPaint.setShader(new android.graphics.RadialGradient(popupX, popupY - bubbleRadius * 0.4f, bubbleRadius, 0x90FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
      canvas.drawCircle(popupX, popupY, bubbleRadius, popupHighlightPaint);


      popupTextPaint.setColor(textColor);
      popupTextPaint.setTextSize(_mainLabelSize * 2.2f);
      String textToDraw = getKeyPopupText(popupKeyValue);
      float textY = popupY - ((popupTextPaint.descent() + popupTextPaint.ascent()) / 2);
      if (textToDraw != null) {
          canvas.drawText(textToDraw, popupX, textY, popupTextPaint);
      }
    }

  }

  public java.util.Map<Character, RectF> getKeyCoordinates() {
      java.util.Map<Character, RectF> map = new java.util.HashMap<>();
      if (_keyboard == null) return map;
      float y = _tc.margin_top;
      for (KeyboardData.Row row : _keyboard.rows) {
          y += row.shift * _tc.row_height;
          float x = _marginLeft + _tc.margin_left;
          float keyH = row.height * _tc.row_height - _tc.vertical_margin;
          for (KeyboardData.Key k : row.keys) {
              x += k.shift * _keyWidth;
              float keyW = _keyWidth * k.width - _tc.horizontal_margin;

              if (k.keys[0] != null && k.keys[0].getKind() == KeyValue.Kind.Char) {
                  map.put(k.keys[0].getChar(), new RectF(x, y, x + keyW, y + keyH));
              }
              x += _keyWidth * k.width;
          }
          y += row.height * _tc.row_height;
      }
      return map;
  }

  @Override
  public void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    popupHandler.removeCallbacks(dismissPopupRunnable);
  }


  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc, KeyboardData.Key k)
  {
    if (drawCustomThemeKey(canvas, x, y, keyW, keyH, tc, false, k)) {
         return;
    }

    float r = (k != null && k.borderRadius >= 0) ? k.borderRadius * _keyWidth : tc.border_radius;
    float w = tc.border_width;
    float padding = w / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    if (w > 0.f)
    {
      float overlap = r - r * 0.85f + w;
      drawBorder(canvas, x, y, x + overlap, y + keyH, tc.border_left_paint, tc);
      drawBorder(canvas, x + keyW - overlap, y, x + keyW, y + keyH, tc.border_right_paint, tc);
      drawBorder(canvas, x, y, x + keyW, y + overlap, tc.border_top_paint, tc);
      drawBorder(canvas, x, y + keyH - overlap, x + keyW, y + keyH, tc.border_bottom_paint, tc);
    }
  }


  void drawBorder(Canvas canvas, float clipl, float clipt, float clipr,
      float clipb, Paint paint, Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    canvas.save();
    canvas.clipRect(clipl, clipt, clipr, clipb);
    canvas.drawRoundRect(_tmpRect, r, r, paint);
    canvas.restore();
  }

  private int labelColor(KeyValue k, boolean isKeyDown, boolean sublabel)
  {
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return _theme.lockedColor;
        return _theme.activatedColor;
      }
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
    {
      if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
        return _theme.greyedLabelColor;
      return _theme.secondaryLabelColor;
    }
    return sublabel ? _theme.subLabelColor : _theme.labelColor;
  }

  private void drawLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyH, boolean isKeyDown, Theme.Computed.Key tc, int colorOverride, KeyboardData.Key key)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, true, key, 0);
    int color = (colorOverride != -1) ? colorOverride : labelColor(kv, isKeyDown, false);
    Paint p = tc.label_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize);
    canvas.drawText(kv.getString(), x, (keyH - p.ascent() - p.descent()) / 2f + y, p);
  }

  private void drawSubLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyW, float keyH, int sub_index, boolean isKeyDown,
      Theme.Computed.Key tc, int colorOverride, KeyboardData.Key key)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, false, key, sub_index);
    int color = (colorOverride != -1) ? colorOverride : labelColor(kv, isKeyDown, true);
    if (_config.colored_sublabels && colorOverride == -1 && !isKeyDown) {
         color = _theme.activatedColor;
    }
    Paint p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize, a);
    float subPadding = _config.keyPadding;
    if (v == Vertical.CENTER)
      y += (keyH - p.ascent() - p.descent()) / 2f;
    else
      y += (v == Vertical.TOP) ? subPadding - p.ascent() : keyH - subPadding - p.descent();
    if (a == Paint.Align.CENTER)
      x += keyW / 2f;
    else
      x += (a == Paint.Align.LEFT) ? subPadding : keyW - subPadding;
    String label = kv.getString();
    int label_len = label.length();

    if (label_len > 3 && kv.getKind() == KeyValue.Kind.String)
      label_len = 3;
    canvas.drawText(label, 0, label_len, x, y, p);
  }

  private void drawIndication(Canvas canvas, KeyboardData.Key k, float x,
      float y, float keyW, float keyH, Theme.Computed tc)
  {
    if (k.indication == null || k.indication.equals(""))
      return;
    Paint p = tc.indication_paint;
    p.setTextSize(_subLabelSize);
    canvas.drawText(k.indication, 0, k.indication.length(),
        x + keyW / 2f, (keyH - p.ascent() - p.descent()) * 4/5 + y, p);
  }

  private float scaleTextSize(KeyValue k, boolean main_label, KeyboardData.Key key, int index)
  {
    float scale = 1.f;
    if (k.hasFlagsAny(KeyValue.FLAG_TINY_FONT)) scale = 0.60f;
    else if (k.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT)) scale = 0.75f;

    if (key != null) {
        if (index >= 0 && index < 9 && key.labelScales[index] > 0) {
            scale *= key.labelScales[index];
        } else if (key.indication != null && key.indication.startsWith("scale:")) {
            try {
                float customScale = Float.parseFloat(key.indication.substring(6));
                scale *= customScale;
            } catch (Exception e) {}
        }
    }

    float label_size = main_label ? _mainLabelSize : _subLabelSize;
    return label_size * scale;
  }

  public void showTutorial(String message) {
    android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_LONG).show();
  }

  private int parseColor(String colorString, int defaultColor) {
    if (colorString == null) {
      return defaultColor;
    }
    String[] components = colorString.split(",");
    if (components.length != 3) {
      return defaultColor;
    }
    try {
      int r = Integer.parseInt(components[0].trim());
      int g = Integer.parseInt(components[1].trim());
      int b = Integer.parseInt(components[2].trim());
      return android.graphics.Color.rgb(r, g, b);
    } catch (NumberFormatException e) {
      return defaultColor;
    }
  }

  public void clearSelection() {
      _selectedKeys.clear();
      _selectedRows.clear();
      invalidate();
  }

  public java.util.Set<KeyboardData.Key> getSelectedKeys() { return _selectedKeys; }
  public java.util.Set<KeyboardData.Row> getSelectedRows() { return _selectedRows; }

  private boolean isSelectModeActive() {
      if (getContext() instanceof LiveLayoutCustomizationActivity) {
          return ((LiveLayoutCustomizationActivity)getContext()).isSelectModeActive();
      }
      return false;
  }

  private KeyboardData.Key dragSourceKey = null;
  private float dragStartX, dragStartY;

  private boolean handleEditTouch(MotionEvent event) {
      int action = event.getActionMasked();
      float tx = event.getX();
      float ty = event.getY();
      KeyboardData.Key key = getKeyAtPosition(tx, ty);
      KeyboardData.Row row = getRowAtPosition(ty);

      boolean resizeMode = false;
      if (getContext() instanceof LiveLayoutCustomizationActivity) {
          resizeMode = ((LiveLayoutCustomizationActivity)getContext()).isResizeMode();
      }

      switch (action) {
          case MotionEvent.ACTION_DOWN:
              isPinching = false;
              isDragging = false;
              dragSourceKey = key;
              dragStartX = tx;
              dragStartY = ty;
              draggingKey = key;
              dragX = tx;
              dragY = ty;
              lastClickedKey = key;
              break;

          case MotionEvent.ACTION_POINTER_DOWN:
              if (event.getPointerCount() == 2) {
                  isPinching = true;
                  float dx = event.getX(0) - event.getX(1);
                  float dy = event.getY(0) - event.getY(1);
                  initialPinchDist = (float) Math.sqrt(dx * dx + dy * dy);
                  activeRow = getRowAtPosition((event.getY(0) + event.getY(1)) / 2);
              }
              break;

          case MotionEvent.ACTION_MOVE:
              if (isPinching && event.getPointerCount() == 2 && activeRow != null) {
                  float dx = event.getX(0) - event.getX(1);
                  float dy = event.getY(0) - event.getY(1);
                  float dist = (float) Math.sqrt(dx * dx + dy * dy);
                  float scale = dist / initialPinchDist;
                  if (Math.abs(scale - 1.0f) > 0.05f) {
                      _editCallback.onRowPinch(activeRow, scale);
                  }
              } else if (!isPinching && draggingKey != null) {
                  float dx = tx - dragX;
                  float dy = ty - dragY;
                  if (Math.abs(dx) > 20 || Math.abs(dy) > 20) {
                      isDragging = true;
                      _editCallback.onRowDrag(row, dx, dy);
                  }
              }
              break;

          case MotionEvent.ACTION_UP:
              if (resizeMode && isDragging && dragSourceKey != null && key != null && key != dragSourceKey) {
                  // SWAP KEYS
                  if (getContext() instanceof LiveLayoutCustomizationActivity) {
                      ((LiveLayoutCustomizationActivity)getContext()).swapKeys(dragSourceKey, key);
                  }
              } else if (!isPinching && !isDragging && lastClickedKey != null) {
                  long now = System.currentTimeMillis();

                  if (event.getMetaState() == KeyEvent.META_CTRL_ON || (_editMode && isSelectModeActive())) {
                      if (key != null) {
                          if (!_selectedKeys.remove(key)) _selectedKeys.add(key);
                      } else if (row != null) {
                          if (!_selectedRows.remove(row)) _selectedRows.add(row);
                      }
                      _editCallback.onSelectionChanged(_selectedKeys, _selectedRows);
                  } else {
                      if (now - lastClickTime < 300 && lastClickedKey == key) {
                          _editCallback.onKeyDoubleClick(lastClickedKey);
                      } else {
                          _editCallback.onKeyClick(lastClickedKey);
                      }
                  }
                  lastClickTime = now;
              }
              draggingKey = null;
              activeRow = null;
              isPinching = false;
              isDragging = false;
              break;

          case MotionEvent.ACTION_POINTER_UP:
              if (event.getPointerCount() <= 2) {
                  // Wait for all fingers to lift before allowing clicks again
              }
              break;
      }
      invalidate();
      return true;
  }

  private boolean drawCustomThemeKey(Canvas canvas, float x, float y, float keyW, float keyH, Theme.Computed.Key tc, boolean isPressed, KeyboardData.Key k) {
      float distortionX = 0;
      float distortionY = 0;

      Paint overridePaint = tc.bg_paint;
      if (k != null) {
          String colorString = _theme.isLightNavBar ? k.colorLight[0] : k.colorDark[0];
          int overrideColor = parseColor(colorString, -1);
          if (overrideColor != -1) {
              overridePaint = new Paint(tc.bg_paint);
              overridePaint.setColor(overrideColor);
          }
      }

      if (k != null && isPressed && "waterdrop".equals(_config.themeName)) {
          int ptrId = _pointers.findPointerForKey(k);
          android.graphics.PointF pos = _pointers.getPointerPos(ptrId);

          if (pos != null) {
              float cx = x + keyW/2;
              float cy = y + keyH/2;
              distortionX = (pos.x - cx) * 0.4f;
              distortionY = (pos.y - cy) * 0.4f;

              float maxDist = Math.min(keyW, keyH) * 0.35f;
              float dist = (float)Math.sqrt(distortionX*distortionX + distortionY*distortionY);
              if (dist > maxDist) {
                  float scale = maxDist / dist;
                  distortionX *= scale;
                  distortionY *= scale;
              }
          }
      }

      return ThemeRenderer.drawKeyBackground(
          canvas, x, y, keyW, keyH,
          overridePaint, tc.border_radius,
          _config.themeName, isPressed,
          distortionX, distortionY
      );
  }
}
