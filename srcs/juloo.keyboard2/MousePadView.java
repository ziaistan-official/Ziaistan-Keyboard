package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.os.Build;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Button;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


public class MousePadView {

    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mTrackpadView;
    private View mMinimizedView;
    private View mCursorView;
    private CursorDrawView mDrawView;

    private boolean isTrackpadVisible = false;
    private boolean isMinimized = false;


    private WindowManager.LayoutParams mTrackpadParams;
    private WindowManager.LayoutParams mMinimizedParams;


    private WindowManager.LayoutParams mCursorParams;
    private float mCursorX, mCursorY;
    private int mScreenWidth, mScreenHeight;


    private GestureDetector mGestureDetector;
    private static final int TRAIL_LENGTH = 10;


    private boolean isMagnifierEnabled = false;
    private float magnifierZoom = 2.0f;
    private ImageButton magnifierToggleBtn;


    private View settingsOverlay;
    private WindowManager.LayoutParams settingsParams;
    private float sensitivity = 1.0f;
    private float cursorSpeed = 1.0f;
    private int cursorSize = 10;
    private int trackpadTransparency = 255;


    private final int[] locationBuffer = new int[2];


    private int tapCount = 0;
    private long lastTapTime = 0;
    private boolean hasMoved = false;
    private float startDownX, startDownY;
    private float lastEventX, lastEventY;
    private static final int TAP_TIMEOUT = 300;
    private android.os.Handler tapHandler = new android.os.Handler();
    private Runnable singleTapRunnable = this::performSystemClick;
    private Runnable doubleTapRunnable = this::performLongClick;

    public MousePadView(Context context) {
        this.mContext = context;
        this.mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        updateScreenDimensions();
        loadSettings();
    }

    private void loadSettings() {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        this.sensitivity = prefs.getFloat("trackpad_sensitivity", 1.0f);
        this.cursorSpeed = prefs.getFloat("trackpad_cursor_speed", 1.0f);
        this.cursorSize = prefs.getInt("trackpad_cursor_size", 10);
        this.magnifierZoom = prefs.getFloat("magnifier_zoom", 2.0f);
        this.trackpadTransparency = prefs.getInt("trackpad_transparency", 255);
    }

