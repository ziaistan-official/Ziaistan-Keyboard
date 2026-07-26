package juloo.keyboard2;

import android.view.KeyEvent;
import java.util.HashMap;

public final class KeyValue implements Comparable<KeyValue>
{
  public static enum Event
  {
    CONFIG,
    SWITCH_TEXT,
    SWITCH_NUMERIC,
    SWITCH_EMOJI,
    SWITCH_BACK_EMOJI,
    SWITCH_CLIPBOARD,
    SWITCH_BACK_CLIPBOARD,
    SWITCH_TYPING_HISTORY,
    CHANGE_METHOD_PICKER,
    CHANGE_METHOD_AUTO,
    ACTION,
    SWITCH_FORWARD,
    SWITCH_BACKWARD,
    SWITCH_GREEKMATH,
    CAPS_LOCK,
    SWITCH_VOICE_TYPING,
    SWITCH_VOICE_TYPING_CHOOSER,
    CYCLE_THEME,
    SET_THEME_GALACTIC,
    SET_THEME_GOLDEN_PEARL,
    SET_THEME_NEON_PUNK,
    SET_THEME_EVERFOREST_LIGHT,
    SET_THEME_COBALT,
    SET_THEME_EPAPER,
    EXPORT_DATA,
    LEARN_FROM_TEXT,
    OPEN_PASSWORD_MANAGER,
    GENERATE_PASSWORD,
    AUTOFILL_PASSWORD,
    OPEN_SECURE_NOTES,
    OPEN_QUICK_NOTE,
    OPEN_TERMUX_COMMANDS,
    MOUSE_PAD,
    SEARCH_REPLACE,
    TOGGLE_SUGGESTIONS,
    SWITCH_GLYPHS,
    INSERT_TIMESTAMP,
    DOUBLE_SPACE,
  }


  public static enum Modifier
  {
    SHIFT,
    GESTURE,
    CTRL,
    ALT,
    META,
    DOUBLE_AIGU,
    DOT_ABOVE,
    DOT_BELOW,
    GRAVE,
    AIGU,
    CIRCONFLEXE,
    TILDE,
    CEDILLE,
    TREMA,
    HORN,
    HOOK_ABOVE,
    DOUBLE_GRAVE,
    SUPERSCRIPT,
    SUBSCRIPT,
    RING,
    CARON,
    MACRON,
    ORDINAL,
    ARROWS,
    BOX,
    OGONEK,
    SLASH,
    ARROW_RIGHT,
    BREVE,
    BAR,
    FN,
    SELECTION_MODE,
  }

  public static enum Editing
  {
    COPY,
    PASTE,
    CUT,
    SELECT_ALL,
    PASTE_PLAIN,
    UNDO,
    REDO,

    REPLACE,
    SHARE,
    ASSIST,
    AUTOFILL,
    DELETE_WORD,
    FORWARD_DELETE_WORD,
    SELECTION_CANCEL,
    ADD_TO_DICTIONARY,
    ADD_TO_DICTIONARY_BATCH,
    MOVE_WORD_BACKWARD_1,
    MOVE_WORD_FORWARD_1,
    MOVE_WORD_BACKWARD_2,
    MOVE_WORD_FORWARD_2,
    MOVE_WORD_BACKWARD_3,
    MOVE_WORD_FORWARD_3,
    MOVE_WORD_BACKWARD_4,
    MOVE_WORD_FORWARD_4,
    MOVE_WORD_BACKWARD_5,
    MOVE_WORD_FORWARD_5,
  }

  public static enum Placeholder
  {
    REMOVED,
    COMPOSE_CANCEL,
    F11,
    F12,
    SHINDOT,
    SINDOT,
    OLE,
    METEG
  }

  public static enum Kind
  {
    Char, Keyevent, Event, Compose_pending, Hangul_initial, Hangul_medial,
    Modifier, Editing, Placeholder,
    String,
    Slider,
    Macro,
    ModifiedChar,
  }

  private static final int FLAGS_OFFSET = 20;
  private static final int KIND_OFFSET = 28;


  public static final int FLAG_LATCH = (1 << FLAGS_OFFSET << 0);

  public static final int FLAG_DOUBLE_TAP_LOCK = (1 << FLAGS_OFFSET << 1);


  public static final int FLAG_SPECIAL = (1 << FLAGS_OFFSET << 2);


  public static final int FLAG_GREYED = (1 << FLAGS_OFFSET << 3);

  public static final int FLAG_KEY_FONT = (1 << FLAGS_OFFSET << 4);

  public static final int FLAG_SMALLER_FONT = (1 << FLAGS_OFFSET << 5);

  public static final int FLAG_SECONDARY = (1 << FLAGS_OFFSET << 6);

  public static final int FLAG_TINY_FONT = (1 << FLAGS_OFFSET << 7);


  private static final int FLAGS_BITS = (0b11111111 << FLAGS_OFFSET);
  private static final int KIND_BITS = (0b1111 << KIND_OFFSET);
  private static final int VALUE_BITS = 0b11111111111111111111;

  static
  {
    check((FLAGS_BITS & KIND_BITS) == 0);
    check(~(FLAGS_BITS | KIND_BITS) == VALUE_BITS);
    check((FLAGS_BITS | KIND_BITS | VALUE_BITS) == ~0);

    check((((Kind.values().length - 1) << KIND_OFFSET) & ~KIND_BITS) == 0);
  }


  private final Comparable _payload;


  private final int _code;

  public Kind getKind()
  {
    return Kind.values()[(_code & KIND_BITS) >>> KIND_OFFSET];
  }

  public int getFlags()
  {
    return (_code & FLAGS_BITS);
  }

  public boolean hasFlagsAny(int has)
  {
    return ((_code & has) != 0);
  }


  public String getString()
  {
    return _payload.toString();
  }


  public char getChar()
  {
    return (char)(_code & VALUE_BITS);
  }


  public int getMetaState()
  {
    return (Integer)_payload;
  }


  public int getKeyevent()
  {
    return (_code & VALUE_BITS);
  }


  public Event getEvent()
  {
    return Event.values()[(_code & VALUE_BITS)];
  }


  public Modifier getModifier()
  {
    return Modifier.values()[(_code & VALUE_BITS)];
  }


