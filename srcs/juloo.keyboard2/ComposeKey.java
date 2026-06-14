package juloo.keyboard2;

import java.util.Arrays;

public final class ComposeKey
{

  public static KeyValue apply(int state, KeyValue kv)
  {
    switch (kv.getKind())
    {
      case Char:
        return apply(state, kv.getChar());
      case String:
        return apply(state, kv.getString());
    }
    return null;
  }


  public static KeyValue apply(int prev, char c)
  {
    char[] states = ComposeKeyData.states;
    char[] edges = ComposeKeyData.edges;
    int prev_length = edges[prev];
    int next = Arrays.binarySearch(states, prev + 1, prev + prev_length, c);
    if (next < 0)
      return null;
    next = edges[next];
    int next_header = states[next];
    if (next_header == 0)
      return KeyValue.makeComposePending(String.valueOf(c), next, 0);
    else if (next_header == 0xFFFF)
    {
      int next_length = edges[next];
      return KeyValue.getKeyByName(
          new String(states, next + 1, next_length - 1));
    }
    else
      return KeyValue.makeCharKey((char)next_header);
  }


  public static KeyValue apply(int prev, String s)
  {
    final int len = s.length();
    int i = 0;
    if (len == 0) return null;
    while (true)
    {
      KeyValue k = apply(prev, s.charAt(i));
      i++;
      if (k == null) return null;
      if (i >= len) return k;
      if (k.getKind() != KeyValue.Kind.Compose_pending)
        return null;
      prev = k.getPendingCompose();
    }
  }


}
