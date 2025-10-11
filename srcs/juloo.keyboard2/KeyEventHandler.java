package juloo.keyboard2;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Looper;
import android.os.Handler;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.widget.Toast;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

public final class KeyEventHandler
  implements Config.IKeyEventHandler,
             ClipboardHistoryService.ClipboardPasteCallback
{
  IReceiver _recv;
  Autocapitalisation _autocap;
  /** State of the system modifiers. It is updated whether a modifier is down
      or up and a corresponding key event is sent. */
  Pointers.Modifiers _mods;
  /** Consistent with [_mods]. This is a mutable state rather than computed
      from [_mods] to ensure that the meta state is correct while up and down
      events are sent for the modifier keys. */
  int _meta_state = 0;
  /** Whether to force sending arrow keys to move the cursor when
      [setSelection] could be used instead. */
  boolean _move_cursor_force_fallback = false;
  boolean mSuggestionsEnabledForThisInput = false;
  private String _pending_font_size_digit = null;
  private final LayoutBasedAutoCorrectionProvider _autoCorrectionProvider;
  private final SuggestionProvider _suggestionProvider;
  private final KeyboardAwareSuggester _keyboardAwareSuggester;
  private final SentenceCorrector _sentenceCorrector;
  private String originalWord = null;
  private String correctedWord = null;
  private boolean justAutoCorrected = false;
  private final java.util.Set<String> revertedWords = new java.util.HashSet<>();

  public KeyEventHandler(IReceiver recv, SuggestionProvider suggestionProvider,
                         LayoutBasedAutoCorrectionProvider autoCorrectionProvider,
                         KeyboardAwareSuggester keyboardAwareSuggester,
                         SentenceCorrector sentenceCorrector)
  {
    _recv = recv;
    _suggestionProvider = suggestionProvider;
    _autoCorrectionProvider = autoCorrectionProvider;
    _keyboardAwareSuggester = keyboardAwareSuggester;
    _sentenceCorrector = sentenceCorrector;
    _autocap = new Autocapitalisation(recv.getHandler(),
        this.new Autocapitalisation_callback());
    _mods = Pointers.Modifiers.EMPTY;
  }

  /** Editing just started. */
  public void started(EditorInfo info)
  {
    _autocap.started(info, _recv.getCurrentInputConnection());
    _move_cursor_force_fallback = should_move_cursor_force_fallback(info);
    final int inputType = info.inputType;
    if (Config.globalConfig().enable_suggestions) {
        if (inputType == InputType.TYPE_NULL) {
            mSuggestionsEnabledForThisInput = false;
        } else {
            final int klass = inputType & InputType.TYPE_MASK_CLASS;
            if (klass == InputType.TYPE_CLASS_TEXT) {
                final int variation = inputType & InputType.TYPE_MASK_VARIATION;
                mSuggestionsEnabledForThisInput = variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
                        variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
                        variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
                        variation != InputType.TYPE_TEXT_VARIATION_FILTER &&
                        variation != InputType.TYPE_TEXT_VARIATION_URI;
            } else {
                mSuggestionsEnabledForThisInput = false;
            }
        }
        // Corresponds to EditorInfo.IME_FLAG_NO_SUGGESTIONS, which is not found in the build environment.
        final int IME_FLAG_NO_SUGGESTIONS = 0x20000000;
        if ((info.imeOptions & IME_FLAG_NO_SUGGESTIONS) != 0) {
            mSuggestionsEnabledForThisInput = false;
        }
    } else {
        mSuggestionsEnabledForThisInput = false;
    }
    _recv.showSuggestions(java.util.Collections.emptyList());
  }

  /** Selection has been updated. */
  public void selection_updated(int oldSelStart, int newSelStart)
  {
    _autocap.selection_updated(oldSelStart, newSelStart);
    if (mSuggestionsEnabledForThisInput) {
        updateSuggestionsFromPrefix();
    }
  }

  /** A key is being pressed. There will not necessarily be a corresponding
      [key_up] event. */
  @Override
  public void key_down(KeyValue key, boolean isSwipe)
  {
    if (key == null)
      return;
    // Stop auto capitalisation when pressing some keys
    switch (key.getKind())
    {
      case Modifier:
        switch (key.getModifier())
        {
          case CTRL:
          case ALT:
          case META:
            _autocap.stop();
            break;
        }
        break;
      case Compose_pending:
        _autocap.stop();
        break;
      case Slider:
        // Don't wait for the next key_up and move the cursor right away. This
        // is called after the trigger distance have been travelled.
        handle_slider(key.getSlider(), key.getSliderRepeat(), true);
        break;
      default: break;
    }
  }

  /** A key has been released. */
  @Override
  public void key_up(KeyValue key, Pointers.Modifiers mods)
  {
    if (key == null)
      return;

    if (justAutoCorrected) {
        if (key.getKind() == KeyValue.Kind.Keyevent && key.getKeyevent() == KeyEvent.KEYCODE_DEL) {
            revertAutoCorrection();
            return; // Intercept the backspace
        }
        // Any other key press commits the correction, so we reset the flag.
        justAutoCorrected = false;
    }

    Pointers.Modifiers old_mods = _mods;
    update_meta_state(mods);
    switch (key.getKind())
    {
      case Char: send_text(String.valueOf(key.getChar())); break;
      case String: send_text(key.getString()); break;
      case ModifiedChar:
        {
            char c = key.getChar();
            int metaState = key.getMetaState();
            KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            KeyEvent[] events = kcm.getEvents(new char[] { c });
            if (events != null && events.length > 0) {
                send_key_down_up(events[0].getKeyCode(), metaState);
            }
        }
        break;
      case Event:
        if (key.getEvent() == KeyValue.Event.EXPORT_DATA) {
          Toast.makeText(_recv.getContext(), "Export from keyboard settings.", Toast.LENGTH_LONG).show();
        } else {
          _recv.handle_event_key(key.getEvent());
        }
        break;
      case Keyevent: send_key_down_up(key.getKeyevent()); break;
      case Modifier: break;
      case Editing: handle_editing_key(key.getEditing()); break;
      case Compose_pending: _recv.set_compose_pending(true); break;
      case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
      case Macro: evaluate_macro(key.getMacro()); break;
    }
    update_meta_state(old_mods);
  }

  @Override
  public void mods_changed(Pointers.Modifiers mods)
  {
    update_meta_state(mods);
  }

  @Override
  public void paste_from_clipboard_pane(String content)
  {
    send_text(content);
  }

  /** Update [_mods] to be consistent with the [mods], sending key events if
      needed. */
  void update_meta_state(Pointers.Modifiers mods)
  {
    // Released modifiers
    Iterator<KeyValue> it = _mods.diff(mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), false);
    // Activated modifiers
    it = mods.diff(_mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), true);
    _mods = mods;
  }

  // private void handleDelKey(int before, int after)
  // {
  //  CharSequence selection = getCurrentInputConnection().getSelectedText(0);

  //  if (selection != null && selection.length() > 0)
  //  getCurrentInputConnection().commitText("", 1);
  //  else
  //  getCurrentInputConnection().deleteSurroundingText(before, after);
  // }

  void sendMetaKey(int eventCode, int meta_flags, boolean down)
  {
    if (down)
    {
      _meta_state = _meta_state | meta_flags;
      send_keyevent(KeyEvent.ACTION_DOWN, eventCode, _meta_state);
    }
    else
    {
      send_keyevent(KeyEvent.ACTION_UP, eventCode, _meta_state);
      _meta_state = _meta_state & ~meta_flags;
    }
  }

  void sendMetaKeyForModifier(KeyValue kv, boolean down)
  {
    switch (kv.getKind())
    {
      case Modifier:
        switch (kv.getModifier())
        {
          case CTRL:
            sendMetaKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON, down);
            break;
          case ALT:
            sendMetaKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_ON, down);
            break;
          case SHIFT:
            sendMetaKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON, down);
            break;
          case META:
            sendMetaKey(KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_ON, down);
            break;
          default:
            break;
        }
        break;
    }
  }

  void send_key_down_up(int keyCode)
  {
    send_key_down_up(keyCode, _meta_state);
  }

  /** Ignores currently pressed system modifiers. */
  void send_key_down_up(int keyCode, int metaState)
  {
    send_keyevent(KeyEvent.ACTION_DOWN, keyCode, metaState);
    send_keyevent(KeyEvent.ACTION_UP, keyCode, metaState);
  }

  void send_keyevent(int eventAction, int eventCode, int metaState)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.sendKeyEvent(new KeyEvent(1, 1, eventAction, eventCode, 0,
          metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    if (eventAction == KeyEvent.ACTION_UP)
      _autocap.event_sent(eventCode, metaState);
  }

  private void addSelectedTextToDictionary() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      CharSequence selectedText = conn.getSelectedText(0);
      if (selectedText == null || selectedText.length() == 0) {
          Toast.makeText(_recv.getContext(), "No text selected.", Toast.LENGTH_SHORT).show();
          return;
      }

      String originalText = selectedText.toString();
      String[] words = originalText.trim().split("\\s+");
      if (words.length >= 1 && words.length <= 5) {
          String newWord = originalText.trim().toLowerCase();
          if (isWordInDictionary(newWord)) {
              Toast.makeText(_recv.getContext(), "Word already in dictionary.", Toast.LENGTH_SHORT).show();
          } else {
              try (FileOutputStream fos = _recv.getContext().openFileOutput("custom.txt", Context.MODE_APPEND)) {
                  fos.write((newWord + "\n").getBytes());
                  Toast.makeText(_recv.getContext(), "Added to custom dictionary", Toast.LENGTH_SHORT).show();
                  _recv.reloadCustomDictionary();
                  new DataSyncService(_recv.getContext()).exportDictionary();
              } catch (IOException e) {
                  e.printStackTrace();
                  Toast.makeText(_recv.getContext(), "Error adding to dictionary", Toast.LENGTH_SHORT).show();
              }
          }
      } else {
          Toast.makeText(_recv.getContext(), "Select 1 to 5 words to add to the dictionary", Toast.LENGTH_SHORT).show();
      }
  }

  void send_text(CharSequence text)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;

    if (" ".equals(text.toString())) {
        handleAutoCorrectionOnSpace();
        return;
    }

    // Any other key press invalidates the revert state and commits the correction.
    justAutoCorrected = false;

    CharSequence selectedText = conn.getSelectedText(0);
    if (selectedText != null && selectedText.length() > 0) {
        String textStr = text.toString();
        String newText = null;
        String originalText = selectedText.toString();

        if ("d".equals(textStr)) {
            addSelectedTextToDictionary();
            return;
        }

        if (Config.globalConfig().case_conversion_and_formatting) {
            if (textStr.matches("\\d")) {
                if (_pending_font_size_digit == null) {
                    _pending_font_size_digit = textStr;
                    return;
                } else {
                    String sizeStr = _pending_font_size_digit + textStr;
                    _pending_font_size_digit = null;
                    int size = Integer.parseInt(sizeStr);
                    if (size >= 10 && size <= 99) {
                        SpannableString sizedText = new SpannableString(originalText);
                        sizedText.setSpan(new AbsoluteSizeSpan(size, true), 0, originalText.length(), 0);
                        conn.commitText(sizedText, 1);
                        return;
                    }
                }
            } else {
                _pending_font_size_digit = null;
            }
            switch (textStr) {
                case "b":
                    SpannableString boldText = new SpannableString(originalText);
                    boldText.setSpan(new StyleSpan(Typeface.BOLD), 0, originalText.length(), 0);
                    conn.commitText(boldText, 1);
                    return;
                case "i":
                    SpannableString italicText = new SpannableString(originalText);
                    italicText.setSpan(new StyleSpan(Typeface.ITALIC), 0, originalText.length(), 0);
                    conn.commitText(italicText, 1);
                    return;
                case "u":
                    newText = originalText.toUpperCase();
                    break;
                case "l":
                    newText = originalText.toLowerCase();
                    break;
                case "s":
                    if (originalText.length() > 0) {
                        newText = Character.toUpperCase(originalText.charAt(0)) + originalText.substring(1).toLowerCase();
                    } else {
                        newText = originalText;
                    }
                    break;
            }
        }
        if (Config.globalConfig().application_integrations) {
            switch (textStr) {
                case "t":
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, originalText);
                    intent.setType("text/plain");
                    intent.setComponent(new ComponentName("com.google.android.apps.translate", "com.google.android.apps.translate.TranslateActivity"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PackageManager pm = _recv.getContext().getPackageManager();
                    if (intent.resolveActivity(pm) != null) {
                        _recv.getContext().startActivity(intent);
                    } else {
                        Toast.makeText(_recv.getContext(), "Google Translate app not found.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                case "k":
                    Intent keepIntent = new Intent(Intent.ACTION_SEND);
                    keepIntent.putExtra(Intent.EXTRA_TEXT, originalText);
                    keepIntent.setType("text/plain");
                    keepIntent.setComponent(new ComponentName("com.google.android.keep", "com.google.android.keep.activities.ShareReceiverActivity"));
                    keepIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PackageManager keepPm = _recv.getContext().getPackageManager();
                    if (keepIntent.resolveActivity(keepPm) != null) {
                        _recv.getContext().startActivity(keepIntent);
                    } else {
                        Toast.makeText(_recv.getContext(), "Google Keep app not found.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                case "o":
                    Intent obsidianIntent = new Intent(Intent.ACTION_SEND);
                    obsidianIntent.putExtra(Intent.EXTRA_TEXT, originalText);
                    obsidianIntent.setType("text/plain");
                    obsidianIntent.setComponent(new ComponentName("md.obsidian", "md.obsidian.MainActivity"));
                    obsidianIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PackageManager obsidianPm = _recv.getContext().getPackageManager();
                    if (obsidianIntent.resolveActivity(obsidianPm) != null) {
                        _recv.getContext().startActivity(obsidianIntent);
                    } else {
                        Toast.makeText(_recv.getContext(), "Obsidian app not found.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                case "c":
                    Intent searchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(originalText)));
                    searchIntent.setComponent(new ComponentName("com.android.chrome", "com.google.android.apps.chrome.IntentDispatcher"));
                    searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PackageManager searchPm = _recv.getContext().getPackageManager();
                    if (searchIntent.resolveActivity(searchPm) != null) {
                        _recv.getContext().startActivity(searchIntent);
                    } else {
                        Toast.makeText(_recv.getContext(), "Google Chrome app not found.", Toast.LENGTH_SHORT).show();
                    }
                    return;
            }
        }

        if (newText != null) {
            conn.commitText(newText, 1);
            ExtractedText et = get_cursor_pos(conn);
            if (et != null) {
                conn.setSelection(et.selectionStart - newText.length(), et.selectionStart);
            }
            return;
        }

        String open = null;
        String close = null;
        if (Config.globalConfig().encapsulation) {
            switch (textStr) {
                case "{": open = "{"; close = "}"; break;
                case "}": open = "{"; close = "}"; break;
                case "[": open = "["; close = "]"; break;
                case "]": open = "["; close = "]"; break;
                case "(": open = "("; close = ")"; break;
                case ")": open = "("; close = ")"; break;
                case "<": open = "<"; close = ">"; break;
                case ">": open = "<"; close = ">"; break;
                case "\"": open = "\""; close = "\""; break;
                case "'": open = "'"; close = "'"; break;
                case "/": open = "/"; close = "/"; break;
                case "\\": open = "\\"; close = "\\"; break;
            }
            if (open != null) {
                conn.commitText(open + selectedText + close, 1);
                // Re-select the original text
                ExtractedText et = get_cursor_pos(conn);
                if (et != null) {
                    conn.setSelection(et.selectionStart - close.length() - selectedText.length(), et.selectionStart - close.length());
                }
                return;
            }
        }
    }

    conn.commitText(text, 1);
    _autocap.typed(text);
    if (mSuggestionsEnabledForThisInput) {
        updateSuggestionsFromPrefix();
    }
  }

  private void handleAutoCorrectionOnSpace() {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null) {
        sendTextVerbatim(" ");
        return;
    }

    CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
    if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
        sendTextVerbatim(" ");
        return;
    }

    int i = textBeforeCursor.length();
    while (i > 0 && Character.isLetter(textBeforeCursor.charAt(i - 1))) {
        i--;
    }
    String word = textBeforeCursor.subSequence(i, textBeforeCursor.length()).toString();
    String lowerCaseWord = word.toLowerCase();

    if (word.isEmpty()) {
        sendTextVerbatim(" ");
        return;
    }

    if (word.length() > 15 && !word.contains(" ")) {
        String segmentedText = _sentenceCorrector.segment(lowerCaseWord);
        if (segmentedText != null && !segmentedText.isEmpty()) {
            conn.deleteSurroundingText(word.length(), 0);
            conn.commitText(segmentedText, 1);
            conn.commitText(" ", 1);
            return;
        }
    }

    if (revertedWords.contains(lowerCaseWord)) {
        revertedWords.remove(lowerCaseWord); // Consume the revert flag for this word
        sendTextVerbatim(" ");
        return;
    }

    if (!revertedWords.isEmpty()) {
        revertedWords.clear();
    }

    if (_suggestionProvider.isValidWord(lowerCaseWord)) {
        sendTextVerbatim(" ");
        return;
    }

    java.util.List<String> corrections = _autoCorrectionProvider.getCorrections(lowerCaseWord);
    java.util.List<String> keyboardSuggestions = _keyboardAwareSuggester.suggest(lowerCaseWord);

    java.util.Set<String> combinedSuggestionsSet = new java.util.LinkedHashSet<>();
    combinedSuggestionsSet.addAll(corrections);
    combinedSuggestionsSet.addAll(keyboardSuggestions);

    if (!combinedSuggestionsSet.isEmpty()) {
        java.util.List<String> combinedSuggestions = new java.util.ArrayList<>(combinedSuggestionsSet);
        String bestCorrection = combinedSuggestions.get(0);

        conn.beginBatchEdit();
        conn.deleteSurroundingText(word.length(), 0);
        conn.commitText(bestCorrection, 1);
        conn.commitText(" ", 1);
        conn.endBatchEdit();

        originalWord = word;
        correctedWord = bestCorrection;
        justAutoCorrected = true;
        _autocap.typed(" ");
        _recv.showSuggestions(combinedSuggestions);
    } else {
        sendTextVerbatim(" ");
    }
  }

  // Helper to send text without triggering correction logic, used for committing a simple space.
  private void sendTextVerbatim(CharSequence text) {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      conn.commitText(text, 1);
      _autocap.typed(text);
      if (mSuggestionsEnabledForThisInput) {
          _recv.showSuggestions(java.util.Collections.emptyList());
      }
  }

  /** See {!InputConnection.performContextMenuAction}. */
  void send_context_menu_action(int id)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    conn.performContextMenuAction(id);
  }

  @SuppressLint("InlinedApi")
  void handle_editing_key(KeyValue.Editing ev)
  {
    switch (ev)
    {
      case COPY: if(is_selection_not_empty()) send_context_menu_action(android.R.id.copy); break;
      case PASTE: send_context_menu_action(android.R.id.paste); break;
      case CUT: if(is_selection_not_empty()) send_context_menu_action(android.R.id.cut); break;
      case SELECT_ALL: send_context_menu_action(android.R.id.selectAll); break;
      case SHARE: send_context_menu_action(android.R.id.shareText); break;
      case PASTE_PLAIN: send_context_menu_action(android.R.id.pasteAsPlainText); break;
      case UNDO: send_context_menu_action(android.R.id.undo); break;
      case REDO: send_context_menu_action(android.R.id.redo); break;
      case REPLACE: send_context_menu_action(android.R.id.replaceText); break;
      case ASSIST: send_context_menu_action(android.R.id.textAssist); break;
      case AUTOFILL: send_context_menu_action(android.R.id.autofill); break;
      case DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case FORWARD_DELETE_WORD: send_key_down_up(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); break;
      case SELECTION_CANCEL: cancel_selection(); break;
      case ADD_TO_DICTIONARY: addSelectedTextToDictionary(); break;
      case MOVE_WORD_BACKWARD_1: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 1, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_FORWARD_1: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, 1, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_BACKWARD_2: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 2, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_FORWARD_2: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, 2, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_BACKWARD_3: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 3, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_FORWARD_3: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, 3, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_BACKWARD_4: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 4, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_FORWARD_4: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, 4, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_BACKWARD_5: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, 5, KeyEvent.META_CTRL_ON); break;
      case MOVE_WORD_FORWARD_5: send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, 5, KeyEvent.META_CTRL_ON); break;
    }
  }

  static ExtractedTextRequest _move_cursor_req = null;

  /** Query the cursor position. The extracted text is empty. Returns [null] if
      the editor doesn't support this operation. */
  ExtractedText get_cursor_pos(InputConnection conn)
  {
    if (_move_cursor_req == null)
    {
      _move_cursor_req = new ExtractedTextRequest();
      _move_cursor_req.hintMaxChars = 0;
    }
    return conn.getExtractedText(_move_cursor_req, 0);
  }

  /** [r] might be negative, in which case the direction is reversed. */
  void handle_slider(KeyValue.Slider s, int r, boolean key_down)
  {
    switch (s)
    {
      case Cursor_left: move_cursor(-r); break;
      case Cursor_right: move_cursor(r); break;
      case Cursor_up: move_cursor_vertical(-r); break;
      case Cursor_down: move_cursor_vertical(r); break;
      case Selection_cursor_left: move_cursor_sel(r, true, key_down); break;
      case Selection_cursor_right: move_cursor_sel(r, false, key_down); break;
    }
  }

  /** Move the cursor right or left, if possible without sending key events.
      Unlike arrow keys, the selection is not removed even if shift is not on.
      Falls back to sending arrow keys events if the editor do not support
      moving the cursor or a modifier other than shift is pressed. */
  void move_cursor(int d)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Continue expanding the selection even if shift is not pressed
      if (sel_end != sel_start)
      {
        sel_end += d;
        if (sel_end == sel_start) // Avoid making the selection empty
          sel_end += d;
      }
      else
      {
        sel_end += d;
        // Leave 'sel_start' where it is if shift is pressed
        if ((_meta_state & KeyEvent.META_SHIFT_ON) == 0)
          sel_start = sel_end;
      }
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Move one of the two side of a selection. If [sel_left] is true, the left
      position is moved, otherwise the right position is moved. */
  void move_cursor_sel(int d, boolean sel_left, boolean key_down)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et != null && can_set_selection(conn))
    {
      int sel_start = et.selectionStart;
      int sel_end = et.selectionEnd;
      // Reorder the selection when the slider has just been pressed. The
      // selection might have been reversed if one end crossed the other end
      // with a previous slider.
      if (key_down && sel_start > sel_end)
      {
        sel_start = et.selectionEnd;
        sel_end = et.selectionStart;
      }
      do
      {
        if (sel_left)
          sel_start += d;
        else
          sel_end += d;
        // Move the cursor twice if moving it once would make the selection
        // empty and stop selection mode.
      } while (sel_start == sel_end);
      if (conn.setSelection(sel_start, sel_end))
        return; // Fallback to sending key events if [setSelection] failed
    }
    move_cursor_fallback(d);
  }

  /** Returns whether the selection can be set using [conn.setSelection()].
      This can happen on Termux or when system modifiers are activated for
      example. */
  boolean can_set_selection(InputConnection conn)
  {
    final int system_mods =
      KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;
    return !_move_cursor_force_fallback && (_meta_state & system_mods) == 0;
  }

  void move_cursor_fallback(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_LEFT, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_RIGHT, d);
  }

  /** Move the cursor up and down. This sends UP and DOWN key events that might
      make the focus exit the text box. */
  void move_cursor_vertical(int d)
  {
    if (d < 0)
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_UP, -d);
    else
      send_key_down_up_repeat(KeyEvent.KEYCODE_DPAD_DOWN, d);
  }

  void evaluate_macro(KeyValue[] keys)
  {
    if (keys.length == 0)
      return;
    // Ignore modifiers that are activated at the time the macro is evaluated
    mods_changed(Pointers.Modifiers.EMPTY);
    evaluate_macro_loop(keys, 0, Pointers.Modifiers.EMPTY, _autocap.pause());
  }

  /** Evaluate the macro asynchronously to make sure event are processed in the
      right order. */
  void evaluate_macro_loop(final KeyValue[] keys, int i, Pointers.Modifiers mods, final boolean autocap_paused)
  {
    boolean should_delay = false;
    KeyValue kv = KeyModifier.modify(keys[i], mods);
    if (kv != null)
    {
      if (kv.hasFlagsAny(KeyValue.FLAG_LATCH))
      {
        // Non-special latchable keys clear latched modifiers
        if (!kv.hasFlagsAny(KeyValue.FLAG_SPECIAL))
          mods = Pointers.Modifiers.EMPTY;
        mods = mods.with_extra_mod(kv);
      }
      else
      {
        key_down(kv, false);
        key_up(kv, mods);
        mods = Pointers.Modifiers.EMPTY;
      }
      should_delay = wait_after_macro_key(kv);
    }
    i++;
    if (i >= keys.length) // Stop looping
    {
      _autocap.unpause(autocap_paused);
    }
    else if (should_delay)
    {
      // Add a delay before sending the next key to avoid race conditions
      // causing keys to be handled in the wrong order. Notably, KeyEvent keys
      // handling is scheduled differently than the other edit functions.
      final int i_ = i;
      final Pointers.Modifiers mods_ = mods;
      _recv.getHandler().postDelayed(new Runnable() {
        public void run()
        {
          evaluate_macro_loop(keys, i_, mods_, autocap_paused);
        }
      }, 1000/30);
    }
    else
      evaluate_macro_loop(keys, i, mods, autocap_paused);
  }

  boolean wait_after_macro_key(KeyValue kv)
  {
    switch (kv.getKind())
    {
      case Keyevent:
      case Editing:
      case Event:
        return true;
      case Slider:
        return _move_cursor_force_fallback;
      default:
        return false;
    }
  }

  /** Repeat calls to [send_key_down_up]. */
  void send_key_down_up_repeat(int event_code, int repeat)
  {
    while (repeat-- > 0)
      send_key_down_up(event_code);
  }

  /** Repeat calls to [send_key_down_up] with a specific meta state. */
  void send_key_down_up_repeat(int event_code, int repeat, int metaState)
  {
    while (repeat-- > 0)
      send_key_down_up(event_code, metaState);
  }

  void cancel_selection()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;
    ExtractedText et = get_cursor_pos(conn);
    if (et == null) return;
    final int curs = et.selectionStart;
    // Notify the receiver as Android's [onUpdateSelection] is not triggered.
    if (conn.setSelection(curs, curs));
      _recv.selection_state_changed(false);
  }

  boolean is_selection_not_empty()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null) return false;
    return (conn.getSelectedText(0) != null);
  }

  /** Workaround some apps which answers to [getExtractedText] but do not react
      to [setSelection] while returning [true]. */
  boolean should_move_cursor_force_fallback(EditorInfo info)
  {
    // This catch Acode: which sets several variations at once.
    if ((info.inputType & InputType.TYPE_MASK_VARIATION & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0)
      return true;
    // Godot editor: Doesn't handle setSelection() but returns true.
    return info.packageName.startsWith("org.godotengine.editor");
  }

  public void updateSuggestionsFromPrefix() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      ExtractedText et = get_cursor_pos(conn);
      if (et != null && et.selectionStart != et.selectionEnd) {
          _recv.showSuggestions(java.util.Collections.emptyList());
          return;
      }

      CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
      if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
          _recv.showSuggestions(java.util.Collections.emptyList());
          return;
      }

      int i = textBeforeCursor.length();
      while (i > 0 && Character.isLetter(textBeforeCursor.charAt(i - 1))) {
          i--;
      }

      String prefix = textBeforeCursor.subSequence(i, textBeforeCursor.length()).toString();

      if (prefix.isEmpty() || (i > 0 && !Character.isWhitespace(textBeforeCursor.charAt(i - 1)) && textBeforeCursor.charAt(i - 1) != '\n')) {
          _recv.showSuggestions(java.util.Collections.emptyList());
      } else {
          // Show prefix-based completions as the user types.
          // Auto-correction is handled separately when the spacebar is pressed.
          java.util.List<String> suggestions = _suggestionProvider.getSuggestions(prefix.toLowerCase());
          _recv.showSuggestions(suggestions);
      }
  }

  private boolean isWordInDictionary(String word) {
      File customDictFile = new File(_recv.getContext().getFilesDir(), "custom.txt");
      if (!customDictFile.exists()) {
          return false;
      }
      try (BufferedReader reader = new BufferedReader(new FileReader(customDictFile))) {
          String line;
          while ((line = reader.readLine()) != null) {
              if (line.trim().equalsIgnoreCase(word)) {
                  return true;
              }
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
      return false;
  }

  public void replaceCurrentWord(String suggestion) {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      conn.beginBatchEdit();
      if (justAutoCorrected && correctedWord != null) {
          // An auto-correction just happened. We need to replace the auto-corrected word.
          conn.deleteSurroundingText(correctedWord.length() + 1, 0); // +1 for the space
      } else {
          // Standard suggestion replacement logic.
          CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
          if (textBeforeCursor != null) {
              int i = textBeforeCursor.length();
              while (i > 0 && Character.isLetter(textBeforeCursor.charAt(i - 1))) {
                  i--;
              }
              int wordLength = textBeforeCursor.length() - i;
              if (wordLength > 0) {
                  conn.deleteSurroundingText(wordLength, 0);
              }
          }
      }

      conn.commitText(suggestion + " ", 1);
      conn.endBatchEdit();

      _autocap.typed(" ");
      _recv.showSuggestions(java.util.Collections.emptyList()); // Clear suggestions after selection

      // Reset the correction state
      justAutoCorrected = false;
      originalWord = null;
      correctedWord = null;
  }

  private void revertAutoCorrection() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null || correctedWord == null || originalWord == null) {
          return;
      }

      conn.beginBatchEdit();
      // Delete the corrected word and the space that was added after it.
      conn.deleteSurroundingText(correctedWord.length() + 1, 0);
      conn.commitText(originalWord, 1);
      conn.endBatchEdit();

      // Prevent this word from being auto-corrected again in this session.
      revertedWords.add(originalWord);

      // Reset state
      justAutoCorrected = false;
      originalWord = null;
      correctedWord = null;

      // Update suggestions for the reverted word
      updateSuggestionsFromPrefix();
  }

  public static interface IReceiver
  {
    public void handle_event_key(KeyValue.Event ev);
    public void set_shift_state(boolean state, boolean lock);
    public void set_compose_pending(boolean pending);
    public void selection_state_changed(boolean selection_is_ongoing);
  void showSuggestions(java.util.List<String> suggestions);
    void reloadCustomDictionary();
    public InputConnection getCurrentInputConnection();
    public Handler getHandler();
    public android.content.Context getContext();
  }

  class Autocapitalisation_callback implements Autocapitalisation.Callback
  {
    @Override
    public void update_shift_state(boolean should_enable, boolean should_disable)
    {
      if (should_enable)
        _recv.set_shift_state(true, false);
      else if (should_disable)
        _recv.set_shift_state(false, false);
    }
  }
}