  public Editing getEditing()
  {
    return Editing.values()[(_code & VALUE_BITS)];
  }


  public Placeholder getPlaceholder()
  {
    return Placeholder.values()[(_code & VALUE_BITS)];
  }


  public int getPendingCompose()
  {
    return (_code & VALUE_BITS);
  }


  public int getHangulPrecomposed()
  {
    return (_code & VALUE_BITS);
  }


  public Slider getSlider()
  {
    return (Slider)_payload;
  }


  public int getSliderRepeat()
  {
    return ((int)(short)(_code & VALUE_BITS));
  }


  public KeyValue[] getMacro()
  {
    return ((Macro)_payload).keys;
  }


  public KeyValue withChar(char c)
  {
    return new KeyValue(String.valueOf(c), Kind.Char, c,
        getFlags() & ~(FLAG_KEY_FONT | FLAG_SMALLER_FONT));
  }

  public KeyValue withKeyevent(int code)
  {
    return new KeyValue(getString(), Kind.Keyevent, code, getFlags());
  }

  public KeyValue withFlags(int f)
  {
    return new KeyValue(_payload, _code, _code, f);
  }

  public KeyValue withSymbol(String symbol)
  {
    if (symbol == null) return this;
    int flags = getFlags() & ~(FLAG_KEY_FONT | FLAG_SMALLER_FONT);
    switch (getKind())
    {
      case Char:
      case Keyevent:
      case Event:
      case Compose_pending:
      case Hangul_initial:
      case Hangul_medial:
      case Modifier:
      case Editing:
      case Placeholder:
        if (symbol.length() > 1)
          flags |= FLAG_SMALLER_FONT;
        return new KeyValue(symbol, _code, _code, flags);
      case Macro:
        return makeMacro(symbol, getMacro(), flags);
      case String:
      default:
        return makeMacro(symbol, new KeyValue[]{ this }, flags);
    }
  }

  @Override
  public boolean equals(Object obj)
  {
    return sameKey((KeyValue)obj);
  }

  @Override
  public int compareTo(KeyValue snd)
  {

    int d = (_code & ~FLAGS_BITS) - (snd._code & ~FLAGS_BITS);
    if (d != 0)
      return d;
    d = _code - snd._code;
    if (d != 0)
      return d;


    return _payload.compareTo(snd._payload);
  }


  public boolean sameKey(KeyValue snd)
  {
    if (snd == null)
      return false;
    return _code == snd._code && _payload.compareTo(snd._payload) == 0;
  }

  @Override
  public int hashCode()
  {
    return _payload.hashCode() + _code;
  }

  public String toString()
  {
    int value = _code & VALUE_BITS;
    return "[KeyValue " + getKind().toString() + "+" + getFlags() + "+" + value + " \"" + getString() + "\"]";
  }

  private KeyValue(Comparable p, int kind, int value, int flags)
  {
    if (p == null)
      throw new NullPointerException("KeyValue payload cannot be null");
    _payload = p;
    _code = (kind & KIND_BITS) | (flags & FLAGS_BITS) | (value & VALUE_BITS);
  }

  public KeyValue(Comparable p, Kind k, int v, int f)
  {
    this(p, (k.ordinal() << KIND_OFFSET), v, f);
  }

  private static KeyValue charKey(String symbol, char c, int flags)
  {
    return new KeyValue(symbol, Kind.Char, c, flags);
  }

  private static KeyValue charKey(int symbol, char c, int flags)
  {
    return charKey(String.valueOf((char)symbol), c, flags | FLAG_KEY_FONT);
  }

  private static KeyValue modifierKey(String symbol, Modifier m, int flags)
  {
    if (symbol.length() > 1)
      flags |= FLAG_SMALLER_FONT;
    return new KeyValue(symbol, Kind.Modifier, m.ordinal(),
        FLAG_LATCH | FLAG_SPECIAL | FLAG_SECONDARY | flags);
  }

  private static KeyValue modifierKey(int symbol, Modifier m, int flags)
  {
    return modifierKey(String.valueOf((char)symbol), m, flags | FLAG_KEY_FONT);
  }

  private static KeyValue diacritic(int symbol, Modifier m)
  {
    return new KeyValue(String.valueOf((char)symbol), Kind.Modifier, m.ordinal(),
        FLAG_LATCH | FLAG_SPECIAL | FLAG_KEY_FONT);
  }

  private static KeyValue eventKey(String symbol, Event e, int flags)
  {
    return new KeyValue(symbol, Kind.Event, e.ordinal(), flags | FLAG_SPECIAL | FLAG_SECONDARY);
  }

  private static KeyValue eventKey(int symbol, Event e, int flags)
  {
    return eventKey(String.valueOf((char)symbol), e, flags | FLAG_KEY_FONT);
  }

  public static KeyValue keyeventKey(String symbol, int code, int flags)
  {
    return new KeyValue(symbol, Kind.Keyevent, code, flags | FLAG_SECONDARY);
  }

  public static KeyValue keyeventKey(int symbol, int code, int flags)
  {
    return keyeventKey(String.valueOf((char)symbol), code, flags | FLAG_KEY_FONT);
  }

  private static KeyValue editingKey(String symbol, Editing action, int flags)
  {
    return new KeyValue(symbol, Kind.Editing, action.ordinal(),
        flags | FLAG_SPECIAL | FLAG_SECONDARY);
  }

  private static KeyValue editingKey(String symbol, Editing action)
  {
    return editingKey(symbol, action, FLAG_SMALLER_FONT);
  }

  private static KeyValue editingKey(int symbol, Editing action)
  {
    return editingKey(String.valueOf((char)symbol), action, FLAG_KEY_FONT);
  }


  public static KeyValue sliderKey(Slider s, int repeatition)
  {

    return new KeyValue(s, Kind.Slider, (short)repeatition & 0xFFFF,
        FLAG_SPECIAL | FLAG_SECONDARY | FLAG_KEY_FONT);
  }


  private static KeyValue placeholderKey(Placeholder id)
  {
    return new KeyValue("", Kind.Placeholder, id.ordinal(), 0);
  }

  private static KeyValue placeholderKey(int symbol, Placeholder id, int flags)
  {
    return new KeyValue(String.valueOf((char)symbol), Kind.Placeholder,
        id.ordinal(), flags | FLAG_KEY_FONT);
  }

