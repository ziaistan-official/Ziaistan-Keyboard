package juloo.keyboard2;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.util.LogPrinter;
import android.view.*;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import android.util.TypedValue;
import android.widget.ViewFlipper;
import android.view.animation.AnimationUtils;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import android.view.inputmethod.InputMethodSubtype;
import java.util.Random;
import java.util.Set;
import juloo.keyboard2.prefs.LayoutsPreference;
import juloo.keyboard2.SuggestionProvider;

public class Keyboard2 extends InputMethodService
  implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private View _inputView;
  private Keyboard2View _keyboardView;
  private boolean _forceKeepClipboardPaneOnNextStart = false;
  private KeyEventHandler _keyeventhandler;
  private SuggestionProvider _suggestionProvider;
  private LayoutBasedAutoCorrectionProvider _autoCorrectionProvider;
  private KeyboardAwareSuggester _keyboardAwareSuggester;
  private RecyclerView _suggestionsRecyclerView;
  private SuggestionAdapter _suggestionAdapter;
  private View _suggestionStrip;
  private TypingHUDManager _typingHUDManager;
  private FrameLayout _suggestionStripContainerTop;
  private FrameLayout _suggestionStripContainerBottom;
  private ViewFlipper _tutorialFlipper;
  private TextView _ziaistanOfficialText;
  private String[] _tutorials;
  private float _lastX;
  private final Handler _tutorialHandler = new Handler();
  private Runnable _tutorialRunnable;
  private final Random _random = new Random();
  private static final int TUTORIAL_TRANSITION_DELAY = 500000;

  private KeyboardData _currentSpecialLayout;

  private KeyboardData _localeTextLayout;
  private ViewGroup _emojiPane = null;
  private ViewGroup _glyphPane = null;
  private ClipboardView _clipboard_pane = null;
  private ViewGroup _mainLayout;
  private FrameLayout _voiceOverlayContainer;
  private VoiceInputView _voiceInputView = null;
  private MousePadView _mousePadView = null;
  private VoiceTypingManager _voiceTypingManager = null;
  private int _lastVoiceInsertionLength = 0;
  private List<String> _multiWordOriginals = null;
  public int actionId;
  private Handler _handler;

  private Config _config;

  private FoldStateTracker _foldStateTracker;


  private static final String[] KEYBOARD_THEMES = {
      "waterdrop", "sponge", "metal", "wood", "glass", "plastic", "leather", "denim", "stone", "brick", "marble", "carbonfiber", "circuit", "grid", "paper", "cork", "fabric", "knitted", "ice", "fire", "sky", "sand", "forestcamo", "chalkboard", "retro",

      "cyberpunk", "liquid_glass", "mechanical_rgb", "magma_ember", "ink_parchment",
      "cosmic_nebula", "sakura_garden", "retro_8bit", "golden_era", "deep_ocean",
      "neon_rain", "candy_crush", "steampunk", "holographic", "spirit_realm",
      "golden_luxury", "sakura_breeze", "bioluminescence", "retro_arcade", "crystal_prism",
      "vaporwave", "noir_rain", "paper_cutout", "star_field", "gears"
  };

  private static class PendingAction {
      enum Type { COMMIT, SELECTION, KEY }
      Type type;
      String text;
      int start, end, keyCode;
      PendingAction(String t) { this.type = Type.COMMIT; this.text = t; }
      PendingAction(int s, int e) { this.type = Type.SELECTION; this.start = s; this.end = e; }
      PendingAction(int k, boolean isKey) { this.type = Type.KEY; this.keyCode = k; }
  }
  private final List<PendingAction> mPendingActions = new ArrayList<>();

  public void setPendingCommitText(String text) {
      synchronized (mPendingActions) { mPendingActions.add(new PendingAction(text)); }
  }

  public void setPendingSelection(int start, int end) {
      synchronized (mPendingActions) { mPendingActions.add(new PendingAction(start, end)); }
  }

  public void setPendingKeyEvent(int keyCode) {
      synchronized (mPendingActions) { mPendingActions.add(new PendingAction(keyCode, true)); }
  }

  public void cycleKeyboardTheme() {
      SharedPreferences prefs = Config.globalPrefs();
      String current = _config.themeName;
      int index = -1;

      for (int i = 0; i < KEYBOARD_THEMES.length; i++) {
          if (KEYBOARD_THEMES[i].equalsIgnoreCase(current)) {
              index = i;
              break;
          }
      }

      int nextIndex = (index + 1) % KEYBOARD_THEMES.length;
      String nextTheme = KEYBOARD_THEMES[nextIndex];

      prefs.edit().putString("theme", nextTheme).apply();
      _config.themeName = nextTheme;

      refresh_config();
      if (_keyboardView != null) {
          _keyboardView.showTutorial("Theme: " + nextTheme.replace("_", " "));
      }
  }


  KeyboardData current_layout_unmodified()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    KeyboardData layout = null;
    int layout_i = _config.get_current_layout();
    if (layout_i >= _config.layouts.size())
      layout_i = 0;
    if (layout_i < _config.layouts.size())
      layout = _config.layouts.get(layout_i);
    if (layout == null)
      layout = _localeTextLayout;
    return layout;
  }


  KeyboardData current_layout()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    return LayoutModifier.modify_layout(current_layout_unmodified());
  }

  void setTextLayout(int l)
  {
    _config.set_current_layout(l);
    _currentSpecialLayout = null;
    final KeyboardData newLayout = current_layout();
    _keyboardView.setKeyboard(newLayout);
    updateWordlist(newLayout);
    if (_autoCorrectionProvider != null) {
        _autoCorrectionProvider.updateLayout(newLayout);
    }
    if (_keyboardAwareSuggester != null) {
        _keyboardAwareSuggester.updateLayout(newLayout);
    }
  }

  private void updateWordlist(KeyboardData layout) {
      if (_suggestionProvider != null && layout != null) {
          _suggestionProvider.setScript(layout.script);
          _suggestionProvider.setWordlist(layout.wordlist);
      }
  }

  void incrTextLayout(int delta)
  {
    int s = _config.layouts.size();
    setTextLayout((_config.get_current_layout() + delta + s) % s);
  }

  void setSpecialLayout(KeyboardData l)
  {
    _currentSpecialLayout = l;
    _keyboardView.setKeyboard(l);
  }

  KeyboardData loadLayout(int layout_id)
  {
    return KeyboardData.load(getResources(), layout_id);
  }


  KeyboardData loadNumpad(int layout_id)
  {
    return LayoutModifier.modify_numpad(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  KeyboardData loadPinentry(int layout_id)
  {
    return LayoutModifier.modify_pinentry(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  private BroadcastReceiver mDictionaryReloadReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION)) {
              if (_suggestionProvider != null) {
                  _suggestionProvider.reloadCustomDictionary();
              }
          } else if (intent.getAction().equals(SuggestionProvider.RELOAD_FILTERS_ACTION)) {
              if (_suggestionProvider != null) {
                  _suggestionProvider.reloadFilters();
              }
          } else if (intent.getAction().equals(NextWordProbability.RELOAD_NEXT_WORD_ACTION)) {
              if (_suggestionProvider != null) {
                  _suggestionProvider.reloadNextWordProbability();
              }
          } else if (intent.getAction().equals(ClipboardHistoryService.RELOAD_CLIPBOARD_HISTORY_ACTION)) {
               // Already handled by ClipboardHistoryService internally
          }

          if (_keyeventhandler != null) {
              _keyeventhandler.invalidateCache();
              _keyeventhandler.triggerUpdateSuggestions();
          }
      }
  };

  @Override
  public void onCreate()
  {
    super.onCreate();
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _handler = new Handler(getMainLooper());
    _foldStateTracker = new FoldStateTracker(this);

    // 1. Initialize an empty Handler for Config temporarily if needed,
    // or just pass a placeholder since KeyEventHandler is ready.
    _suggestionProvider = new SuggestionProvider(this);
    _autoCorrectionProvider = new LayoutBasedAutoCorrectionProvider(_suggestionProvider);
    _keyboardAwareSuggester = new KeyboardAwareSuggester(this, _suggestionProvider);
    _keyeventhandler = new KeyEventHandler(this.new Receiver(), _suggestionProvider, _autoCorrectionProvider, _keyboardAwareSuggester);

    // Initialize Config as early as possible
    Config.initGlobalConfig(prefs, getResources(), _keyeventhandler, _foldStateTracker.isUnfolded());
    prefs.registerOnSharedPreferenceChangeListener(this);
    _config = Config.globalConfig();
    _tutorials = getResources().getStringArray(R.array.tutorials);
    _inputView = inflate_view(R.layout.keyboard);
    _mainLayout = (ViewGroup) _inputView;
    _keyboardView = _inputView.findViewById(R.id.keyboard_view);
    _voiceOverlayContainer = _inputView.findViewById(R.id.voice_overlay_container);
    setupSuggestionStrip();
    _keyboardView.reset();
    Logs.set_debug_logs(getResources().getBoolean(R.bool.debug_logs));
    ClipboardHistoryService.on_startup(this, _keyeventhandler);
    IndexingService.getInstance(this).startIndexing();
    _foldStateTracker.setChangedCallback(() -> { refresh_config(); });

    IntentFilter filter = new IntentFilter();
    filter.addAction(CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION);
    filter.addAction(SuggestionProvider.RELOAD_FILTERS_ACTION);
    filter.addAction(NextWordProbability.RELOAD_NEXT_WORD_ACTION);
    if (VERSION.SDK_INT >= 33) {
      registerReceiver(mDictionaryReloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      registerReceiver(mDictionaryReloadReceiver, filter);
    }

  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (_voiceTypingManager != null) {
      _voiceTypingManager.destroy();
      _voiceTypingManager = null;
    }
    if (_typingHUDManager != null) {
        _typingHUDManager.cleanup();
    }
    unregisterReceiver(mDictionaryReloadReceiver);
    _foldStateTracker.close();
  }

  private List<InputMethodSubtype> getEnabledSubtypes(InputMethodManager imm)
  {
    String pkg = getPackageName();
    for (InputMethodInfo imi : imm.getEnabledInputMethodList())
      if (imi.getPackageName().equals(pkg))
        return imm.getEnabledInputMethodSubtypeList(imi, true);
    return Arrays.asList();
  }

  @TargetApi(12)
  private ExtraKeys extra_keys_of_subtype(InputMethodSubtype subtype)
  {
    String extra_keys = subtype.getExtraValueOf("extra_keys");
    String script = subtype.getExtraValueOf("script");
    if (extra_keys != null)
      return ExtraKeys.parse(script, extra_keys);
    return ExtraKeys.EMPTY;
  }

  private void refreshAccentsOption(InputMethodManager imm, List<InputMethodSubtype> enabled_subtypes)
  {
    List<ExtraKeys> extra_keys = new ArrayList<ExtraKeys>();
    for (InputMethodSubtype s : enabled_subtypes)
      extra_keys.add(extra_keys_of_subtype(s));
    _config.extra_keys_subtype = ExtraKeys.merge(extra_keys);
  }

  InputMethodManager get_imm()
  {
    return (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  }

  @TargetApi(12)
  private InputMethodSubtype defaultSubtypes(InputMethodManager imm, List<InputMethodSubtype> enabled_subtypes)
  {
    if (VERSION.SDK_INT < 24)
      return imm.getCurrentInputMethodSubtype();


    InputMethodSubtype current_subtype = imm.getCurrentInputMethodSubtype();
    if (current_subtype == null)
      return null;
    for (InputMethodSubtype s : enabled_subtypes)
      if (s.getLanguageTag().equals(current_subtype.getLanguageTag()))
        return s;
    return null;
  }

  private void refreshSubtypeImm()
  {
    InputMethodManager imm = get_imm();
    _config.shouldOfferVoiceTyping = true;
    KeyboardData default_layout = null;
    _config.extra_keys_subtype = null;
    if (VERSION.SDK_INT >= 12)
    {
      List<InputMethodSubtype> enabled_subtypes = getEnabledSubtypes(imm);
      InputMethodSubtype subtype = defaultSubtypes(imm, enabled_subtypes);
      if (subtype != null)
      {
        String s = subtype.getExtraValueOf("default_layout");
        if (s != null)
          default_layout = LayoutsPreference.layout_of_string(getResources(), s);
        refreshAccentsOption(imm, enabled_subtypes);
      }
    }
    if (default_layout == null)
      default_layout = loadLayout(R.xml.latn_qwerty_us);
    _localeTextLayout = default_layout;
    if (_autoCorrectionProvider != null) {
        _autoCorrectionProvider.updateLayout(_localeTextLayout);
    }
    if (_keyboardAwareSuggester != null) {
        _keyboardAwareSuggester.updateLayout(_localeTextLayout);
    }
  }

  private String actionLabel_of_imeAction(int action)
  {
    int res;
    switch (action)
    {
      case EditorInfo.IME_ACTION_NEXT: res = R.string.key_action_next; break;
      case EditorInfo.IME_ACTION_DONE: res = R.string.key_action_done; break;
      case EditorInfo.IME_ACTION_GO: res = R.string.key_action_go; break;
      case EditorInfo.IME_ACTION_PREVIOUS: res = R.string.key_action_prev; break;
      case EditorInfo.IME_ACTION_SEARCH: res = R.string.key_action_search; break;
      case EditorInfo.IME_ACTION_SEND: res = R.string.key_action_send; break;
      case EditorInfo.IME_ACTION_UNSPECIFIED:
      case EditorInfo.IME_ACTION_NONE:
      default: return null;
    }
    return getResources().getString(res);
  }

  private void refresh_action_label(EditorInfo info)
  {


    if (info.actionLabel != null)
    {
      _config.actionLabel = info.actionLabel.toString();
      actionId = info.actionId;
      _config.swapEnterActionKey = false;
    }
    else
    {
      int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
      _config.actionLabel = actionLabel_of_imeAction(action);
      actionId = action;

      if (!_config.swapEnterActionKey) {
          _config.swapEnterActionKey =
            (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;
      }
    }
  }


  private void refresh_config()
  {
    int prev_theme = _config.theme;
    _config.refresh(getResources(), _foldStateTracker.isUnfolded());
    refreshSubtypeImm();

    if (prev_theme != _config.theme)
    {
      _inputView = inflate_view(R.layout.keyboard);
      _mainLayout = (ViewGroup) _inputView;
      _keyboardView = _inputView.findViewById(R.id.keyboard_view);
      _voiceOverlayContainer = _inputView.findViewById(R.id.voice_overlay_container);
      setupSuggestionStrip();
      _emojiPane = null;
      _clipboard_pane = null;
      _voiceInputView = null;
      setInputView(_inputView);
    }
    _keyboardView.reset();
    updateWordlist(current_layout());
  }

  private KeyboardData refresh_special_layout(EditorInfo info)
  {
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_PHONE:
      case InputType.TYPE_CLASS_DATETIME:
        if (_config.selected_number_layout == NumberLayout.PIN)
          return loadPinentry(R.xml.pin);
        else if (_config.selected_number_layout == NumberLayout.NUMBER)
          return loadNumpad(R.xml.numeric);
      default:
        break;
    }
    return null;
  }

  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
        synchronized (mPendingActions) {
            if (!mPendingActions.isEmpty()) {
                ic.beginBatchEdit();
                for (PendingAction action : mPendingActions) {
                    switch (action.type) {
                        case COMMIT:
                            if (action.text.length() > 1024) {
                                for (int i = 0; i < action.text.length(); i += 1024) {
                                    ic.commitText(action.text.substring(i, Math.min(i + 1024, action.text.length())), 1);
                                }
                            } else {
                                ic.commitText(action.text, 1);
                            }
                            break;
                        case SELECTION: ic.setSelection(action.start, action.end); break;
                        case KEY:
                            long now = System.currentTimeMillis();
                            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, action.keyCode, 0, 0, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
                            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, action.keyCode, 0, 0, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
                            break;
                    }
                }
                mPendingActions.clear();
                ic.endBatchEdit();
            }
        }
    }
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting)
  {
    if (!restarting) {
        ClipboardHistoryService.get_service(this).startNewTypingSession();
    }
    refresh_config();
    refresh_action_label(info);
    _currentSpecialLayout = refresh_special_layout(info);
    _keyboardView.setKeyboard(current_layout());
    _keyeventhandler.started(info);
    if (_forceKeepClipboardPaneOnNextStart || (restarting && ((_clipboard_pane != null && _clipboard_pane.isShown()) || (_emojiPane != null && _emojiPane.isShown())))) {
      _forceKeepClipboardPaneOnNextStart = false;
      return;
    }
    _forceKeepClipboardPaneOnNextStart = false;
    setInputView(_inputView);
    _keyeventhandler.triggerUpdateSuggestions();
    Logs.debug_startup_input_view(info, _config);
  }

  @Override
  public void setInputView(View v)
  {
    ViewParent parent = v.getParent();
    if (parent != null && parent instanceof ViewGroup)
      ((ViewGroup)parent).removeView(v);
    super.setInputView(v);
    updateSoftInputWindowLayoutParams();
    v.requestApplyInsets();
  }


  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParams();
  }

  private void updateSoftInputWindowLayoutParams() {
    final Window window = getWindow().getWindow();


    if (VERSION.SDK_INT >= 35)
    {
      WindowManager.LayoutParams wattrs = window.getAttributes();
      wattrs.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

      wattrs.setFitInsetsTypes(0);
      window.setDecorFitsSystemWindows(false);
    }
    updateLayoutHeightOf(window, ViewGroup.LayoutParams.MATCH_PARENT);
    final View inputArea = window.findViewById(android.R.id.inputArea);

    updateLayoutHeightOf(
            (View) inputArea.getParent(),
            isFullscreenMode()
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT);
    updateLayoutGravityOf((View) inputArea.getParent(), Gravity.BOTTOM);

  }

  private static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
    final WindowManager.LayoutParams params = window.getAttributes();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      window.setAttributes(params);
    }
  }

  private static void updateLayoutHeightOf(final View view, final int layoutHeight) {
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  private static void updateLayoutGravityOf(final View view, final int layoutGravity) {
    final ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp instanceof LinearLayout.LayoutParams) {
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (lp instanceof FrameLayout.LayoutParams) {
      final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    }
  }

  @Override
  public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)
  {
    refreshSubtypeImm();
    KeyboardData layout = current_layout();
    _keyboardView.setKeyboard(layout);
    updateWordlist(layout);
  }

  @Override
  public void onWindowHidden() {
    super.onWindowHidden();
    if (_keyeventhandler != null) _keyeventhandler.finished(getCurrentInputConnection());
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd)
  {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    _keyeventhandler.selection_updated(oldSelStart, newSelStart, newSelEnd);
    if ((oldSelStart == oldSelEnd) != (newSelStart == newSelEnd))
      _keyboardView.set_selection_state(newSelStart != newSelEnd);
  }

  @Override
  public void onFinishInputView(boolean finishingInput)
  {
    super.onFinishInputView(finishingInput);
    if (_keyeventhandler != null) _keyeventhandler.finished();
    if (_config != null && !_config.incognito_mode) {
      InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) {
          String text = et.text.toString();

          boolean isPassword = false;
          EditorInfo editorInfo = getCurrentInputEditorInfo();
          if (editorInfo != null) {
             int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
             isPassword = (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                           variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                           variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
          }
          if (!isPassword) {
             ClipboardHistoryService.get_service(this).addTypingHistory(text);
          }
        }
      }
    }
    _suggestionProvider.nextWordProbability.saveProbabilities();
    _tutorialHandler.removeCallbacks(_tutorialRunnable);
    if (_suggestionStrip != null) {
      _suggestionStrip.setVisibility(View.GONE);
    }
    if (_keyboardView != null) {
      _keyboardView.reset();
    }
    if (_typingHUDManager != null) {
        _typingHUDManager.updateHUD(null, null, false, 0, 0, 0, 0);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
          if (_voiceOverlayContainer != null && _voiceOverlayContainer.getVisibility() == View.VISIBLE) {
               if (_voiceTypingManager != null) _voiceTypingManager.cancel();
               hideVoiceOverlay();
               return true;
          }
          if (_clipboard_pane != null && _clipboard_pane.isShown()) {
              _keyeventhandler.key_up(KeyValue.getSpecialKeyByName("switch_back_clipboard"), Pointers.Modifiers.EMPTY);
              return true;
          }
          if (_glyphPane != null && _glyphPane.isShown()) {
              setInputView(_inputView);
              return true;
          }
      }
      return super.onKeyDown(keyCode, event);
  }

  private void setupSuggestionStrip() {
    _suggestionsRecyclerView = _inputView.findViewById(R.id.suggestions_recycler_view);
    _suggestionStrip = _inputView.findViewById(R.id.suggestions_strip);
    if (_typingHUDManager == null) {
        _typingHUDManager = new TypingHUDManager(this);
    }
    _suggestionStripContainerTop = _inputView.findViewById(R.id.suggestion_strip_container_top);
    _suggestionStripContainerBottom = _inputView.findViewById(R.id.suggestion_strip_container_bottom);
    _tutorialFlipper = _inputView.findViewById(R.id.tutorial_flipper);
    _ziaistanOfficialText = _inputView.findViewById(R.id.ziaistan_official_text);


    Context themedContext = new ContextThemeWrapper(this, _config.theme);
    LayoutInflater inflater = LayoutInflater.from(themedContext);
    for (String tutorial : _tutorials) {
        TextView textView = (TextView) inflater.inflate(R.layout.suggestion_item, _tutorialFlipper, false);
        textView.setText(tutorial);
        textView.setTextSize(12);
        _tutorialFlipper.addView(textView);
    }

    _tutorialRunnable = new Runnable() {
        @Override
        public void run() {
            if (_tutorialFlipper != null && _tutorials.length > 0) {
                int next = _random.nextInt(_tutorials.length);
                while (next == _tutorialFlipper.getDisplayedChild()) {
                    next = _random.nextInt(_tutorials.length);
                }
                _tutorialFlipper.setInAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_in_right));
                _tutorialFlipper.setOutAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_out_left));
                _tutorialFlipper.setDisplayedChild(next);
                _tutorialHandler.postDelayed(this, TUTORIAL_TRANSITION_DELAY);
            }
        }
    };


    _tutorialFlipper.setOnTouchListener((v, event) -> {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                _lastX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                _tutorialHandler.removeCallbacks(_tutorialRunnable);
                float currentX = event.getX();
                if (_lastX < currentX) {
                    _tutorialFlipper.setInAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_in_left));
                    _tutorialFlipper.setOutAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_out_right));
                    _tutorialFlipper.showPrevious();
                }
                if (_lastX > currentX) {
                    _tutorialFlipper.setInAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_in_right));
                    _tutorialFlipper.setOutAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_out_left));
                    _tutorialFlipper.showNext();
                }
                _tutorialHandler.postDelayed(_tutorialRunnable, TUTORIAL_TRANSITION_DELAY);
                break;
        }
        return false;
    });

    _suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    _suggestionAdapter = new SuggestionAdapter();
    _suggestionsRecyclerView.setAdapter(_suggestionAdapter);

    _suggestionsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null && layoutManager.findLastVisibleItemPosition() >= _suggestionAdapter.getItemCount() - 4) {
                recyclerView.post(() -> _suggestionAdapter.increaseLimit());
            }
        }
    });

    _inputView.findViewById(R.id.suggestion_strip_handle).setOnClickListener(v -> {
        _config.suggestionStripOnTop = !_config.suggestionStripOnTop;
        SharedPreferences.Editor editor = Config.globalPrefs().edit();
        editor.putBoolean("suggestion_strip_on_top", _config.suggestionStripOnTop);
        editor.apply();
        updateSuggestionStripPosition();
    });
    updateSuggestionStripPosition();
  }

  private void updateSuggestionStripPosition() {
    if (_suggestionStrip == null || _suggestionStripContainerTop == null || _suggestionStripContainerBottom == null) {
        return;
    }


    ViewGroup parent = (ViewGroup) _suggestionStrip.getParent();
    if (parent != null) {
        parent.removeView(_suggestionStrip);
    }

    if (_config.suggestionStripOnTop) {
        _suggestionStripContainerTop.addView(_suggestionStrip);
        _suggestionStripContainerTop.setVisibility(View.VISIBLE);
        _suggestionStripContainerBottom.setVisibility(View.GONE);
    } else {
        _suggestionStripContainerBottom.addView(_suggestionStrip);
        _suggestionStripContainerTop.setVisibility(View.GONE);
        _suggestionStripContainerBottom.setVisibility(View.VISIBLE);
    }
  }

  private void showVoiceOverlay() {
      if (_voiceOverlayContainer != null && _voiceInputView != null) {

          if (_keyboardView != null && _keyboardView.getHeight() > 0) {
              ViewGroup.LayoutParams lp = _voiceOverlayContainer.getLayoutParams();
              lp.height = _keyboardView.getHeight();
              _voiceOverlayContainer.setLayoutParams(lp);
          }

          if (_voiceInputView.getParent() != _voiceOverlayContainer) {
              if (_voiceInputView.getParent() != null) ((ViewGroup)_voiceInputView.getParent()).removeView(_voiceInputView);
              _voiceOverlayContainer.addView(_voiceInputView);
          }
          _voiceOverlayContainer.setVisibility(View.VISIBLE);
      } else if (_voiceInputView != null) {
          setInputView(_voiceInputView);
      }
  }

  private void hideVoiceOverlay() {
       if (_voiceOverlayContainer != null && _voiceOverlayContainer.getVisibility() == View.VISIBLE) {
           _voiceOverlayContainer.setVisibility(View.GONE);
       } else {
           setInputView(_inputView);
       }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key)
  {
    if ("enable_typing_hud".equals(_key) && _prefs.getBoolean(_key, false)) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(this, "Please grant Overlay permission for Typing HUD", android.widget.Toast.LENGTH_LONG).show();
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }
    refresh_config();
    updateSuggestionStripPosition();
    if (_clipboard_pane != null) {
        _clipboard_pane.updateActionRowPosition();
    }
    _keyboardView.setKeyboard(current_layout());
  }

  @Override
  public boolean onEvaluateFullscreenMode()
  {

    return false;
  }


  private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {
      private List<SuggestionProvider.Suggestion> suggestions = new ArrayList<>();
      private int displayLimit = 3;

      public void setSuggestions(List<SuggestionProvider.Suggestion> suggestions, boolean incremental) {
          if (this.suggestions.equals(suggestions)) return;

          if (incremental && !this.suggestions.isEmpty() && suggestions.size() > this.suggestions.size()) {
              boolean matches = true;
              int checkCount = Math.min(this.suggestions.size(), 100);
              for (int i = 0; i < checkCount; i++) {
                  if (!this.suggestions.get(i).equals(suggestions.get(i))) {
                      matches = false;
                      break;
                  }
              }
              if (matches) {
                  int oldItemCount = getItemCount();
                  this.suggestions = suggestions;
                  int newItemCount = getItemCount();
                  if (newItemCount > oldItemCount) {
                      notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount);
                  }
                  return;
              }
          }

          this.suggestions = suggestions;
          if (!incremental) {
              this.displayLimit = 3;
          }
          notifyDataSetChanged();
      }

      public void increaseLimit() {
          if (displayLimit < suggestions.size()) {
              int oldLimit = getItemCount();
              displayLimit = suggestions.size();
              int newLimit = getItemCount();
              if (newLimit > oldLimit) {
                  notifyItemRangeInserted(oldLimit, newLimit - oldLimit);
              }
          }
      }

      @NonNull
      @Override
      public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
          Context themedContext = new ContextThemeWrapper(Keyboard2.this, _config.theme);
          View view = LayoutInflater.from(themedContext).inflate(R.layout.suggestion_item, parent, false);

          // UI Optimization: Fixed 4 equal sections per "view"
          int parentWidth = parent.getWidth();
          if (parentWidth <= 0) parentWidth = parent.getMeasuredWidth();
          if (parentWidth > 0) {
              ViewGroup.LayoutParams lp = view.getLayoutParams();
              // Account for 3dp margin on each side (total 6dp per slot)
              float density = parent.getContext().getResources().getDisplayMetrics().density;
              int marginPx = (int) (3 * density * 2);
              lp.width = (parentWidth / 3) - marginPx;
              view.setLayoutParams(lp);
          }
          return new ViewHolder(view);
      }

      @Override
      public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
          holder.textView.setGravity(Gravity.CENTER);
          holder.textView.setSingleLine(true);
          holder.textView.setEllipsize(null);

          // Apply a constant 0.9x scale (~16sp) globally, disabling auto-sizing for performance
          TextViewCompat.setAutoSizeTextTypeWithDefaults(holder.textView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
          holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

          if (position < suggestions.size()) {
              SuggestionProvider.Suggestion suggestion = suggestions.get(position);
              holder.textView.setText(suggestion.word);

              if (SuggestionProvider.FEATURE_TUTORIAL.equals(suggestion.source)) {
                  holder.textView.setTypeface(null, android.graphics.Typeface.ITALIC);
                  holder.textView.setAlpha(0.7f);
                  holder.textView.setTextColor(0xFFAAAAAA);
                  holder.textView.setOnTouchListener(null);
              } else {
                  holder.textView.setTypeface(null, android.graphics.Typeface.NORMAL);
                  holder.textView.setAlpha(1.0f);

                  Integer color = _config.suggestion_category_colors.get(suggestion.source);

                  // Priority check for color matching:
                  // 1. Direct source match (e.g. "typed", "filters", "next_word")
                  // 2. Specific search type match (e.g. "deletion", "substitution" -> "autocorrect")
                  // 3. Underlying dictionary source match (WordSource)

                  if (color == null) {
                      if (suggestion.source.equals(SuggestionProvider.SEARCH_DELETION) ||
                          suggestion.source.equals(SuggestionProvider.SEARCH_INSERTION) ||
                          suggestion.source.equals(SuggestionProvider.SEARCH_SUBSTITUTION) ||
                          suggestion.source.equals(SuggestionProvider.SEARCH_TRANSPOSITION) ||
                          suggestion.source.equals(SuggestionProvider.SEARCH_DOUBLING) ||
                          suggestion.source.equals(SuggestionProvider.SEARCH_SINGLING) ||
                          suggestion.source.equals(SuggestionProvider.FEATURE_AUTOCORRECT)) {
                          color = _config.suggestion_category_colors.get("autocorrect");
                      } else if (suggestion.source.equals(SuggestionProvider.SEARCH_KEYBOARD_AWARE) ||
                               suggestion.source.equals(SuggestionProvider.FEATURE_KEYBOARD_AWARE) ||
                               suggestion.source.equals(SuggestionProvider.FEATURE_KEYBOARD_AWARE_PREFIX)) {
                          color = _config.suggestion_category_colors.get("keyboard_aware");
                      }
                  }

                  if (color == null) {
                      SuggestionProvider.WordSource ws = suggestion.wordSource != SuggestionProvider.WordSource.NONE
                          ? suggestion.wordSource
                          : _suggestionProvider.getWordSource(suggestion.word);

                      switch (ws) {
                          case TYPED: color = _config.suggestion_category_colors.get("typed"); break;
                          case FILTERS: color = _config.suggestion_category_colors.get("filters"); break;
                          case NEXT_WORD: color = _config.suggestion_category_colors.get("next_word"); break;
                          case CUSTOM: color = _config.suggestion_category_colors.get("custom"); break;
                          case COMMON: color = _config.suggestion_category_colors.get("common"); break;
                          case WORDLIST: color = _config.suggestion_category_colors.get("wordlist"); break;
                          case FIELD: color = _config.suggestion_category_colors.get("field"); break;
                      }
                  }

                  holder.textView.setTextColor(color != null ? color : 0xFFFFFFFF);

                  holder.textView.setOnTouchListener(new SuggestionTouchListener(holder.textView, suggestion));
              }
          } else {
              // Empty space for padding to 5 sections
              holder.textView.setText("");
              holder.textView.setOnTouchListener(null);
              holder.textView.setTypeface(null, android.graphics.Typeface.NORMAL);
              holder.textView.setAlpha(1.0f);
          }
      }

      @Override
      public int getItemCount() {
          if (suggestions.isEmpty()) return 0;
          return Math.min(suggestions.size(), displayLimit);
      }

      class ViewHolder extends RecyclerView.ViewHolder {
          TextView textView;
          ViewHolder(@NonNull View itemView) {
              super(itemView);
              textView = (TextView) itemView;
          }
      }
  }

  private class SuggestionTouchListener implements View.OnTouchListener {
      private float startX, startY;
      private boolean isDragging = false;
      private final float swipeThresholdPx;
      private final int touchSlop;
      private final View view;
      private final SuggestionProvider.Suggestion suggestion;
      private boolean longPressed = false;
      private final Handler handler = new Handler();
      private final Runnable longPressRunnable = new Runnable() {
          @Override
          public void run() {
              longPressed = true;
              if (_keyeventhandler != null) {
                  _keyeventhandler.blacklistSuggestion(suggestion);
                  vibrate(50);
              }
              if (view.getParent() != null) {
                  view.getParent().requestDisallowInterceptTouchEvent(true);
              }
              view.animate().alpha(0.3f).setDuration(200).start();
          }
      };

      private void vibrate(int duration) {
          android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
          if (v != null) {
              if (android.os.Build.VERSION.SDK_INT >= 26) {
                  v.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
              } else {
                  v.vibrate(duration);
              }
          }
      }

      public SuggestionTouchListener(View view, SuggestionProvider.Suggestion suggestion) {
          this.view = view;
          this.suggestion = suggestion;
          this.swipeThresholdPx = 30f * view.getResources().getDisplayMetrics().density;
          this.touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
      }

      @Override
      public boolean onTouch(View v, MotionEvent event) {
          switch (event.getAction()) {
              case MotionEvent.ACTION_DOWN:
                  startX = event.getRawX();
                  startY = event.getRawY();
                  isDragging = false;
                  longPressed = false;
                  handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());

                  v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start();
                  v.setTranslationY(0);
                  return true;

              case MotionEvent.ACTION_MOVE:
                  float currentX = event.getRawX();
                  float currentY = event.getRawY();
                  float deltaX = currentX - startX;
                  float deltaY = currentY - startY;

                  // Only cancel long-press if movement is significant
                  if (Math.abs(deltaY) > touchSlop * 0.5 || Math.abs(deltaX) > touchSlop * 0.5) {
                       handler.removeCallbacks(longPressRunnable);
                  }

                  if (!isDragging && Math.abs(deltaY) > touchSlop * 0.8f && Math.abs(deltaY) > Math.abs(deltaX)) {
                       isDragging = true;
                       if (v.getParent() != null) {
                           v.getParent().requestDisallowInterceptTouchEvent(true);
                       }
                  }

                  if (isDragging) {
                       v.setTranslationY(deltaY);
                       float alpha = 1.0f - Math.min(0.6f, Math.abs(deltaY) / (swipeThresholdPx * 2));
                       v.setAlpha(alpha);
                       // Disallow parent from intercepting while we are dragging
                       if (v.getParent() != null) {
                           v.getParent().requestDisallowInterceptTouchEvent(true);
                       }
                       return true;
                  }
                  return false;

              case MotionEvent.ACTION_CANCEL:
                  handler.removeCallbacks(longPressRunnable);
                  v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                  v.setTranslationY(0);
                  isDragging = false;
                  return true;

              case MotionEvent.ACTION_UP:
                  handler.removeCallbacks(longPressRunnable);
                  v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();

                  if (longPressed) {
                      v.setTranslationY(0);
                      isDragging = false;
                      return true;
                  }

                  float endX = event.getRawX();
                  float endY = event.getRawY();
                  float totalDeltaX = endX - startX;
                  float totalDeltaY = endY - startY;

                  if (isDragging) {
                      if (totalDeltaY < -swipeThresholdPx) {
                          if (_keyeventhandler != null) {
                              _keyeventhandler.promoteSuggestion(suggestion);
                          }
                      } else if (totalDeltaY > swipeThresholdPx) {
                           if (_keyeventhandler != null) {
                              _keyeventhandler.deprioritizeSuggestion(suggestion);
                          }
                      }
                  } else if (Math.abs(totalDeltaX) < touchSlop && Math.abs(totalDeltaY) < touchSlop) {
                      // Click
                      v.performClick();
                      if (_keyeventhandler != null) {
                          _keyeventhandler.replaceCurrentWord(suggestion);
                      }
                  }

                  v.setTranslationY(0);
                  isDragging = false;
                  return true;
          }
          return false;
      }

  }

  private SuggestionProvider.SuggestionMode lastMode = SuggestionProvider.SuggestionMode.NONE;

  private void populateSuggestions(List<SuggestionProvider.Suggestion> suggestions, SuggestionProvider.SuggestionMode mode) {
    if (_suggestionStrip == null || _suggestionsRecyclerView == null || _suggestionAdapter == null) {
        return;
    }

    _suggestionStrip.setVisibility(View.VISIBLE);
    _ziaistanOfficialText.setVisibility(View.VISIBLE);

    List<SuggestionProvider.Suggestion> mutableSuggestions = new ArrayList<>(suggestions);

    if (mode == SuggestionProvider.SuggestionMode.NEXT_WORD && mutableSuggestions.size() < 3) {
        // Only show 1 tutorial if suggestions are sparse
        if (_tutorials.length > 0) {
            mutableSuggestions.add(new SuggestionProvider.Suggestion(_tutorials[_random.nextInt(_tutorials.length)], SuggestionProvider.FEATURE_TUTORIAL));
        }
    }

    boolean isIncremental = (mode == lastMode && !mutableSuggestions.isEmpty() && _suggestionAdapter.getItemCount() > 0 && mode != SuggestionProvider.SuggestionMode.NONE);

    if (mutableSuggestions.isEmpty()) {
        _suggestionsRecyclerView.setVisibility(View.GONE);
        _tutorialFlipper.setVisibility(View.VISIBLE);
        _tutorialHandler.removeCallbacks(_tutorialRunnable);
        _tutorialHandler.postDelayed(_tutorialRunnable, TUTORIAL_TRANSITION_DELAY);
    } else {
        _tutorialHandler.removeCallbacks(_tutorialRunnable);
        _tutorialFlipper.setVisibility(View.GONE);
        _suggestionsRecyclerView.setVisibility(View.VISIBLE);
        _suggestionAdapter.setSuggestions(mutableSuggestions, isIncremental);
        if (!isIncremental) {
            _suggestionsRecyclerView.scrollToPosition(0);
        }
    }
    lastMode = mode;
  }


  private void showSuggestions(List<SuggestionProvider.Suggestion> suggestions, SuggestionProvider.SuggestionMode mode) {
      populateSuggestions(suggestions, mode);
  }


  public class Receiver implements KeyEventHandler.IReceiver
  {
    public void handle_event_key(KeyValue.Event ev)
    {
      switch (ev)
      {
        case CONFIG:
          Intent intent = new Intent(Keyboard2.this, SettingsActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
          break;

        case SWITCH_TEXT:
          _currentSpecialLayout = null;
          KeyboardData layout = current_layout();
          _keyboardView.setKeyboard(layout);
          updateWordlist(layout);
          break;

        case SWITCH_NUMERIC:
          setSpecialLayout(loadNumpad(R.xml.numeric));
          break;

        case SWITCH_EMOJI:
          if (_emojiPane == null)
            _emojiPane = (ViewGroup)inflate_view(R.layout.emoji_pane);

          EmojiGridView grid = _emojiPane.findViewById(R.id.emoji_grid);
          if (grid != null) {
              grid.resetToDefaultTab();
          }

          setInputView(_emojiPane);
          break;

        case SWITCH_CLIPBOARD:
          if (_clipboard_pane == null) {
            _clipboard_pane = (ClipboardView) inflate_view(R.layout.clipboard_pane);
            _clipboard_pane.setKeyboardReceiver(this);
          }
          if (_keyboardView != null && _keyboardView.getHeight() > 0) {
              _clipboard_pane.setKeyboardHeight(_keyboardView.getHeight());
          }
          _clipboard_pane.showTypingHistory(false);
          setInputView(_clipboard_pane);
          break;

        case SWITCH_TYPING_HISTORY:
          if (_clipboard_pane == null) {
            _clipboard_pane = (ClipboardView) inflate_view(R.layout.clipboard_pane);
            _clipboard_pane.setKeyboardReceiver(this);
          }
          if (_keyboardView != null && _keyboardView.getHeight() > 0) {
              _clipboard_pane.setKeyboardHeight(_keyboardView.getHeight());
          }
          _clipboard_pane.showTypingHistory(true);
          setInputView(_clipboard_pane);
          break;

        case SWITCH_BACK_EMOJI:
        case SWITCH_BACK_CLIPBOARD:
          setInputView(_inputView);
          break;

        case CHANGE_METHOD_PICKER:
          get_imm().showInputMethodPicker();
          break;

        case CHANGE_METHOD_AUTO:
          if (VERSION.SDK_INT < 28)
            get_imm().switchToLastInputMethod(getConnectionToken());
          else
            switchToNextInputMethod(false);
          break;

        case ACTION:
          InputConnection conn = getCurrentInputConnection();
          if (conn != null)
            conn.performEditorAction(actionId);
          break;

        case TOGGLE_SUGGESTIONS: {
          _config.enable_suggestions = !_config.enable_suggestions;
          Config.globalPrefs().edit().putBoolean("enable_suggestions", _config.enable_suggestions).apply();
          _keyeventhandler.triggerUpdateSuggestions();
          _keyboardView.showTutorial("Clipboard Auto Completion : " + (_config.enable_suggestions ? "ON" : "OFF"));
          _keyboardView.invalidate();
          break;
        }
        case SWITCH_FORWARD:
          incrTextLayout(1);
          break;

        case SWITCH_BACKWARD:
          incrTextLayout(-1);
          break;

        case SWITCH_GREEKMATH:
          setSpecialLayout(loadNumpad(R.xml.greekmath));
          break;

        case CAPS_LOCK:
          set_shift_state(true, true);
          break;

        case SWITCH_VOICE_TYPING:
          startVoiceTyping();
          break;

        case SWITCH_VOICE_TYPING_CHOOSER:
          VoiceImeSwitcher.choose_voice_ime(Keyboard2.this, get_imm(),
              Config.globalPrefs());
          break;

        case MOUSE_PAD:
          if (_mousePadView == null) {
              _mousePadView = new MousePadView(Keyboard2.this);
          }

          _mousePadView.show(_config.theme);

          break;

        case OPEN_TERMUX_COMMANDS:
          new TermuxCommandsController(Keyboard2.this, this).showCommandsDialog();
          break;

        case CYCLE_THEME: {
          SharedPreferences prefs = Config.globalPrefs();
          String currentTheme = prefs.getString("theme", "galactic");
          String[] themeValues = getResources().getStringArray(R.array.pref_theme_values);
          List<String> themeCycle = Arrays.asList(themeValues);
          int currentIndex = themeCycle.indexOf(currentTheme);
          if (currentIndex == -1) currentIndex = 0;
          int nextIndex = (currentIndex + 1) % themeCycle.size();
          String nextTheme = themeCycle.get(nextIndex);
          setTheme(nextTheme);
          break;
        }
        case SET_THEME_GALACTIC:
          setTheme("galactic");
          break;
        case SET_THEME_GOLDEN_PEARL:
          setTheme("goldenpearl");
          break;
        case SET_THEME_NEON_PUNK:
          setTheme("neonpunk");
          break;
        case SET_THEME_EVERFOREST_LIGHT:
          setTheme("everforestlight");
          break;
        case SET_THEME_COBALT:
          setTheme("cobalt");
          break;
        case SET_THEME_EPAPER:
          setTheme("epaper");
          break;
        case LEARN_FROM_TEXT:
          _keyeventhandler.learnFromTextField();
          break;

        case SWITCH_GLYPHS:
          if (_glyphPane == null) {
            _glyphPane = (ViewGroup)inflate_view(R.layout.glyph_pane);

            final LinearLayout groupsBar = _glyphPane.findViewById(R.id.glyph_groups_bar);
            final GlyphGridView glyphGrid = _glyphPane.findViewById(R.id.glyph_grid);
            final GlyphPaneView glyphPaneRoot = (GlyphPaneView) _glyphPane;

            if (_keyboardView != null && _keyboardView.getHeight() > 0) {
                glyphPaneRoot.setKeyboardHeight(_keyboardView.getHeight());
            } else {
                glyphPaneRoot.setKeyboardHeight(getResources().getDimensionPixelSize(R.dimen.glyph_pane_height));
            }

            for (int i = 0; i < Glyph.getNumGroups(); i++) {
                Glyph.Group group = Glyph.getGroup(i);
                TextView btn = new TextView(new ContextThemeWrapper(Keyboard2.this, _config.theme));
                btn.setText(group.name);
                btn.setPadding(20, 10, 20, 10);
                btn.setGravity(Gravity.CENTER);
                btn.setTextColor(0xFFAAAAAA);
                btn.setTextSize(14);
                final int groupIdx = i;
                btn.setOnClickListener(v -> glyphGrid.setGlyphGroup(groupIdx));
                groupsBar.addView(btn);
            }
            glyphGrid.setOnGroupChangeListener(newGroup -> {
                for (int i = 0; i < groupsBar.getChildCount(); i++) {
                    View v = groupsBar.getChildAt(i);
                    if (v instanceof TextView) {
                        v.setBackgroundColor(i == newGroup ? 0x40FFFFFF : 0x00000000);
                    }
                }
            });
            glyphGrid.setGlyphGroup(0);
          }
          setInputView(_glyphPane);
          break;
      }
    }

    private void setTheme(String themeName) {
        Config.globalPrefs().edit().putString("theme", themeName).apply();
        refresh_config();
        updateSuggestionStripPosition();
        _keyboardView.setKeyboard(current_layout());
    }

    public void set_shift_state(boolean state, boolean lock)
    {
      _keyboardView.set_shift_state(state, lock);
    }

    public void set_compose_pending(boolean pending)
    {
      _keyboardView.set_compose_pending(pending);
    }

    public void selection_state_changed(boolean selection_is_ongoing)
    {
      _keyboardView.set_selection_state(selection_is_ongoing);
    }

    @Override
    public String getScript() {
        KeyboardData layout = current_layout();
        return layout != null ? layout.script : "latin";
    }

    @Override
    public void updateTypingHUD(String typed, String corrected, boolean showArrow) {
        if (_config == null || !_config.enable_typing_hud || _typingHUDManager == null) return;
        _typingHUDManager.updateHUD(typed, corrected, showArrow, _config.typing_hud_duration,
                _config.typing_hud_bg_color, _config.typing_hud_txt_color, _config.typing_hud_txt_size);
    }

    @Override
    public void showSuggestions(List<SuggestionProvider.Suggestion> suggestions, SuggestionProvider.SuggestionMode mode) {
        Keyboard2.this.showSuggestions(suggestions, mode);
    }

    @Override
    public void reloadCustomDictionary() {
        if (_suggestionProvider != null) {
            _suggestionProvider.reloadCustomDictionary();
        }
    }

    public InputConnection getCurrentInputConnection()
    {
      return Keyboard2.this.getCurrentInputConnection();
    }

    public EditorInfo getCurrentInputEditorInfo() {
        return Keyboard2.this.getCurrentInputEditorInfo();
    }

    public Handler getHandler()
    {
      return _handler;
    }

    public android.content.Context getContext() {
        return Keyboard2.this;
    }

    @Override
    public void showTutorial(String tutorial) {
        _keyboardView.showTutorial(tutorial);
    }

    @Override
    public java.util.Map<Character, android.graphics.RectF> getKeyCoordinates() {
        if (_keyboardView != null) {
            return _keyboardView.getKeyCoordinates();
        }
        return java.util.Collections.emptyMap();
    }

    @Override
    public void updateRenameBuffer(String text) {
        if (_inputView != null) {
            TextView preview = _inputView.findViewById(R.id.rename_text_preview);
            if (preview != null) {
                preview.setText(text);
            }
        }
    }

    @Override
    public void onRenameConfirmed(String newName) {

        if (_inputView != null) {
            _inputView.findViewById(R.id.rename_bar).setVisibility(View.GONE);
        }


        if (_clipboard_pane != null) {
            setInputView(_clipboard_pane);

            _clipboard_pane.finishRenaming(newName);
        }
    }

    @Override
    public void onRenameCancelled() {
        if (_inputView != null) {
            _inputView.findViewById(R.id.rename_bar).setVisibility(View.GONE);
        }
        if (_clipboard_pane != null) {
            setInputView(_clipboard_pane);
        }
    }

    public void startRenamingInMainLayout(String currentName) {

        setInputView(_inputView);
        View renameBar = _inputView.findViewById(R.id.rename_bar);
        if (renameBar != null) {
            renameBar.setVisibility(View.VISIBLE);


            renameBar.findViewById(R.id.rename_confirm_button).setOnClickListener(v -> {
                _keyeventhandler.stopRenaming(true);
            });
            renameBar.findViewById(R.id.rename_cancel_button).setOnClickListener(v -> {
                _keyeventhandler.stopRenaming(false);
            });
        }
        _keyeventhandler.startRenaming(currentName);
    }

    @Override
    public void showUndoPasteButton() {
        if (_clipboard_pane != null) {
            _clipboard_pane.showUndoPaste();
        }
    }

    @Override
    public void handleCustomCommand(String command) {
        if ("cycle_theme".equals(command)) {
            cycleKeyboardTheme();
        }
    }

    @Override
    public View getKeyboardView() {
        return _keyboardView;
    }

    public void undoLastPaste() {
        _keyeventhandler.undoLastPaste();
    }
  }

  private IBinder getConnectionToken()
  {
    return getWindow().getWindow().getAttributes().token;
  }

  private View inflate_view(int layout)
  {
    return View.inflate(new ContextThemeWrapper(this, _config.theme), layout, null);
  }

  private void startVoiceTyping() {
      if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
          android.widget.Toast.makeText(this, R.string.voice_permission_rationale, android.widget.Toast.LENGTH_LONG).show();
          Intent intent = new Intent(this, SettingsActivity.class);
          intent.putExtra(SettingsActivity.EXTRA_REQUEST_VOICE_PERMISSION, true);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
          return;
      }

      if (_voiceTypingManager == null) {
          _voiceTypingManager = new VoiceTypingManager(this, new VoiceTypingManager.VoiceTypingListener() {
              @Override
              public void onVoiceTypingStarted() {
                  if (_voiceInputView != null) {
                      _voiceInputView.setStatus(getString(R.string.voice_listening));
                      _voiceInputView.showListeningState();
                  }
              }

              @Override
              public void onVoiceTypingStopped() {
                  if (_voiceInputView != null) {
                      _voiceInputView.setStatus(getString(R.string.voice_tap_to_speak));
                      _voiceInputView.showIdleState();
                  }
              }

              @Override
              public void onVoiceTypingError(String error) {
                  if (_voiceInputView != null) {
                      String err = String.format(getString(R.string.voice_error), error);
                      _voiceInputView.showErrorState(err);
                  }
              }

              @Override
              public void onVoiceTypingResult(String text, boolean isPartial) {
                  if (_voiceInputView != null) {
                      _voiceInputView.setTranscription(text, isPartial);
                  }
                  if (!isPartial) {
                      InputConnection ic = getCurrentInputConnection();
                      if (ic != null) {
                          String toInsert = text + " ";
                          ic.commitText(toInsert, 1);
                          _lastVoiceInsertionLength = toInsert.length();
                      }
                      if (_voiceInputView != null) {
                          _voiceInputView.setTranscription("", false);
                      }
                  }
              }

              @Override
              public void onVoiceCommand(String command) {
                  InputConnection ic = getCurrentInputConnection();
                  if (ic == null) return;

                  switch (command) {
                      case "delete_char":








                          ic.deleteSurroundingText(1, 0);
                          break;
                      case "delete_word":
                      case "delete_last_word":
                           CharSequence before = ic.getTextBeforeCursor(50, 0);
                           if (before != null && before.length() > 0) {
                               String s = before.toString().trim();
                               int lastSpace = s.lastIndexOf(' ');
                               int lengthToDelete = (lastSpace == -1) ? s.length() : (s.length() - lastSpace - 1);

                               lengthToDelete += (before.length() - s.length());




                               ic.deleteSurroundingText(lengthToDelete + 1, 0);
                           } else {
                               sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                           }
                           break;
                      case "delete_sentence":
                           CharSequence beforeSent = ic.getTextBeforeCursor(200, 0);
                            if (beforeSent != null && beforeSent.length() > 0) {
                               String s = beforeSent.toString();
                               int lastPunct = Math.max(s.lastIndexOf('.'), Math.max(s.lastIndexOf('?'), s.lastIndexOf('!')));
                               int length = (lastPunct == -1) ? s.length() : (s.length() - lastPunct - 1);
                               ic.deleteSurroundingText(length, 0);
                           }
                           break;
                      case "delete_all":


                          ic.performContextMenuAction(android.R.id.selectAll);
                          ic.performContextMenuAction(android.R.id.cut);
                          break;
                      case "new_line": ic.commitText("\n", 1); break;
                      case "space": ic.commitText(" ", 1); break;
                      case "tab": ic.commitText("\t", 1); break;
                      case "select_all": ic.performContextMenuAction(android.R.id.selectAll); break;
                      case "copy": ic.performContextMenuAction(android.R.id.copy); break;
                      case "paste": ic.performContextMenuAction(android.R.id.paste); break;
                      case "cut": ic.performContextMenuAction(android.R.id.cut); break;
                      case "undo": ic.performContextMenuAction(android.R.id.undo); break;
                      case "go_start":
                           ic.setSelection(0, 0);
                           break;
                      case "go_end":

                           ic.setSelection(Integer.MAX_VALUE, Integer.MAX_VALUE);
                           break;
                  }
              }

              @Override
              public void onRmsChanged(float rmsdB) {
                  if (_voiceInputView != null) {
                      _voiceInputView.updateAudioLevel(rmsdB);
                  }
              }

              @Override
              public void onReplaceModeResult(String text) {
                  InputConnection ic = getCurrentInputConnection();
                  if (ic != null && text != null && !text.isEmpty()) {

                      ic.commitText(text, 1);
                      if (_voiceInputView != null) {
                          _voiceInputView.setStatus(getString(R.string.voice_tap_to_speak));
                          _voiceInputView.showIdleState();
                      }
                  }
              }
          });
      }

      if (_voiceInputView == null) {
          _voiceInputView = new VoiceInputView(new ContextThemeWrapper(this, _config.theme));
          _voiceInputView.setCallback(new VoiceInputView.Callback() {
              @Override
              public void onMicClick() {
                  if (_voiceTypingManager != null) {
                      if (_voiceTypingManager.isListening()) {
                          _voiceTypingManager.stopListening();
                      } else {
                          _voiceTypingManager.startListening();
                      }
                  }
              }

              @Override
              public void onCloseClick() {
                   if (_voiceTypingManager != null) {
                       _voiceTypingManager.cancel();
                   }
                   hideVoiceOverlay();
              }

              @Override
              public void onUndoClick() {
                  _keyeventhandler.handle_editing_key(KeyValue.Editing.UNDO);
              }

              @Override
              public void onRedoClick() {
                  _keyeventhandler.handle_editing_key(KeyValue.Editing.REDO);
              }

              @Override
              public void onSettingsClick() {
                   Intent intent = new Intent(Keyboard2.this, SettingsActivity.class);
                   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                   startActivity(intent);
              }

              @Override
              public void onKeyClick(int keyCode) {
                  if (keyCode >= 0) {
                      InputConnection ic = getCurrentInputConnection();
                      if (ic != null) {
                          long now = System.currentTimeMillis();
                          ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
                          ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
                      }
                  } else {
                      switch (keyCode) {
                          case -101: _keyeventhandler.handle_editing_key(KeyValue.Editing.MOVE_WORD_BACKWARD_1); break;
                          case -102: _keyeventhandler.handle_editing_key(KeyValue.Editing.MOVE_WORD_FORWARD_1); break;
                          case -201: _keyeventhandler.handle_editing_key(KeyValue.Editing.DELETE_WORD); break;
                          case -202: _keyeventhandler.handle_editing_key(KeyValue.Editing.FORWARD_DELETE_WORD); break;
                          case -203: _keyeventhandler.handle_editing_key(KeyValue.Editing.REPLACE); break;
                          case -204: _keyeventhandler.handle_editing_key(KeyValue.Editing.ASSIST); break;
                          case -301: {
                              InputConnection ic = getCurrentInputConnection();
                              if (ic != null) {
                                  _keyeventhandler.handleMoveWord(ic, 1, false, true);
                              }
                              break;
                          }
                          case -303: {
                              InputConnection ic = getCurrentInputConnection();
                              if (ic != null) {
                                  _keyeventhandler.handleMoveWord(ic, 1, true, true);
                              }
                              break;
                          }
                          case -302: {
                              InputConnection ic = getCurrentInputConnection();
                              if (ic != null) {
                                  _keyeventhandler.handleSelectCurrentWord(ic);
                              }
                              break;
                          }
                      }
                  }
              }

              @Override
              public void onCommandButtonDown() {
                  if (_voiceTypingManager != null) {
                      _voiceTypingManager.setCommandMode(true);
                       _voiceInputView.showCommandState();
                  }
              }

              @Override
              public void onSuggestionSelected(int wordIndex, String suggestion) {
                  InputConnection ic = getCurrentInputConnection();
                  if (ic != null) {
                      ic.commitText(suggestion, 1);
                      if (_voiceInputView != null) _voiceInputView.hideSuggestions();
                  }
              }
          });
      }

      showVoiceOverlay();

      java.util.Locale locale = null;


      KeyboardData layout = current_layout();
      if (layout != null) {
          if ("urdu".equalsIgnoreCase(layout.script)) {
              locale = new java.util.Locale("ur");
          } else if ("latin".equalsIgnoreCase(layout.script)) {







          }
      }

      if (locale == null && android.os.Build.VERSION.SDK_INT >= 24) {
          InputMethodSubtype subtype = get_imm().getCurrentInputMethodSubtype();
          if (subtype != null && subtype.getLanguageTag() != null && !subtype.getLanguageTag().isEmpty()) {
              locale = java.util.Locale.forLanguageTag(subtype.getLanguageTag());
          }
      }


      if (layout != null && layout.name != null && layout.name.toLowerCase().contains("urdu") && locale == null) {
          locale = new java.util.Locale("ur");
      }

      _voiceTypingManager.startListening(locale);
  }

    public void openClipboardWithSearch(String query, boolean isTypingHistory) {
        _forceKeepClipboardPaneOnNextStart = true;
        if (_clipboard_pane == null) {
            _clipboard_pane = (ClipboardView) inflate_view(R.layout.clipboard_pane);
            _clipboard_pane.setKeyboardReceiver(this.new Receiver());
        }
        if (_keyboardView != null && _keyboardView.getHeight() > 0) {
            _clipboard_pane.setKeyboardHeight(_keyboardView.getHeight());
        }
        _clipboard_pane.showTypingHistory(isTypingHistory);
        setInputView(_clipboard_pane);
        _clipboard_pane.performSearchFromExternal(query);
    }
}
