package juloo.keyboard2;

public final class Gesture
{

  int current_dir;
  final Corner starting_corner;

  State state;

  public Gesture(int starting_direction)
  {
    current_dir = starting_direction;
    state = State.Swiped;
    starting_corner = get_corner(starting_direction);
  }

  enum State
  {
    Cancelled,
    Swiped,
    Rotating_clockwise,
    Rotating_anticlockwise,
    Ended_swipe,
    Ended_center,
    Ended_clockwise,
    Ended_anticlockwise
  }

  enum Name
  {
    None,
    Swipe,
    Roundtrip,
    Circle,
    Anticircle,
    CircleNE,
    AnticircleNE,
    CircleSE,
    AnticircleSE,
    CircleSW,
    AnticircleSW,
    CircleNW,
    AnticircleNW,
  }

  enum Corner {
    NE, SE, SW, NW, None
  }


  static Corner get_corner(int direction)
  {

    if (direction >= 1 && direction <= 3) return Corner.NE;
    if (direction >= 5 && direction <= 7) return Corner.SE;
    if (direction >= 9 && direction <= 11) return Corner.SW;
    if (direction >= 13 && direction <= 15 || direction == 0) return Corner.NW;
    return Corner.None;
  }


  static final int ROTATION_THRESHOLD = 2;


  public Name get_gesture()
  {
    if (state == State.Cancelled) return Name.None;
    if (state == State.Swiped || state == State.Ended_swipe) return Name.Swipe;
    if (state == State.Ended_center) return Name.Roundtrip;
    if (state == State.Rotating_clockwise || state == State.Ended_clockwise) {
        switch (starting_corner) {
            case NE: return Name.CircleNE;
            case SE: return Name.CircleSE;
            case SW: return Name.CircleSW;
            case NW: return Name.CircleNW;
            default: return Name.Circle;
        }
    }
    if (state == State.Rotating_anticlockwise || state == State.Ended_anticlockwise) {
        switch (starting_corner) {
            case NE: return Name.AnticircleNE;
            case SE: return Name.AnticircleSE;
            case SW: return Name.AnticircleSW;
            case NW: return Name.AnticircleNW;
            default: return Name.Anticircle;
        }
    }
    return Name.None;
  }

  public boolean is_in_progress()
  {
    switch (state)
    {
      case Swiped:
      case Rotating_clockwise:
      case Rotating_anticlockwise:
        return true;
    }
    return false;
  }

  public int current_direction() { return current_dir; }


  public boolean changed_direction(int direction)
  {
    int d = dir_diff(current_dir, direction);
    boolean clockwise = d > 0;
    switch (state)
    {
      case Swiped:
        if (Math.abs(d) < Config.globalConfig().circle_sensitivity)
          return false;

        state = (clockwise) ?
          State.Rotating_clockwise : State.Rotating_anticlockwise;
        current_dir = direction;
        return true;

      case Rotating_clockwise:
      case Rotating_anticlockwise:
        current_dir = direction;
        if ((state == State.Rotating_clockwise) == clockwise)
          return false;
        state = State.Cancelled;
        return true;
    }
    return false;
  }


  public boolean moved_to_center()
  {
    switch (state)
    {
      case Swiped: state = State.Ended_center; return true;
      case Rotating_clockwise: state = State.Ended_clockwise; return false;
      case Rotating_anticlockwise: state = State.Ended_anticlockwise; return false;
    }
    return false;
  }


  public void pointer_up()
  {
    switch (state)
    {
      case Swiped: state = State.Ended_swipe; break;
      case Rotating_clockwise: state = State.Ended_clockwise; break;
      case Rotating_anticlockwise: state = State.Ended_anticlockwise; break;
    }
  }

  static int dir_diff(int d1, int d2)
  {
    final int n = 16;

    if (d1 == d2)
      return 0;
    int left = (d1 - d2 + n) % n;
    int right = (d2 - d1 + n) % n;
    return (left < right) ? -left : right;
  }
}
