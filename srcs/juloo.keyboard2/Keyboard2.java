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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.view.animation.AnimationUtils;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import juloo.keyboard2.prefs.LayoutsPreference;
import juloo.keyboard2.SuggestionProvider;

public class Keyboard2 extends InputMethodService
  implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private View _inputView;
  private Keyboard2View _keyboardView;
  private KeyEventHandler _keyeventhandler;
  private SuggestionProvider _suggestionProvider;
  private LayoutBasedAutoCorrectionProvider _autoCorrectionProvider;
  private KeyboardAwareSuggester _keyboardAwareSuggester;
  private SentenceCorrector _sentenceCorrector;
  private HorizontalScrollView _suggestionStripScroll;
  private View _suggestionStrip;
  private GridLayout _suggestionsGrid;
  private FrameLayout _suggestionStripContainerTop;
  private FrameLayout _suggestionStripContainerBottom;
  private ViewFlipper _tutorialFlipper;
  private TextView _ziaistanOfficialText;
  private String[] _tutorials;
  private float _lastX;
  private final Handler _tutorialHandler = new Handler();
  private Runnable _tutorialRunnable;
  private final Random _random = new Random();
  private static final int TUTORIAL_TRANSITION_DELAY = 500000; // 500 seconds
  /** If not 'null', the layout to use instead of [_config.current_layout]. */
  private KeyboardData _currentSpecialLayout;
  /** Layout associated with the currently selected locale. Not 'null'. */
  private KeyboardData _localeTextLayout;
  private ViewGroup _emojiPane = null;
  private ClipboardView _clipboard_pane = null;
  public int actionId; // Action performed by the Action key.
  private Handler _handler;

  private Config _config;

  private FoldStateTracker _foldStateTracker;

  /** Layout currently visible before it has been modified. */
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

  /** Layout currently visible. */
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
    if (_autoCorrectionProvider != null) {
        _autoCorrectionProvider.updateLayout(newLayout);
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

  /** Load a layout that contains a numpad. */
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
          }
      }
  };

  @Override
  public void onCreate()
  {
    super.onCreate();
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _handler = new Handler(getMainLooper());
    _suggestionProvider = new SuggestionProvider(this);
    _autoCorrectionProvider = new LayoutBasedAutoCorrectionProvider(_suggestionProvider);
    _keyboardAwareSuggester = new KeyboardAwareSuggester(this, _suggestionProvider);
    _sentenceCorrector = new SentenceCorrector(_suggestionProvider, _keyboardAwareSuggester);
    _keyeventhandler = new KeyEventHandler(this.new Receiver(), _suggestionProvider, _autoCorrectionProvider, _keyboardAwareSuggester, _sentenceCorrector);
    _foldStateTracker = new FoldStateTracker(this);
    Config.initGlobalConfig(prefs, getResources(), _keyeventhandler, _foldStateTracker.isUnfolded());
    prefs.registerOnSharedPreferenceChangeListener(this);
    _config = Config.globalConfig();
    _tutorials = getResources().getStringArray(R.array.tutorials);
    _inputView = inflate_view(R.layout.keyboard);
    _keyboardView = _inputView.findViewById(R.id.keyboard_view);
    setupSuggestionStrip();
    _keyboardView.reset();
    Logs.set_debug_logs(getResources().getBoolean(R.bool.debug_logs));
    ClipboardHistoryService.on_startup(this, _keyeventhandler);
    _foldStateTracker.setChangedCallback(() -> { refresh_config(); });

    IntentFilter filter = new IntentFilter(CustomDictionarySettingsActivity.RELOAD_CUSTOM_DICTIONARY_ACTION);
    registerReceiver(mDictionaryReloadReceiver, filter);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
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
    // Android might return a random subtype, for example, the first in the
    // list alphabetically.
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
    // First try to look at 'info.actionLabel', if it isn't set, look at
    // 'imeOptions'.
    if (info.actionLabel != null)
    {
      _config.actionLabel = info.actionLabel.toString();
      actionId = info.actionId;
      _config.swapEnterActionKey = false;
    }
    else
    {
      int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
      _config.actionLabel = actionLabel_of_imeAction(action); // Might be null
      actionId = action;
      _config.swapEnterActionKey =
        (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;
    }
  }

  /** Might re-create the keyboard view. [_keyboardView.setKeyboard()] and
      [setInputView()] must be called soon after. */
  private void refresh_config()
  {
    int prev_theme = _config.theme;
    _config.refresh(getResources(), _foldStateTracker.isUnfolded());
    refreshSubtypeImm();
    // Refreshing the theme config requires re-creating the views
    if (prev_theme != _config.theme)
    {
      _inputView = inflate_view(R.layout.keyboard);
      _keyboardView = _inputView.findViewById(R.id.keyboard_view);
      setupSuggestionStrip();
      _emojiPane = null;
      _clipboard_pane = null;
      setInputView(_inputView);
    }
    _keyboardView.reset();
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
  public void onStartInputView(EditorInfo info, boolean restarting)
  {
    refresh_config();
    refresh_action_label(info);
    _currentSpecialLayout = refresh_special_layout(info);
    _keyboardView.setKeyboard(current_layout());
    _keyeventhandler.started(info);
    setInputView(_inputView);
    updateSuggestionsFromPrefix(""); // Ensure tutorials are shown on start
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
    // On API >= 35, Keyboard2View behaves as edge-to-edge
    // APIs 30 to 34 have visual artifact when edge-to-edge is enabled
    if (VERSION.SDK_INT >= 35)
    {
      WindowManager.LayoutParams wattrs = window.getAttributes();
      wattrs.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      // Allow to draw behind system bars
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
    _keyboardView.setKeyboard(current_layout());
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd)
  {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    _keyeventhandler.selection_updated(oldSelStart, newSelStart);
    if ((oldSelStart == oldSelEnd) != (newSelStart == newSelEnd))
      _keyboardView.set_selection_state(newSelStart != newSelEnd);
  }

  @Override
  public void onFinishInputView(boolean finishingInput)
  {
    super.onFinishInputView(finishingInput);
    _tutorialHandler.removeCallbacks(_tutorialRunnable); // Stop timer on finish
    if (_suggestionStrip != null) {
      _suggestionStrip.setVisibility(View.GONE);
    }
    if (_keyboardView != null) {
      _keyboardView.reset();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_BACK && _clipboard_pane != null && _clipboard_pane.isShown()) {
          _keyeventhandler.key_up(KeyValue.getSpecialKeyByName("switch_back_clipboard"), Pointers.Modifiers.EMPTY);
          return true; // Consume the event
      }
      return super.onKeyDown(keyCode, event);
  }

  private void setupSuggestionStrip() {
    _suggestionStripScroll = _inputView.findViewById(R.id.suggestions_strip_scroll);
    _suggestionStrip = _inputView.findViewById(R.id.suggestions_strip);
    _suggestionsGrid = _inputView.findViewById(R.id.suggestions_grid);
    _suggestionStripContainerTop = _inputView.findViewById(R.id.suggestion_strip_container_top);
    _suggestionStripContainerBottom = _inputView.findViewById(R.id.suggestion_strip_container_bottom);
    _tutorialFlipper = _inputView.findViewById(R.id.tutorial_flipper);
    _ziaistanOfficialText = _inputView.findViewById(R.id.ziaistan_official_text);

    // Populate the ViewFlipper with TextViews for each tutorial
    Context themedContext = new ContextThemeWrapper(this, _config.theme);
    LayoutInflater inflater = LayoutInflater.from(themedContext);
    for (String tutorial : _tutorials) {
        TextView textView = (TextView) inflater.inflate(R.layout.suggestion_item, _tutorialFlipper, false);
        textView.setText(tutorial);
        textView.setTextSize(12); // Smaller text size for tips
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

    // Set up swipe gesture listener
    _tutorialFlipper.setOnTouchListener((v, event) -> {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                _lastX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                _tutorialHandler.removeCallbacks(_tutorialRunnable); // Reset timer on manual interaction
                float currentX = event.getX();
                if (_lastX < currentX) { // Swiped right
                    _tutorialFlipper.setInAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_in_left));
                    _tutorialFlipper.setOutAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_out_right));
                    _tutorialFlipper.showPrevious();
                }
                if (_lastX > currentX) { // Swiped left
                    _tutorialFlipper.setInAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_in_right));
                    _tutorialFlipper.setOutAnimation(AnimationUtils.loadAnimation(Keyboard2.this, R.anim.slide_out_left));
                    _tutorialFlipper.showNext();
                }
                _tutorialHandler.postDelayed(_tutorialRunnable, TUTORIAL_TRANSITION_DELAY); // Restart timer
                break;
        }
        return false;
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

    // Detach from parent
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

  @Override
  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key)
  {
    refresh_config();
    updateSuggestionStripPosition();
    _keyboardView.setKeyboard(current_layout());
  }

  @Override
  public boolean onEvaluateFullscreenMode()
  {
    /* Entirely disable fullscreen mode. */
    return false;
  }

  private void populateSuggestions(List<String> suggestions) {
    if (_suggestionStrip == null || _suggestionsGrid == null) {
        return;
    }

    _suggestionStrip.setVisibility(View.VISIBLE);
    _ziaistanOfficialText.setVisibility(View.VISIBLE);
    _suggestionsGrid.removeAllViews();

    if (suggestions.isEmpty()) {
        // Show tutorial view if no suggestions are provided
        _suggestionStripScroll.setVisibility(View.GONE);
        _tutorialFlipper.setVisibility(View.VISIBLE);
        _tutorialHandler.removeCallbacks(_tutorialRunnable); // Ensure no duplicates
        _tutorialHandler.postDelayed(_tutorialRunnable, TUTORIAL_TRANSITION_DELAY);
    } else {
        // Show suggestions view
        _tutorialHandler.removeCallbacks(_tutorialRunnable); // Stop the timer
        _tutorialFlipper.setVisibility(View.GONE);
        _suggestionStripScroll.setVisibility(View.VISIBLE);

        Context themedContext = new ContextThemeWrapper(this, _config.theme);
        LayoutInflater inflater = LayoutInflater.from(themedContext);
        View.OnClickListener suggestionClickListener = v -> {
            TextView tv = (TextView) v;
            String suggestion = tv.getText().toString();
            if (suggestion.length() > 0) {
                _keyeventhandler.replaceCurrentWord(suggestion);
            }
        };

        for (String suggestion : suggestions) {
            TextView suggestionView = (TextView) inflater.inflate(R.layout.suggestion_item, _suggestionsGrid, false);
            suggestionView.setText(suggestion);
            suggestionView.setOnClickListener(suggestionClickListener);
            _suggestionsGrid.addView(suggestionView);
        }
    }
  }

  private void updateSuggestionsFromPrefix(String prefix) {
    if (_suggestionProvider == null) {
        populateSuggestions(new ArrayList<>());
        return;
    }

    final List<String> suggestions;
    if (prefix != null && !prefix.isEmpty()) {
        suggestions = _suggestionProvider.getSuggestions(prefix);
    } else {
        suggestions = new ArrayList<>();
    }
    populateSuggestions(suggestions);
  }

  private void showSuggestions(List<String> suggestions) {
      populateSuggestions(suggestions);
  }

  /** Not static */
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
          _keyboardView.setKeyboard(current_layout());
          break;

        case SWITCH_NUMERIC:
          setSpecialLayout(loadNumpad(R.xml.numeric));
          break;

        case SWITCH_EMOJI:
          if (_emojiPane == null)
            _emojiPane = (ViewGroup)inflate_view(R.layout.emoji_pane);
          setInputView(_emojiPane);
          break;

        case SWITCH_CLIPBOARD:
          if (_clipboard_pane == null) {
            _clipboard_pane = (ClipboardView) inflate_view(R.layout.clipboard_pane);
          }
          // Ensure the clipboard pane has the same height as the keyboard view
          ViewGroup.LayoutParams lp = _clipboard_pane.getLayoutParams();
          if (lp == null) {
              lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, _keyboardView.getHeight());
          } else {
              lp.height = _keyboardView.getHeight();
          }
          _clipboard_pane.setLayoutParams(lp);
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
          if (!VoiceImeSwitcher.switch_to_voice_ime(Keyboard2.this, get_imm(),
                Config.globalPrefs()))
            _config.shouldOfferVoiceTyping = false;
          break;

        case SWITCH_VOICE_TYPING_CHOOSER:
          VoiceImeSwitcher.choose_voice_ime(Keyboard2.this, get_imm(),
              Config.globalPrefs());
          break;

        case CYCLE_THEME: {
          SharedPreferences prefs = Config.globalPrefs();
          String currentTheme = prefs.getString("theme", "galactic");
          List<String> themeCycle = Arrays.asList(
              "galactic", "goldenpearl", "neonpunk",
              "everforestlight", "cobalt", "epaper"
          );
          int currentIndex = themeCycle.indexOf(currentTheme);
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
    public void showSuggestions(List<String> suggestions) {
        Keyboard2.this.showSuggestions(suggestions);
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

    public Handler getHandler()
    {
      return _handler;
    }

    public android.content.Context getContext() {
        return Keyboard2.this;
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
}