  public static KeyValue makeStringKey(String str)
  {
    return makeStringKey(str, 0);
  }

  public static KeyValue makeCharKey(char c)
  {
    return makeCharKey(c, null, 0);
  }

  public static KeyValue makeCharKey(char c, String symbol, int flags)
  {
    if (symbol == null)
      symbol = String.valueOf(c);
    return new KeyValue(symbol, Kind.Char, c, flags);
  }

  public static KeyValue makeCharKey(int symbol, char c, int flags)
  {
    return makeCharKey(c, String.valueOf((char)symbol), flags | FLAG_KEY_FONT);
  }

  public static KeyValue makeComposePending(String symbol, int state, int flags)
  {
    return new KeyValue(symbol, Kind.Compose_pending, state,
        flags | FLAG_LATCH);
  }

  public static KeyValue makeComposePending(int symbol, int state, int flags)
  {
    return makeComposePending(String.valueOf((char)symbol), state,
        flags | FLAG_KEY_FONT);
  }

  public static KeyValue makeHangulInitial(String symbol, int initial_idx)
  {
    return new KeyValue(symbol, Kind.Hangul_initial, initial_idx * 588 + 44032,
        FLAG_LATCH);
  }

  public static KeyValue makeHangulMedial(int precomposed, int medial_idx)
  {
    precomposed += medial_idx * 28;
    return new KeyValue(String.valueOf((char)precomposed), Kind.Hangul_medial,
        precomposed, FLAG_LATCH);
  }

  public static KeyValue makeHangulFinal(int precomposed, int final_idx)
  {
    precomposed += final_idx;
    return KeyValue.makeCharKey((char)precomposed);
  }

  public static KeyValue makeActionKey(String symbol)
  {
    return eventKey(symbol, Event.ACTION, FLAG_SMALLER_FONT);
  }


  public static KeyValue makeStringKey(String str, int flags)
  {
    if (str.length() == 1)
      return new KeyValue(str, Kind.Char, str.charAt(0), flags);
    else
      return new KeyValue(str, Kind.String, 0, flags | FLAG_SMALLER_FONT);
  }

  public static KeyValue makeModifiedCharKey(char c, int metaState)
  {
    return new KeyValue(metaState, Kind.ModifiedChar, c, 0);
  }

  public static KeyValue makeMacro(String symbol, KeyValue[] keys, int flags)
  {
    if (symbol.length() > 1)
      flags |= FLAG_SMALLER_FONT;
    return new KeyValue(new Macro(keys, symbol), Kind.Macro, 0, flags);
  }


  public static KeyValue makeInternalModifier(Modifier mod)
  {
    return new KeyValue("", Kind.Modifier, mod.ordinal(), 0);
  }


  public static KeyValue getKeyByName(String name)
  {
    if (name == null) return null;
    name = name.trim();

    if (name.startsWith(":")) {
        try { return KeyValueParser.parse(name); }
        catch (KeyValueParser.ParseError _e) { return makeStringKey(name); }
    }

    KeyValue k = getSpecialKeyByName(name);
    if (k != null) return k;

    if (name.contains(":")) {
        int idx = name.lastIndexOf(':');
        String symbol = name.substring(0, idx);
        String payload = name.substring(idx + 1);
        if (payload.length() > 0) {
            KeyValue pk = getSpecialKeyByName(payload);
            if (pk != null) return pk.withSymbol(symbol);
        }
        try { return KeyValueParser.parse(name); }
        catch (KeyValueParser.ParseError _e) {}
    }

    try { return KeyValueParser.parse(name); }
    catch (KeyValueParser.ParseError _e) { return makeStringKey(name); }
  }

  private static java.util.Map<Integer, String> keycodeToName = null;
  private static java.util.Map<String, String> symbolToName = null;

  public static String getCanonicalName(KeyValue kv) {
      if (kv == null) return "";
      String action = getRawActionName(kv);
      String symbol = kv.getString();

      if (!isDefaultSymbol(kv, symbol, action)) {
          return symbol + ":" + action;
      }
      return action;
  }

  private static boolean isDefaultSymbol(KeyValue kv, String symbol, String action) {
      if (kv.getKind() == Kind.Char) {
          return symbol.length() == 1 && symbol.charAt(0) == kv.getChar();
      }
      KeyValue def = getSpecialKeyByName(action);
      if (def != null) return symbol.equals(def.getString());
      return symbol.equals(action);
  }

