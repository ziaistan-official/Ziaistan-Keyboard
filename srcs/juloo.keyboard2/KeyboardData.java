package juloo.keyboard2;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Xml;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.xmlpull.v1.XmlPullParser;

public final class KeyboardData
{
  public final List<Row> rows;

  public final float keysWidth;

  public final float keysHeight;

  public final Modmap modmap;

  public final String script;

  public final String numpad_script;

  public final String name;

  public final boolean bottom_row;

  public final boolean embedded_number_row;

  public final boolean locale_extra_keys;

  public final String wordlist;

  public final Map<Character, List<Character>> surroundings;

  private Map<KeyValue, KeyPos> _key_pos = null;

  public KeyboardData mapKeys(MapKey f)
  {
    ArrayList<Row> rows_ = new ArrayList<Row>();
    for (Row r : rows)
      rows_.add(r.mapKeys(f));
    return new KeyboardData(this, rows_);
  }


  public KeyboardData addExtraKeys(Iterator<Map.Entry<KeyValue, PreferredPos>> extra_keys)
  {

    ArrayList<KeyValue> unplaced_keys = new ArrayList<KeyValue>();
    ArrayList<Row> rows = new ArrayList<Row>(this.rows);
    while (extra_keys.hasNext())
    {
      Map.Entry<KeyValue, PreferredPos> kp = extra_keys.next();
      if (!add_key_to_preferred_pos(rows, kp.getKey(), kp.getValue()))
        unplaced_keys.add(kp.getKey());
    }
    for (KeyValue kv : unplaced_keys)
      add_key_to_preferred_pos(rows, kv, PreferredPos.ANYWHERE);
    return new KeyboardData(this, rows);
  }


  boolean add_key_to_preferred_pos(List<Row> rows, KeyValue kv, PreferredPos pos)
  {
    if (pos.next_to != null)
    {
      KeyPos next_to_pos = getKeys().get(pos.next_to);

      if (next_to_pos != null)
      {
        for (KeyPos p : pos.positions)
          if ((p.row == -1 || p.row == next_to_pos.row)
              && (p.col == -1 || p.col == next_to_pos.col)
              && add_key_to_pos(rows, kv, next_to_pos.with_dir(p.dir)))
            return true;
        if (add_key_to_pos(rows, kv, next_to_pos.with_dir(-1)))
          return true;
      }
    }
    for (KeyPos p : pos.positions)
      if (add_key_to_pos(rows, kv, p))
        return true;
    return false;
  }


  boolean add_key_to_pos(List<Row> rows, KeyValue kv, KeyPos p)
  {
    int i_row = p.row;
    int i_row_end = Math.min(p.row, rows.size() - 1);
    if (p.row == -1) { i_row = 0; i_row_end = rows.size() - 1; }
    for (; i_row <= i_row_end; i_row++)
    {
      Row row = rows.get(i_row);
      int i_col = p.col;
      int i_col_end = Math.min(p.col, row.keys.size() - 1);
      if (p.col == -1) { i_col = 0; i_col_end = row.keys.size() - 1; }
      for (; i_col <= i_col_end; i_col++)
      {
        Key col = row.keys.get(i_col);
        int i_dir = p.dir;
        int i_dir_end = p.dir;
        if (p.dir == -1) { i_dir = 1; i_dir_end = 4; }
        for (; i_dir <= i_dir_end; i_dir++)
        {
          if (col.getKeyValue(i_dir) == null)
          {
            row.keys.set(i_col, col.withKeyValue(i_dir, kv));
            return true;
          }
        }
      }
    }
    return false;
  }

