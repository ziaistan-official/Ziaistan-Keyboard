package juloo.keyboard2;

import android.content.res.Resources;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Glyph
{
  private final KeyValue _kv;

  protected Glyph(String bytecode)
  {
    this._kv = new KeyValue(bytecode, KeyValue.Kind.String, 0, 0);
  }

  public KeyValue kv()
  {
    return _kv;
  }

  public static class Group {
      public final String name;
      public final List<Glyph> glyphs;
      public Group(String name, List<Glyph> glyphs) {
          this.name = name;
          this.glyphs = glyphs;
      }
  }

  private final static List<Glyph> _all = new ArrayList<>();
  private final static List<Group> _groups = new ArrayList<>();
  private final static HashMap<String, Glyph> _stringMap = new HashMap<>();

  public static void init(Resources res)
  {
    if (!_all.isEmpty())
      return;

    try
    {
      InputStream inputStream = res.openRawResource(R.raw.glyphs);
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
      String line;

      while ((line = reader.readLine()) != null && !line.isEmpty())
      {
        Glyph g = new Glyph(line);
        _all.add(g);
        _stringMap.put(line, g);
      }

      if ((line = reader.readLine()) != null)
      {
        String[] tokens = line.split(" ");
        int last = 0;
        for (int i = 0; i < tokens.length; i++)
        {
          String[] part = tokens[i].split(":");
          if (part.length == 2) {
              String name = part[0];
              int next = Integer.parseInt(part[1]);
              _groups.add(new Group(name, _all.subList(last, Math.min(next, _all.size()))));
              last = next;
          }
        }
      }
    }
    catch (IOException e) { Logs.exn("Glyph.init() failed", e); }
  }

  public static int getNumGroups()
  {
    return _groups.size();
  }

  public static Group getGroup(int index) {
      return _groups.get(index);
  }

  public static List<Glyph> getGlyphsByGroup(int groupIndex)
  {
    return _groups.get(groupIndex).glyphs;
  }

  public static Glyph getGlyphByString(String value)
  {
    return _stringMap.get(value);
  }
}