  public static String getRawActionName(KeyValue kv) {
      if (kv == null) return "";

      if (kv.getKind() == Kind.Editing) {
          switch (kv.getEditing()) {
              case COPY: return "copy";
              case PASTE: return "paste";
              case CUT: return "cut";
              case SELECT_ALL: return "select_all";
              case PASTE_PLAIN: return "paste_as_plain_text";
              case UNDO: return "undo";
              case REDO: return "redo";
              case REPLACE: return "replace_text";
              case SHARE: return "share_text";
              case ASSIST: return "text_assist";
              case AUTOFILL: return "autofill";
              case DELETE_WORD: return "delete_word";
              case FORWARD_DELETE_WORD: return "forward_delete_word";
              case SELECTION_CANCEL: return "selection_cancel";
              case MOVE_WORD_BACKWARD_1: return "move_word_backward_1";
              case MOVE_WORD_FORWARD_1: return "move_word_forward_1";
              case MOVE_WORD_BACKWARD_2: return "move_word_backward_2";
              case MOVE_WORD_FORWARD_2: return "move_word_forward_2";
              case MOVE_WORD_BACKWARD_3: return "move_word_backward_3";
              case MOVE_WORD_FORWARD_3: return "move_word_forward_3";
              case MOVE_WORD_BACKWARD_4: return "move_word_backward_4";
              case MOVE_WORD_FORWARD_4: return "move_word_forward_4";
              case MOVE_WORD_BACKWARD_5: return "move_word_backward_5";
              case MOVE_WORD_FORWARD_5: return "move_word_forward_5";
              default: return kv.getEditing().name().toLowerCase();
          }
      }

      if (kv.getKind() == Kind.Event) {
          switch (kv.getEvent()) {
              case CONFIG: return "config";
              case SWITCH_TEXT: return "switch_text";
              case SWITCH_NUMERIC: return "switch_numeric";
              case SWITCH_EMOJI: return "switch_emoji";
              case SWITCH_BACK_EMOJI: return "switch_back_emoji";
              case SWITCH_CLIPBOARD: return "switch_clipboard";
              case SWITCH_BACK_CLIPBOARD: return "switch_back_clipboard";
              case SWITCH_TYPING_HISTORY: return "typing_history";
              case SWITCH_FORWARD: return "switch_forward";
              case SWITCH_BACKWARD: return "switch_backward";
              case SWITCH_GREEKMATH: return "switch_greekmath";
              case CHANGE_METHOD_PICKER: return "change_method";
              case CHANGE_METHOD_AUTO: return "change_method_prev";
              case ACTION: return "action";
              case CAPS_LOCK: return "capslock";
              case SWITCH_VOICE_TYPING: return "voice_typing";
              case SWITCH_VOICE_TYPING_CHOOSER: return "voice_typing_chooser";
              case CYCLE_THEME: return "cycle_theme";
              case EXPORT_DATA: return "export_data";
              case LEARN_FROM_TEXT: return "learn_from_text";
              case OPEN_PASSWORD_MANAGER: return "password_manager";
              case GENERATE_PASSWORD: return "generate_password";
              case AUTOFILL_PASSWORD: return "autofill_password";
              case OPEN_SECURE_NOTES: return "secure_notes";
              case OPEN_QUICK_NOTE: return "quick_note";
              case OPEN_TERMUX_COMMANDS: return "termux_commands";
              case MOUSE_PAD: return "mouse_pad";
              case SEARCH_REPLACE: return "search_replace";
              case TOGGLE_SUGGESTIONS: return "toggle_suggestions";
              case SWITCH_GLYPHS: return "switch_glyphs";
              case INSERT_TIMESTAMP: return "insert_timestamp";
              default: return kv.getEvent().name().toLowerCase();
          }
      }

      if (kv.getKind() == Kind.Modifier) {
          switch (kv.getModifier()) {
              case SHIFT: return "shift";
              case CTRL: return "ctrl";
              case ALT: return "alt";
              case META: return "meta";
              case FN: return "fn";
              case GESTURE: return "gesture";
              case SELECTION_MODE: return "selection_mode";
              default: return kv.getModifier().name().toLowerCase();
          }
      }

      if (kv.getKind() == Kind.Keyevent) {
          if (keycodeToName == null) {
              keycodeToName = new java.util.HashMap<>();
              String[] names = {"esc", "enter", "up", "right", "down", "left", "page_up", "page_down", "home", "end", "backspace", "delete", "insert", "tab", "menu"};
              for (String name : names) {
                  KeyValue k = getSpecialKeyByName(name);
                  if (k != null && k.getKind() == Kind.Keyevent) {
                      keycodeToName.put(k.getKeyevent(), name);
                  }
              }
          }
          String name = keycodeToName.get(kv.getKeyevent());
          if (name != null) return name;
      }

      if (kv.getKind() == Kind.Macro) {
          StringBuilder sb = new StringBuilder();
          KeyValue[] subkeys = kv.getMacro();
          for (int i = 0; i < subkeys.length; i++) {
              if (i > 0) sb.append(",");
              sb.append(getRawActionName(subkeys[i]));
          }
          return sb.toString();
      }

      if (symbolToName == null) {
          symbolToName = new java.util.HashMap<>();
          String[] specialNames = {"space", "nbsp", "nnbsp", "zwj", "zwnj"};
          for (String name : specialNames) {
              KeyValue special = getSpecialKeyByName(name);
              if (special != null) {
                  symbolToName.put(special.getString(), name);
              }
          }
      }

      if (kv.getKind() == Kind.Char) {
          char c = kv.getChar();
          if (c == '?') return "\\?";
          if (c == '#') return "\\#";
          if (c == '@') return "\\@";
          if (c == '\\') return "\\\\";
          return String.valueOf(c);
      }

      String s = kv.getString();
      if (symbolToName != null && symbolToName.containsKey(s)) return symbolToName.get(s);
      return s;
  }

