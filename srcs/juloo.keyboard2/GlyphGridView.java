package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;

public class GlyphGridView extends GridView
  implements GridView.OnItemClickListener, GridView.OnTouchListener
{
  private final Handler _handler = new Handler(Looper.getMainLooper());
  private Glyph _longClickedGlyph;
  private List<Glyph> _glyphArray;
  private int _currentGroup = 0;
  private float _startX, _startY;
  private static final int SWIPE_THRESHOLD = 100;

  public interface OnGroupChangeListener {
      void onGroupChanged(int newGroup);
  }
  private OnGroupChangeListener _groupChangeListener;

  public void setOnGroupChangeListener(OnGroupChangeListener listener) {
      _groupChangeListener = listener;
  }


  public GlyphGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    Glyph.init(context.getResources());
    setOnItemClickListener(this);
    setOnTouchListener(this);

    setNumColumns(10);
    setGlyphGroup(0);
  }

  public void setGlyphGroup(int group)
  {
    if (group >= 0 && group < Glyph.getNumGroups()) {
        _currentGroup = group;
        _glyphArray = Glyph.getGlyphsByGroup(group);
        setAdapter(new GlyphViewAdapter(getContext(), _glyphArray));
        if (_groupChangeListener != null) {
            _groupChangeListener.onGroupChanged(group);
        }
    }
  }

  public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
  {
    if (pos < _glyphArray.size()) {
        Glyph glyph = _glyphArray.get(pos);
        Config.globalConfig().handler.key_up(glyph.kv(), Pointers.Modifiers.EMPTY);
    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
      switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
              _startX = event.getX();
              _startY = event.getY();
              break;
          case MotionEvent.ACTION_UP:
              float endX = event.getX();
              float endY = event.getY();
              float deltaX = endX - _startX;
              float deltaY = endY - _startY;

              if (Math.abs(deltaX) > SWIPE_THRESHOLD && Math.abs(deltaX) > Math.abs(deltaY)) {
                  if (deltaX > 0) {
                      // Swipe Right -> Previous Group
                      if (_currentGroup > 0) {
                          setGlyphGroup(_currentGroup - 1);
                      }
                  } else {
                      // Swipe Left -> Next Group
                      if (_currentGroup < Glyph.getNumGroups() - 1) {
                          setGlyphGroup(_currentGroup + 1);
                      }
                  }
                  return true;
              }
              break;
      }
      return false;
  }

  static class GlyphView extends TextView
  {
    public GlyphView(Context context)
    {
      super(context);
      Theme theme = new Theme(context, null);
      setTextColor(theme.labelColor);
      setGravity(Gravity.CENTER);
    }

    public void setGlyph(Glyph glyph)
    {
      setText(glyph.kv().getString());
    }
  }

  static class GlyphViewAdapter extends BaseAdapter
  {
    Context _button_context;
    List<Glyph> _glyphArray;

    public GlyphViewAdapter(Context context, List<Glyph> glyphArray)
    {
      _button_context = context;
      _glyphArray = glyphArray;
    }

    public int getCount()
    {
      return (_glyphArray == null) ? 0 : _glyphArray.size();
    }

    public Object getItem(int pos)
    {
      return _glyphArray.get(pos);
    }

    public long getItemId(int pos)
    {
      return pos;
    }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      GlyphView view = (GlyphView)convertView;
      if (view == null)
        view = new GlyphView(_button_context);

      Glyph glyph = _glyphArray.get(pos);
      String s = glyph.kv().getString();
      float textSize = 30; // Default

      if (s.length() == 1) {
          char c = s.charAt(0);
          if ((c >= 0x064B && c <= 0x065F) || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK) {
              // Arabic diacritics and other smaller characters (marks)
              textSize = 52;
          } else if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
                     Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
                     s.equals("ﷺ") || s.equals("ﷲ") || s.equals("﷽") || s.equals("ﷻ")) {
              // Special large glyphs / ligatures
              textSize = 20;
          }
      } else if (s.length() > 1) {
          // Likely a ligature or complex glyph
          textSize = 20;
      }

      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
      view.setGlyph(glyph);
      return view;
    }
  }
}
