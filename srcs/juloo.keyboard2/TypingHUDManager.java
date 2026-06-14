package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ImageView;

public class TypingHUDManager {
    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mHudView;
    private TextView mTypedWord;
    private TextView mCorrectedWord;
    private ImageView mArrow;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsVisible = false;
    private WindowManager.LayoutParams mParams;

    public TypingHUDManager(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void updateHUD(final String typed, final String corrected, final boolean showArrow, final int duration,
                         final int bgColor, final int txtColor, final int txtSize) {
        mHandler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(mContext)) {
                return;
            }

            if (mHudView == null) {
                initHUD();
            }

            applyStyles(bgColor, txtColor, txtSize);

            // If we are showing a correction/arrow, ignore empty typing updates that follow immediately
            if (!showArrow && (typed == null || typed.isEmpty())) {
                if (mArrow.getVisibility() == View.VISIBLE) {
                    return;
                }
                mHandler.removeCallbacks(mHideRunnable);
                hideHUD();
                return;
            }

            mHandler.removeCallbacks(mHideRunnable);

            mTypedWord.setText(typed != null ? typed : "");

            if (showArrow && corrected != null && !corrected.isEmpty()) {
                mCorrectedWord.setText(corrected);
                mCorrectedWord.setVisibility(View.VISIBLE);
                mArrow.setVisibility(View.VISIBLE);
            } else {
                mCorrectedWord.setVisibility(View.GONE);
                mArrow.setVisibility(View.GONE);
            }

            showHUD();
            // Use configured duration only for the final state (with arrow/correction)
            // For initial typing, hide slightly faster (2s)
            int actualDuration = showArrow ? duration : 2000;
            mHandler.postDelayed(mHideRunnable, actualDuration);
        });
    }

    private void initHUD() {
        mHudView = LayoutInflater.from(mContext).inflate(R.layout.typing_hud, null);
        mTypedWord = mHudView.findViewById(R.id.hud_typed_word);
        mCorrectedWord = mHudView.findViewById(R.id.hud_corrected_word);
        mArrow = mHudView.findViewById(R.id.hud_arrow);

        mHudView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mParams == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        mHandler.removeCallbacks(mHideRunnable);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            mWindowManager.updateViewLayout(mHudView, mParams);
                        } catch (Exception e) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        SharedPreferences.Editor editor = DirectBootAwarePreferences.get_shared_preferences(mContext).edit();
                        editor.putInt("typing_hud_x", mParams.x);
                        editor.putInt("typing_hud_y", mParams.y);
                        editor.apply();
                        Config.globalConfig().typing_hud_x = mParams.x;
                        Config.globalConfig().typing_hud_y = mParams.y;
                        mHandler.postDelayed(mHideRunnable, 2000);
                        return true;
                }
                return false;
            }
        });
    }

    private void applyStyles(int bgColor, int txtColor, int txtSize) {
        if (mHudView == null) return;

        Drawable typedBg = mTypedWord.getBackground();
        if (typedBg != null) {
            typedBg = typedBg.mutate();
            if (typedBg instanceof GradientDrawable) {
                ((GradientDrawable) typedBg).setColor(bgColor);
            }
            mTypedWord.setBackground(typedBg);
        }

        Drawable correctedBg = mCorrectedWord.getBackground();
        if (correctedBg != null) {
            correctedBg = correctedBg.mutate();
            if (correctedBg instanceof GradientDrawable) {
                ((GradientDrawable) correctedBg).setColor(bgColor);
            }
            mCorrectedWord.setBackground(correctedBg);
        }

        mTypedWord.setTextColor(txtColor);
        mTypedWord.setTextSize(TypedValue.COMPLEX_UNIT_SP, txtSize);

        mCorrectedWord.setTextColor(txtColor);
        mCorrectedWord.setTextSize(TypedValue.COMPLEX_UNIT_SP, txtSize);

        mArrow.setColorFilter(txtColor, PorterDuff.Mode.SRC_IN);
    }

    private void showHUD() {
        if (mHudView == null) return;

        if (mIsVisible) {
            mHudView.animate().cancel();
            mHudView.setAlpha(1f);
            return;
        }

        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT);

        mParams.gravity = Gravity.TOP | Gravity.START;

        int savedX = Config.globalConfig().typing_hud_x;
        int savedY = Config.globalConfig().typing_hud_y;

        if (savedX != -1 && savedY != -1) {
            mParams.x = savedX;
            mParams.y = savedY;
        } else {
            // Default position: top center
            android.util.DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            mParams.x = (metrics.widthPixels / 2); // Initial estimate, wrap_content makes it tricky
            mParams.y = 100;
        }

        try {
            if (mHudView.getParent() != null) {
                mWindowManager.removeViewImmediate(mHudView);
            }
            mWindowManager.addView(mHudView, mParams);
            mIsVisible = true;
            mHudView.setAlpha(0f);
            mHudView.animate().alpha(1f).setDuration(200).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideHUD() {
        if (!mIsVisible || mHudView == null) return;

        mHudView.animate().cancel();
        mHudView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (mHudView == null) return;
            try {
                if (mHudView.getParent() != null) {
                    mWindowManager.removeViewImmediate(mHudView);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mIsVisible = false;
        }).start();
    }

    private final Runnable mHideRunnable = this::hideHUD;

    public void cleanup() {
        mHandler.removeCallbacks(mHideRunnable);
        if (mIsVisible && mHudView != null) {
            try {
                mWindowManager.removeView(mHudView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mIsVisible = false;
        mHudView = null;
    }
}
