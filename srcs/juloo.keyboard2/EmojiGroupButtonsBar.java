package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout.LayoutParams;
import android.widget.LinearLayout;

public class EmojiGroupButtonsBar extends LinearLayout
{
  private EmojiGridView _emoji_grid = null;

  public EmojiGroupButtonsBar(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    Emoji.init(context.getResources());

    if (Config.globalConfig().emoji_favorites_enabled && Config.globalConfig().emoji_favorites_first) {
        add_group(EmojiGridView.GROUP_FAVORITES, "★");
    }

    for (int i = 0; i < Emoji.getNumGroups(); i++)
    {
      Emoji first = Emoji.getEmojisByGroup(i).get(0);
      add_group(i, first.kv().getString());
    }

    if (Config.globalConfig().emoji_show_kaomoji) {
        add_group(-2, "(・ω・)");
    }

    if (Config.globalConfig().emoji_favorites_enabled && !Config.globalConfig().emoji_favorites_first) {
        add_group(EmojiGridView.GROUP_FAVORITES, "★");
    }
  }

  void add_group(int id, String symbol)
  {
    addView(this.new EmojiGroupButton(getContext(), id, symbol),
        new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 1.f));
  }

  EmojiGridView get_emoji_grid()
  {
    if (_emoji_grid == null)
      _emoji_grid = (EmojiGridView)((ViewGroup)(getParent())).findViewById(R.id.emoji_grid);
    return _emoji_grid;
  }

  class EmojiGroupButton extends Button implements View.OnTouchListener
  {
    int _group_id;

    public EmojiGroupButton(Context context, int group_id, String symbol)
    {
      super(context, null, 0);
      _group_id = group_id;
      setText(symbol);
      setOnTouchListener(this);

      Theme theme = new Theme(context, null);
      setBackgroundColor(theme.colorKey);
      setTextColor(theme.labelColor);
    }

    public boolean onTouch(View view, MotionEvent event)
    {
      if (event.getAction() != MotionEvent.ACTION_DOWN)
        return false;
      get_emoji_grid().setEmojiGroup(_group_id);
      return true;
    }
  }
}
