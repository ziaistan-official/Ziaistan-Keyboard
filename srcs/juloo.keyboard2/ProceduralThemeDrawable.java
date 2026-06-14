package juloo.keyboard2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class ProceduralThemeDrawable extends Drawable {
    private final String themeName;
    private final Paint bgPaint;
    private final float radius;
    private boolean isPressed = false;

    public ProceduralThemeDrawable(String themeName, int color, float radius) {
        this.themeName = themeName;
        this.bgPaint = new Paint();
        this.bgPaint.setColor(color);
        this.bgPaint.setStyle(Paint.Style.FILL);
        this.bgPaint.setAntiAlias(true);
        this.radius = radius;
    }

    @Override
    public void draw(Canvas canvas) {
        ThemeRenderer.drawKeyBackground(
            canvas,
            getBounds().left, getBounds().top,
            getBounds().width(), getBounds().height(),
            bgPaint, radius, themeName, isPressed, 0, 0
        );
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean pressed = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        if (isPressed != pressed) {
            isPressed = pressed;
            invalidateSelf();
            return true;
        }
        return super.onStateChange(state);
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bgPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
