package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.Build.VERSION;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public final class Utils
{

  public static String capitalize_string(String s)
  {
    if (s.length() < 1)
      return s;

    int i = s.offsetByCodePoints(0, 1);
    return s.substring(0, i).toUpperCase(Locale.getDefault()) + s.substring(i);
  }


  public static void show_dialog_on_ime(AlertDialog dialog, IBinder token)
  {
    Window win = dialog.getWindow();
    WindowManager.LayoutParams lp = win.getAttributes();
    lp.token = token;
    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    win.setAttributes(lp);
    win.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  public static String read_all_utf8(InputStream inp) throws Exception
  {
    InputStreamReader reader = new InputStreamReader(inp, "UTF-8");
    StringBuilder out = new StringBuilder();
    int buff_length = 8000;
    char[] buff = new char[buff_length];
    int l;
    while ((l = reader.read(buff, 0, buff_length)) != -1)
      out.append(buff, 0, l);
    return out.toString();
  }

  public static boolean isUrdu(String word) {
    if (word == null) return false;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if (c >= 0x0600 && c <= 0x06FF) return true;
    }
    return false;
  }

  public static boolean urduStartsWith(String word, String prefix) {
      if (word == null || prefix == null) return false;
      return normalizeUrdu(word.toLowerCase()).startsWith(normalizeUrdu(prefix.toLowerCase()));
  }

  public static boolean isUrduDiacritic(char c) {
    return (c >= 0x064B && c <= 0x065F) || c == 0x0670;
  }

  public static boolean hasUrduDiacritic(String word) {
    if (word == null) return false;
    for (int i = 0; i < word.length(); i++) {
      if (isUrduDiacritic(word.charAt(i))) return true;
    }
    return false;
  }

  public static String normalizeUrdu(String word) {
    if (word == null) return null;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if (!isUrduDiacritic(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static boolean isWordPart(char c) {
    if (Character.isLetterOrDigit(c)) return true;
    if (c == '\\' || c == ';' || c == ',' || c == '"' || c == '=' || c == '\'' || c == '.' || c == '/') return true;
    int type = Character.getType(c);
    return type == Character.NON_SPACING_MARK ||
           type == Character.COMBINING_SPACING_MARK ||
           type == Character.ENCLOSING_MARK;
  }

  public static boolean containsSpecialSymbol(String word) {
    if (word == null) return false;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if (c == '\\' || c == ';' || c == ',' || c == '"' || c == '=' || c == '\'' || c == '.' || c == '/') {
        return true;
      }
    }
    return false;
  }

  public static boolean hasDigit(String word) {
    if (word == null) return false;
    for (int i = 0; i < word.length(); i++) {
      if (Character.isDigit(word.charAt(i))) return true;
    }
    return false;
  }

  public static boolean isShort(String word) {
    return word != null && word.length() < 3;
  }

  public static boolean isMistakenSpace(char c) {
    char lower = Character.toLowerCase(c);
    return "zxcvbnm".indexOf(lower) != -1 || "زخچوبنم".indexOf(lower) != -1;
  }

  public static boolean fuzzyPhraseMatch(String typed, String stored) {
    if (typed == null || stored == null) return false;
    if (typed.isEmpty()) return true;

    int ti = 0;
    int si = 0;

    while (ti < typed.length() && si < stored.length()) {
      char tc = Character.toLowerCase(typed.charAt(ti));
      char sc = Character.toLowerCase(stored.charAt(si));

      if (tc == sc) {
        ti++;
        si++;
      } else if (sc == ' ' && isMistakenSpace(tc)) {
        ti++;
        si++;
      } else {
        return false;
      }
    }

    return ti == typed.length();
  }

  public static String matchCase(String original, String replacement) {
    if (original == null || original.isEmpty() || replacement == null || replacement.isEmpty()) {
      return replacement;
    }

    boolean allCaps = true;
      boolean titleCase = Character.isUpperCase(original.codePointAt(0));
      boolean firstLower = Character.isLowerCase(original.codePointAt(0));

      for (int i = 0; i < original.length(); i++) {
          if (Character.isLowerCase(original.charAt(i))) {
              allCaps = false;
              break;
          }
      }

      if (allCaps && original.length() > 1) {
          return replacement.toUpperCase(Locale.getDefault());
      } else if (titleCase) {
          // If original is like "PaKiStAn", we try to keep that pattern if possible,
          // but usually title-case is enough.
          // However, the user specifically mentioned "PaKiStAn" remaining as "PaKiStAn".
          // If replacement is longer or shorter, we match the prefix casing.
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < replacement.length(); i++) {
              if (i < original.length()) {
                  if (Character.isUpperCase(original.charAt(i))) {
                      sb.append(Character.toUpperCase(replacement.charAt(i)));
                  } else {
                      sb.append(Character.toLowerCase(replacement.charAt(i)));
                  }
              } else {
                  // For the rest of the replacement, use the last char's case or lowercase
                  sb.append(Character.toLowerCase(replacement.charAt(i)));
              }
          }
          return sb.toString();
      } else if (firstLower) {
          return replacement.toLowerCase(Locale.getDefault());
      }

      return replacement;
  }
}