  public KeyboardData addNumPad(KeyboardData num_pad)
  {
    ArrayList<Row> extendedRows = new ArrayList<Row>();
    Iterator<Row> iterNumPadRows = num_pad.rows.iterator();
    for (Row row : rows)
    {
      ArrayList<KeyboardData.Key> keys = new ArrayList<Key>(row.keys);
      if (iterNumPadRows.hasNext())
      {
        Row numPadRow = iterNumPadRows.next();
        List<Key> nps = numPadRow.keys;
        if (nps.size() > 0) {
          float firstNumPadShift = 0.5f + keysWidth - row.keysWidth;
          keys.add(nps.get(0).withShift(firstNumPadShift));
          for (int i = 1; i < nps.size(); i++)
            keys.add(nps.get(i));
        }
      }
      extendedRows.add(new Row(keys, row.height, row.shift));
    }
    return new KeyboardData(this, extendedRows);
  }


  public KeyboardData insert_row(Row row, int i)
  {
    ArrayList<Row> rows_ = new ArrayList<Row>(this.rows);
    rows_.add(i, row.updateWidth(keysWidth));
    return new KeyboardData(this, rows_);
  }

  public Key findKeyWithValue(KeyValue kv)
  {
    KeyPos pos = getKeys().get(kv);
    if (pos == null || pos.row >= rows.size())
      return null;
    return rows.get(pos.row).get_key_at_pos(pos);
  }


  public Map<KeyValue, KeyPos> getKeys()
  {
    if (_key_pos == null)
    {
      _key_pos = new HashMap<KeyValue, KeyPos>();
      for (int r = 0; r < rows.size(); r++)
        rows.get(r).getKeys(_key_pos, r);
    }
    return _key_pos;
  }

  private static Map<Integer, KeyboardData> _layoutCache = new HashMap<Integer, KeyboardData>();

  public static Row load_row(Resources res, int res_id) throws Exception
  {
    return parse_row(res.getXml(res_id));
  }

  public static KeyboardData load_num_pad(Resources res) throws Exception
  {
    return parse_keyboard(res.getXml(R.xml.numpad));
  }


  public static KeyboardData load(Resources res, int id)
  {
    if (_layoutCache.containsKey(id))
      return _layoutCache.get(id);
    KeyboardData l = null;
    XmlResourceParser parser = null;
    try
    {
      parser = res.getXml(id);
      l = parse_keyboard(parser);
    }
    catch (Exception e)
    {
      Logs.exn("Failed to load layout id " + id, e);
    }
    if (parser != null)
      parser.close();
    _layoutCache.put(id, l);
    return l;
  }


  public static KeyboardData load_string(String src)
  {
    try
    {
      return load_string_exn(src);
    }
    catch (Exception e)
    {
      return null;
    }
  }


