package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtraKeys
{
  public static final ExtraKeys EMPTY = new ExtraKeys(Collections.EMPTY_LIST);

  Collection<ExtraKey> _ks;

  public ExtraKeys(Collection<ExtraKey> ks)
  {
    _ks = ks;
  }


  public void compute(Map<KeyValue, KeyboardData.PreferredPos> dst, Query q)
  {
    for (ExtraKey k : _ks)
      k.compute(dst, q);
  }

  public static ExtraKeys parse(String script, String str)
  {
    Collection<ExtraKey> dst = new ArrayList<ExtraKey>();
    String[] ks = str.split("\\|");
    for (int i = 0; i < ks.length; i++)
      dst.add(ExtraKey.parse(ks[i], script));
    return new ExtraKeys(dst);
  }


  public static ExtraKeys merge(List<ExtraKeys> kss)
  {
    Map<KeyValue, ExtraKey> merged_keys = new HashMap<KeyValue, ExtraKey>();
    for (ExtraKeys ks : kss)
      for (ExtraKey k : ks._ks)
      {
        ExtraKey k2 = merged_keys.get(k.kv);
        if (k2 != null)
          k = k.merge_with(k2);
        merged_keys.put(k.kv, k);
      }
    return new ExtraKeys(merged_keys.values());
  }

  final static class ExtraKey
  {

    final KeyValue kv;

    final String script;

    final List<KeyValue> alternatives;

    final KeyValue next_to;

    ExtraKey(KeyValue kv_, String script_, List<KeyValue> alts_, KeyValue next_to_)
    {
      kv = kv_;
      script = script_;
      alternatives = alts_;
      next_to = next_to_;
    }


    public void compute(Map<KeyValue, KeyboardData.PreferredPos> dst, Query q)
    {






      boolean use_alternative = (alternatives.size() == 1 && !dst.containsKey(kv));
      if
        ((q.script == null || script == null || q.script.equals(script))
        && (alternatives.size() == 0 || !q.present.containsAll(alternatives)))
      {
        KeyValue kv_ = use_alternative ? alternatives.get(0) : kv;
        KeyboardData.PreferredPos pos = KeyboardData.PreferredPos.DEFAULT;
        if (next_to != null)
        {
          pos = new KeyboardData.PreferredPos(pos);
          pos.next_to = next_to;
        }
        dst.put(kv_, pos);
      }
    }


    public ExtraKey merge_with(ExtraKey k2)
    {
      String script_ = one_or_none(script, k2.script);
      List<KeyValue> alts = new ArrayList<KeyValue>(alternatives);
      KeyValue next_to_ = one_or_none(next_to, k2.next_to);
      alts.addAll(k2.alternatives);
      return new ExtraKey(kv, script_, alts, next_to_);
    }


    <E> E one_or_none(E a, E b)
    {
      return (a == null) ? b : (b == null || a.equals(b)) ? a : null;
    }


    public static ExtraKey parse(String str, String script)
    {
      String[] split_on_at = str.split("@", 2);
      String[] key_names = split_on_at[0].split(":");
      KeyValue kv = KeyValue.getKeyByName(key_names[0]);
      KeyValue[] alts = new KeyValue[key_names.length-1];
      for (int i = 1; i < key_names.length; i++)
        alts[i-1] = KeyValue.getKeyByName(key_names[i]);
      KeyValue next_to = null;
      if (split_on_at.length > 1)
        next_to = KeyValue.getKeyByName(split_on_at[1]);
      return new ExtraKey(kv, script, Arrays.asList(alts), next_to);
    }
  }

  public final static class Query
  {

    final String script;

    final Set<KeyValue> present;

    public Query(String script_, Set<KeyValue> present_)
    {
      script = script_;
      present = present_;
    }
  }
}