  public static KeyValue getSpecialKeyByName(String name)
  {
    if (name == null) return null;
    switch (name.toLowerCase())
    {

      case "\\?": return makeStringKey("?");
      case "\\#": return makeStringKey("#");
      case "\\@": return makeStringKey("@");
      case "\\\\": return makeStringKey("\\");


      case "shift": return modifierKey(0xE00A, Modifier.SHIFT, FLAG_DOUBLE_TAP_LOCK);
      case "ctrl": return modifierKey("Ctrl", Modifier.CTRL, 0);
      case "alt": return modifierKey("Alt", Modifier.ALT, 0);
      case "accent_aigu": return diacritic(0xE050, Modifier.AIGU);
      case "accent_caron": return diacritic(0xE051, Modifier.CARON);
      case "accent_cedille": return diacritic(0xE052, Modifier.CEDILLE);
      case "accent_circonflexe": return diacritic(0xE053, Modifier.CIRCONFLEXE);
      case "accent_grave": return diacritic(0xE054, Modifier.GRAVE);
      case "accent_macron": return diacritic(0xE055, Modifier.MACRON);
      case "accent_ring": return diacritic(0xE056, Modifier.RING);
      case "accent_tilde": return diacritic(0xE057, Modifier.TILDE);
      case "accent_trema": return diacritic(0xE058, Modifier.TREMA);
      case "accent_ogonek": return diacritic(0xE059, Modifier.OGONEK);
      case "accent_dot_above": return diacritic(0xE05A, Modifier.DOT_ABOVE);
      case "accent_double_aigu": return diacritic(0xE05B, Modifier.DOUBLE_AIGU);
      case "accent_slash": return diacritic(0xE05C, Modifier.SLASH);
      case "accent_arrow_right": return diacritic(0xE05D, Modifier.ARROW_RIGHT);
      case "accent_breve": return diacritic(0xE05E, Modifier.BREVE);
      case "accent_bar": return diacritic(0xE05F, Modifier.BAR);
      case "accent_dot_below": return diacritic(0xE060, Modifier.DOT_BELOW);
      case "accent_horn": return diacritic(0xE061, Modifier.HORN);
      case "accent_hook_above": return diacritic(0xE062, Modifier.HOOK_ABOVE);
      case "accent_double_grave": return diacritic(0xE063, Modifier.DOUBLE_GRAVE);
      case "superscript": return modifierKey("Sup", Modifier.SUPERSCRIPT, 0);
      case "subscript": return modifierKey("Sub", Modifier.SUBSCRIPT, 0);
      case "ordinal": return modifierKey("Ord", Modifier.ORDINAL, 0);
      case "arrows": return modifierKey("Arr", Modifier.ARROWS, 0);
      case "box": return modifierKey("Box", Modifier.BOX, 0);
      case "fn": return modifierKey("Fn", Modifier.FN, 0);
      case "meta": return modifierKey("Meta", Modifier.META, 0);



      case "combining_dot_above": return makeCharKey(0xE15A, '\u0307', 0);
      case "combining_double_aigu": return makeCharKey(0xE15B, '\u030B', 0);
      case "combining_slash": return makeCharKey(0xE15C, '\u0337', 0);
      case "combining_arrow_right": return makeCharKey(0xE15D, '\u20D7', 0);
      case "combining_breve": return makeCharKey(0xE15E, '\u0306', 0);
      case "combining_bar": return makeCharKey(0xE15F, '\u0335', 0);
      case "combining_aigu": return makeCharKey(0xE150, '\u0301', 0);
      case "combining_caron": return makeCharKey(0xE151, '\u030C', 0);
      case "combining_cedille": return makeCharKey(0xE152, '\u0327', 0);
      case "combining_circonflexe": return makeCharKey(0xE153, '\u0302', 0);
      case "combining_grave": return makeCharKey(0xE154, '\u0300', 0);
      case "combining_macron": return makeCharKey(0xE155, '\u0304', 0);
      case "combining_ring": return makeCharKey(0xE156, '\u030A', 0);
      case "combining_tilde": return makeCharKey(0xE157, '\u0303', 0);
      case "combining_trema": return makeCharKey(0xE158, '\u0308', 0);
      case "combining_ogonek": return makeCharKey(0xE159, '\u0328', 0);
      case "combining_dot_below": return makeCharKey(0xE160, '\u0323', 0);
      case "combining_horn": return makeCharKey(0xE161, '\u031B', 0);
      case "combining_hook_above": return makeCharKey(0xE162, '\u0309', 0);

      case "combining_vertical_tilde": return makeCharKey(0xE200, '\u033E', 0);
      case "combining_inverted_breve": return makeCharKey(0xE201, '\u0311', 0);
      case "combining_pokrytie": return makeCharKey(0xE202, '\u0487', 0);
      case "combining_slavonic_psili": return makeCharKey(0xE203, '\u0486', 0);
      case "combining_slavonic_dasia": return makeCharKey(0xE204, '\u0485', 0);
      case "combining_payerok": return makeCharKey(0xE205, '\uA67D', 0);
      case "combining_titlo": return makeCharKey(0xE206, '\u0483', 0);
      case "combining_vzmet": return makeCharKey(0xE207, '\uA66F', 0);
      case "combining_arabic_v": return makeCharKey(0xE208, '\u065A', 0);
      case "combining_arabic_inverted_v": return makeCharKey(0xE209, '\u065B', 0);
      case "combining_shaddah": return makeCharKey(0xE210, '\u0651', 0);
      case "combining_sukun": return makeCharKey(0xE211, '\u0652', 0);
      case "combining_fatha": return makeCharKey(0xE212, '\u064E', 0);
      case "combining_dammah": return makeCharKey(0xE213, '\u064F', 0);
      case "combining_kasra": return makeCharKey(0xE214, '\u0650', 0);
      case "combining_hamza_above": return makeCharKey(0xE215, '\u0654', 0);
      case "combining_hamza_below": return makeCharKey(0xE216, '\u0655', 0);
      case "combining_alef_above": return makeCharKey(0xE217, '\u0670', 0);
      case "combining_fathatan": return makeCharKey(0xE218, '\u064B', 0);
      case "combining_kasratan": return makeCharKey(0xE219, '\u064D', 0);
      case "combining_dammatan": return makeCharKey(0xE220, '\u064C', 0);
      case "combining_alef_below": return makeCharKey(0xE221, '\u0656', 0);
      case "combining_kavyka": return makeCharKey(0xE222, '\uA67C', 0);
      case "combining_palatalization": return makeCharKey(0xE223, '\u0484', 0);


      case "config": return eventKey("config", Event.CONFIG, FLAG_SMALLER_FONT);
      case "switch_text": return eventKey("ABC", Event.SWITCH_TEXT, FLAG_SMALLER_FONT);
      case "switch_numeric": return eventKey("123+", Event.SWITCH_NUMERIC, FLAG_SMALLER_FONT);
      case "switch_emoji": return eventKey("switch_emoji", Event.SWITCH_EMOJI, FLAG_SMALLER_FONT);
      case "switch_back_emoji": return eventKey("ABC", Event.SWITCH_BACK_EMOJI, 0);
      case "switch_clipboard":
      case "clipboard":
      case "clipboard_pane": return eventKey("switch_clipboard", Event.SWITCH_CLIPBOARD, 0);
      case "switch_back_clipboard": return eventKey("ABC", Event.SWITCH_BACK_CLIPBOARD, 0);
      case "typing_history": return eventKey("♲", Event.SWITCH_TYPING_HISTORY, FLAG_SMALLER_FONT);
      case "switch_forward": return eventKey("switch_forward", Event.SWITCH_FORWARD, FLAG_SMALLER_FONT);
      case "switch_backward": return eventKey("switch_backward", Event.SWITCH_BACKWARD, FLAG_SMALLER_FONT);
      case "switch_greekmath": return eventKey("πλ∇¬", Event.SWITCH_GREEKMATH, FLAG_SMALLER_FONT);
      case "change_method": return eventKey("change_method", Event.CHANGE_METHOD_PICKER, FLAG_SMALLER_FONT);
      case "change_method_prev": return eventKey("change_method_prev", Event.CHANGE_METHOD_AUTO, FLAG_SMALLER_FONT);
      case "action": return eventKey("Action", Event.ACTION, FLAG_SMALLER_FONT);
      case "capslock": return eventKey("capslock", Event.CAPS_LOCK, 0);
      case "voice_typing": return eventKey("voice_typing", Event.SWITCH_VOICE_TYPING, FLAG_SMALLER_FONT);
      case "voice_typing_chooser": return eventKey("voice_typing_chooser", Event.SWITCH_VOICE_TYPING_CHOOSER, FLAG_SMALLER_FONT);
      case "cycle_theme": return eventKey("cycle_theme", Event.CYCLE_THEME, FLAG_TINY_FONT);
      case "set_theme_galactic": return eventKey("𓊆", Event.SET_THEME_GALACTIC, FLAG_TINY_FONT);
      case "set_theme_golden_pearl": return eventKey("𓊇", Event.SET_THEME_GOLDEN_PEARL, FLAG_TINY_FONT);
      case "set_theme_neon_punk": return eventKey("𓊈", Event.SET_THEME_NEON_PUNK, FLAG_TINY_FONT);
      case "set_theme_everforest_light": return eventKey("𓊉", Event.SET_THEME_EVERFOREST_LIGHT, FLAG_TINY_FONT);
      case "set_theme_cobalt": return eventKey("𓄲", Event.SET_THEME_COBALT, FLAG_TINY_FONT);
      case "set_theme_epaper": return eventKey("𓄳", Event.SET_THEME_EPAPER, FLAG_TINY_FONT);
      case "export_data": return eventKey("➣", Event.EXPORT_DATA, 0);
      case "learn_from_text": return eventKey("⍟", Event.LEARN_FROM_TEXT, FLAG_SMALLER_FONT);
      case "password_manager": return eventKey("𓇳", Event.OPEN_PASSWORD_MANAGER, FLAG_TINY_FONT);
      case "generate_password": return eventKey("𓏲", Event.GENERATE_PASSWORD, FLAG_TINY_FONT);
      case "autofill_password": return eventKey("𓍯", Event.AUTOFILL_PASSWORD, FLAG_TINY_FONT);
      case "secure_notes": return eventKey("🗒", Event.OPEN_SECURE_NOTES, 0);
      case "quick_note": return eventKey("✎", Event.OPEN_QUICK_NOTE, 0);
      case "termux_commands": return eventKey("⌨", Event.OPEN_TERMUX_COMMANDS, 0);
      case "mouse_pad": return eventKey("🖱", Event.MOUSE_PAD, 0);
      case "search_replace":
      case "find_replace":
      case "find & replace": return eventKey("🔍", Event.SEARCH_REPLACE, 0);
      case "toggle_suggestions": return eventKey("⇶", Event.TOGGLE_SUGGESTIONS, 0);
      case "switch_glyphs": return eventKey("𓇔", Event.SWITCH_GLYPHS, 0);
      case "insert_timestamp": return eventKey("timestamp", Event.INSERT_TIMESTAMP, FLAG_SMALLER_FONT);
      case "double_space": return eventKey("double_space", Event.DOUBLE_SPACE, FLAG_SMALLER_FONT);


      case "esc": return keyeventKey("Esc", KeyEvent.KEYCODE_ESCAPE, FLAG_SMALLER_FONT);
      case "enter": return keyeventKey("enter", KeyEvent.KEYCODE_ENTER, 0);
      case "up": return keyeventKey("up", KeyEvent.KEYCODE_DPAD_UP, 0);
      case "right": return keyeventKey("right", KeyEvent.KEYCODE_DPAD_RIGHT, FLAG_SMALLER_FONT);
      case "down": return keyeventKey("down", KeyEvent.KEYCODE_DPAD_DOWN, 0);
      case "left": return keyeventKey("left", KeyEvent.KEYCODE_DPAD_LEFT, FLAG_SMALLER_FONT);
      case "page_up": return keyeventKey("page_up", KeyEvent.KEYCODE_PAGE_UP, 0);
      case "page_down": return keyeventKey("page_down", KeyEvent.KEYCODE_PAGE_DOWN, 0);
      case "home": return keyeventKey("home", KeyEvent.KEYCODE_MOVE_HOME, FLAG_SMALLER_FONT);
      case "end": return keyeventKey("end", KeyEvent.KEYCODE_MOVE_END, FLAG_SMALLER_FONT);
      case "backspace":
      case "back": return keyeventKey("backspace", KeyEvent.KEYCODE_DEL, 0);
      case "delete":
      case "del": return keyeventKey("delete", KeyEvent.KEYCODE_FORWARD_DEL, 0);
      case "insert": return keyeventKey("Ins", KeyEvent.KEYCODE_INSERT, FLAG_SMALLER_FONT);
      case "f1": return keyeventKey("F1", KeyEvent.KEYCODE_F1, 0);
      case "f2": return keyeventKey("F2", KeyEvent.KEYCODE_F2, 0);
      case "f3": return keyeventKey("F3", KeyEvent.KEYCODE_F3, 0);
      case "f4": return keyeventKey("F4", KeyEvent.KEYCODE_F4, 0);
      case "f5": return keyeventKey("F5", KeyEvent.KEYCODE_F5, 0);
      case "f6": return keyeventKey("F6", KeyEvent.KEYCODE_F6, 0);
      case "f7": return keyeventKey("F7", KeyEvent.KEYCODE_F7, 0);
      case "f8": return keyeventKey("F8", KeyEvent.KEYCODE_F8, 0);
      case "f9": return keyeventKey("F9", KeyEvent.KEYCODE_F9, 0);
      case "f10": return keyeventKey("F10", KeyEvent.KEYCODE_F10, 0);
      case "f11": return keyeventKey("F11", KeyEvent.KEYCODE_F11, FLAG_SMALLER_FONT);
      case "f12": return keyeventKey("F12", KeyEvent.KEYCODE_F12, FLAG_SMALLER_FONT);
      case "tab": return keyeventKey(0xE00F, KeyEvent.KEYCODE_TAB, FLAG_SMALLER_FONT);
      case "menu": return keyeventKey("Menu", KeyEvent.KEYCODE_MENU, FLAG_SMALLER_FONT);
      case "scroll_lock": return keyeventKey("Scrl", KeyEvent.KEYCODE_SCROLL_LOCK, FLAG_SMALLER_FONT);


      case "\\t": return charKey("\\t", '\t', 0);
      case "\\n": return charKey("\\n", '\n', 0);
      case "space": return charKey("space", ' ', FLAG_SMALLER_FONT | FLAG_GREYED);
      case "nbsp": return charKey("\u237d", '\u00a0', FLAG_SMALLER_FONT);
      case "nnbsp": return charKey("\u2423", '\u202F', FLAG_SMALLER_FONT);


      case "lrm": return charKey("↱", '\u200e', 0);
      case "rlm": return charKey("↰", '\u200f', 0);
      case "b(": return charKey("(", ')', 0);
      case "b)": return charKey(")", '(', 0);
      case "b[": return charKey("[", ']', 0);
      case "b]": return charKey("]", '[', 0);
      case "b{": return charKey("{", '}', 0);
      case "b}": return charKey("}", '{', 0);
      case "blt": return charKey("<", '>', 0);
      case "bgt": return charKey(">", '<', 0);


      case "qamats": return charKey("\u05E7\u05B8", '\u05B8', 0);
      case "patah": return charKey("\u05E4\u05B7", '\u05B7', 0);
      case "sheva": return charKey("\u05E9\u05B0", '\u05B0', 0);
      case "dagesh": return charKey("\u05D3\u05BC", '\u05BC', 0);
      case "hiriq": return charKey("\u05D7\u05B4", '\u05B4', 0);
      case "segol": return charKey("\u05E1\u05B6", '\u05B6', 0);
      case "tsere": return charKey("\u05E6\u05B5", '\u05B5', 0);
      case "holam": return charKey("\u05D5\u05B9", '\u05B9', 0);
      case "qubuts": return charKey("\u05E7\u05BB", '\u05BB', 0);
      case "hataf_patah": return charKey("\u05D7\u05B2\u05E4\u05B7", '\u05B2', 0);
      case "hataf_qamats": return charKey("\u05D7\u05B3\u05E7\u05B8", '\u05B3', 0);
      case "hataf_segol": return charKey("\u05D7\u05B1\u05E1\u05B6", '\u05B1', 0);
      case "shindot": return charKey("\u05E9\u05C1", '\u05C1', 0);
      case "shindot_placeholder": return placeholderKey(Placeholder.SHINDOT);
      case "sindot": return charKey("\u05E9\u05C2", '\u05C2', 0);
      case "sindot_placeholder": return placeholderKey(Placeholder.SINDOT);

      case "geresh": return charKey("\u05F3", '\u05F3', 0);
      case "gershayim": return charKey("\u05F4", '\u05F4', 0);
      case "maqaf": return charKey("\u05BE", '\u05BE', 0);

      case "rafe": return charKey("\u05E4\u05BF", '\u05BF', 0);
      case "ole": return charKey("\u05E2\u05AB", '\u05AB', 0);
      case "ole_placeholder": return placeholderKey(Placeholder.OLE);
      case "meteg": return charKey("\u05DE\u05BD", '\u05BD', 0);
      case "meteg_placeholder": return placeholderKey(Placeholder.METEG);

      case "zwj": return charKey("zwj", '\u200D', 0);
      case "zwnj":
      case "halfspace": return charKey("zwnj", '\u200C', 0);


      case "add_to_dictionary": return editingKey("add_to_dict", Editing.ADD_TO_DICTIONARY);
      case "add_to_dictionary_batch": return editingKey("++", Editing.ADD_TO_DICTIONARY_BATCH);
      case "copy": return editingKey("copy", Editing.COPY);
      case "paste": return editingKey("paste", Editing.PASTE);
      case "cut": return editingKey("cut", Editing.CUT);
      case "select_all":
      case "selectall": return editingKey("select_all", Editing.SELECT_ALL);
      case "share_text":
      case "sharetext": return editingKey("share", Editing.SHARE);
      case "paste_as_plain_text":
      case "pasteasplaintext": return editingKey("paste_plain", Editing.PASTE_PLAIN);
      case "undo": return editingKey("undo", Editing.UNDO);
      case "redo": return editingKey("redo", Editing.REDO);
      case "delete_word": return editingKey("del_word", Editing.DELETE_WORD);
      case "forward_delete_word": return editingKey("fwd_del_word", Editing.FORWARD_DELETE_WORD);
      case "cursor_left": return sliderKey(Slider.Cursor_left, 1);
      case "cursor_right": return sliderKey(Slider.Cursor_right, 1);
      case "cursor_up": return sliderKey(Slider.Cursor_up, 1);
      case "cursor_down": return sliderKey(Slider.Cursor_down, 1);
      case "selection_cancel": return editingKey("Esc", Editing.SELECTION_CANCEL, FLAG_SMALLER_FONT);
      case "selection_cursor_left": return sliderKey(Slider.Selection_cursor_left, -1);
      case "selection_cursor_right": return sliderKey(Slider.Selection_cursor_right, 1);
      case "move_word_backward_1": return editingKey("<<", Editing.MOVE_WORD_BACKWARD_1);
      case "move_word_forward_1": return editingKey(">>", Editing.MOVE_WORD_FORWARD_1);
      case "move_word_backward_2": return editingKey("<<", Editing.MOVE_WORD_BACKWARD_2);
      case "move_word_forward_2": return editingKey(">>", Editing.MOVE_WORD_FORWARD_2);
      case "move_word_backward_3": return editingKey("<<", Editing.MOVE_WORD_BACKWARD_3);
      case "move_word_forward_3": return editingKey(">>", Editing.MOVE_WORD_FORWARD_3);
      case "move_word_backward_4": return editingKey("<<", Editing.MOVE_WORD_BACKWARD_4);
      case "move_word_forward_4": return editingKey(">>", Editing.MOVE_WORD_FORWARD_4);
      case "move_word_backward_5": return editingKey("<<", Editing.MOVE_WORD_BACKWARD_5);
      case "move_word_forward_5": return editingKey(">>", Editing.MOVE_WORD_FORWARD_5);

      case "replace_text":
      case "replacetext": return editingKey("repl", Editing.REPLACE);
      case "text_assist":
      case "textassist": return editingKey(0xE038, Editing.ASSIST);
      case "autofill": return editingKey("auto", Editing.AUTOFILL);


      case "compose": return makeComposePending(0xE016, ComposeKeyData.compose, FLAG_SECONDARY);
      case "compose_cancel": return placeholderKey(0xE01A, Placeholder.COMPOSE_CANCEL, FLAG_SECONDARY);


      case "removed": return placeholderKey(Placeholder.REMOVED);
      case "f11_placeholder": return placeholderKey(Placeholder.F11);
      case "f12_placeholder": return placeholderKey(Placeholder.F12);


      case "ㄱ": return makeHangulInitial("ㄱ", 0);
      case "ㄲ": return makeHangulInitial("ㄲ", 1);
      case "ㄴ": return makeHangulInitial("ㄴ", 2);
      case "ㄷ": return makeHangulInitial("ㄷ", 3);
      case "ㄸ": return makeHangulInitial("ㄸ", 4);
      case "ㄹ": return makeHangulInitial("ㄹ", 5);
      case "ㅁ": return makeHangulInitial("ㅁ", 6);
      case "ㅂ": return makeHangulInitial("ㅂ", 7);
      case "ㅃ": return makeHangulInitial("ㅃ", 8);
      case "ㅅ": return makeHangulInitial("ㅅ", 9);
      case "ㅆ": return makeHangulInitial("ㅆ", 10);
      case "ㅇ": return makeHangulInitial("ㅇ", 11);
      case "ㅈ": return makeHangulInitial("ㅈ", 12);
      case "ㅉ": return makeHangulInitial("ㅉ", 13);
      case "ㅊ": return makeHangulInitial("ㅊ", 14);
      case "ㅋ": return makeHangulInitial("ㅋ", 15);
      case "ㅌ": return makeHangulInitial("ㅌ", 16);
      case "ㅍ": return makeHangulInitial("ㅍ", 17);
      case "ㅎ": return makeHangulInitial("ㅎ", 18);


      case "ஔ": case "ந": case "ல": case "ழ": case "௯":
      case "ஷ": case "ே": case "௨": case "ஜ": case "ங": case "ன":
      case "௦": case "ை": case "ூ": case "ம": case "ஆ": case "௭":
      case "௪": case "ா": case "ஶ": case "௬": case "வ": case "ஸ":
      case "௮": case "ட": case "ப": case "ஈ": case "௩": case "ஒ":
      case "ௌ": case "உ": case "௫": case "ய": case "ர": case "ு":
      case "இ": case "ோ": case "ஓ": case "ஃ": case "ற": case "த":
      case "௧": case "ண": case "ஏ": case "ஊ": case "ொ": case "ஞ":
      case "அ": case "எ": case "ச": case "ெ": case "ஐ": case "ி":
      case "௹": case "ள": case "ஹ": case "௰": case "ௐ": case "௱":
      case "௲": case "௳":
        return makeStringKey(name, FLAG_SMALLER_FONT);


      case "අ": case "ආ": case "ඇ": case "ඈ": case "ඉ":
      case "ඊ": case "උ": case "ඌ": case "ඍ": case "ඎ":
      case "ඏ": case "ඐ": case "එ": case "ඒ": case "ඓ":
      case "ඔ": case "ඕ": case "ඖ": case "ක": case "ඛ":
      case "ග": case "ඝ": case "ඞ": case "ඟ": case "ච":
      case "ඡ": case "ජ": case "ඣ": case "ඤ": case "ඥ":
      case "ඦ": case "ට": case "ඨ": case "ඩ": case "ඪ":
      case "ණ": case "ඬ": case "ත": case "ථ": case "ද":
      case "ධ": case "න": case "ඳ": case "ප": case "ඵ":
      case "බ": case "භ": case "ම": case "ඹ": case "ය":
      case "ර": case "ල": case "ව": case "ශ": case "ෂ":
      case "ස": case "හ": case "ළ": case "ෆ":

      case "෦": case "෧": case "෨": case "෩": case "෪":
      case "෫": case "෬": case "෭": case "෮": case "෯":
      case "ෲ": case "ෳ":

      case "\u0d81": case "\u0d82": case "\u0d83": case "\u0d8ca":
      case "\u0dcf": case "\u0dd0": case "\u0dd1": case "\u0dd2":
      case "\u0dd3": case "\u0dd4": case "\u0dd6": case "\u0dd8":
      case "\u0dd9": case "\u0dda": case "\u0ddb": case "\u0ddc":
      case "\u0ddd": case "\u0dde": case "\u0ddf":

      case "𑇡": case "𑇢": case "𑇣": case "𑇤": case "𑇥":
      case "𑇦": case "𑇧": case "𑇨": case "𑇩": case "𑇪":
      case "𑇫": case "𑇬": case "𑇭": case "𑇮": case "𑇯":
      case "𑇰": case "𑇱": case "𑇲": case "𑇳": case "𑇴":

      case "෴": case "₨":
        return makeStringKey(name, FLAG_SMALLER_FONT);


      case "selection_mode": return makeInternalModifier(Modifier.SELECTION_MODE);

      default: return null;
    }
  }


  private static void check(boolean b)
  {
    if (!b)
      throw new RuntimeException("Assertion failure");
  }

  public static enum Slider
  {
    Cursor_left(0xE008),
    Cursor_right(0xE006),
    Cursor_up(0xE005),
    Cursor_down(0xE007),
    Selection_cursor_left(0xE008),
    Selection_cursor_right(0xE006);

    final String symbol;

    Slider(int symbol_)
    {
      symbol = String.valueOf((char)symbol_);
    }

    @Override
    public String toString() { return symbol; }
  };

  public static final class Macro implements Comparable<Macro>
  {
    public final KeyValue[] keys;
    private final String _symbol;

    public Macro(KeyValue[] keys_, String sym_)
    {
      keys = keys_;
      _symbol = sym_;
    }

    public String toString() { return _symbol; }

    @Override
    public int compareTo(Macro snd)
    {
      int d = keys.length - snd.keys.length;
      if (d != 0) return d;
      for (int i = 0; i < keys.length; i++)
      {
        d = keys[i].compareTo(snd.keys[i]);
        if (d != 0) return d;
      }
      return _symbol.compareTo(snd._symbol);
    }
  };
}