  private static String escapeXml(String s) {
      if (s == null) return "";
      return s.replace("&", "&amp;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
              .replace("\"", "&quot;")
              .replace("'", "&apos;");
  }

  public static String serialize_to_unified_xml(KeyboardData layout) {
      StringBuilder sb = new StringBuilder();
      sb.append("<!--\n");
      sb.append("  ZIAISTAN KEYBOARD PREMIUM LAYOUT FILE\n");
      sb.append("-->\n\n");
      sb.append("<ziaistan_custom_layout version=\"1\">\n");

      sb.append("  <metadata>\n");
      sb.append("    <layout_name>").append(escapeXml(layout.name != null ? layout.name : "Custom")).append("</layout_name>\n");
      sb.append("  </metadata>\n");


      sb.append("  <keyboard script=\"").append(escapeXml(layout.script)).append("\"");
      if (layout.wordlist != null) sb.append(" wordlist=\"").append(escapeXml(layout.wordlist)).append("\"");
      sb.append(">\n");
      for (Row row : layout.rows) {
          sb.append("    <row height=\"").append(row.height).append("\" shift=\"").append(row.shift).append("\">\n");
          for (Key key : row.keys) {
              sb.append("      <key width=\"").append(key.width).append("\" shift=\"").append(key.shift).append("\"");
              if (key.borderRadius >= 0) sb.append(" border_radius=\"").append(key.borderRadius).append("\"");

              String[] synonyms = {"c", "nw", "ne", "sw", "se", "w", "e", "n", "s"};
              for (int i = 0; i < 9; i++) {
                  if (key.keys[i] != null) {
                      sb.append(" ").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.keys[i]))).append("\"");
                  }
                  if (key.circle != null && key.circle[i] != null) {
                       sb.append(" circ_").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.circle[i]))).append("\"");
                  }
                  if (key.anticircle_v2 != null && key.anticircle_v2[i] != null) {
                       sb.append(" anti_").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.anticircle_v2[i]))).append("\"");
                  }
                  if (key.colorLight[i] != null) sb.append(" ").append(synonyms[i]).append("_color_light=\"").append(escapeXml(key.colorLight[i])).append("\"");
                  if (key.colorDark[i] != null) sb.append(" ").append(synonyms[i]).append("_color_dark=\"").append(escapeXml(key.colorDark[i])).append("\"");
                  if (key.labelScales[i] > 0) sb.append(" ").append(synonyms[i]).append("_scale=\"").append(key.labelScales[i]).append("\"");
              }
              if (key.anticircle != null) sb.append(" anticircle=\"").append(escapeXml(KeyValue.getCanonicalName(key.anticircle))).append("\"");
              if (key.indication != null) sb.append(" indication=\"").append(escapeXml(key.indication)).append("\"");
              sb.append(" />\n");
          }
          sb.append("    </row>\n");
      }
      sb.append("  </keyboard>\n");

      if (layout.modmap != null) {
          sb.append("  <modmap>\n");
          for (Modmap.M m : Modmap.M.values()) {
              Map<KeyValue, KeyValue> map = layout.modmap._map[m.ordinal()];
              if (map != null) {
                  String tagName = m.name().toLowerCase();
                  for (Map.Entry<KeyValue, KeyValue> entry : map.entrySet()) {
                      sb.append("    <").append(tagName)
                        .append(" a=\"").append(escapeXml(KeyValue.getCanonicalName(entry.getKey())))
                        .append("\" b=\"").append(escapeXml(KeyValue.getCanonicalName(entry.getValue())))
                        .append("\" />\n");
                  }
              }
          }
          sb.append("  </modmap>\n");
      }

      Map<Character, List<Character>> map = layout.surroundings;
      if (map == null) map = KeyboardLayoutAnalyzer.getAdjacencyMap(layout);

      if (map != null && !map.isEmpty()) {
          sb.append("  <surroundings>\n");
          for (Map.Entry<Character, List<Character>> entry : map.entrySet()) {
              sb.append("    <char value=\"").append(escapeXml(String.valueOf(entry.getKey()))).append("\" neighbors=\"");
              for (char c : entry.getValue()) sb.append(escapeXml(String.valueOf(c)));
              sb.append("\" />\n");
          }
          sb.append("  </surroundings>\n");
      }

      sb.append("</ziaistan_custom_layout>");
      return sb.toString();
  }

  public static KeyboardData load_string_exn(String src) throws Exception
  {
    XmlPullParser parser = Xml.newPullParser();
    parser.setInput(new StringReader(src));
    int eventType = parser.getEventType();
    while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
        eventType = parser.next();
    }
    if (eventType == XmlPullParser.END_DOCUMENT) throw new Exception("Empty XML");

    if (parser.getName().equals("ziaistan_custom_layout")) {
        return parse_unified_layout(parser);
    }
    return parse_keyboard(parser);
  }

  private static KeyboardData parse_unified_layout(XmlPullParser parser) throws Exception {
      KeyboardData data = null;
      String layoutName = null;
      Map<Character, List<Character>> surroundings = null;
      while (next_tag(parser)) {
          String tag = parser.getName();
          if (tag.equals("metadata")) {
              while (next_tag(parser)) {
                  if (parser.getName().equals("layout_name")) {
                      layoutName = parser.nextText();
                  } else {
                      while (parser.next() != XmlPullParser.END_TAG) continue;
                  }
              }
          } else if (tag.equals("theme")) {
              parse_embedded_theme(parser);
          } else if (tag.equals("keyboard")) {
              data = parse_keyboard_content(parser);
          } else if (tag.equals("surroundings")) {
              surroundings = parse_surroundings(parser);
          } else {
              while (parser.next() != XmlPullParser.END_TAG) continue;
          }
      }
      if (data != null) {
          String script = (data.script != null && !data.script.isEmpty()) ? data.script : KeyboardLayoutAnalyzer.detectScript(data.rows);
          data = new KeyboardData(data.rows, data.keysWidth, data.modmap, script, data.numpad_script, layoutName != null ? layoutName : data.name, data.bottom_row, data.embedded_number_row, data.locale_extra_keys, data.wordlist, surroundings != null ? surroundings : data.surroundings);
      }
      return data;
  }

  private static void parse_embedded_theme(XmlPullParser parser) throws Exception {
      android.content.SharedPreferences.Editor editor = Config.globalPrefs().edit();
      String themeId = "embedded_" + System.currentTimeMillis();
      editor.putString("theme", themeId);
      while (next_tag(parser)) {
          String key = parser.getAttributeValue(null, "name");
          String val = parser.getAttributeValue(null, "value");
          if (key != null && val != null) {
              if (val.startsWith("#")) {
                  editor.putInt("theme_color_" + key + "_" + themeId, android.graphics.Color.parseColor(val));
              } else {
                  try {
                      editor.putInt("theme_" + key + "_" + themeId, Integer.parseInt(val));
                  } catch (NumberFormatException e) {
                      editor.putString("theme_" + key + "_" + themeId, val);
                  }
              }
          }
          while (parser.next() != XmlPullParser.END_TAG) continue;
      }
      editor.apply();
      if (Config.globalConfig() != null) Config.globalConfig().themeName = themeId;
  }

  private static KeyboardData parse_keyboard(XmlPullParser parser) throws Exception
  {
    if (!expect_tag(parser, "keyboard"))
      throw error(parser, "Expected tag <keyboard>");
    return parse_keyboard_content(parser);
  }

  private static KeyboardData parse_keyboard_content(XmlPullParser parser) throws Exception
  {
    String layoutTheme = parser.getAttributeValue(null, "theme");
    if (layoutTheme != null && Config.globalConfig() != null) {
        Config.globalPrefs().edit().putString("theme", layoutTheme).apply();
    }
    boolean bottom_row = attribute_bool(parser, "bottom_row", true);
    boolean embedded_number_row = attribute_bool(parser, "embedded_number_row", false);
    boolean locale_extra_keys = attribute_bool(parser, "locale_extra_keys", true);
    float specified_kw = attribute_float(parser, "width", 0f);
    String script = parser.getAttributeValue(null, "script");
    if (script != null && script.equals(""))
      throw error(parser, "'script' attribute cannot be empty");
    String numpad_script = parser.getAttributeValue(null, "numpad_script");
    if (numpad_script == null)
      numpad_script = script;
    else if (numpad_script.equals(""))
      throw error(parser, "'numpad_script' attribute cannot be empty");
    String name = parser.getAttributeValue(null, "name");
    String wordlist = parser.getAttributeValue(null, "wordlist");
    ArrayList<Row> rows = new ArrayList<Row>();
    Modmap modmap = null;
    Map<Character, List<Character>> surroundings = null;
    while (next_tag(parser))
    {
      switch (parser.getName())
      {
        case "row":
          rows.add(Row.parse(parser));
          break;
        case "modmap":
          if (modmap != null)
            throw error(parser, "Multiple '<modmap>' are not allowed");
          modmap = parse_modmap(parser);
          break;
        case "surroundings":
          surroundings = parse_surroundings(parser);
          break;
        default:
          throw error(parser, "Expecting tag <row>, got <" + parser.getName() + ">");
      }
    }
    float kw = (specified_kw != 0f) ? specified_kw : compute_max_width(rows);
    return new KeyboardData(rows, kw, modmap, script, numpad_script, name, bottom_row, embedded_number_row, locale_extra_keys, wordlist, surroundings);
  }

  private static Map<Character, List<Character>> parse_surroundings(XmlPullParser parser) throws Exception {
      Map<Character, List<Character>> map = new HashMap<>();
      int eventType;
      while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
          if (eventType == XmlPullParser.START_TAG) {
              String tagName = parser.getName();
              if (tagName.equals("extra") || tagName.equals("char")) {
                  String key = parser.getAttributeValue(null, "key");
                  if (key == null) key = parser.getAttributeValue(null, "value");
                  String neighbors = parser.getAttributeValue(null, "neighbors");
                  if (key != null && neighbors != null && !key.isEmpty()) {
                      List<Character> list = new ArrayList<>();
                      String cleanNeighbors = neighbors.replace("\u00A0", "").replace(",", "");
                      for (char c : cleanNeighbors.toCharArray()) {
                          if (!Character.isWhitespace(c)) list.add(c);
                      }
                      map.put(key.charAt(0), list);
                  }
              }
              // Skip children of the tag we just found
              int depth = 1;
              while (depth != 0) {
                  int ev = parser.next();
                  if (ev == XmlPullParser.START_TAG) depth++;
                  else if (ev == XmlPullParser.END_TAG) depth--;
              }
          } else if (eventType == XmlPullParser.END_TAG) {
              // Hit </surroundings>
              break;
          }
      }
      return map;
  }

  private static float compute_max_width(List<Row> rows)
  {
    float w = 0.f;
    for (Row r : rows)
      w = Math.max(w, r.keysWidth);
    return w;
  }

  private static Row parse_row(XmlPullParser parser) throws Exception
  {
    if (!expect_tag(parser, "row"))
      throw error(parser, "Expected tag <row>");
    return Row.parse(parser);
  }

  protected KeyboardData(List<Row> rows_, float kw, Modmap mm, String sc,
      String npsc, String name_, boolean bottom_row_, boolean embedded_number_row_, boolean locale_extra_keys_, String wordlist_)
  {
    this(rows_, kw, mm, sc, npsc, name_, bottom_row_, embedded_number_row_, locale_extra_keys_, wordlist_, null);
  }

  protected KeyboardData(List<Row> rows_, float kw, Modmap mm, String sc,
      String npsc, String name_, boolean bottom_row_, boolean embedded_number_row_, boolean locale_extra_keys_, String wordlist_, Map<Character, List<Character>> surr)
  {
    float kh = 0.f;
    for (Row r : rows_)
      kh += r.height + r.shift;
    rows = rows_;
    modmap = mm;
    script = sc;
    numpad_script = npsc;
    name = name_;
    keysWidth = Math.max(kw, 1f);
    keysHeight = kh;
    bottom_row = bottom_row_;
    embedded_number_row = embedded_number_row_;
    locale_extra_keys = locale_extra_keys_;
    wordlist = wordlist_;
    surroundings = surr;
  }


  protected KeyboardData(KeyboardData src, List<Row> rows)
  {
    this(rows, compute_max_width(rows), src.modmap, src.script,
        src.numpad_script, src.name, src.bottom_row, src.embedded_number_row, src.locale_extra_keys, src.wordlist, src.surroundings);
  }

  public static class Row
  {
    public final List<Key> keys;

    public final float height;

    public final float shift;

    public final float keysWidth;

    protected Row(List<Key> keys_, float h, float s)
    {
      float kw = 0.f;
      for (Key k : keys_) kw += k.width + k.shift;
      keys = keys_;
      height = Math.max(h, keys_.size() == 0 ? 0.0f : 0.5f);
      shift = Math.max(s, 0f);
      keysWidth = kw;
    }

    public static Row parse(XmlPullParser parser) throws Exception
    {
      ArrayList<Key> keys = new ArrayList<Key>();
      int status;
      float h = attribute_float(parser, "height", 1f);
      float shift = attribute_float(parser, "shift", 0f);
      float scale = attribute_float(parser, "scale", 0f);
      while (expect_tag(parser, "key"))
        keys.add(Key.parse(parser));
      Row row = new Row(keys, h, shift);
      if (scale > 0f)
        row = row.updateWidth(scale);
      return row;
    }

    public Row copy()
    {
      return new Row(new ArrayList<Key>(keys), height, shift);
    }

    public void getKeys(Map<KeyValue, KeyPos> dst, int row)
    {
      for (int c = 0; c < keys.size(); c++)
        keys.get(c).getKeys(dst, row, c);
    }

    public Map<KeyValue, KeyPos> getKeys(int row)
    {
      Map<KeyValue, KeyPos> dst = new HashMap<KeyValue, KeyPos>();
      getKeys(dst, row);
      return dst;
    }

    public Row mapKeys(MapKey f)
    {
      ArrayList<Key> keys_ = new ArrayList<Key>();
      for (Key k : keys)
        keys_.add(f.apply(k));
      return new Row(keys_, height, shift);
    }


    public Row updateWidth(float newWidth)
    {
      final float s = newWidth / keysWidth;
      return mapKeys(new MapKey(){
        public Key apply(Key k) { return k.scaleWidth(s); }
      });
    }

    public Key get_key_at_pos(KeyPos pos)
    {
      if (pos.col >= keys.size())
        return null;
      return keys.get(pos.col);
    }
  }

  public static class Key
  {

    public final KeyValue[] keys;

    public final KeyValue[] circle;
    public final KeyValue[] anticircle_v2;

    public final KeyValue anticircle;

    private final int keysflags;

    public final float width;

    public final float shift;

    public final float borderRadius;

    public final String indication;

    public final float[] labelScales;

    public final String[] colorDark;

    public final String[] colorLight;


    public static final int F_LOC = 1;
    public static final int ALL_FLAGS = F_LOC;

    protected Key(KeyValue[] ks, KeyValue antic, int f, float w, float s, String i, String[] cd, String[] cl)
    {
      this(ks, new KeyValue[9], new KeyValue[9], antic, f, w, s, -1f, i, new float[9], cd, cl);
    }

    protected Key(KeyValue[] ks, KeyValue[] circ, KeyValue[] anti_v2, KeyValue antic, int f, float w, float s, float br, String i, float[] ls, String[] cd, String[] cl)
    {
      keys = ks;
      circle = circ;
      anticircle_v2 = anti_v2;
      anticircle = antic;
      keysflags = f;
      width = Math.max(w, 0f);
      shift = Math.max(s, 0f);
      borderRadius = br;
      indication = i;
      labelScales = ls != null ? ls : new float[9];
      colorDark = cd;
      colorLight = cl;
    }

    static final Key EMPTY = new Key(new KeyValue[9], new KeyValue[9], new KeyValue[9], null, 0, 1.f, 1.f, -1f, null, new float[9], new String[9], new String[9]);


    static String get_key_attr(XmlPullParser parser, String syn1, String syn2)
        throws Exception
    {
      String name1 = parser.getAttributeValue(null, syn1);
      String name2 = parser.getAttributeValue(null, syn2);
      if (name1 != null && name2 != null)
        throw error(parser,
            "'"+syn1+"' and '"+syn2+"' are synonyms and cannot be passed at the same time.");
      return (name1 == null) ? name2 : name1;
    }


    static int parse_key_attr(XmlPullParser parser, String key_val, KeyValue[] ks,
        int index)
        throws Exception
    {
      if (key_val == null)
        return 0;
      int flags = 0;
      String name_loc = stripPrefix(key_val, "loc ");
      if (name_loc != null)
      {
        flags |= F_LOC;
        key_val = name_loc;
      }
      ks[index] = KeyValue.getKeyByName(key_val);
      return (flags << index);
    }

    static KeyValue parse_nonloc_key_attr(XmlPullParser parser, String attr_name) throws Exception
    {
      String name = parser.getAttributeValue(null, attr_name);
      if (name == null)
        return null;
      return KeyValue.getKeyByName(name);
    }

    static String stripPrefix(String s, String prefix)
    {
      return s.startsWith(prefix) ? s.substring(prefix.length()) : null;
    }

    public static Key parse(XmlPullParser parser) throws Exception
    {
      KeyValue[] ks = new KeyValue[9];
      KeyValue[] circ = new KeyValue[9];
      KeyValue[] anti_v2 = new KeyValue[9];
      String[] colorDark = new String[9];
      String[] colorLight = new String[9];
      float[] labelScales = new float[9];
      int keysflags = 0;

      String[] synonyms = {"c", "nw", "ne", "sw", "se", "w", "e", "n", "s"};
      for (int i = 0; i < 9; i++) {
        String keyAttr = get_key_attr(parser, "key" + i, synonyms[i]);
        if (keyAttr != null) {
            keysflags |= parse_key_attr(parser, keyAttr, ks, i);
        }

        circ[i] = parse_nonloc_key_attr(parser, "circ_" + synonyms[i]);
        anti_v2[i] = parse_nonloc_key_attr(parser, "anti_" + synonyms[i]);

        colorDark[i] = parser.getAttributeValue(null, synonyms[i] + "_color_dark");
        if (colorDark[i] == null && i == 0) colorDark[i] = parser.getAttributeValue(null, "color_dark");

        colorLight[i] = parser.getAttributeValue(null, synonyms[i] + "_color_light");
        if (colorLight[i] == null && i == 0) colorLight[i] = parser.getAttributeValue(null, "color_light");

        String scaleStr = parser.getAttributeValue(null, synonyms[i] + "_scale");
        if (scaleStr != null) {
            labelScales[i] = Float.parseFloat(scaleStr);
        } else if (i == 0) {
            scaleStr = parser.getAttributeValue(null, "label_scale");
            if (scaleStr != null) labelScales[0] = Float.parseFloat(scaleStr);
        }
      }

      KeyValue anticircle = parse_nonloc_key_attr(parser, "anticircle");
      float width = attribute_float(parser, "width", 1f);
      float shift = attribute_float(parser, "shift", 0.f);
      float borderRadius = attribute_float(parser, "border_radius", -1f);
      String indication = parser.getAttributeValue(null, "indication");
      String labelScale = parser.getAttributeValue(null, "label_scale");
      if (labelScale != null && indication == null) {
          indication = "scale:" + labelScale;
      }
      while (parser.next() != XmlPullParser.END_TAG)
        continue;
      return new Key(ks, circ, anti_v2, anticircle, keysflags, width, shift, borderRadius, indication, labelScales, colorDark, colorLight);
    }


    public boolean keyHasFlag(int index, int flag)
    {
      return (keysflags & (flag << index)) != 0;
    }


    public Key scaleWidth(float s)
    {
      return new Key(keys, circle, anticircle_v2, anticircle, keysflags, width * s, shift, borderRadius, indication, labelScales, colorDark, colorLight);
    }

    public void getKeys(Map<KeyValue, KeyPos> dst, int row, int col)
    {
      for (int i = 0; i < keys.length; i++)
        if (keys[i] != null)
          dst.put(keys[i], new KeyPos(row, col, i));
    }

    public KeyValue getKeyValue(int i)
    {
      return keys[i];
    }

    public Key withKeyValue(int i, KeyValue kv)
    {
      KeyValue[] ks = new KeyValue[keys.length];
      for (int j = 0; j < keys.length; j++) ks[j] = keys[j];
      ks[i] = kv;
      int flags = (keysflags & ~(ALL_FLAGS << i));
      return new Key(ks, circle, anticircle_v2, anticircle, flags, width, shift, borderRadius, indication, labelScales, colorDark, colorLight);
    }

    public Key withShift(float s)
    {
      return new Key(keys, circle, anticircle_v2, anticircle, keysflags, width, s, borderRadius, indication, labelScales, colorDark, colorLight);
    }

    public boolean hasValue(KeyValue kv)
    {
      for (int i = 0; i < keys.length; i++)
        if (keys[i] != null && keys[i].equals(kv))
          return true;
      return false;
    }
  }


  public static abstract interface MapKey {
    public Key apply(Key k);
  }

  public static abstract class MapKeyValues implements MapKey {
    abstract public KeyValue apply(KeyValue c, boolean localized);

    public Key apply(Key k)
    {
      KeyValue[] ks = new KeyValue[k.keys.length];
      for (int i = 0; i < ks.length; i++)
        if (k.keys[i] != null)
          ks[i] = apply(k.keys[i], k.keyHasFlag(i, Key.F_LOC));
      return new Key(ks, k.circle, k.anticircle_v2, k.anticircle, k.keysflags, k.width, k.shift, k.borderRadius, k.indication, k.labelScales, k.colorDark, k.colorLight);
    }
  }

  public static Modmap parse_modmap(XmlPullParser parser) throws Exception
  {
    Modmap mm = new Modmap();
    while (next_tag(parser))
    {
      Modmap.M m;
      switch (parser.getName())
      {
        case "shift": m = Modmap.M.Shift; break;
        case "fn": m = Modmap.M.Fn; break;
        case "ctrl": m = Modmap.M.Ctrl; break;
        default:
          throw error(parser, "Expecting tag <shift> or <fn>, got <" +
              parser.getName() + ">");
      }
      parse_modmap_mapping(parser, mm, m);
    }
    return mm;
  }

  private static void parse_modmap_mapping(XmlPullParser parser, Modmap mm,
      Modmap.M m) throws Exception
  {
    KeyValue a = KeyValue.getKeyByName(parser.getAttributeValue(null, "a"));
    KeyValue b = KeyValue.getKeyByName(parser.getAttributeValue(null, "b"));
    while (parser.next() != XmlPullParser.END_TAG)
      continue;
    mm.add(m, a, b);
  }


  public final static class KeyPos
  {
    public final int row;
    public final int col;
    public final int dir;

    public KeyPos(int r, int c, int d)
    {
      row = r;
      col = c;
      dir = d;
    }

    public KeyPos with_dir(int d)
    {
      return new KeyPos(row, col, d);
    }
  }


  public final static class PreferredPos
  {

    public static final PreferredPos DEFAULT;
    public static final PreferredPos ANYWHERE;


    public KeyValue next_to = null;


    public KeyPos[] positions = ANYWHERE_POSITIONS;

    public PreferredPos() {}
    public PreferredPos(KeyValue next_to_) { next_to = next_to_; }
    public PreferredPos(KeyPos[] pos) { positions = pos; }
    public PreferredPos(KeyValue next_to_, KeyPos[] pos) { next_to = next_to_; positions = pos; }

    public PreferredPos(PreferredPos src)
    {
      next_to = src.next_to;
      positions = src.positions;
    }

    static final KeyPos[] ANYWHERE_POSITIONS =
      new KeyPos[]{ new KeyPos(-1, -1, -1) };

    static
    {
      DEFAULT = new PreferredPos(new KeyPos[]{
          new KeyPos(1, -1, 4),
          new KeyPos(1, -1, 3),
          new KeyPos(2, -1, 2),
          new KeyPos(2, -1, 1)
        });
      ANYWHERE = new PreferredPos();
    }
  }




  private static boolean next_tag(XmlPullParser parser) throws Exception
  {
    int status;
    do
    {
      status = parser.next();
      if (status == XmlPullParser.END_DOCUMENT || status == XmlPullParser.END_TAG)
        return false;
    }
    while (status != XmlPullParser.START_TAG);
    return true;
  }


  private static boolean expect_tag(XmlPullParser parser, String name) throws Exception
  {
    if (!next_tag(parser))
      return false;
    if (!parser.getName().equals(name))
      throw error(parser, "Expecting tag <" + name + ">, got <" +
          parser.getName() + ">");
    return true;
  }

  private static boolean attribute_bool(XmlPullParser parser, String attr, boolean default_val)
  {
    String val = parser.getAttributeValue(null, attr);
    if (val == null)
      return default_val;
    return val.equals("true");
  }

  private static float attribute_float(XmlPullParser parser, String attr, float default_val)
  {
    String val = parser.getAttributeValue(null, attr);
    if (val == null)
      return default_val;
    return Float.parseFloat(val);
  }


  private static Exception error(XmlPullParser parser, String message)
  {
    return new Exception(message + " " + parser.getPositionDescription());
  }
}
