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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Toast;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.content.ClipboardManager;
import android.content.ClipData;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class KeyEventHandler
  implements Config.IKeyEventHandler,
             ClipboardHistoryService.ClipboardPasteCallback
{
  IReceiver _recv;
  Autocapitalisation _autocap;
  UndoRedoManager _undoRedoManager;
  SoundManager _soundManager;

  Pointers.Modifiers _mods;

  int _meta_state = 0;

  boolean _move_cursor_force_fallback = false;
  boolean mSuggestionsEnabledForThisInput = false;
  private String _pending_font_size_digit = null;
  private final LayoutBasedAutoCorrectionProvider _autoCorrectionProvider;
  private final SuggestionProvider _suggestionProvider;
  private final KeyboardAwareSuggester _keyboardAwareSuggester;
  private String originalWord = null;
  private String correctedWord = null;
  private boolean justAutoCorrected = false;
  private int expectedCursorPos = -1;
  private final java.util.Set<String> revertedWords = new java.util.HashSet<>();
  private final List<String> contextHistory = new ArrayList<>();
  private long lastSpaceTime = 0;
  private StringBuilder renameBuffer = null;
  private boolean isRenaming = false;
  private int lastPastedLength = 0;
  private InputInterceptor mInterceptor;
  private final AtomicLong suggestionTaskId = new AtomicLong(0);
  private volatile SuggestionTask currentSuggestionTask = null;

  private static class SuggestionTask implements CancellationSignal {
      private final long id;
      private final String prefix;
      private final String context;
      private final String fullContext;
      private final boolean charBeforeMatch;
      private volatile boolean cancelled = false;

      SuggestionTask(long id, String prefix, String context, String fullContext, boolean charBeforeMatch) {
          this.id = id;
          this.prefix = prefix;
          this.context = context;
          this.fullContext = fullContext;
          this.charBeforeMatch = charBeforeMatch;
      }

      void cancel() {
          cancelled = true;
      }

      @Override
      public boolean isCancelled() {
          return cancelled;
      }
  }

  private SuggestionProvider.SuggestionMode currentSuggestionMode = SuggestionProvider.SuggestionMode.NONE;
  private String lastPrefix = "";
  private String lastContext = "";
  private String lastWordForCorrection = "";
  private String lastPhraseTrigger = "";

  private static class SuggestionCache {
      String context;
      List<SuggestionProvider.Suggestion> suggestions;
      SuggestionProvider.SuggestionMode mode;
  }
  private final SuggestionCache _suggestionCache = new SuggestionCache();

  public interface InputInterceptor {
      boolean onInput(KeyValue key, Pointers.Modifiers mods);
  }

  public void setInterceptor(InputInterceptor interceptor) {
      this.mInterceptor = interceptor;
  }

  private final android.content.BroadcastReceiver autofillReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String text = intent.getStringExtra("text");
            final String username = intent.getStringExtra("username");
            final String password = intent.getStringExtra("password");

            if (text != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    robustPaste(text, true);
                }, 500);
            } else if (username != null && password != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    robustPaste(username, false);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        robustKey(KeyEvent.KEYCODE_TAB);
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            robustPaste(password, true);
                        }, 600);
                    }, 600);
                }, 500);
            }
        }

        private void robustPaste(String content, boolean clearSuggestions) {
            InputConnection ic = _recv.getCurrentInputConnection();
            if (ic == null) {
                if (_recv.getContext() instanceof Keyboard2) {
                    ((Keyboard2) _recv.getContext()).setPendingCommitText(content);
                }
            } else {
                sendTextVerbatim(content, clearSuggestions);
            }
        }

        private void robustKey(int keyCode) {
            InputConnection ic = _recv.getCurrentInputConnection();
            if (ic == null) {
                if (_recv.getContext() instanceof Keyboard2) {
                    ((Keyboard2) _recv.getContext()).setPendingKeyEvent(keyCode);
                }
            } else {
                send_key_down_up(keyCode);
            }
        }
  };

  public KeyEventHandler(IReceiver recv, SuggestionProvider suggestionProvider,
                         LayoutBasedAutoCorrectionProvider autoCorrectionProvider,
                         KeyboardAwareSuggester keyboardAwareSuggester)
  {
    _recv = recv;
    _suggestionProvider = suggestionProvider;
    _autoCorrectionProvider = autoCorrectionProvider;
    _keyboardAwareSuggester = keyboardAwareSuggester;
    _undoRedoManager = new UndoRedoManager();
    _autocap = new Autocapitalisation(recv.getHandler(),
        this.new Autocapitalisation_callback());
    _mods = Pointers.Modifiers.EMPTY;
    _soundManager = new SoundManager();
    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(recv.getContext())
            .registerReceiver(autofillReceiver, new android.content.IntentFilter("juloo.keyboard2.AUTOFILL_RESULT"));
  }


  public void finished() {
    finished(_recv.getCurrentInputConnection());
  }

  public void finished(InputConnection ic) {
    clearGhostText(ic);
  }

  public void started(EditorInfo info)
  {
    clearGhostText(null);
    _undoRedoManager.clear();
    contextHistory.clear();
    InputConnection ic = _recv.getCurrentInputConnection();
    if (ic != null) {
        _autocap.started(info, ic);
    }
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

        final int IME_FLAG_NO_SUGGESTIONS = 0x20000000;
        if ((info.imeOptions & IME_FLAG_NO_SUGGESTIONS) != 0) {
            mSuggestionsEnabledForThisInput = false;
        }
    } else {
        mSuggestionsEnabledForThisInput = false;
    }
    _recv.showSuggestions(java.util.Collections.emptyList(), SuggestionProvider.SuggestionMode.NONE);

    if (mSuggestionsEnabledForThisInput) {
        refreshFieldWords();
    }
  }

  private void refreshFieldWords() {
      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic == null) return;
      CharSequence before = ic.getTextBeforeCursor(1000, 0);
      CharSequence after = ic.getTextAfterCursor(1000, 0);
      String text = (before != null ? before.toString() : "") + (after != null ? after.toString() : "");
      _suggestionProvider.updateFieldWords(text, lastPrefix);
  }


  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    _autocap.selection_updated(oldSelStart, newSelStart);


    if (newSelStart != newSelEnd) {
        justAutoCorrected = false;
        expectedCursorPos = -1;
    } else if (justAutoCorrected) {


        if (expectedCursorPos != -1 && newSelStart != expectedCursorPos) {
            justAutoCorrected = false;
            expectedCursorPos = -1;
        }





    } else {
        expectedCursorPos = -1;
    }

    if (mSuggestionsEnabledForThisInput) {
        updateSuggestionsFromPrefix();
    }
  }


  @Override
  public void key_down(KeyValue key, boolean isSwipe)
  {
    if (key == null)
      return;

    if (mInterceptor != null) return;


    if (Config.globalConfig().sound_on_keypress) {
        boolean playSound = true;
        if (key.getKind() == KeyValue.Kind.Keyevent) {
             int code = key.getKeyevent();
             if (code == KeyEvent.KEYCODE_SPACE && !Config.globalConfig().sound_on_space) playSound = false;
             else if (code == KeyEvent.KEYCODE_DEL && !Config.globalConfig().sound_on_delete) playSound = false;
             else if (code == KeyEvent.KEYCODE_ENTER && !Config.globalConfig().sound_on_action) playSound = false;
        } else if (key.getKind() == KeyValue.Kind.Char && key.getChar() == ' ' && !Config.globalConfig().sound_on_space) {
             playSound = false;
        } else if (key.getKind() == KeyValue.Kind.Event && key.getEvent() == KeyValue.Event.ACTION && !Config.globalConfig().sound_on_action) {
             playSound = false;
        }

        if (playSound) {
            if (_soundManager != null) {
                 _soundManager.playClick(Config.globalConfig().sound_volume);
            }
        }
    }


    if (Config.globalConfig().vibrate_custom) {
        boolean vibrate = true;
        if (key.getKind() == KeyValue.Kind.Keyevent) {
             int code = key.getKeyevent();
             if (code == KeyEvent.KEYCODE_SPACE && !Config.globalConfig().vibrate_on_space) vibrate = false;
             else if (code == KeyEvent.KEYCODE_DEL && !Config.globalConfig().vibrate_on_delete) vibrate = false;
             else if (code == KeyEvent.KEYCODE_ENTER && !Config.globalConfig().vibrate_on_action) vibrate = false;
        } else if (key.getKind() == KeyValue.Kind.Char && key.getChar() == ' ' && !Config.globalConfig().vibrate_on_space) {
             vibrate = false;
        } else if (key.getKind() == KeyValue.Kind.Event && key.getEvent() == KeyValue.Event.ACTION && !Config.globalConfig().vibrate_on_action) {
             vibrate = false;
        }

        if (vibrate) {
             VibratorCompat.vibrate(_recv.getContext(), Config.globalConfig().vibrate_duration);
        }
    }

    if (isRenaming) {
        return;
    }


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


        handle_slider(key.getSlider(), key.getSliderRepeat(), true);
        break;
      default: break;
    }
  }


  @Override
  public void key_up(KeyValue key, Pointers.Modifiers mods)
  {
    if (key == null)
      return;

    if (mInterceptor != null) {
        if (mInterceptor.onInput(key, mods)) return;
    }

    if (isRenaming) {
        handleRenamingInput(key);
        return;
    }


    if (key.getKind() == KeyValue.Kind.String) {
        String cmd = key.getString();
        if (cmd != null && cmd.equals("cycle_theme")) {
            _recv.handleCustomCommand("cycle_theme");
            return;
        }
    }


    if (justAutoCorrected) {
        if (key.getKind() == KeyValue.Kind.Keyevent && key.getKeyevent() == KeyEvent.KEYCODE_DEL) {
            revertAutoCorrection(false);
            return;
        }

        boolean isSpaceOrDoubleSpace = false;
        if (key.getKind() == KeyValue.Kind.Keyevent && key.getKeyevent() == KeyEvent.KEYCODE_SPACE) {
            isSpaceOrDoubleSpace = true;
        } else if (key.getKind() == KeyValue.Kind.Char && key.getChar() == ' ') {
            isSpaceOrDoubleSpace = true;
        } else if (key.getKind() == KeyValue.Kind.Event && key.getEvent() == KeyValue.Event.DOUBLE_SPACE) {
            isSpaceOrDoubleSpace = true;
        }

        if (!isSpaceOrDoubleSpace) {
            justAutoCorrected = false;
        }
    }

    Pointers.Modifiers old_mods = _mods;
    update_meta_state(mods);
    switch (key.getKind())
    {
      case Char:
          if (key.getChar() == '\t' && currentCompletionGhostText != null) {
              acceptCompletion();
          } else {
              send_text(String.valueOf(key.getChar()));
          }
          break;
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
        if (key.getEvent() == KeyValue.Event.DOUBLE_SPACE) {
            handleDoubleSpaceKey();
        } else if (key.getEvent() == KeyValue.Event.EXPORT_DATA) {
          Toast.makeText(_recv.getContext(), "Export from keyboard settings.", Toast.LENGTH_LONG).show();
        } else if (key.getEvent() == KeyValue.Event.OPEN_PASSWORD_MANAGER) {
           Intent intent = new Intent();
           intent.setComponent(new ComponentName(_recv.getContext(), "juloo.keyboard2.passwordmanager.ui.PasswordManagerActivity"));
           intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           _recv.getContext().startActivity(intent);
        } else if (key.getEvent() == KeyValue.Event.OPEN_SECURE_NOTES) {
           Intent intent = new Intent();
           intent.setComponent(new ComponentName(_recv.getContext(), "juloo.keyboard2.passwordmanager.ui.PasswordManagerActivity"));
           intent.putExtra("target", "secure_notes");
           intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           _recv.getContext().startActivity(intent);
        } else if (key.getEvent() == KeyValue.Event.OPEN_QUICK_NOTE) {
           Intent intent = new Intent();
           intent.setComponent(new ComponentName(_recv.getContext(), "juloo.keyboard2.passwordmanager.ui.QuickNoteActivity"));
           intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           _recv.getContext().startActivity(intent);
        } else if (key.getEvent() == KeyValue.Event.OPEN_TERMUX_COMMANDS) {
           _recv.handle_event_key(KeyValue.Event.OPEN_TERMUX_COMMANDS);
        } else if (key.getEvent() == KeyValue.Event.GENERATE_PASSWORD) {
           handleGeneratePassword();
        } else if (key.getEvent() == KeyValue.Event.AUTOFILL_PASSWORD) {
           handleAutofillPassword();
        } else if (key.getEvent() == KeyValue.Event.SEARCH_REPLACE) {
           new SearchReplaceController(_recv.getContext(), _recv).showSearchReplaceDialog();
        } else if (key.getEvent() == KeyValue.Event.INSERT_TIMESTAMP) {
           insertTimestamp();
        } else {
          _recv.handle_event_key(key.getEvent());
        }
        break;
      case Keyevent:
        {
          final int keyCode = key.getKeyevent();
          final InputConnection conn = _recv.getCurrentInputConnection();

          if (currentCompletionGhostText != null || !_ghost_accept_history.isEmpty()) {
              if (keyCode == KeyEvent.KEYCODE_TAB && currentCompletionGhostText != null) {
                  acceptCompletion();
                  break;
              } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && currentCompletionGhostText != null) {
                  acceptOneWord();
                  break;
              } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                  if (!_ghost_accept_history.isEmpty()) {
                      uncompleteOneWord();
                      break;
                  }
              } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && currentGhostCompletions != null && currentGhostCompletions.size() > 1) {
                  currentGhostIndex = (currentGhostIndex - 1 + currentGhostCompletions.size()) % currentGhostCompletions.size();
                  showGhostText(conn);
                  break;
              } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentGhostCompletions != null && currentGhostCompletions.size() > 1) {
                  currentGhostIndex = (currentGhostIndex + 1) % currentGhostCompletions.size();
                  showGhostText(conn);
                  break;
              }
          }

          if (keyCode == KeyEvent.KEYCODE_DEL) {
              if (conn != null) {
                  if (currentCompletionGhostText != null) {
                      clearGhostText(conn);
                  }

                  if (Config.globalConfig().revert_on_backspace && justAutoCorrected) {
                      revertAutoCorrection(false);
                      justAutoCorrected = false;
                      return;
                  }

                  if (Config.globalConfig().delete_swallows_space) {
                       CharSequence before = conn.getTextBeforeCursor(2, 0);
                       if (before != null && before.length() >= 2) {
                           if (Character.isWhitespace(before.charAt(before.length()-1)) &&
                               Character.isWhitespace(before.charAt(before.length()-2))) {
                               commitDeleteSurroundingText(conn, 1, 0);
                           }
                       }
                  }

                  if (!revertedWords.isEmpty()) {
                      CharSequence textBefore = conn.getTextBeforeCursor(1, 0);
                      if (textBefore != null && textBefore.length() > 0) {
                          if (!Utils.isWordPart(textBefore.charAt(0))) revertedWords.clear();
                      } else {
                          revertedWords.clear();
                      }
                  }

                  CharSequence selected = conn.getSelectedText(0);
                  if (selected != null && selected.length() > 0) {
                      recordAndCommitText(conn, "");
                  } else {
                      CharSequence before = conn.getTextBeforeCursor(2, 0);
                      int deleteLength = 1;
                      if (before != null && before.length() > 0) {
                          if (Character.isLowSurrogate(before.charAt(before.length() - 1))) {
                              if (before.length() >= 2 && Character.isHighSurrogate(before.charAt(before.length() - 2))) {
                                  deleteLength = 2;
                              }
                          }
                      }
                      if (!commitDeleteSurroundingText(conn, deleteLength, 0)) {
                          send_key_down_up(KeyEvent.KEYCODE_DEL);
                      }
                  }
                  captureTypingHistory(false);
              } else {
                  send_key_down_up(keyCode);
              }
          } else {
              send_key_down_up(keyCode);
          }
          break;
        }
      case Modifier: break;
      case Editing: handle_editing_key(key.getEditing()); break;
      case Compose_pending: _recv.set_compose_pending(true); break;
      case Slider: handle_slider(key.getSlider(), key.getSliderRepeat(), false); break;
      case Macro: evaluate_macro(key.getMacro()); break;
    }
    update_meta_state(old_mods);
  }


  private boolean isScriptMatch(String word) {
      String script = _recv.getScript();
      if (script == null) return true;
      if ("urdu".equalsIgnoreCase(script)) {
          for (int i = 0; i < word.length(); i++) {
              char c = word.charAt(i);
              if (c >= 0x0600 && c <= 0x06FF) return true;
          }
          return false;
      } else {
          for (int i = 0; i < word.length(); i++) {
              char c = word.charAt(i);
              if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
          }
          return false;
      }
  }

  private void recordWordAndSequence(String word) {
      if (Config.globalConfig().incognito_mode || word == null || word.length() <= 1) return;
      final String lower = word.toLowerCase();

      if (_suggestionProvider.isValidWord(lower) && isScriptMatch(lower)) {
          final String currentCtx = getContextString();
          KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
              if (!currentCtx.isEmpty()) {
                  _suggestionProvider.trackWordSequence(currentCtx, lower);
              }
          });
          contextHistory.add(lower);
          if (contextHistory.size() > NextWordProbability.MAX_CONTEXT_LENGTH) contextHistory.remove(0);
      }

      _suggestionProvider.recordTypedWord(lower);
  }

  private void recordWordBeforeCursor() {
      if (Config.globalConfig().incognito_mode) return;
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;
      CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
      if (textBeforeCursor == null || textBeforeCursor.length() == 0) return;

      int end = textBeforeCursor.length();
      while (end > 0 && !Utils.isWordPart(textBeforeCursor.charAt(end - 1))) end--;

      if (end == 0) return;

      int start = end;
      while (start > 0 && Utils.isWordPart(textBeforeCursor.charAt(start - 1))) start--;

      String word = textBeforeCursor.subSequence(start, end).toString();
      recordWordAndSequence(word);
  }

  private String currentCompletionGhostText = null;
  private List<String> currentGhostCompletions = null;
  private int currentGhostIndex = 0;
  private android.widget.PopupWindow indicatorPopup = null;
  private android.widget.TextView indicatorText = null;

  private long _last_history_capture = 0;
  private void captureTypingHistory() {
      captureTypingHistory(true);
  }

  private void captureTypingHistory(boolean triggerAutocompletion) {
      if (Config.globalConfig().incognito_mode) return;

      long now = System.currentTimeMillis();
      _last_history_capture = now;

      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic == null) return;

      EditorInfo editorInfo = _recv.getCurrentInputEditorInfo();
      if (editorInfo != null) {
         int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
         boolean isPassword = (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                       variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                       variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
         if (isPassword) return;
      }

      CharSequence before = ic.getTextBeforeCursor(200, 0);
      if (before != null && triggerAutocompletion) {
          updateGhostAutocompletion(ic, before.toString());
      }

      recordTypingHistorySnapshot();
  }

  private void recordTypingHistorySnapshot() {
      if (Config.globalConfig().incognito_mode) return;
      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic == null) return;

      EditorInfo editorInfo = _recv.getCurrentInputEditorInfo();
      if (editorInfo != null) {
         int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
         boolean isPassword = (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                       variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                       variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
         if (isPassword) return;
      }

      final String ghost = currentCompletionGhostText;
      final Context context = _recv.getContext();
      KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
          InputConnection currentIc = _recv.getCurrentInputConnection();
          if (currentIc == null) return;
          ExtractedText et = currentIc.getExtractedText(new ExtractedTextRequest(), 0);
          if (et != null && et.text != null) {
              String fullText = et.text.toString();
              if (ghost != null && fullText.endsWith(ghost)) {
                  fullText = fullText.substring(0, fullText.length() - ghost.length());
              }
              ClipboardHistoryService.get_service(context).updateCurrentTypingSession(fullText);
          }
      });
  }

  private String getLastNWords(String text, int n) {
      int wordCount = 0;
      int i = text.length() - 1;
      boolean inWord = false;

      // Skip trailing whitespace
      while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
          i--;
      }

      int end = i + 1;
      while (i >= 0 && wordCount < n) {
          char c = text.charAt(i);
          if (Character.isWhitespace(c)) {
              if (inWord) {
                  wordCount++;
                  if (wordCount == n) break;
              }
              inWord = false;
          } else {
              inWord = true;
          }
          i--;
      }

      if (i < 0 && inWord) wordCount++;

      if (wordCount < n) return null;

      return text.substring(i + 1, end);
  }

  private long _ghost_task_id = 0;
  private final Handler _ghost_handler = new Handler(Looper.getMainLooper());
  private Runnable _ghost_runnable = null;

  private void updateGhostAutocompletion(InputConnection ic, String currentText) {
      if (!Config.globalConfig().clipboard_show_inline_suggestions) {
          clearGhostText(ic);
          return;
      }
      if (!_ghost_accept_history.isEmpty()) return; // Don't interrupt active navigation

      if (_ghost_runnable != null) {
          _ghost_handler.removeCallbacks(_ghost_runnable);
      }

      _ghost_runnable = () -> triggerGhostAutocompletion(ic, currentText);
      _ghost_handler.postDelayed(_ghost_runnable, 500);
  }

  private void triggerGhostAutocompletion(InputConnection ic, String currentText) {
      CharSequence after = ic.getTextAfterCursor(1, 0);
      if (after != null && after.length() > 0) {
          clearGhostText(ic);
          return;
      }

      String prefix = getLastNWords(currentText, Config.globalConfig().clipboard_autocomplete_min_words);
      if (prefix == null) {
          clearGhostText(ic);
          return;
      }

      String triggerWordsStr = Config.globalConfig().clipboard_autocomplete_trigger_words;
      if (triggerWordsStr != null && !triggerWordsStr.trim().isEmpty()) {
          String[] triggers = triggerWordsStr.split(",");
          boolean matched = false;
          String prefixLower = prefix.toLowerCase();
          for (String t : triggers) {
              String trimmed = t.trim().toLowerCase();
              if (!trimmed.isEmpty() && prefixLower.contains(trimmed)) {
                  matched = true;
                  break;
              }
          }
          if (!matched) {
              clearGhostText(ic);
              return;
          }
      }

      final long taskId = ++_ghost_task_id;
      final Context ctx = _recv.getContext();

      final String finalPrefix = prefix;
      KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
          List<ClipboardHistoryService.SentenceMatch> matches = ClipboardHistoryService.get_service(ctx).getSentenceCompletions(finalPrefix);
          List<String> completions = new ArrayList<>();
          for (ClipboardHistoryService.SentenceMatch m : matches) {
              if (m.originalTyped.equals(m.correctedPrefix)) {
                  completions.add(m.completion);
              }
          }

          _recv.getHandler().post(() -> {
              if (taskId != _ghost_task_id) return;

              InputConnection currentIc = _recv.getCurrentInputConnection();
              if (currentIc == null) return;

              if (!completions.isEmpty()) {
                  currentGhostCompletions = completions;
                  currentGhostIndex = 0;
                  showGhostText(currentIc);
              } else {
                  clearGhostText(currentIc);
              }
          });
      });
  }

  private void showGhostText(InputConnection ic) {
      if (currentGhostCompletions == null || currentGhostCompletions.isEmpty()) return;
      if (currentGhostIndex < 0) currentGhostIndex = 0;
      if (currentGhostIndex >= currentGhostCompletions.size()) currentGhostIndex = currentGhostCompletions.size() - 1;

      String completion = currentGhostCompletions.get(currentGhostIndex);
      currentCompletionGhostText = completion;

      SpannableStringBuilder ssb = new SpannableStringBuilder(completion);
      ssb.setSpan(new ForegroundColorSpan(0x88AAAAAA), 0, completion.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      ic.setComposingText(ssb, 0);

      showIndicatorPopup();
  }

  private void showIndicatorPopup() {
      if (currentGhostCompletions == null || currentGhostCompletions.size() <= 1) {
          hideIndicatorPopup();
          return;
      }

      _recv.getHandler().post(() -> {
          Context ctx = _recv.getContext();
          if (indicatorPopup == null) {
              indicatorText = new android.widget.TextView(ctx);
              indicatorText.setTextColor(0xFFFFFFFF);
              indicatorText.setTextSize(Config.globalConfig().clipboard_multi_suggestions_size * 0.5f);
              indicatorText.setBackgroundResource(android.R.drawable.toast_frame);
              indicatorText.setPadding(10, 5, 10, 5);

              indicatorPopup = new android.widget.PopupWindow(indicatorText,
                  android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                  android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
              indicatorPopup.setTouchable(false);
          }

          indicatorText.setText((currentGhostIndex + 1) + " of " + currentGhostCompletions.size());

          View inputView = _recv.getKeyboardView();
          if (inputView != null && inputView.getWindowToken() != null) {
              try {
                  // Fallback: show at top of keyboard if we don't have cursor position
                  indicatorPopup.showAtLocation(inputView, android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, -50);
              } catch (Exception e) {}
          }
      });
  }

  private void hideIndicatorPopup() {
      if (indicatorPopup != null) {
          _recv.getHandler().post(() -> {
              if (indicatorPopup != null && indicatorPopup.isShowing()) {
                  try {
                      indicatorPopup.dismiss();
                  } catch (Exception e) {}
              }
          });
      }
  }

  private void clearGhostText(InputConnection ic) {
      if (ic != null && currentCompletionGhostText != null) {
          ic.beginBatchEdit();
          ic.setComposingText("", 0);
          ic.finishComposingText();
          ic.endBatchEdit();
      }
      currentCompletionGhostText = null;
      currentGhostCompletions = null;
      currentGhostIndex = 0;
      _ghost_accept_history.clear();
      hideIndicatorPopup();
  }

  private void acceptCompletion() {
      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic != null && (currentCompletionGhostText != null || !_ghost_accept_history.isEmpty())) {
          String toCommit = getFullRemainingCompletion();
          ic.beginBatchEdit();
          ic.setComposingText("", 0);
          ic.finishComposingText();
          if (!toCommit.isEmpty()) {
              ic.commitText(toCommit, 1);
          }
          ic.endBatchEdit();
          clearGhostText(null); // Just clear local state
      }
  }

  private final List<String> _ghost_accept_history = new ArrayList<>();

  private void acceptOneWord() {
      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic != null && currentCompletionGhostText != null) {
          String text = currentCompletionGhostText;
          int i = 0;
          while (i < text.length() && text.charAt(i) == ' ') i++;
          while (i < text.length() && text.charAt(i) != ' ') i++;
          while (i < text.length() && text.charAt(i) == ' ') i++;

          if (i > 0) {
              String toCommit = text.substring(0, i);

              ic.beginBatchEdit();
              ic.setComposingText("", 0);
              ic.finishComposingText();
              ic.commitText(toCommit, 1);

              _ghost_accept_history.add(toCommit);

              String fullRemaining = getFullRemainingCompletion();
              String lookahead = limitWords(fullRemaining, 5);

              if (lookahead != null && !lookahead.isEmpty()) {
                  currentCompletionGhostText = lookahead;
                  SpannableStringBuilder ssb = new SpannableStringBuilder(lookahead);
                  ssb.setSpan(new ForegroundColorSpan(0x88AAAAAA), 0, lookahead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                  ic.setComposingText(ssb, 0);
              } else {
                  currentCompletionGhostText = null;
              }
              ic.endBatchEdit();
          }
      }
  }

  private String getFullRemainingCompletion() {
      if (currentGhostCompletions == null || currentGhostIndex >= currentGhostCompletions.size()) return "";
      String full = currentGhostCompletions.get(currentGhostIndex);

      StringBuilder accepted = new StringBuilder();
      for (String s : _ghost_accept_history) accepted.append(s);

      if (full.startsWith(accepted.toString())) {
          return full.substring(accepted.length());
      }
      return "";
  }

  private void uncompleteOneWord() {
      InputConnection ic = _recv.getCurrentInputConnection();
      if (ic != null) {
          if (!_ghost_accept_history.isEmpty()) {
              String lastWord = _ghost_accept_history.remove(_ghost_accept_history.size() - 1);
              ic.beginBatchEdit();
              ic.deleteSurroundingText(lastWord.length(), 0);

              String fullRemaining = getFullRemainingCompletion();
              String lookahead = limitWords(fullRemaining, 5);

              currentCompletionGhostText = lookahead;
              SpannableStringBuilder ssb = new SpannableStringBuilder(lookahead);
              ssb.setSpan(new ForegroundColorSpan(0x88AAAAAA), 0, lookahead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
              ic.setComposingText(ssb, 0);
              ic.endBatchEdit();
              showIndicatorPopup();
          } else if (currentCompletionGhostText != null) {
              ic.setComposingText("", 0);
              currentCompletionGhostText = null;
              hideIndicatorPopup();
          }
      }
  }

  private String limitWords(String text, int maxWords) {
      if (text == null || text.isEmpty()) return text;
      String[] words = text.split("\\s+");
      if (words.length <= maxWords) return text;

      int wordCount = 0;
      int lastIdx = 0;
      boolean inWord = false;
      for (int i = 0; i < text.length(); i++) {
          if (Character.isWhitespace(text.charAt(i))) {
              if (inWord) {
                  wordCount++;
                  inWord = false;
                  if (wordCount == maxWords) {
                      lastIdx = i;
                      break;
                  }
              }
          } else {
              inWord = true;
          }
      }
      if (wordCount < maxWords) return text;
      return text.substring(0, lastIdx);
  }

  public void startRenaming(String currentName) {
      isRenaming = true;
      renameBuffer = new StringBuilder(currentName != null ? currentName : "");
      _recv.updateRenameBuffer(renameBuffer.toString());
  }

  public void stopRenaming(boolean save) {
      isRenaming = false;
      if (save) {
          _recv.onRenameConfirmed(renameBuffer.toString());
      } else {
          _recv.onRenameCancelled();
      }
      renameBuffer = null;
  }

  private void handleRenamingInput(KeyValue key) {
      if (renameBuffer == null) return;

      switch (key.getKind()) {
          case Char:
              renameBuffer.append(key.getChar());
              break;
          case String:
              renameBuffer.append(key.getString());
              break;
          case Keyevent:
              if (key.getKeyevent() == KeyEvent.KEYCODE_DEL) {
                  if (renameBuffer.length() > 0) {
                      renameBuffer.deleteCharAt(renameBuffer.length() - 1);
                  }
              } else if (key.getKeyevent() == KeyEvent.KEYCODE_ENTER) {
                  stopRenaming(true);
                  return;
              } else if (key.getKeyevent() == KeyEvent.KEYCODE_SPACE) {
                  renameBuffer.append(' ');
              }
              break;
          case Event:
              if (key.getEvent() == KeyValue.Event.ACTION) {
                  stopRenaming(true);
                  return;
              }
              break;
          default:
              break;
      }
      _recv.updateRenameBuffer(renameBuffer.toString());
  }

  @Override
  public void mods_changed(Pointers.Modifiers mods)
  {
    update_meta_state(mods);
  }

  @Override
  public void onGestureFinished(java.util.List<android.graphics.PointF> path) {

  }

  @Override
  public void paste_from_clipboard_pane(String content)
  {
    if (content != null) {
        lastPastedLength = content.length();
        _recv.showUndoPasteButton();

        InputConnection conn = _recv.getCurrentInputConnection();
        if (conn != null) {
            recordAndCommitText(conn, content);
        } else {
            ClipboardManager cm = (ClipboardManager) _recv.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(null, content));
        }
    }
  }

  public void undoLastPaste() {
      if (lastPastedLength > 0) {
          InputConnection conn = _recv.getCurrentInputConnection();
          if (conn != null) {
              commitDeleteSurroundingText(conn, lastPastedLength, 0);
              lastPastedLength = 0;
          }
      }
  }


  void update_meta_state(Pointers.Modifiers mods)
  {

    Iterator<KeyValue> it = _mods.diff(mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), false);

    it = mods.diff(_mods);
    while (it.hasNext())
      sendMetaKeyForModifier(it.next(), true);
    _mods = mods;
  }











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

      String newWord = selectedText.toString().trim();
      if (newWord.isEmpty()) {
          Toast.makeText(_recv.getContext(), "No text selected.", Toast.LENGTH_SHORT).show();
          return;
      }

      String[] words = newWord.split("\\s+");
      if (words.length > 5) {
          Toast.makeText(_recv.getContext(), "You can only add up to 5 words at a time.", Toast.LENGTH_SHORT).show();
          return;
      }

      if (isWordInDictionary(newWord)) {
          Toast.makeText(_recv.getContext(), "Word already in dictionary.", Toast.LENGTH_SHORT).show();
      } else {
          updateCustomDictionary(java.util.Collections.singleton(newWord));
          Toast.makeText(_recv.getContext(), "Added to custom dictionary", Toast.LENGTH_SHORT).show();
      }
  }

  private void updateCustomDictionary(java.util.Collection<String> newWords) {
      KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
          Map<String, Set<String>> scriptToWords = new HashMap<>();
          for (String w : newWords) {
              String script = Utils.isUrdu(w) ? "ur" : "en";
              scriptToWords.computeIfAbsent(script, k -> new HashSet<>()).add(w);
          }

          for (Map.Entry<String, Set<String>> entry : scriptToWords.entrySet()) {
              String script = entry.getKey();
              String fileName = "custom_" + script + ".txt";
              File customDictFile = new File(_recv.getContext().getFilesDir(), fileName);
              java.util.Set<String> words = new java.util.HashSet<>();
              if (customDictFile.exists()) {
                  try (BufferedReader reader = new BufferedReader(new FileReader(customDictFile))) {
                      String line;
                      while ((line = reader.readLine()) != null) {
                          words.add(line.trim());
                      }
                  } catch (IOException e) {
                      e.printStackTrace();
                  }
              }

              words.addAll(entry.getValue());
              java.util.List<String> sortedWords = new java.util.ArrayList<>(words);
              java.util.Collections.sort(sortedWords);

              try (FileOutputStream fos = _recv.getContext().openFileOutput(fileName, Context.MODE_PRIVATE)) {
                  for (String word : sortedWords) {
                      fos.write((word + "\n").getBytes());
                  }
              } catch (IOException e) {
                  e.printStackTrace();
              }

              // Export to external backup
              try {
                  File externalDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
                  if (!externalDir.exists()) externalDir.mkdirs();
                  File externalFile = new File(externalDir, fileName);
                  try (FileOutputStream fos = new FileOutputStream(externalFile)) {
                      for (String word : sortedWords) {
                          fos.write((word + "\n").getBytes());
                      }
                  }
                  DriveSyncHelper.syncFileToDrive(_recv.getContext(), externalFile, "text/plain");
              } catch (Exception e) {
                  e.printStackTrace();
              }
          }

          _recv.getHandler().post(() -> _recv.reloadCustomDictionary());
      });
  }

  void send_text(CharSequence text)
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null)
      return;

    if (" ".equals(text.toString())) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSpaceTime < 1500) {
            if (justAutoCorrected) {
                revertAutoCorrection(true);
                sendTextVerbatim(" ", false);
            } else {
                if (Config.globalConfig().double_space_period) {
                    InputConnection ic = _recv.getCurrentInputConnection();
                    if (ic != null) {
                        commitDeleteSurroundingText(ic, 1, 0);
                        sendTextVerbatim(". ");
                    }
                } else {
                    sendTextVerbatim(" ", false);
                }
            }
        } else {
            if (Config.globalConfig().auto_add_user_words) {
                InputConnection ic = _recv.getCurrentInputConnection();
                if (ic != null) {
                    CharSequence tb = ic.getTextBeforeCursor(50, 0);
                    if (tb != null && tb.length() > 0) {
                        int i = tb.length();
                        while (i > 0 && Utils.isWordPart(tb.charAt(i - 1))) i--;
                        String w = tb.subSequence(i, tb.length()).toString();
                        if (w.length() > 1 && !_suggestionProvider.isValidWord(w)) {
                             updateCustomDictionary(java.util.Collections.singleton(w));
                        }
                    }
                }
            }

            if (Config.globalConfig().auto_correct_space) {
                handleAutoCorrectionOnSpace();
            } else {
                InputConnection ic = _recv.getCurrentInputConnection();
                String wordToTransition = null;
                if (ic != null) {
                    CharSequence tb = ic.getTextBeforeCursor(50, 0);
                    if (tb != null && tb.length() > 0) {
                        int i = tb.length();
                        while (i > 0 && Utils.isWordPart(tb.charAt(i - 1))) i--;
                        wordToTransition = tb.subSequence(i, tb.length()).toString();
                    }
                }

                recordWordBeforeCursor();
                sendTextVerbatim(" ", false);
                if (mSuggestionsEnabledForThisInput && wordToTransition != null && !wordToTransition.isEmpty()) {
                    _recv.updateTypingHUD(wordToTransition, wordToTransition, true);
                }

                if (mSuggestionsEnabledForThisInput) {
                    currentSuggestionMode = SuggestionProvider.SuggestionMode.NEXT_WORD;
                    lastContext = getContextString();
                    final String ctx = lastContext;
                    final SuggestionProvider.SuggestionMode mode = currentSuggestionMode;
                    KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
                        List<SuggestionProvider.Suggestion> raw = _suggestionProvider.getNextWordSuggestions(ctx);
                        final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(raw, ctx);
                        _recv.getHandler().post(() -> _recv.showSuggestions(prioritized, mode));
                    });
                }
            }
            if (mSuggestionsEnabledForThisInput) refreshFieldWords();
        }
        lastSpaceTime = currentTime;
        return;
    }


    if (Config.globalConfig().smart_punctuation) {
        if (text.length() == 1 && ",.?!;:".contains(text)) {
             recordWordBeforeCursor();
             CharSequence before = conn.getTextBeforeCursor(1, 0);
             if (before != null && before.length() > 0 && Character.isWhitespace(before.charAt(0))) {
                  commitDeleteSurroundingText(conn, 1, 0);
             }
        }
    }


    justAutoCorrected = false;

    CharSequence selectedText = conn.getSelectedText(0);
    if (selectedText != null && selectedText.length() > 0) {
        String textStr = text.toString();
        String newText = null;
        String originalText = selectedText.toString();

        if (("d".equalsIgnoreCase(textStr) || "ڈ".equals(textStr)) && Config.globalConfig().shortcut_learn_d) {
            learnFromTextField();
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
                        recordAndCommitText(conn, sizedText);
                        return;
                    }
                }
            } else {
                _pending_font_size_digit = null;
            }
            if ("b".equals(textStr) && Config.globalConfig().format_bold_b) {
                SpannableString boldText = new SpannableString(originalText);
                boldText.setSpan(new StyleSpan(Typeface.BOLD), 0, originalText.length(), 0);
                recordAndCommitText(conn, boldText);
                return;
            } else if ("i".equals(textStr) && Config.globalConfig().format_italic_i) {
                SpannableString italicText = new SpannableString(originalText);
                italicText.setSpan(new StyleSpan(Typeface.ITALIC), 0, originalText.length(), 0);
                recordAndCommitText(conn, italicText);
                return;
            } else if ("u".equals(textStr) && Config.globalConfig().format_upper_u) {
                newText = originalText.toUpperCase();
            } else if (("l".equals(textStr) || "ل".equals(textStr)) && Config.globalConfig().format_lower_l) {
                newText = originalText.toLowerCase();
            } else if ("s".equals(textStr) && Config.globalConfig().format_sentence_s) {
                if (originalText.length() > 0) {
                    newText = Character.toUpperCase(originalText.charAt(0)) + originalText.substring(1).toLowerCase();
                } else {
                    newText = originalText;
                }
            }
        }
        if (Config.globalConfig().application_integrations) {
            if ("t".equals(textStr) && Config.globalConfig().shortcut_translate_t) {
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
            } else if ("k".equals(textStr) && Config.globalConfig().shortcut_keep_k) {
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
            } else if ("o".equals(textStr) && Config.globalConfig().shortcut_obsidian_o) {
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
            } else if ("c".equals(textStr) && Config.globalConfig().shortcut_chrome_c) {
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
            recordAndCommitText(conn, newText);
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
                recordAndCommitText(conn, open + selectedText + close);

                ExtractedText et = get_cursor_pos(conn);
                if (et != null) {
                    conn.setSelection(et.selectionStart - close.length() - selectedText.length(), et.selectionStart - close.length());
                }
                return;
            }
        }
    }

    if (text.length() > 1) {
        _recv.updateTypingHUD(null, null, false);
    }

    conn.beginBatchEdit();
    recordAndCommitText(conn, text);
    conn.endBatchEdit();
    _autocap.typed(text);
    if (mSuggestionsEnabledForThisInput && text.length() <= 1) {
        updateSuggestionsFromPrefix();
    }


    captureTypingHistory();
  }

  private void handleAutoCorrectionOnSpace() {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null) {
      sendTextVerbatim(" ", false);
      return;
    }

    CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
    if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
      sendTextVerbatim(" ", false);
      return;
    }

    int i = textBeforeCursor.length();
    while (i > 0 && Utils.isWordPart(textBeforeCursor.charAt(i - 1))) {
      i--;
    }
    final String word = textBeforeCursor.subSequence(i, textBeforeCursor.length()).toString();
    final String lowerCaseWord = word.toLowerCase();

    if (word.isEmpty()) {
      sendTextVerbatim(" ", false);
      return;
    }

    if (revertedWords.contains(lowerCaseWord)) {
      sendTextVerbatim(" ", false);
      return;
    }

    if (!revertedWords.isEmpty()) {
      revertedWords.clear();
    }

    if (_suggestionProvider.isValidWordForAutoCorrect(lowerCaseWord) || Utils.containsSpecialSymbol(word) || Utils.isShort(word) || Utils.hasDigit(word)) {
      recordWordAndSequence(word);
      sendTextVerbatim(" ", false);
      currentSuggestionMode = SuggestionProvider.SuggestionMode.NEXT_WORD;
      lastContext = getContextString();

      final String ctx = lastContext;
      final SuggestionProvider.SuggestionMode mode = currentSuggestionMode;
      KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
        List<SuggestionProvider.Suggestion> raw = _suggestionProvider.getNextWordSuggestions(ctx);
        final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(raw, ctx);
        _recv.getHandler().post(() -> _recv.showSuggestions(prioritized, mode));
      });
      return;
    }

    // Move search to background to keep UI responsive
    sendTextVerbatim(" ", false);
    final String contextAtSpace = getContextString();

    KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
      lastWordForCorrection = lowerCaseWord;
      java.util.List<String> corrections = _autoCorrectionProvider.getCorrections(lowerCaseWord);
      java.util.List<String> keyboardSuggestions = _keyboardAwareSuggester.suggest(lowerCaseWord);

      List<SuggestionProvider.Suggestion> combined = new ArrayList<>();
      int corrRank = 0;
      for (String s : corrections) {
        combined.add(new SuggestionProvider.Suggestion(s, SuggestionProvider.FEATURE_AUTOCORRECT, 0, _suggestionProvider.getWordSource(s), corrRank++));
      }
      int kbRank = 0;
      for (String s : keyboardSuggestions) {
        combined.add(new SuggestionProvider.Suggestion(s, SuggestionProvider.FEATURE_KEYBOARD_AWARE, 0, _suggestionProvider.getWordSource(s), kbRank++));
      }

      final java.util.List<SuggestionProvider.Suggestion> filteredCombined = filterAndPrioritize(combined, contextAtSpace);

      if (!filteredCombined.isEmpty()) {
        final String bestCorrection = filteredCombined.get(0).word;
        _recv.getHandler().post(() -> {
          applyAsynchronousAutoCorrection(word, bestCorrection, filteredCombined);
        });
      }
    });
  }

  private void applyAsynchronousAutoCorrection(String original, String corrected, List<SuggestionProvider.Suggestion> suggestions) {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null) return;

    // Verify that the word before cursor is still the one we wanted to correct followed by space
    CharSequence before = conn.getTextBeforeCursor(original.length() + 1, 0);
    if (before == null || !before.toString().equals(original + " ")) {
      return;
    }

    String matchedCorrected = Utils.matchCase(original, corrected);

    conn.beginBatchEdit();
    _undoRedoManager.beginBatch();
    commitDeleteSurroundingText(conn, original.length() + 1, 0);
    recordAndCommitText(conn, matchedCorrected);
    recordAndCommitText(conn, " ");
    _undoRedoManager.endBatch();
    conn.endBatchEdit();

    _suggestionProvider.recordTypedWord(matchedCorrected);

    originalWord = original;
    correctedWord = matchedCorrected;
    justAutoCorrected = true;

    // Correctly calculate expected cursor position after replacement
    expectedCursorPos = _autocap._cursor - original.length() + corrected.length();

    if (mSuggestionsEnabledForThisInput) {
        _recv.updateTypingHUD(original, matchedCorrected, true);
    }

    currentSuggestionMode = SuggestionProvider.SuggestionMode.AUTO_CORRECTION;
    _recv.showSuggestions(suggestions, currentSuggestionMode);
    if (Config.globalConfig().vibrate_on_correction) {
      VibratorCompat.vibrate(_recv.getContext(), Config.globalConfig().vibrate_duration);
    }
    refreshFieldWords();
    captureTypingHistory();
  }


  private void sendTextVerbatim(CharSequence text) {
      sendTextVerbatim(text, true);
  }

  private void sendTextVerbatim(CharSequence text, boolean clearSuggestions) {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      recordAndCommitText(conn, text);

      _autocap.typed(text);
      if (mSuggestionsEnabledForThisInput && clearSuggestions) {
          _recv.showSuggestions(java.util.Collections.emptyList(), SuggestionProvider.SuggestionMode.NONE);
      }

      captureTypingHistory();
  }


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
    InputConnection conn = _recv.getCurrentInputConnection();
    switch (ev)
    {
      case COPY:
          if(is_selection_not_empty()) {
              if (conn != null) {
                  if (!conn.performContextMenuAction(android.R.id.copy)) {
                      CharSequence selected = conn.getSelectedText(0);
                      if (selected != null) {
                          ClipboardManager cm = (ClipboardManager) _recv.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                          cm.setPrimaryClip(ClipData.newPlainText(null, selected));
                      }
                  }
              }
          }
          break;
      case PASTE:
          if (conn != null) {
             ClipboardManager cm = (ClipboardManager) _recv.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
             if (cm.hasPrimaryClip()) {
                 ClipData clip = cm.getPrimaryClip();
                 if (clip != null && clip.getItemCount() > 0) {
                     CharSequence text = clip.getItemAt(0).getText();
                     if (text != null) {
                         paste_from_clipboard_pane(text.toString());
                     }
                 }
             }
          }
          break;
      case CUT:
          if(is_selection_not_empty()) {
              if (conn != null) {
                  clearGhostText(conn);
                  CharSequence selected = conn.getSelectedText(0);
                  if (selected != null) {
                      ClipboardManager cm = (ClipboardManager) _recv.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                      cm.setPrimaryClip(ClipData.newPlainText(null, selected));
                      recordAndCommitText(conn, "");
                  }
              }
          }
          break;
      case SELECT_ALL:
          if (conn != null) {

             if (!conn.performContextMenuAction(android.R.id.selectAll)) {

                 ExtractedText et = conn.getExtractedText(new ExtractedTextRequest(), 0);
                 if (et != null && et.text != null) {
                     conn.setSelection(0, et.text.length());
                 } else {

                     conn.setSelection(0, 1000000);
                 }
             }
          }
          break;
      case SHARE: send_context_menu_action(android.R.id.shareText); break;
      case PASTE_PLAIN: send_context_menu_action(android.R.id.pasteAsPlainText); break;
      case UNDO:
          if (conn != null) {
              _undoRedoManager.undo(conn);
          }
          break;
      case REDO:
          if (conn != null) {
              _undoRedoManager.redo(conn);
          }
          break;
      case REPLACE: send_context_menu_action(android.R.id.replaceText); break;
      case ASSIST: send_context_menu_action(android.R.id.textAssist); break;
      case AUTOFILL: send_context_menu_action(android.R.id.autofill); break;
      case DELETE_WORD:
          if (Config.globalConfig().swipe_delete_word) {
              if (conn != null) {
                  clearGhostText(conn);
                  handleDeleteWord(conn, false);
              }
              else send_key_down_up(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
          }
          break;
      case FORWARD_DELETE_WORD:
          if (Config.globalConfig().swipe_delete_word) {
              if (conn != null) {
                  clearGhostText(conn);
                  handleDeleteWord(conn, true);
              }
              else send_key_down_up(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
          }
          break;
      case SELECTION_CANCEL: cancel_selection(); break;
      case ADD_TO_DICTIONARY: addSelectedTextToDictionary(); break;
      case ADD_TO_DICTIONARY_BATCH: addSelectedTextToDictionaryBatch(); break;
      case MOVE_WORD_BACKWARD_1: handleMoveWord(conn, 1, false, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_FORWARD_1: handleMoveWord(conn, 1, true, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_BACKWARD_2: handleMoveWord(conn, 2, false, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_FORWARD_2: handleMoveWord(conn, 2, true, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_BACKWARD_3: handleMoveWord(conn, 3, false, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_FORWARD_3: handleMoveWord(conn, 3, true, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_BACKWARD_4: handleMoveWord(conn, 4, false, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_FORWARD_4: handleMoveWord(conn, 4, true, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_BACKWARD_5: handleMoveWord(conn, 5, false, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
      case MOVE_WORD_FORWARD_5: handleMoveWord(conn, 5, true, (_meta_state & KeyEvent.META_SHIFT_ON) != 0); break;
    }
  }

  static ExtractedTextRequest _move_cursor_req = null;


  ExtractedText get_cursor_pos(InputConnection conn)
  {
    if (_move_cursor_req == null)
    {
      _move_cursor_req = new ExtractedTextRequest();
      _move_cursor_req.hintMaxChars = 0;
    }
    return conn.getExtractedText(_move_cursor_req, 0);
  }


  void handle_slider(KeyValue.Slider s, int r, boolean key_down)
  {
    if (!Config.globalConfig().swipe_space_cursor &&
        (s == KeyValue.Slider.Cursor_left || s == KeyValue.Slider.Cursor_right ||
         s == KeyValue.Slider.Cursor_up || s == KeyValue.Slider.Cursor_down)) {
         return;
    }
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

      if (sel_end != sel_start)
      {
        sel_end += d;
        if (sel_end == sel_start)
          sel_end += d;
      }
      else
      {
        sel_end += d;

        if ((_meta_state & KeyEvent.META_SHIFT_ON) == 0)
          sel_start = sel_end;
      }
      if (conn.setSelection(sel_start, sel_end))
        return;
    }
    move_cursor_fallback(d);
  }


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


      } while (sel_start == sel_end);
      if (conn.setSelection(sel_start, sel_end))
        return;
    }
    move_cursor_fallback(d);
  }


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

    mods_changed(Pointers.Modifiers.EMPTY);
    evaluate_macro_loop(keys, 0, Pointers.Modifiers.EMPTY, _autocap.pause());
  }


  void evaluate_macro_loop(final KeyValue[] keys, int i, Pointers.Modifiers mods, final boolean autocap_paused)
  {
    boolean should_delay = false;
    KeyValue kv = KeyModifier.modify(keys[i], mods);
    if (kv != null)
    {
      if (kv.hasFlagsAny(KeyValue.FLAG_LATCH))
      {

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
    if (i >= keys.length)
    {
      _autocap.unpause(autocap_paused);
    }
    else if (should_delay)
    {



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


  void send_key_down_up_repeat(int event_code, int repeat)
  {
    while (repeat-- > 0)
      send_key_down_up(event_code);
  }


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

    if (conn.setSelection(curs, curs));
      _recv.selection_state_changed(false);
  }

  boolean is_selection_not_empty()
  {
    InputConnection conn = _recv.getCurrentInputConnection();
    if (conn == null) return false;
    return (conn.getSelectedText(0) != null);
  }


  boolean should_move_cursor_force_fallback(EditorInfo info)
  {

    if ((info.inputType & InputType.TYPE_MASK_VARIATION & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0)
      return true;

    return info.packageName.startsWith("org.godotengine.editor");
  }

  public void invalidateCache() {
      _suggestionCache.context = null;
  }

  public void triggerUpdateSuggestions() {
      if (!mSuggestionsEnabledForThisInput) return;

      switch (currentSuggestionMode) {
          case PREFIX:
              updateSuggestionsFromPrefix();
              break;
          case NEXT_WORD:
              final String ctx = lastContext;
              final SuggestionProvider.SuggestionMode mode = currentSuggestionMode;
              KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
                  List<SuggestionProvider.Suggestion> raw = _suggestionProvider.getNextWordSuggestions(ctx);
                  final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(raw, ctx);
                  _recv.getHandler().post(() -> _recv.showSuggestions(prioritized, mode));
              });
              break;
          case AUTO_CORRECTION:
              refreshAutoCorrectionSuggestions();
              break;
          default:
              updateSuggestionsFromPrefix();
              break;
      }
  }

  private String getContextString() {
      synchronized (contextHistory) {
          if (contextHistory.isEmpty()) return "";
          StringBuilder sb = new StringBuilder();
          for (String w : contextHistory) {
              if (sb.length() > 0) sb.append(" ");
              sb.append(w);
          }
          return sb.toString();
      }
  }

  private void refreshAutoCorrectionSuggestions() {
      if (lastWordForCorrection == null || lastWordForCorrection.isEmpty()) return;
      final String word = lastWordForCorrection;
      final String ctx = lastContext;
      final SuggestionProvider.SuggestionMode mode = currentSuggestionMode;
      KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
          List<String> corrections = _autoCorrectionProvider.getCorrections(word);
          List<String> keyboardSuggestions = _keyboardAwareSuggester.suggest(word);

          List<SuggestionProvider.Suggestion> combined = new ArrayList<>();
          int corrRank = 0;
          for (String s : corrections) {
              combined.add(new SuggestionProvider.Suggestion(s, SuggestionProvider.FEATURE_AUTOCORRECT, 0, _suggestionProvider.getWordSource(s), corrRank++));
          }
          int kbRank = 0;
          for (String s : keyboardSuggestions) {
              combined.add(new SuggestionProvider.Suggestion(s, SuggestionProvider.FEATURE_KEYBOARD_AWARE, 0, _suggestionProvider.getWordSource(s), kbRank++));
          }

          final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(combined, ctx);
          _recv.getHandler().post(() -> _recv.showSuggestions(prioritized, mode));
      });
  }

  public void promoteSuggestion(SuggestionProvider.Suggestion suggestion) {
      invalidateCache();
      String feature = (currentSuggestionMode == SuggestionProvider.SuggestionMode.NEXT_WORD)
              ? SuggestionProvider.FEATURE_NEXT_WORD
              : SuggestionProvider.FEATURE_PREFIX;
      if (feature.equals(SuggestionProvider.FEATURE_NEXT_WORD)) {
          _suggestionProvider.promoteWord(suggestion.word, feature, lastContext);
      } else {
          _suggestionProvider.promoteWord(suggestion.word, feature, null);
      }
      triggerUpdateSuggestions();
  }

  public void deprioritizeSuggestion(SuggestionProvider.Suggestion suggestion) {
      invalidateCache();
      String feature = (currentSuggestionMode == SuggestionProvider.SuggestionMode.NEXT_WORD)
              ? SuggestionProvider.FEATURE_NEXT_WORD
              : SuggestionProvider.FEATURE_PREFIX;
      if (feature.equals(SuggestionProvider.FEATURE_NEXT_WORD)) {
          _suggestionProvider.deprioritizeWord(suggestion.word, feature, lastContext);
      } else {
          _suggestionProvider.deprioritizeWord(suggestion.word, feature, null);
      }
      triggerUpdateSuggestions();
  }

  public void blacklistSuggestion(SuggestionProvider.Suggestion suggestion) {
      invalidateCache();
      String feature = (currentSuggestionMode == SuggestionProvider.SuggestionMode.NEXT_WORD)
              ? SuggestionProvider.FEATURE_NEXT_WORD
              : SuggestionProvider.FEATURE_PREFIX;
      if (feature.equals(SuggestionProvider.FEATURE_NEXT_WORD)) {
          _suggestionProvider.blacklistWord(suggestion.word, feature, lastContext);
      } else {
          _suggestionProvider.blacklistWord(suggestion.word, feature, null);
      }
      triggerUpdateSuggestions();
  }

  private boolean isBlacklistedGlobal(String word, String ctx, Set<String> ctxBlack, Collection<Set<String>> featureBlack) {
      if (word == null) return false;
      if (ctxBlack != null && ctxBlack.contains(word)) return true;
      if (featureBlack != null) {
          synchronized (featureBlack) {
              for (Set<String> fb : featureBlack) {
                  if (fb != null && fb.contains(word)) return true;
              }
          }
      }
      return false;
  }

  private boolean isDeprioritizedGlobal(String word, String ctx, Set<String> ctxDeprio, Collection<Set<String>> featureDeprio) {
      if (ctxDeprio != null && ctxDeprio.contains(word)) return true;
      for (Set<String> fd : featureDeprio) {
          if (fd.contains(word)) return true;
      }
      return false;
  }

  private boolean isPromotedGlobal(String word, String ctx, List<String> ctxPromoted, Collection<List<String>> featurePromoted) {
      if (ctxPromoted != null && ctxPromoted.contains(word)) return true;
      for (List<String> fp : featurePromoted) {
          if (fp.contains(word)) return true;
      }
      return false;
  }

  private List<SuggestionProvider.Suggestion> filterAndPrioritize(List<SuggestionProvider.Suggestion> rawSuggestions, String contextWord) {
      if (rawSuggestions == null || rawSuggestions.isEmpty()) return Collections.emptyList();

      final String ctx = contextWord != null ? contextWord.toLowerCase() : null;

      final Set<String> ctxBlack = ctx != null ? _suggestionProvider.contextBlacklisted.get(ctx) : null;
      final Collection<Set<String>> featureBlack = _suggestionProvider.featureBlacklisted.values();

      final Set<String> ctxDeprio = ctx != null ? _suggestionProvider.contextDeprioritized.get(ctx) : null;
      final Collection<Set<String>> featureDeprio = _suggestionProvider.featureDeprioritized.values();

      List<SuggestionProvider.Suggestion> result = new ArrayList<>();
      List<SuggestionProvider.Suggestion> deprioritized = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for (SuggestionProvider.Suggestion s : rawSuggestions) {
          if (s == null || s.word == null) continue;
          String wordLower = s.word.toLowerCase();

          if (!seen.add(wordLower)) continue;

          if (isBlacklistedGlobal(wordLower, ctx, ctxBlack, featureBlack)) continue;

          if (isDeprioritizedGlobal(wordLower, ctx, ctxDeprio, featureDeprio)) {
              deprioritized.add(s);
              continue;
          }

          result.add(s);
      }

      result.addAll(deprioritized);
      return result;
  }

  public void updateSuggestionsFromPrefix() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      if (is_selection_not_empty()) {
          cancelCurrentSuggestionTask();
          currentSuggestionMode = SuggestionProvider.SuggestionMode.NONE;
          _recv.showSuggestions(Collections.emptyList(), currentSuggestionMode);
          return;
      }

      final long taskId = suggestionTaskId.incrementAndGet();

      KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
          // Offload heavy context extraction and TRIE filtering to background
          InputConnection ic = _recv.getCurrentInputConnection();
          if (ic == null) return;

          CharSequence textBeforeCursor = ic.getTextBeforeCursor(150, 0);
          if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
              _recv.getHandler().post(() -> {
                  cancelCurrentSuggestionTask();
                  currentSuggestionMode = SuggestionProvider.SuggestionMode.NONE;
                  _recv.showSuggestions(Collections.emptyList(), currentSuggestionMode);
              });
              return;
          }

          int i = textBeforeCursor.length();
          while (i > 0 && Utils.isWordPart(textBeforeCursor.charAt(i - 1))) {
              i--;
          }

          final String prefix = textBeforeCursor.subSequence(i, textBeforeCursor.length()).toString();
          final String prefixLower = prefix.toLowerCase();

          // Cache check
          final String fullContext = textBeforeCursor.toString();
          synchronized (_suggestionCache) {
              if (fullContext.equals(_suggestionCache.context)) {
                  _recv.getHandler().post(() -> _recv.showSuggestions(_suggestionCache.suggestions, _suggestionCache.mode));
                  return;
              }
          }

          // Use n-gram tokenizer for consistent context extraction
          String contextPart = textBeforeCursor.subSequence(0, i).toString();
          List<String> allWords = NextWordProbability.tokenize(contextPart);
          final String contextString;
          if (!allWords.isEmpty()) {
              int startIdx = Math.max(0, allWords.size() - NextWordProbability.MAX_CONTEXT_LENGTH);
              List<String> contextWords = allWords.subList(startIdx, allWords.size());

              synchronized (contextHistory) {
                  contextHistory.clear();
                  contextHistory.addAll(contextWords);
              }

              StringBuilder sb = new StringBuilder();
              for (String w : contextWords) {
                  if (sb.length() > 0) sb.append(" ");
                  sb.append(w);
              }
              contextString = sb.toString();
          } else {
              synchronized (contextHistory) {
                  contextHistory.clear();
              }
              contextString = null;
          }

          final boolean charBeforeMatch = i == 0 || Character.isWhitespace(textBeforeCursor.charAt(i - 1)) || textBeforeCursor.charAt(i - 1) == '\n';

          lastPrefix = prefixLower;
          if (contextString != null) {
              lastContext = contextString;
          }
          if (mSuggestionsEnabledForThisInput) {
              _recv.updateTypingHUD(prefixLower, null, false);
          }

          cancelCurrentSuggestionTask();
          final SuggestionTask newTask = new SuggestionTask(taskId, prefixLower, contextString, fullContext, charBeforeMatch);
          currentSuggestionTask = newTask;

          if (newTask.isCancelled()) return;

          SuggestionProvider.SuggestionMode finalMode;
          if (newTask.prefix.isEmpty()) {
              finalMode = SuggestionProvider.SuggestionMode.NEXT_WORD;
          } else if (!newTask.charBeforeMatch) {
              finalMode = SuggestionProvider.SuggestionMode.NONE;
          } else {
              finalMode = SuggestionProvider.SuggestionMode.PREFIX;
          }

          if (finalMode == SuggestionProvider.SuggestionMode.NONE) {
               postSuggestions(newTask, Collections.emptyList(), finalMode);
               return;
          }

          // Call the centralized search engine once
          String sourcePriority = Config.globalConfig().suggestion_source_priority;
          String searchPriority = Config.globalConfig().suggestion_search_priority;

          List<SuggestionProvider.Suggestion> results = _suggestionProvider.getSuggestions(
              newTask.prefix,
              newTask.context,
              500,
              newTask,
              sourcePriority,
              searchPriority,
              _autoCorrectionProvider,
              _keyboardAwareSuggester
          );

          String phraseTrigger = getLastNWords(fullContext, Config.globalConfig().clipboard_autocomplete_min_words);

          if (Config.globalConfig().clipboard_show_strip_suggestions && phraseTrigger != null && !phraseTrigger.isEmpty()) {
              lastPhraseTrigger = phraseTrigger;
              List<ClipboardHistoryService.SentenceMatch> matches = ClipboardHistoryService.get_service(_recv.getContext()).getSentenceCompletions(phraseTrigger);
              for (ClipboardHistoryService.SentenceMatch m : matches) {
                  boolean exact = m.originalTyped.equalsIgnoreCase(m.correctedPrefix);
                  String suggestionText;
                  if (exact) {
                      // exact match prefix, suggest suffix
                      suggestionText = m.completion;
                      // Ensure suffix doesn't start with a partial word
                      if (!suggestionText.isEmpty() && !Character.isWhitespace(suggestionText.charAt(0)) && !m.correctedPrefix.isEmpty() && !Character.isWhitespace(m.correctedPrefix.charAt(m.correctedPrefix.length()-1))) {
                           // Broken word suffix, skip
                           continue;
                      }
                  } else {
                      // fuzzy match prefix, suggest full phrase
                      suggestionText = m.correctedPrefix + m.completion;
                  }

                  if (!suggestionText.trim().isEmpty()) {
                      SuggestionProvider.Suggestion s = new SuggestionProvider.Suggestion(suggestionText, exact ? "clipboard_phrase_suffix" : "clipboard_phrase_full", 0, SuggestionProvider.WordSource.CLIPBOARD);
                      if (exact) results.add(0, s); else results.add(s);
                  }
              }
          }

          if (newTask.isCancelled()) return;

          final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(results, newTask.context);
          postSuggestions(newTask, prioritized, finalMode);

          if (newTask.charBeforeMatch && !newTask.prefix.isEmpty()) {
              updateGhostAutocompletion(ic, fullContext);
          }
      });
  }

  private void cancelCurrentSuggestionTask() {
      SuggestionTask task = currentSuggestionTask;
      if (task != null) {
          task.cancel();
      }
  }

  private void postSuggestions(SuggestionTask task, List<SuggestionProvider.Suggestion> suggestions, SuggestionProvider.SuggestionMode mode) {
      _recv.getHandler().post(() -> {
          if (task.isCancelled()) return;

          synchronized (_suggestionCache) {
              _suggestionCache.context = task.fullContext;
              _suggestionCache.suggestions = suggestions;
              _suggestionCache.mode = mode;
          }
          currentSuggestionMode = mode;
          _recv.showSuggestions(suggestions, mode);
      });
  }

  private boolean isWordInDictionary(String word) {
      String script = Utils.isUrdu(word) ? "ur" : "en";
      File customDictFile = new File(_recv.getContext().getFilesDir(), "custom_" + script + ".txt");
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


  public void replaceCurrentWord(SuggestionProvider.Suggestion suggestion) {
      _suggestionCache.context = null; // Invalidate cache
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      String wordToReplace = "";
      String finalSuggestion = suggestion.word;

      conn.beginBatchEdit();

      if (suggestion.source != null && suggestion.source.startsWith("clipboard_phrase")) {
          if ("clipboard_phrase_full".equals(suggestion.source) && !lastPhraseTrigger.isEmpty()) {
              commitDeleteSurroundingText(conn, lastPhraseTrigger.length(), 0);
          }
          // if suffix, delete nothing, just append.
      } else if (justAutoCorrected && correctedWord != null) {
          wordToReplace = correctedWord;
          conn.deleteSurroundingText(correctedWord.length() + 1, 0);
          // If we just autocorrected, we should try to match the case of the ORIGINAL word before correction
          if (originalWord != null) {
              finalSuggestion = Utils.matchCase(originalWord, suggestion.word);
          }
      } else {
          CharSequence textBeforeCursor = conn.getTextBeforeCursor(50, 0);
          if (textBeforeCursor != null) {
              int i = textBeforeCursor.length();
              while (i > 0 && Utils.isWordPart(textBeforeCursor.charAt(i - 1))) {
                  i--;
              }
              int wordLength = textBeforeCursor.length() - i;
              if (wordLength > 0) {
                  wordToReplace = textBeforeCursor.subSequence(i, textBeforeCursor.length()).toString();
                  commitDeleteSurroundingText(conn, wordLength, 0);
                  finalSuggestion = Utils.matchCase(wordToReplace, suggestion.word);
              }
          }
      }

      recordWordAndSequence(finalSuggestion);

      currentSuggestionMode = SuggestionProvider.SuggestionMode.NEXT_WORD;
      lastContext = getContextString();
      if (mSuggestionsEnabledForThisInput) {
          _recv.updateTypingHUD(wordToReplace, finalSuggestion, true);
      }

      String spacing = Config.globalConfig().space_after_suggestion ? " " : "";
      recordAndCommitText(conn, finalSuggestion + spacing);
      conn.endBatchEdit();

      if (Config.globalConfig().space_after_suggestion) {
          _autocap.typed(" ");
      }

      final String ctx = lastContext;
      final SuggestionProvider.SuggestionMode mode = currentSuggestionMode;
      KeyboardExecutors.SUGGESTION_EXECUTOR.execute(() -> {
          List<SuggestionProvider.Suggestion> raw = _suggestionProvider.getNextWordSuggestions(ctx);
          final List<SuggestionProvider.Suggestion> prioritized = filterAndPrioritize(raw, ctx);
          _recv.getHandler().post(() -> _recv.showSuggestions(prioritized, mode));
      });

      justAutoCorrected = false;
      expectedCursorPos = -1;
      originalWord = null;
      correctedWord = null;
  }

  private void revertAutoCorrection(boolean learnWord) {
      _suggestionCache.context = null; // Invalidate cache
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null || correctedWord == null || originalWord == null) {
          return;
      }




      CharSequence selected = conn.getSelectedText(0);
      if (selected != null && selected.length() > 0) {
          justAutoCorrected = false;
          expectedCursorPos = -1;
          return;
      }

      conn.beginBatchEdit();

      _undoRedoManager.beginBatch();
      commitDeleteSurroundingText(conn, correctedWord.length() + 1, 0);
      recordAndCommitText(conn, originalWord);
      _undoRedoManager.endBatch();
      conn.endBatchEdit();


      revertedWords.add(originalWord.toLowerCase());

      if (learnWord && Config.globalConfig().add_user_words_on_double_space && originalWord.length() > 1 && !isWordInDictionary(originalWord)) {
          updateCustomDictionary(java.util.Collections.singleton(originalWord));
      }

      justAutoCorrected = false;
      expectedCursorPos = -1;
      originalWord = null;
      correctedWord = null;


      updateSuggestionsFromPrefix();
  }

  private void handleDoubleSpaceKey() {
      if (justAutoCorrected) {
          revertAutoCorrection(true);
          sendTextVerbatim(" ", false);
      } else {
          sendTextVerbatim(" ", false);
      }
  }

  public static interface IReceiver
  {
    public void handle_event_key(KeyValue.Event ev);
    public void set_shift_state(boolean state, boolean lock);
    public void set_compose_pending(boolean pending);
    public void selection_state_changed(boolean selection_is_ongoing);
    void showSuggestions(java.util.List<SuggestionProvider.Suggestion> suggestions, SuggestionProvider.SuggestionMode mode);
    void reloadCustomDictionary();
    void updateRenameBuffer(String text);
    void onRenameConfirmed(String newName);
    void onRenameCancelled();
    void showUndoPasteButton();
    void handleCustomCommand(String command);
    public InputConnection getCurrentInputConnection();
    public EditorInfo getCurrentInputEditorInfo();
    public Handler getHandler();
    public android.content.Context getContext();
    void showTutorial(String tutorial);
    java.util.Map<Character, android.graphics.RectF> getKeyCoordinates();
    String getScript();
    void updateTypingHUD(String typed, String corrected, boolean showArrow);
    View getKeyboardView();
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

  public void learnFromTextField() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      CharSequence selectedText = conn.getSelectedText(0);
      final String textToLearn;

      if (selectedText != null && selectedText.length() > 0) {
          textToLearn = selectedText.toString();
      } else {
          ExtractedText extractedText = conn.getExtractedText(new ExtractedTextRequest(), 0);
          if (extractedText == null || extractedText.text == null) {
              Toast.makeText(_recv.getContext(), "No text to learn.", Toast.LENGTH_SHORT).show();
              return;
          }
          textToLearn = extractedText.text.toString();
      }

      if (textToLearn.isEmpty()) {
          Toast.makeText(_recv.getContext(), "No text to learn.", Toast.LENGTH_SHORT).show();
          return;
      }

      if (Config.globalConfig().learn_new_words && !Config.globalConfig().incognito_mode) {
          KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
              _suggestionProvider.learnFromText(textToLearn);
          });
          invalidateCache();
          triggerUpdateSuggestions();
          Toast.makeText(_recv.getContext(), "Learned from text field.", Toast.LENGTH_SHORT).show();
      } else {
          Toast.makeText(_recv.getContext(), "Learning disabled in settings.", Toast.LENGTH_SHORT).show();
      }
  }

  private void addSelectedTextToDictionaryBatch() {
      InputConnection conn = _recv.getCurrentInputConnection();
      if (conn == null) return;

      CharSequence selectedText = conn.getSelectedText(0);
      if (selectedText == null || selectedText.length() == 0) {
          Toast.makeText(_recv.getContext(), "No text selected.", Toast.LENGTH_SHORT).show();
          return;
      }

      String sanitizedText = selectedText.toString().replaceAll("[^\\p{L}]+", " ");
      String[] words = sanitizedText.trim().split("\\s+");
      java.util.Set<String> uniqueWords = new java.util.HashSet<>();
      for (String word : words) {
          String finalWord = word.toLowerCase();
          if (finalWord.length() > 1 && !isWordInDictionary(finalWord)) {
              uniqueWords.add(finalWord);
          }
      }

      if (uniqueWords.isEmpty()) {
          Toast.makeText(_recv.getContext(), "All words are already in the dictionary, single characters, or the selection is empty.", Toast.LENGTH_SHORT).show();
          return;
      }

      updateCustomDictionary(uniqueWords);
      Toast.makeText(_recv.getContext(), uniqueWords.size() + " words added to custom dictionary", Toast.LENGTH_SHORT).show();
  }

   private void handleGeneratePassword() {
       juloo.keyboard2.passwordmanager.PasswordGenerator.Options options = new juloo.keyboard2.passwordmanager.PasswordGenerator.Options();
       options.length = 40;
       String password = juloo.keyboard2.passwordmanager.PasswordGenerator.generatePassword(options);

       sendTextVerbatim(password);

       Intent intent = new Intent(Intent.ACTION_VIEW);
       intent.setComponent(new ComponentName(_recv.getContext(), "juloo.keyboard2.passwordmanager.ui.SavePasswordActivity"));
       intent.putExtra("password", password);


       EditorInfo editorInfo = _recv.getCurrentInputEditorInfo();
       if (editorInfo != null && editorInfo.packageName != null) {
           intent.putExtra("package_name", editorInfo.packageName);
       }

       intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       _recv.getContext().startActivity(intent);
   }










    private void handleAutofillPassword() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setComponent(new ComponentName(_recv.getContext(), "juloo.keyboard2.passwordmanager.ui.AutofillSelectorActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        _recv.getContext().startActivity(intent);
    }

    private void recordAndCommitText(InputConnection ic, CharSequence text) {
        if (ic == null) return;
        currentCompletionGhostText = null;
        if (text.length() > 0) _ghost_accept_history.clear();
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            _undoRedoManager.recordReplace(selected.toString(), text.toString());
        } else {
            _undoRedoManager.recordInsert(text.toString());
        }
        ic.commitText(text, 1);
    }

    private boolean commitDeleteSurroundingText(InputConnection ic, int before, int after) {
        if (ic == null) return false;
        CharSequence b = ic.getTextBeforeCursor(before, 0);
        CharSequence a = ic.getTextAfterCursor(after, 0);
        _undoRedoManager.recordDelete(b != null ? b.toString() : "", a != null ? a.toString() : "");
        return ic.deleteSurroundingText(before, after);
    }

    private void handleDeleteWord(InputConnection conn, boolean forward) {
        if (conn == null) return;


        CharSequence selected = conn.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            recordAndCommitText(conn, "");
            return;
        }

        if (forward) {
            CharSequence textAfter = conn.getTextAfterCursor(50, 0);
            if (textAfter == null || textAfter.length() == 0) return;
            int i = 0;




            while (i < textAfter.length() && !Utils.isWordPart(textAfter.charAt(i))) {
                i++;
            }
            while (i < textAfter.length() && Utils.isWordPart(textAfter.charAt(i))) {
                i++;
            }
            if (i > 0) {
                commitDeleteSurroundingText(conn, 0, i);
            }
        } else {
            CharSequence textBefore = conn.getTextBeforeCursor(50, 0);
            if (textBefore == null || textBefore.length() == 0) return;
            int i = textBefore.length();

            while (i > 0 && !Utils.isWordPart(textBefore.charAt(i - 1))) {
                i--;
            }

            while (i > 0 && Utils.isWordPart(textBefore.charAt(i - 1))) {
                i--;
            }
            int deleteLen = textBefore.length() - i;
            if (deleteLen > 0) {
                commitDeleteSurroundingText(conn, deleteLen, 0);
            }
        }
    }

    public void handleMoveWord(InputConnection conn, int count, boolean forward, boolean withSelection) {
        if (conn == null) return;

        ExtractedText et = get_cursor_pos(conn);
        if (et == null) return;

        int currentStart = et.selectionStart;
        int currentEnd = et.selectionEnd;

        if (currentStart > currentEnd) {
             int tmp = currentStart;
             currentStart = currentEnd;
             currentEnd = tmp;
        }

        int targetOffset = 0;

        for (int c = 0; c < count; c++) {
            if (forward) {






                CharSequence after = conn.getTextAfterCursor(100 + targetOffset, 0);
                if (after == null || after.length() <= targetOffset) break;






                String text = after.toString();
                if (targetOffset > 0) {
                    if (targetOffset >= text.length()) break;
                    text = text.substring(targetOffset);
                }

                int i = 0;

                while (i < text.length() && !Utils.isWordPart(text.charAt(i))) {
                    i++;
                }

                while (i < text.length() && Utils.isWordPart(text.charAt(i))) {
                    i++;
                }

                if (i == 0 && text.length() > 0) i = 1;

                targetOffset += i;
            } else {

                CharSequence before = conn.getTextBeforeCursor(100 + targetOffset, 0);
                if (before == null || before.length() <= targetOffset) break;

                String text = before.toString();



                int len = text.length();
                int effectiveEnd = len - targetOffset;
                if (effectiveEnd <= 0) break;

                int i = effectiveEnd;

                while (i > 0 && !Utils.isWordPart(text.charAt(i - 1))) {
                    i--;
                }

                while (i > 0 && Utils.isWordPart(text.charAt(i - 1))) {
                    i--;
                }

                int moveAmount = effectiveEnd - i;
                if (moveAmount == 0 && effectiveEnd > 0) moveAmount = 1;

                targetOffset += moveAmount;
            }
        }

        if (withSelection) {



            if (forward) {
                int newEnd = currentEnd + targetOffset;
                conn.setSelection(currentStart, newEnd);
            } else {
                int newStart = currentStart - targetOffset;
                if (newStart < 0) newStart = 0;
                conn.setSelection(newStart, currentEnd);
            }
        } else {

            if (forward) {
                int newPos = currentEnd + targetOffset;
                conn.setSelection(newPos, newPos);
            } else {
                int newPos = currentStart - targetOffset;
                if (newPos < 0) newPos = 0;
                conn.setSelection(newPos, newPos);
            }
        }
    }

    private void insertTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM-dd-yyyy-h-mm-a", Locale.US);
        String timestamp = sdf.format(new Date()).toUpperCase();
        send_text(timestamp);
    }

    public void handleSelectCurrentWord(InputConnection conn) {
        if (conn == null) return;

        ExtractedText et = get_cursor_pos(conn);
        if (et == null) return;

        int currentPos = et.selectionStart;

        CharSequence textBefore = conn.getTextBeforeCursor(50, 0);
        CharSequence textAfter = conn.getTextAfterCursor(50, 0);

        int start = 0;
        if (textBefore != null) {
            int i = textBefore.length();
            while (i > 0 && Utils.isWordPart(textBefore.charAt(i - 1))) {
                i--;
            }
            start = currentPos - (textBefore.length() - i);
        }

        int end = currentPos;
        if (textAfter != null) {
            int i = 0;
            while (i < textAfter.length() && Utils.isWordPart(textAfter.charAt(i))) {
                i++;
            }
            end = currentPos + i;
        }

        if (start < end) {
            conn.setSelection(start, end);
        }
    }
}
