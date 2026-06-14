package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.os.Handler;
import android.os.Looper;

public class EmojiGridView extends GridView
  implements GridView.OnItemClickListener, GridView.OnItemLongClickListener, GridView.OnTouchListener
{
  public static final int GROUP_FAVORITES = -3;
  public static final int GROUP_LAST_USE = -1;

  private static final String LAST_USE_PREF = "emoji_last_use";
  private static final String FAVORITES_PREF = "emoji_favorites";
  private final Handler _handler = new Handler(Looper.getMainLooper());
  private Emoji _longClickedEmoji;

  private List<Emoji> _emojiArray;
  private HashMap<Emoji, Integer> _lastUsed;
  private Set<Emoji> _favoritesSet;


  public EmojiGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    Emoji.init(context.getResources());
    migrateOldPrefs();
    setOnItemClickListener(this);
    setOnItemLongClickListener(this);
    setOnTouchListener(this);
    loadLastUsed();
    loadFavorites();

    Config config = Config.globalConfig();
    int columns = config.orientation_landscape ? config.emoji_columns_landscape : config.emoji_columns_portrait;
    setNumColumns(columns);

    post(new Runnable() {
        @Override
        public void run() {
            GridView recentEmojiGrid = ((View) getParent()).findViewById(R.id.recent_emoji_grid);
            View recentEmojiLabel = ((View) getParent()).findViewById(R.id.recent_emoji_label);
            if (Config.globalConfig().emoji_show_recent) {
                if (recentEmojiLabel != null) recentEmojiLabel.setVisibility(View.VISIBLE);
                recentEmojiGrid.setVisibility(View.VISIBLE);
                recentEmojiGrid.setAdapter(new EmojiViewAdpater(getContext(), getLastEmojis(), false));
                recentEmojiGrid.setOnItemClickListener(EmojiGridView.this);
                recentEmojiGrid.setOnItemLongClickListener(EmojiGridView.this);
                recentEmojiGrid.setNumColumns(columns);
            } else {
                if (recentEmojiLabel != null) recentEmojiLabel.setVisibility(View.GONE);
                recentEmojiGrid.setVisibility(View.GONE);
            }
        }
    });
    setEmojiGroup(config.emoji_favorites_first && config.emoji_favorites_enabled ? GROUP_FAVORITES : 0);
  }

  @Override
  public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
      List<Emoji> list;
      if (parent.getId() == R.id.recent_emoji_grid) {
          list = getLastEmojis();
      } else {
          list = _emojiArray;
      }
      if (position >= list.size()) return false;
      _longClickedEmoji = list.get(position);

      Config config = Config.globalConfig();
      if (config.emoji_favorites_enabled && config.emoji_long_press_add_favorite) {
          toggleFavorite(_longClickedEmoji);
          return true;
      }
      if (config.emoji_long_press_name) {






          android.widget.Toast.makeText(getContext(), _longClickedEmoji.kv().getString(), android.widget.Toast.LENGTH_SHORT).show();

          return true;
      }

      _handler.post(mLongPressed);
      return true;
  }

  private final Runnable mLongPressed = new Runnable() {
      public void run() {
          if (_longClickedEmoji != null) {
              Config.globalConfig().handler.key_up(_longClickedEmoji.kv(), Pointers.Modifiers.EMPTY);
              _handler.postDelayed(this, 100);
          }
      }
  };

  @Override
  public boolean onTouch(View v, MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
          _handler.removeCallbacks(mLongPressed);
          _longClickedEmoji = null;
      }
      return false;
  }

  public void resetToDefaultTab() {
      if (Config.globalConfig().emoji_favorites_first && Config.globalConfig().emoji_favorites_enabled) {
          setEmojiGroup(GROUP_FAVORITES);
      }
  }

  public void setEmojiGroup(int group)
  {
    _currentGroup = group;
    boolean isKaomoji = (group == -2);
    if (isKaomoji) {
        if (Config.globalConfig().emoji_show_kaomoji) {
            _emojiArray = new ArrayList<>();
            for (int i = 0; i < Emoji.getNumKaomojiGroups(); i++) {
                _emojiArray.addAll(Emoji.getKaomojisByGroup(i));
            }
        } else {
            _emojiArray = new ArrayList<>();
        }
    } else if (group == GROUP_FAVORITES) {
        _emojiArray = new ArrayList<>(_favoritesSet);
    } else {
        _emojiArray = Emoji.getEmojisByGroup(group);
    }
    setAdapter(new EmojiViewAdpater(getContext(), _emojiArray, isKaomoji));
  }

  public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
  {
    List<Emoji> emojiList;
    if (parent.getId() == R.id.recent_emoji_grid) {
        emojiList = getLastEmojis();
    } else {
        emojiList = _emojiArray;
    }

    if (pos < emojiList.size()) {
        Emoji emoji = emojiList.get(pos);
        Config config = Config.globalConfig();
        Integer used = _lastUsed.get(emoji);
        _lastUsed.put(emoji, (used == null) ? 1 : used.intValue() + 1);
        config.handler.key_up(emoji.kv(), Pointers.Modifiers.EMPTY);
        saveLastUsed();

        if (config.emoji_vibrate) {
             VibratorCompat.vibrate(getContext(), config.vibrate_duration);
        }
        if (config.emoji_sound) {
             android.media.AudioManager am = (android.media.AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
             if (am != null) am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD);
        }
    }
  }

  private List<Emoji> getLastEmojis()
  {
    List<Emoji> list = new ArrayList<>(_lastUsed.keySet());
    Collections.sort(list, new Comparator<Emoji>()
        {
          public int compare(Emoji a, Emoji b)
          {
            return _lastUsed.get(b) - _lastUsed.get(a);
          }
        });
    return list;
  }

  private void saveLastUsed()
  {
    int limit = Config.globalConfig().emoji_history_size;
    if (_lastUsed.size() > limit) {
        List<Emoji> list = getLastEmojis();
        for (int i = limit; i < list.size(); i++) {
            _lastUsed.remove(list.get(i));
        }
    }

    SharedPreferences.Editor edit;
    try { edit = emojiSharedPreferences().edit(); }
    catch (Exception _e) { return; }
    HashSet<String> set = new HashSet<String>();
    for (Emoji emoji : _lastUsed.keySet())
      set.add(String.valueOf(_lastUsed.get(emoji)) + "-" + emoji.kv().getString());
    edit.putStringSet(LAST_USE_PREF, set);
    edit.apply();
    updateRecentAdapter();
  }

  private void updateRecentAdapter() {
      if (!Config.globalConfig().emoji_show_recent) return;
      post(() -> {
          View parent = (View) getParent();
          if (parent != null) {
              GridView recentEmojiGrid = parent.findViewById(R.id.recent_emoji_grid);
              if (recentEmojiGrid != null) {
                  Config config = Config.globalConfig();
                  int columns = config.orientation_landscape ? config.emoji_columns_landscape : config.emoji_columns_portrait;
                  recentEmojiGrid.setNumColumns(columns);
                  recentEmojiGrid.setAdapter(new EmojiViewAdpater(getContext(), getLastEmojis(), false));
                  recentEmojiGrid.setOnItemClickListener(EmojiGridView.this);
                  recentEmojiGrid.setOnItemLongClickListener(EmojiGridView.this);
              }
          }
      });
  }

  private void loadLastUsed()
  {
    _lastUsed = new HashMap<Emoji, Integer>();
    SharedPreferences prefs;


    try { prefs = emojiSharedPreferences(); }
    catch (Exception _e) { return; }
    Set<String> lastUseSet = prefs.getStringSet(LAST_USE_PREF, null);
    if (lastUseSet != null)
      for (String emojiData : lastUseSet)
      {
        String[] data = emojiData.split("-", 2);
        Emoji emoji;
        if (data.length != 2)
          continue ;
        emoji = Emoji.getEmojiByString(data[1]);
        if (emoji == null)
          continue ;
        _lastUsed.put(emoji, Integer.valueOf(data[0]));
      }
  }

  private void saveFavorites()
  {
    SharedPreferences.Editor edit;
    try { edit = getContext().getSharedPreferences(FAVORITES_PREF, Context.MODE_PRIVATE).edit(); }
    catch (Exception _e) { return; }
    HashSet<String> set = new HashSet<String>();
    for (Emoji emoji : _favoritesSet)
      set.add(emoji.kv().getString());
    edit.putStringSet("favorites", set);
    edit.apply();
  }

  private void loadFavorites()
  {
    _favoritesSet = new HashSet<>();
    SharedPreferences prefs;
    try { prefs = getContext().getSharedPreferences(FAVORITES_PREF, Context.MODE_PRIVATE); }
    catch (Exception _e) { return; }
    Set<String> favSet = prefs.getStringSet("favorites", null);
    if (favSet != null) {
      for (String emojiStr : favSet) {
        Emoji emoji = Emoji.getEmojiByString(emojiStr);
        if (emoji != null) _favoritesSet.add(emoji);
      }
    }
  }

  private void toggleFavorite(Emoji emoji) {
      if (_favoritesSet.contains(emoji)) {
          _favoritesSet.remove(emoji);
          android.widget.Toast.makeText(getContext(), R.string.toast_favorite_removed, android.widget.Toast.LENGTH_SHORT).show();
      } else {
          _favoritesSet.add(emoji);
          android.widget.Toast.makeText(getContext(), R.string.toast_favorite_added, android.widget.Toast.LENGTH_SHORT).show();
      }
      saveFavorites();
      if (_currentGroup == GROUP_FAVORITES) {
          setEmojiGroup(GROUP_FAVORITES);
      }
  }

  private int _currentGroup = 0;

  SharedPreferences emojiSharedPreferences()
  {
    return getContext().getSharedPreferences("emoji_last_use", Context.MODE_PRIVATE);
  }

  private void migrateOldPrefs()
  {
    final String MIGRATION_CHECK_KEY = "MIGRATION_COMPLETE";

    SharedPreferences prefs;
    try { prefs = emojiSharedPreferences(); }
    catch (Exception e) { return; }

    Set<String> lastUsed = prefs.getStringSet(LAST_USE_PREF, null);
    if (lastUsed != null && !prefs.getBoolean(MIGRATION_CHECK_KEY, false))
    {
      SharedPreferences.Editor edit = prefs.edit();
      edit.clear();

      Set<String> lastUsedNew = new HashSet<>();
      for (String entry : lastUsed)
      {
        String[] data = entry.split("-", 2);
        try
        {
          lastUsedNew.add(Integer.parseInt(data[0]) + "-" + Emoji.mapOldNameToValue(data[1]));
        }
        catch (IllegalArgumentException ignored) {}
      }
      edit.putStringSet(LAST_USE_PREF, lastUsedNew);

      edit.putBoolean(MIGRATION_CHECK_KEY, true);
      edit.apply();
    }
  }

  static class EmojiView extends TextView
  {
    public EmojiView(Context context)
    {
      super(context);
      Theme theme = new Theme(context, null);
      setTextColor(theme.labelColor);
      setGravity(Gravity.CENTER);
    }

    public void setEmoji(Emoji emoji)
    {
      setText(emoji.kv().getString());
    }
  }

  static class EmojiViewAdpater extends BaseAdapter
  {
    Context _button_context;
    boolean _isKaomoji;

    List<Emoji> _emojiArray;

    public EmojiViewAdpater(Context context, List<Emoji> emojiArray, boolean isKaomoji)
    {
      _button_context = context;
      _emojiArray = emojiArray;
      _isKaomoji = isKaomoji;
    }

    public int getCount()
    {
      if (_emojiArray == null)
        return (0);
      return (_emojiArray.size());
    }

    public Object getItem(int pos)
    {
      return (_emojiArray.get(pos));
    }

    public long getItemId(int pos)
    {
      return (pos);
    }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      EmojiView view = (EmojiView)convertView;

      if (view == null)
        view = new EmojiView(_button_context);

      Config config = Config.globalConfig();
      if (_isKaomoji) {
          view.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18 * config.emoji_kaomoji_size_factor);
      } else {
          view.setTextSize(TypedValue.COMPLEX_UNIT_PX, 48 * config.emoji_size_factor);
      }

      view.setEmoji(_emojiArray.get(pos));
      return view;
    }
  }
}
