package juloo.keyboard2.prefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.preference.DialogPreference;

import juloo.keyboard2.R;

public class IntSlideBarPreference extends DialogPreference
{
  private int _min;
  private int _max;
  private int _value;
  private SeekBar _seekBar;
  private TextView _valueText;
  private String _summaryFormat;

  public IntSlideBarPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setDialogLayoutResource(R.layout.pref_dialog_slidebar);
    setPositiveButtonText(android.R.string.ok);
    setNegativeButtonText(android.R.string.cancel);

    _min = attrs.getAttributeIntValue(null, "min", 0);
    _max = attrs.getAttributeIntValue(null, "max", 100);
    _summaryFormat = (String)getSummary();
  }

  @Override
  protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue)
  {
    if (restorePersistedValue)
      _value = getPersistedInt(_min);
    else
    {
      _value = (Integer)defaultValue;
      persistInt(_value);
    }
    updateText();
  }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index)
  {
    return a.getInt(index, _min);
  }

  @Override
  protected View onCreateDialogView()
  {
    View view = super.onCreateDialogView();
    _seekBar = view.findViewById(R.id.seekbar);
    _valueText = view.findViewById(R.id.value);

    _seekBar.setMax(_max - _min);
    _seekBar.setProgress(_value - _min);
    _seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        _value = progress + _min;
        updateText();
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });

    updateText();
    return view;
  }

  private void updateText()
  {
    if (_valueText != null)
      _valueText.setText(String.valueOf(_value));
    if (_summaryFormat != null)
      setSummary(String.format(_summaryFormat, _value));
  }

  @Override
  protected void onDialogClosed(boolean positiveResult)
  {
    if (positiveResult)
    {
      persistInt(_value);
      notifyChanged();
    }
  }
}
