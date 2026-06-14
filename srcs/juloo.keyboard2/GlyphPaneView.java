package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class GlyphPaneView extends LinearLayout {
    private int mFixedKeyboardHeight = -1;

    public GlyphPaneView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setKeyboardHeight(int height) {
        mFixedKeyboardHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mFixedKeyboardHeight > 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(mFixedKeyboardHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