    private void updateScreenDimensions() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        }
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
    }

    public void show(int themeResId) {
        if (!canDrawOverlays()) {
            promptEnableOverlay();
            return;
        }

        if (isTrackpadVisible) return;

        Context themedContext = new ContextThemeWrapper(mContext, themeResId);
        createTrackpadOverlay(themedContext);
        createCursorOverlay();

        isTrackpadVisible = true;
        isMinimized = false;
    }

    public void hide() {
        cleanup();
        removeTrackpadOverlay();
        removeMinimizedOverlay();
        removeCursorOverlay();
        isTrackpadVisible = false;
        isMinimized = false;
    }

    private void createTrackpadOverlay(Context context) {
        if (mTrackpadView != null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        mTrackpadView = inflater.inflate(R.layout.mouse_pad_overlay, null);


        if (mTrackpadView != null && mTrackpadView.getBackground() != null) {
             mTrackpadView.getBackground().setAlpha(trackpadTransparency);
        }


        android.util.TypedValue typedValue = new android.util.TypedValue();
        int keyColor = 0xFFCCCCCC;
        if (context.getTheme().resolveAttribute(R.attr.colorKey, typedValue, true)) {
            keyColor = typedValue.data;
        }
        String themeName = Config.globalConfig().themeName;

        View closeBtn = mTrackpadView.findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(v -> hide());
        applyTheme(closeBtn, themeName, keyColor);

        View minBtn = mTrackpadView.findViewById(R.id.btn_minimize);
        minBtn.setOnClickListener(v -> minimize(context));
        applyTheme(minBtn, themeName, keyColor);

        View leftBtn = mTrackpadView.findViewById(R.id.btn_left_click);
        leftBtn.setOnClickListener(v -> performSystemClick());
        applyTheme(leftBtn, themeName, keyColor);

        View rightBtn = mTrackpadView.findViewById(R.id.btn_right_click);
        rightBtn.setOnClickListener(v -> performRightClick());
        applyTheme(rightBtn, themeName, keyColor);


        ImageButton settingsBtn = mTrackpadView.findViewById(R.id.btn_settings);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> showSettings());
            applyTheme(settingsBtn, themeName, keyColor);
        }


        magnifierToggleBtn = mTrackpadView.findViewById(R.id.btn_magnifier_toggle);
        if (magnifierToggleBtn != null) {
            magnifierToggleBtn.setOnClickListener(v -> toggleMagnifier());
            applyTheme(magnifierToggleBtn, themeName, keyColor);
        }


        View surface = mTrackpadView.findViewById(R.id.trackpad_surface);

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

                return false;
            }
        });

        surface.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    hasMoved = false;
                    startDownX = event.getX();
                    startDownY = event.getY();
                    lastEventX = startDownX;
                    lastEventY = startDownY;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTapTime < TAP_TIMEOUT) {
                        tapCount++;
                    } else {
                        tapCount = 1;
                    }
                    lastTapTime = currentTime;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float currentX = event.getX();
                    float currentY = event.getY();


                    float totalDx = currentX - startDownX;
                    float totalDy = currentY - startDownY;

                    int touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();

                    touchSlop = Math.max(touchSlop, 20);

                    if (!hasMoved && (Math.abs(totalDx) > touchSlop || Math.abs(totalDy) > touchSlop)) {
                        hasMoved = true;




                        tapCount = 0;
                    }

                    if (hasMoved) {

                        float deltaX = currentX - lastEventX;
                        float deltaY = currentY - lastEventY;
                        moveCursor(deltaX * sensitivity * cursorSpeed, deltaY * sensitivity * cursorSpeed);
                    }

                    lastEventX = currentX;
                    lastEventY = currentY;
                    break;

                case MotionEvent.ACTION_UP:
                    if (!hasMoved) {

                        if (tapCount == 1) {
                            tapHandler.postDelayed(singleTapRunnable, TAP_TIMEOUT);
                        } else if (tapCount == 2) {
                            tapHandler.removeCallbacks(singleTapRunnable);
                            tapHandler.postDelayed(doubleTapRunnable, TAP_TIMEOUT);
                        } else if (tapCount == 3) {
                            tapHandler.removeCallbacks(singleTapRunnable);
                            tapHandler.removeCallbacks(doubleTapRunnable);
                            performRightClick();
                            tapCount = 0;
                        }
                    }
                    break;
            }
            return true;
        });


        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        mTrackpadParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        mTrackpadParams.gravity = Gravity.TOP | Gravity.START;

        mTrackpadParams.x = mScreenWidth - 400;
        if (mTrackpadParams.x < 0) mTrackpadParams.x = 0;
        mTrackpadParams.y = mScreenHeight - 600;
        if (mTrackpadParams.y < 0) mTrackpadParams.y = 0;


        View titleBar = mTrackpadView.findViewById(R.id.title_bar);
        if (titleBar != null) {
            titleBar.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = mTrackpadParams.x;
                            initialY = mTrackpadParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            mTrackpadParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                            mTrackpadParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                            try {
                                mWindowManager.updateViewLayout(mTrackpadView, mTrackpadParams);
                            } catch (Exception e) { e.printStackTrace(); }
                            return true;
                    }
                    return false;
                }
            });
        }


        View resizeHandle = mTrackpadView.findViewById(R.id.resize_handle);
        if (resizeHandle != null) {
            resizeHandle.setOnTouchListener(new View.OnTouchListener() {
                private int initialWidth;
                private int initialHeight;
                private float initialTouchX;
                private float initialTouchY;
                private View contentFrame;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (contentFrame == null) {
                        View surface = mTrackpadView.findViewById(R.id.trackpad_surface);
                        if (surface != null && surface.getParent() instanceof View) {
                            contentFrame = (View) surface.getParent();
                        }
                    }

                    if (contentFrame == null) return false;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialWidth = contentFrame.getWidth();
                            initialHeight = contentFrame.getHeight();
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int newWidth = initialWidth + (int) (event.getRawX() - initialTouchX);
                            int newHeight = initialHeight + (int) (event.getRawY() - initialTouchY);

                            if (newWidth < 200) newWidth = 200;
                            if (newHeight < 200) newHeight = 200;

                            android.view.ViewGroup.LayoutParams lp = contentFrame.getLayoutParams();
                            lp.width = newWidth;
                            lp.height = newHeight;
                            contentFrame.setLayoutParams(lp);
                            return true;
                    }
                    return false;
                }
            });
        }

        try {
            mWindowManager.addView(mTrackpadView, mTrackpadParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeTrackpadOverlay() {
        if (mTrackpadView != null) {
            try {
                mWindowManager.removeView(mTrackpadView);
            } catch (Exception e) { e.printStackTrace(); }
            mTrackpadView = null;
        }
    }

    private void minimize(Context context) {
        if (isMinimized) return;
        removeTrackpadOverlay();
        createMinimizedOverlay(context);
        isMinimized = true;
    }

    private void maximize(Context context) {
        if (!isMinimized) return;
        removeMinimizedOverlay();
        createTrackpadOverlay(context);
        isMinimized = false;
    }

    private void createMinimizedOverlay(Context context) {
        if (mMinimizedView != null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        mMinimizedView = inflater.inflate(R.layout.mouse_pad_minimized, null);


        View maxBtn = mMinimizedView.findViewById(R.id.btn_maximize);
        maxBtn.setOnClickListener(null);


        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        int savedX = prefs.getInt("minimized_x", 20);
        int savedY = prefs.getInt("minimized_y", 20);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        mMinimizedParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        mMinimizedParams.gravity = Gravity.TOP | Gravity.START;
        mMinimizedParams.x = savedX;
        mMinimizedParams.y = savedY;


        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging = false;
            private final int touchSlop = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mMinimizedParams.x;
                        initialY = mMinimizedParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            mMinimizedParams.x = initialX + (int) dx;
                            mMinimizedParams.y = initialY + (int) dy;
                            try {
                                mWindowManager.updateViewLayout(mMinimizedView, mMinimizedParams);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {

                            maximize(context);
                        } else {

                            android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                                .edit()
                                .putInt("minimized_x", mMinimizedParams.x)
                                .putInt("minimized_y", mMinimizedParams.y)
                                .apply();
                        }
                        return true;
                }
                return false;
            }
        };

        maxBtn.setOnTouchListener(dragListener);

        try {
            mWindowManager.addView(mMinimizedView, mMinimizedParams);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void removeMinimizedOverlay() {
        if (mMinimizedView != null) {
            try {
                mWindowManager.removeView(mMinimizedView);
            } catch (Exception e) { e.printStackTrace(); }
            mMinimizedView = null;
        }
    }

    private void createCursorOverlay() {
        if (mCursorView != null) return;

        mCursorX = mScreenWidth / 2f;
        mCursorY = mScreenHeight / 2f;

        mDrawView = new CursorDrawView(mContext);
        mCursorView = mDrawView;

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;


        mCursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        mCursorParams.gravity = Gravity.TOP | Gravity.START;
        mCursorParams.x = 0;
        mCursorParams.y = 0;

        try {
            mWindowManager.addView(mCursorView, mCursorParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeCursorOverlay() {
        if (mCursorView != null) {
            try {
                mWindowManager.removeView(mCursorView);
            } catch (Exception e) { e.printStackTrace(); }
            mCursorView = null;
        }
    }

    private float getScreenX() {
        float x = mCursorX;
        if (mCursorView != null) {
            mCursorView.getLocationOnScreen(locationBuffer);
            x += locationBuffer[0];
        }
        return x;
    }

    private float getScreenY() {
        float y = mCursorY;
        if (mCursorView != null) {
            mCursorView.getLocationOnScreen(locationBuffer);
            y += locationBuffer[1];
        }
        return y;
    }

    private void moveCursor(float dx, float dy) {
        mCursorX += dx * 2.5f;
        mCursorY += dy * 2.5f;

        mCursorX = Math.max(0, Math.min(mCursorX, mScreenWidth));
        mCursorY = Math.max(0, Math.min(mCursorY, mScreenHeight));


        if (isMagnifierEnabled) {
            GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
            if (service != null) {
                service.updateMagnification(true, magnifierZoom, getScreenX(), getScreenY());
            }
        }

        if (mDrawView != null) {
            mDrawView.updatePosition(mCursorX, mCursorY);
        }
    }

    private void performSystemClick() {
        GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
        if (service != null) {

            service.click(getScreenX(), getScreenY());
            if (mDrawView != null) mDrawView.animateClick();
        } else {
            promptEnableAccessibility();
        }
    }

    private void performLongClick() {
        GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
        if (service != null) {
            service.longClick(getScreenX(), getScreenY());
            if (mDrawView != null) mDrawView.animateClick();
        } else {
            promptEnableAccessibility();
        }
    }

    private void performRightClick() {
        GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
        if (service != null) {

            service.rightClick(getScreenX(), getScreenY());
             if (mDrawView != null) mDrawView.animateClick();
        } else {
             promptEnableAccessibility();
        }
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(mContext);
        }
        return true;
    }

    private void promptEnableOverlay() {
        Toast.makeText(mContext, "Please grant Overlay permission for Mouse Pad.", Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + mContext.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void promptEnableAccessibility() {
        Toast.makeText(mContext, "Please enable Accessibility Service.", Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) { e.printStackTrace(); }
    }



    private void toggleMagnifier() {
        GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
        if (service == null) {
            promptEnableAccessibility();
            return;
        }

        isMagnifierEnabled = !isMagnifierEnabled;

        if (isMagnifierEnabled) {
            if (magnifierToggleBtn != null) {
                magnifierToggleBtn.setBackgroundColor(0xFF4CAF50);
            }
            service.updateMagnification(true, magnifierZoom, mCursorX, mCursorY);
        } else {
            if (magnifierToggleBtn != null) {
                magnifierToggleBtn.setBackgroundColor(0xFF757575);
            }
            service.updateMagnification(false, 1.0f, 0, 0);
        }
    }

    private void showSettings() {
        if (settingsOverlay != null) return;

        settingsOverlay = LayoutInflater.from(mContext).inflate(R.layout.mouse_pad_settings_dialog, null);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        settingsParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        settingsParams.gravity = Gravity.CENTER;

        Button closeBtn = settingsOverlay.findViewById(R.id.btn_close_settings);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissSettings());
        }

        setupSeekBars(settingsOverlay);

        try {
            mWindowManager.addView(settingsOverlay, settingsParams);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void dismissSettings() {
        if (settingsOverlay != null) {
            try {
                mWindowManager.removeView(settingsOverlay);
            } catch (Exception e) { e.printStackTrace(); }
            settingsOverlay = null;
        }
    }

    private void setupSeekBars(View settingsView) {

        SeekBar magnifierSizeBar = settingsView.findViewById(R.id.seekbar_magnifier_size);
        if (magnifierSizeBar != null) {
            magnifierSizeBar.setEnabled(false);
        }


        SeekBar zoomBar = settingsView.findViewById(R.id.seekbar_zoom_level);
        if (zoomBar != null) {
            zoomBar.setProgress((int)(magnifierZoom * 10));
            zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;

                    magnifierZoom = progress / 10.0f;
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putFloat("magnifier_zoom", magnifierZoom).apply();

                    if (isMagnifierEnabled) {
                        GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
                        if (service != null) {
                            service.updateMagnification(true, magnifierZoom, mCursorX, mCursorY);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }


        SeekBar cursorSizeBar = settingsView.findViewById(R.id.seekbar_cursor_size);
        if (cursorSizeBar != null) {
            cursorSizeBar.setProgress(cursorSize);
            cursorSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    cursorSize = progress;
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putInt("trackpad_cursor_size", cursorSize).apply();
                    if (mDrawView != null) {
                        mDrawView.updateCursorSize(cursorSize);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }


        SeekBar cursorSpeedBar = settingsView.findViewById(R.id.seekbar_cursor_speed);
        if (cursorSpeedBar != null) {
            cursorSpeedBar.setProgress((int)(cursorSpeed * 50));
            cursorSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    cursorSpeed = progress / 50.0f;
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putFloat("trackpad_cursor_speed", cursorSpeed).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }


        SeekBar sensitivityBar = settingsView.findViewById(R.id.seekbar_sensitivity);
        if (sensitivityBar != null) {
            sensitivityBar.setProgress((int)(sensitivity * 50));
            sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    sensitivity = progress / 50.0f;
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putFloat("trackpad_sensitivity", sensitivity).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }


        SeekBar transparencyBar = settingsView.findViewById(R.id.seekbar_transparency);
        if (transparencyBar != null) {
            transparencyBar.setMax(255);
            if (Build.VERSION.SDK_INT >= 26) {
                transparencyBar.setMin(25);
            }
            transparencyBar.setProgress(trackpadTransparency);
            transparencyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    if (progress < 25) progress = 25;

                    trackpadTransparency = progress;
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putInt("trackpad_transparency", trackpadTransparency).apply();


                    if (mTrackpadView != null) {
                         mTrackpadView.setAlpha(trackpadTransparency / 255.0f);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void applyTheme(View view, String themeName, int color) {
        if (view == null || themeName == null) return;

        float radius = 15f;
        view.setBackground(new ProceduralThemeDrawable(themeName, color, radius));
    }

    private void cleanup() {
        if (isMagnifierEnabled) {
            GlobalActionAccessibilityService service = GlobalActionAccessibilityService.getInstance();
            if (service != null) {
                service.updateMagnification(false, 1.0f, 0, 0);
            }
            isMagnifierEnabled = false;
        }
        dismissSettings();
    }

    private static class CursorDrawView extends View {
        private Paint paintCursorFill;
        private Paint paintCursorOutline;
        private Paint paintTrail;
        private boolean isClickAnimating = false;
        private float clickRadius = 0;
        private int cursorSize = 10;

        private List<PointF> trail = new ArrayList<>();
        private float currentWorldX, currentWorldY;

        public CursorDrawView(Context context) {
            super(context);
            paintCursorFill = new Paint();
            paintCursorFill.setColor(Color.BLACK);
            paintCursorFill.setStyle(Paint.Style.FILL);

            paintCursorOutline = new Paint();
            paintCursorOutline.setColor(Color.CYAN);
            paintCursorOutline.setStyle(Paint.Style.STROKE);
            paintCursorOutline.setStrokeWidth(3);
            paintCursorOutline.setStrokeJoin(Paint.Join.ROUND);
            paintCursorOutline.setShadowLayer(10, 0, 0, Color.CYAN);

            paintTrail = new Paint();
            paintTrail.setColor(Color.CYAN);
            paintTrail.setStrokeWidth(4);
            paintTrail.setStyle(Paint.Style.STROKE);
            paintTrail.setStrokeCap(Paint.Cap.ROUND);
            paintTrail.setShadowLayer(8, 0, 0, Color.BLUE);

            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        public void updateCursorSize(int size) {
            this.cursorSize = size;
            invalidate();
        }

        public void updatePosition(float worldX, float worldY) {
            this.currentWorldX = worldX;
            this.currentWorldY = worldY;

            trail.add(new PointF(worldX, worldY));
            if (trail.size() > TRAIL_LENGTH) {
                trail.remove(0);
            }
            invalidate();
        }

        public void animateClick() {
            isClickAnimating = true;
            clickRadius = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);


            float drawX = currentWorldX;
            float drawY = currentWorldY;


            if (trail.size() > 1) {
                Path trailPath = new Path();
                boolean first = true;
                for (PointF p : trail) {
                    if (first) {
                        trailPath.moveTo(p.x, p.y);
                        first = false;
                    } else {
                        trailPath.lineTo(p.x, p.y);
                    }
                }
                canvas.drawPath(trailPath, paintTrail);
            }


            if (isClickAnimating) {
                Paint ripplePaint = new Paint(paintCursorOutline);
                ripplePaint.setStyle(Paint.Style.STROKE);
                ripplePaint.setStrokeWidth(3);
                ripplePaint.setAlpha((int)(255 * (1 - clickRadius/60f)));
                canvas.drawCircle(drawX, drawY, clickRadius, ripplePaint);
                clickRadius += 5;
                if (clickRadius > 60) {
                    isClickAnimating = false;
                } else {
                    invalidate();
                }
            }


            Path path = new Path();
            float scale = cursorSize / 10f;

            path.moveTo(drawX, drawY);
            path.lineTo(drawX, drawY + 45 * scale);
            path.lineTo(drawX + 12 * scale, drawY + 33 * scale);
            path.lineTo(drawX + 24 * scale, drawY + 57 * scale);
            path.lineTo(drawX + 30 * scale, drawY + 54 * scale);
            path.lineTo(drawX + 18 * scale, drawY + 30 * scale);
            path.lineTo(drawX + 33 * scale, drawY + 30 * scale);
            path.close();

            canvas.drawPath(path, paintCursorFill);
            canvas.drawPath(path, paintCursorOutline);
        }
    }
}
