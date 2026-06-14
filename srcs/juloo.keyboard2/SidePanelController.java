package juloo.keyboard2;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SidePanelController implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Context context;
    private final WindowManager windowManager;
    private final GlobalActionAccessibilityService service;
    private final SharedPreferences prefs;
    private final Typeface specialFont;

    private static SidePanelController instance;
    public static SidePanelController getInstance() { return instance; }

    private View leftTrigger;
    private View rightTrigger;
    private View bottomTrigger;

    private MenuView menuView;
    private BalloonView balloonView;
    private TextView popupLabelView;

    private int activeTabIndex = -1;
    private boolean isEnabled = false;
    private boolean isMenuShowing = false;

    private int triggerWidthPx;
    private int triggerTransparency = 0;

    private boolean hapticEnabled = true;
    private boolean soundEnabled = true;

    private enum AnimationStyle {
        CLASSIC, FLUID, ELASTIC, RIBBON, ARROW,
        PLASMA, VORTEX, MAGNET, CHAIN, BEAM, PIXEL,
        JELLY, GHOST, LAVA, TAFFY, SNAKE, WORM, GUM, ROOTS, LIGHTNING, BLOB,

        VENOM, TESLA_COIL, ZIPPER, PORTAL, ORIGAMI, SONAR, BLACKHOLE, HEAVY_CHAIN, MATRIX, ARROW_CLUSTER,

        CYBERPUNK, LIQUID_GLASS, MECHANICAL_RGB, MAGMA_EMBER, INK_PARCHMENT,
        COSMIC_NEBULA, SAKURA_GARDEN, RETRO_8BIT, GOLDEN_ERA, DEEP_OCEAN,
        NEON_RAIN, CANDY_CRUSH, STEAMPUNK, HOLOGRAPHIC, SPIRIT_REALM,
        GOLDEN_LUXURY, SAKURA_BREEZE, BIOLUMINESCENCE, RETRO_ARCADE, CRYSTAL_PRISM,
        VAPORWAVE, NOIR_RAIN, PAPER_CUTOUT, STAR_FIELD, GEARS,

        RANDOM
    }
    private AnimationStyle animationStyle = AnimationStyle.CLASSIC;
    private AnimationStyle currentRandomStyle = AnimationStyle.CLASSIC;

    public SidePanelController(Context context, GlobalActionAccessibilityService service) {
        instance = this;
        this.context = context;
        this.service = service;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.prefs.registerOnSharedPreferenceChangeListener(this);

        Typeface font = Typeface.DEFAULT;
        try {
            font = Typeface.createFromAsset(context.getAssets(), "special_font.ttf");
        } catch (Exception e) {}
        this.specialFont = font;

        loadConfig();
    }

    private void loadConfig() {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int widthDp = prefs.getInt("side_panel_sensitivity", 20);
        this.triggerWidthPx = (int) (widthDp * metrics.density);
        this.triggerTransparency = prefs.getInt("side_panel_transparency", 0);

        this.hapticEnabled = prefs.getBoolean("side_panel_haptic", true);
        this.soundEnabled = prefs.getBoolean("side_panel_sound", true);

        String styleName = prefs.getString("side_panel_animation_style", "Classic");
        if ("Water Drop".equalsIgnoreCase(styleName)) styleName = "Fluid";
        try {
            this.animationStyle = AnimationStyle.valueOf(styleName.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            this.animationStyle = AnimationStyle.CLASSIC;
        }

        if (isEnabled) {
            removeTriggers();
            addTriggers();
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.isEnabled == enabled) return;
        this.isEnabled = enabled;
        if (enabled) {
            addTriggers();
        } else {
            removeTriggers();
            hideMenu();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("enable_side_panel".equals(key)) {
            setEnabled(sharedPreferences.getBoolean(key, false));
        } else if ("side_panel_sensitivity".equals(key) || "side_panel_transparency".equals(key) || "side_panel_animation_style".equals(key)) {
            loadConfig();
        } else if ("side_panel_haptic".equals(key) || "side_panel_sound".equals(key)) {
            loadConfig();
        } else if (key.startsWith("side_panel_") && key.endsWith("_glyph")) {
            if (leftTrigger != null) leftTrigger.invalidate();
            if (rightTrigger != null) rightTrigger.invalidate();
            if (bottomTrigger != null) bottomTrigger.invalidate();
        }
    }

    private int getTriggerColor() {
        if (triggerTransparency == 0) return 0x00000000;
        int alpha = (int) ((triggerTransparency / 100f) * 255);
        return (alpha << 24) | 0x00FF0000;
    }

    private void addTriggers() {
        int color = getTriggerColor();
        if (leftTrigger == null) {
            leftTrigger = new TriggerView(context, Gravity.LEFT);
            leftTrigger.setBackgroundColor(color);
            leftTrigger.setOnTouchListener(new TriggerTouchListener(Gravity.LEFT));
            try { windowManager.addView(leftTrigger, createTriggerParams(Gravity.LEFT)); } catch (Exception e) {}
        }
        if (rightTrigger == null) {
            rightTrigger = new TriggerView(context, Gravity.RIGHT);
            rightTrigger.setBackgroundColor(color);
            rightTrigger.setOnTouchListener(new TriggerTouchListener(Gravity.RIGHT));
            try { windowManager.addView(rightTrigger, createTriggerParams(Gravity.RIGHT)); } catch (Exception e) {}
        }
        if (bottomTrigger == null) {
            bottomTrigger = new TriggerView(context, Gravity.BOTTOM);
            bottomTrigger.setBackgroundColor(color);
            bottomTrigger.setOnTouchListener(new TriggerTouchListener(Gravity.BOTTOM));
            try { windowManager.addView(bottomTrigger, createTriggerParams(Gravity.BOTTOM)); } catch (Exception e) {}
        }
        updateSystemGestureExclusions();
    }

    private void updateSystemGestureExclusions() {
        if (Build.VERSION.SDK_INT >= 29) {
            Runnable update = () -> {
                if (leftTrigger != null) applyExclusion(leftTrigger);
                if (rightTrigger != null) applyExclusion(rightTrigger);
                if (bottomTrigger != null) applyExclusion(bottomTrigger);
            };
            if (leftTrigger != null) leftTrigger.post(update);
        }
    }

    private void applyExclusion(View v) {
        if (Build.VERSION.SDK_INT >= 29) {
            List<android.graphics.Rect> rects = new ArrayList<>();
            rects.add(new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight()));
            v.setSystemGestureExclusionRects(rects);
        }
    }

    private void removeTriggers() {
        if (leftTrigger != null) { try { windowManager.removeView(leftTrigger); } catch (Exception e) {} leftTrigger = null; }
        if (rightTrigger != null) { try { windowManager.removeView(rightTrigger); } catch (Exception e) {} rightTrigger = null; }
        if (bottomTrigger != null) { try { windowManager.removeView(bottomTrigger); } catch (Exception e) {} bottomTrigger = null; }
    }

    private class TriggerView extends View {
        private final int gravity;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TriggerView(Context context, int gravity) {
            super(context);
            this.gravity = gravity;
            paint.setTypeface(specialFont);
            paint.setTextSize(20 * context.getResources().getDisplayMetrics().density);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setShadowLayer(5, 0, 0, Color.BLACK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Glyphs are now shown in the popup instead of on the side triggers.
        }
    }

    private WindowManager.LayoutParams createTriggerParams(int gravity) {
        int w = WindowManager.LayoutParams.MATCH_PARENT;
        int h = WindowManager.LayoutParams.MATCH_PARENT;
        if (gravity == Gravity.LEFT || gravity == Gravity.RIGHT) w = triggerWidthPx;
        else if (gravity == Gravity.BOTTOM) h = triggerWidthPx;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = gravity;
        return params;
    }

    private class TriggerTouchListener implements View.OnTouchListener {
        private final int gravity;
        private float startX, startY;
        private long startTime;
        private int currentTab = -1;
        private boolean isSwiping = false;
        private boolean tabLocked = false;
        private int lastSelection = -1;
        private VelocityTracker velocityTracker;

        public TriggerTouchListener(int gravity) {
            this.gravity = gravity;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();

            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    startTime = System.currentTimeMillis();
                    currentTab = calculateTab(v, event);
                    isSwiping = false;
                    tabLocked = false;
                    lastSelection = -1;


                    if (animationStyle == AnimationStyle.RANDOM) {
                        pickRandomStyle();
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float currentX = event.getRawX();
                    float currentY = event.getRawY();
                    float dx = Math.abs(currentX - startX);
                    float dy = Math.abs(currentY - startY);

                    float distFromEdge;
                    float maxDist;
                    if (gravity == Gravity.LEFT) {
                        distFromEdge = currentX;
                        maxDist = dm.widthPixels / 2f;
                    } else if (gravity == Gravity.RIGHT) {
                        distFromEdge = dm.widthPixels - currentX;
                        maxDist = dm.widthPixels / 2f;
                    } else {
                        distFromEdge = dm.heightPixels - currentY;
                        maxDist = dm.heightPixels / 7.0f;
                    }

                    if (isSwiping && isMenuShowing) {
                        if (distFromEdge < 10) {
                            hideMenu();
                            isSwiping = false;
                            return true;
                        }

                        float perpendicular = (gravity == Gravity.BOTTOM) ? dy : dx;
                        if (!tabLocked) {
                            if (perpendicular > 80) {
                                tabLocked = true;
                            }
                        }
                    }

                    if (!isSwiping) {
                        float threshold = 10 * context.getResources().getDisplayMetrics().density;
                        boolean active = (gravity == Gravity.BOTTOM) ? dy > threshold : dx > threshold;
                        if (active) {
                            isSwiping = true;
                            showMenu(gravity, currentTab);
                            if (balloonView != null) {
                                balloonView.setAnchor(startX, startY, gravity);
                            }
                        }
                    }

                    if (isSwiping) {
                        if (tabLocked && distFromEdge < 50) {
                            tabLocked = false;
                        }

                        if (!tabLocked) {
                            int newTab = calculateTabRaw(currentX, currentY);
                            if (newTab != currentTab && newTab != -1) {
                                currentTab = newTab;
                                showMenu(gravity, currentTab);
                                if (balloonView != null) {
                                    balloonView.setAnchor(startX, startY, gravity);
                                }
                            }
                        }

                        if (isMenuShowing) {
                            int newSelection = updateMenuSelection(distFromEdge, maxDist, gravity, currentX, currentY);
                            if (newSelection != lastSelection && newSelection != -1) {
                                lastSelection = newSelection;
                                if (hapticEnabled) {
                                    triggerVibration();
                                }
                            }
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping && isMenuShowing) {
                        velocityTracker.computeCurrentVelocity(1000);
                        float vx = Math.abs(velocityTracker.getXVelocity());
                        float vy = Math.abs(velocityTracker.getYVelocity());
                        float velocity = (gravity == Gravity.BOTTOM) ? vy : vx;
                        long duration = System.currentTimeMillis() - startTime;

                        if (velocity > 2000 && duration < 300) {
                            executeFastSwipeAction(gravity, currentTab);
                        } else {
                            executeMenuSelection();
                        }
                        hideMenu();
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    isSwiping = false;
                    return true;
            }
            return false;
        }

        private int calculateTab(View v, MotionEvent event) {
            if (gravity == Gravity.BOTTOM) return (int) (event.getX() / (v.getWidth() / 3));
            else return (int) (event.getY() / (v.getHeight() / 5));
        }

        private int calculateTabRaw(float rawX, float rawY) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (gravity == Gravity.BOTTOM) {
                int tab = (int) (rawX / (dm.widthPixels / 3));
                if (tab >= 3) tab = 2; if (tab < 0) tab = 0;
                return tab;
            } else {
                int tab = (int) (rawY / (dm.heightPixels / 5));
                if (tab >= 5) tab = 4; if (tab < 0) tab = 0;
                return tab;
            }
        }
    }

    private void pickRandomStyle() {
        AnimationStyle[] all = AnimationStyle.values();

        int max = all.length - 1;
        if (max > 0) {
            currentRandomStyle = all[new Random().nextInt(max)];
        } else {
            currentRandomStyle = AnimationStyle.CLASSIC;
        }
    }

    private void triggerVibration() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(20);
            }
        }
    }

    private void showMenu(int gravity, int tabIndex) {
        this.activeTabIndex = tabIndex;
        if (menuView == null) {
            menuView = new MenuView(context);
        }

        List<ActionItem> actions = getActionsForTab(gravity, tabIndex);
        if (actions.isEmpty()) {
            isMenuShowing = false;
            return;
        }

        menuView.setActions(actions);

        if (balloonView == null) {
            balloonView = new BalloonView(context);
        }
        if (balloonView.getParent() != null) windowManager.removeView(balloonView);

        WindowManager.LayoutParams balloonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowManager.addView(balloonView, balloonParams);

        if (popupLabelView == null) {
            popupLabelView = new TextView(context);
            popupLabelView.setTextSize(24);
            popupLabelView.setTextColor(0xFFFFFFFF);
            popupLabelView.setTypeface(specialFont);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(0xAA000000); // Semi-transparent black background for contrast
            gd.setCornerRadius(20 * context.getResources().getDisplayMetrics().density);
            popupLabelView.setBackground(gd);

            int p = (int) (16 * context.getResources().getDisplayMetrics().density);
            popupLabelView.setPadding(p, p / 2, p, p / 2);
            popupLabelView.setGravity(Gravity.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                popupLabelView.setElevation(25);
            }
        }

        if (popupLabelView.getParent() != null) windowManager.removeView(popupLabelView);

        WindowManager.LayoutParams labelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        labelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        labelParams.x = 0;
        labelParams.y = (int) (100 * context.getResources().getDisplayMetrics().density);

        windowManager.addView(popupLabelView, labelParams);
        popupLabelView.setVisibility(View.INVISIBLE);

        isMenuShowing = true;
    }

    private void hideMenu() {
        if (balloonView != null && balloonView.getParent() != null) {
            try { windowManager.removeView(balloonView); } catch (Exception e) {}
        }
        if (popupLabelView != null && popupLabelView.getParent() != null) {
            try { windowManager.removeView(popupLabelView); } catch (Exception e) {}
        }
        isMenuShowing = false;
    }

    private int updateMenuSelection(float currentDist, float maxDist, int gravity, float currentX, float currentY) {
        if (menuView != null) {
            menuView.updateSelection(currentDist, maxDist);

            ActionItem item = menuView.getSelectedAction();
            boolean triggered = (item != null);

            if (popupLabelView != null) {
                String side = (gravity == Gravity.LEFT) ? "left" : (gravity == Gravity.RIGHT) ? "right" : "bottom";
                String tabGlyph = prefs.getString("side_panel_" + side + "_" + activeTabIndex + "_glyph", "");

                if (item != null) {
                    String icon = (item.glyph != null && !item.glyph.isEmpty()) ? item.glyph : ActionRegistry.getIconForAction(item.label);
                    popupLabelView.setText((tabGlyph.isEmpty() ? "" : tabGlyph + " ") + icon + "\n" + item.label);
                    popupLabelView.setVisibility(View.VISIBLE);
                } else if (!tabGlyph.isEmpty() && activeTabIndex != -1) {
                    popupLabelView.setText(tabGlyph);
                    popupLabelView.setVisibility(View.VISIBLE);
                } else {
                    popupLabelView.setVisibility(View.INVISIBLE);
                }

                if (popupLabelView.getVisibility() == View.VISIBLE) {
                    windowManager.updateViewLayout(popupLabelView, popupLabelView.getLayoutParams());
                }
            }

            if (balloonView != null) {
                balloonView.updateTarget(currentX, currentY);
                balloonView.updateRender(currentDist, triggered);
            }

            return menuView.getSelectedIndex();
        }
        return -1;
    }

    private void executeFastSwipeAction(int gravity, int tabIndex) {
        String side = (gravity == Gravity.LEFT) ? "left" : (gravity == Gravity.RIGHT) ? "right" : "bottom";
        String key = "side_panel_" + side + "_" + tabIndex + "_fast";
        String config = prefs.getString(key, null);
        if (config != null && !config.isEmpty()) {
            ActionItem item = parseAction(config);
            if (item != null) {
                if (hapticEnabled) triggerVibration();
                if (soundEnabled) {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f);
                }
                performAction(item);
            }
        }
    }

    private void executeMenuSelection() {
        if (menuView != null) {
            ActionItem item = menuView.getSelectedAction();
            if (item != null) {
                if (soundEnabled) {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f);
                    }
                }
                performAction(item);
            }
        }
    }

    public void performAction(ActionItem item) {
        if (item.type == ActionType.CUSTOM && "cycle_theme".equals(item.data)) {
            cycleTheme();
            return;
        }

        if (item.type == ActionType.GLOBAL) {
            service.performGlobalAction(Integer.parseInt(item.data));
        } else if (item.type == ActionType.ACTIVITY || item.type == ActionType.SETTINGS) {
            launchActivity(item.data);
        } else if (item.type == ActionType.KEY) {
            handleKeyAction(item.data);
        } else if (item.type == ActionType.CUSTOM) {
            if (item.data.startsWith("app:")) {
                launchActivity(item.data.substring(4));
            } else if (item.data.startsWith("activity:")) {
                launchSmartActivity(item.data.substring(9));
            } else {
                service.performCustomAction(item.data);
            }
        } else if (item.type == ActionType.INTENT_CAT) {
            try {
                String[] parts = item.data.split("\\|");
                Intent intent = new Intent(parts[0]);
                if (parts.length > 1) intent.addCategory(parts[1]);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {}
        } else if (item.type == ActionType.INTENT) {
            try {
                Intent intent = new Intent(item.data);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {}
        }
    }

    private void cycleTheme() {
        AnimationStyle[] styles = AnimationStyle.values();
        int current = animationStyle.ordinal();
        int next = (current + 1) % styles.length;
        animationStyle = styles[next];
        prefs.edit().putString("side_panel_animation_style", animationStyle.name()).apply();

        String styleName = animationStyle.name().replace("_", " ");
        styleName = styleName.substring(0, 1).toUpperCase() + styleName.substring(1).toLowerCase();
        Toast.makeText(context, "Theme: " + styleName, Toast.LENGTH_SHORT).show();
    }

    private void launchActivity(String data) {
        try {
            Intent intent;
            if (data.contains("/")) {
                intent = new Intent(Intent.ACTION_MAIN);
                String[] cmp = data.split("/");
                intent.setClassName(cmp[0], cmp[1]);
            } else {
                intent = new Intent(data);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchSmartActivity(String className) {
        if (className == null || className.isEmpty()) return;
        if (className.contains("/")) {
            try {
                String[] parts = className.split("/");
                Intent intent = new Intent();
                intent.setClassName(parts[0], parts[1]);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                Toast.makeText(context, "Launch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        PackageManager pm = context.getPackageManager();
        String guessedPackage = null;
        String[] parts = className.split("\\.");
        if (parts.length >= 3) {
            guessedPackage = parts[0] + "." + parts[1] + "." + parts[2];
        }

        if (guessedPackage != null) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(guessedPackage, className));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception e) {}
        }

        Toast.makeText(context, "Searching...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            for (PackageInfo pkg : packages) {
                if (pkg.activities != null) {
                    for (ActivityInfo act : pkg.activities) {
                        if (act.name.equals(className) || act.name.endsWith("." + className)) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.setComponent(new ComponentName(pkg.packageName, act.name));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                                return;
                            } catch (Exception e) {}
                        }
                    }
                }
            }
            showToast("Activity not found.");
        }).start();
    }

    private void showToast(String msg) {
        if (service != null && service.getMainLooper() != null) {
            new android.os.Handler(service.getMainLooper()).post(() ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void handleKeyAction(String keyCodeStr) {
        int keyCode = Integer.parseInt(keyCodeStr);
        android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (audio == null) return;
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_VOLUME_UP:
                    audio.adjustVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI); break;
                case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
                    audio.adjustVolume(android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI); break;
                case android.view.KeyEvent.KEYCODE_VOLUME_MUTE:
                    audio.adjustVolume(android.media.AudioManager.ADJUST_TOGGLE_MUTE, android.media.AudioManager.FLAG_SHOW_UI); break;
            }
        } else {
            Toast.makeText(context, "Key injection requires active IME context", Toast.LENGTH_SHORT).show();
        }
    }

    private List<ActionItem> getActionsForTab(int gravity, int tabIndex) {
        List<ActionItem> list = new ArrayList<>();
        String side = (gravity == Gravity.LEFT) ? "left" : (gravity == Gravity.RIGHT) ? "right" : "bottom";
        String key = "side_panel_" + side + "_" + tabIndex;
        String config = prefs.getString(key, null);

        if (config != null && !config.isEmpty()) {
            String[] items = config.split(",");
            for (String itemStr : items) {
                ActionItem item = parseAction(itemStr);
                if (item != null) list.add(item);
            }
        }
        return list;
    }

    public ActionItem parseAction(String config) {
        try {
            String[] parts = config.split(":", 4);
            if (parts.length < 2) return null;
            String typeStr = parts[0];
            String data = parts[1];
            String label = (parts.length > 2) ? parts[2] : data;
            String glyph = (parts.length > 3) ? parts[3] : null;

            ActionType type;
            if ("global".equals(typeStr)) type = ActionType.GLOBAL;
            else if ("app".equals(typeStr)) type = ActionType.ACTIVITY;
            else if ("setting".equals(typeStr)) type = ActionType.SETTINGS;
            else if ("key".equals(typeStr)) type = ActionType.KEY;
            else if ("intent".equals(typeStr)) type = ActionType.INTENT;
            else if ("custom".equals(typeStr)) type = ActionType.CUSTOM;
            else if ("activity".equals(typeStr)) type = ActionType.CUSTOM;
            else if ("intent_cat".equals(typeStr)) type = ActionType.INTENT_CAT;
            else return null;

            return new ActionItem(label, type, data, glyph);
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public enum ActionType { GLOBAL, ACTIVITY, SETTINGS, KEY, INTENT, CUSTOM, INTENT_CAT }

    public static class ActionItem {
        String label;
        ActionType type;
        String data;
        String glyph;
        ActionItem(String label, ActionType type, String data, String glyph) {
            this.label = label;
            this.type = type;
            this.data = data;
            this.glyph = glyph;
        }
    }

    private class MenuView extends GridLayout {
        private List<ActionItem> actions;
        private int selectedIndex = -1;

        public MenuView(Context context) {
            super(context);
        }

        public void setActions(List<ActionItem> actions) {
            this.actions = actions;
        }

        public void updateSelection(float currentDist, float maxDist) {
            if (actions == null || actions.isEmpty()) {
                selectedIndex = -1;
                return;
            }
            float threshold = 25 * context.getResources().getDisplayMetrics().density;
            if (currentDist < threshold) {
                selectedIndex = -1;
                return;
            }
            float step = maxDist / actions.size();
            int index = (int) (currentDist / step);

            if (index < 0) index = 0;
            if (index >= actions.size()) index = actions.size() - 1;
            selectedIndex = index;
        }

        public ActionItem getSelectedAction() {
            if (selectedIndex >= 0 && selectedIndex < actions.size()) {
                return actions.get(selectedIndex);
            }
            return null;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }
    }

    private class BalloonView extends View {
        private Paint paint;
        private Paint shadowPaint;
        private float anchorX, anchorY;
        private float tipX, tipY;
        private float drag;
        private boolean triggered;
        private long tick = 0;
        private Random random = new Random();


        private class Particle {
            float x, y, vx, vy, life, size;
            int color;
        }
        private List<Particle> particles = new ArrayList<>();

        public BalloonView(Context context) {
            super(context);
            paint = new Paint();
            paint.setAntiAlias(true);

            shadowPaint = new Paint();
            shadowPaint.setAntiAlias(true);
            shadowPaint.setColor(0x88000000);
            shadowPaint.setMaskFilter(new BlurMaskFilter(20, BlurMaskFilter.Blur.NORMAL));
        }

        public void setAnchor(float x, float y, int gravity) {
            this.anchorX = x;
            this.anchorY = y;
            this.tipX = x;
            this.tipY = y;
            particles.clear();
        }

        public void updateTarget(float x, float y) {
            this.tipX = x;
            this.tipY = y;
        }

        public void updateRender(float drag, boolean triggered) {
            this.drag = drag;
            this.triggered = triggered;
            this.tick++;
            invalidate();
        }

        private void spawnParticles(int count, int color, float size, float speed) {
            if (drag < 20) return;
            for (int i = 0; i < count; i++) {
                Particle p = new Particle();
                p.x = tipX + (random.nextFloat() - 0.5f) * 20;
                p.y = tipY + (random.nextFloat() - 0.5f) * 20;
                double angle = random.nextDouble() * Math.PI * 2;
                p.vx = (float) Math.cos(angle) * speed;
                p.vy = (float) Math.sin(angle) * speed;
                p.life = 1.0f;
                p.size = size * (0.5f + random.nextFloat());
                p.color = color;
                particles.add(p);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);


            Iterator<Particle> it = particles.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.x += p.vx;
                p.y += p.vy;
                p.life -= 0.05f;
                if (p.life <= 0) it.remove();
                else {
                    paint.setShader(null);
                    paint.setColor(p.color);
                    paint.setAlpha((int)(p.life * 255));
                    canvas.drawCircle(p.x, p.y, p.size, paint);
                }
            }
            paint.setAlpha(255);

            if (drag < 5) return;

            paint.setStyle(Paint.Style.FILL);

            Shader defaultShader = new LinearGradient(anchorX, anchorY, tipX, tipY,
                new int[]{0xCC00B4DB, 0xCC0083B0}, null, Shader.TileMode.CLAMP);
            paint.setShader(defaultShader);


            AnimationStyle effectiveStyle = (animationStyle == AnimationStyle.RANDOM) ? currentRandomStyle : animationStyle;

            switch (effectiveStyle) {

                case CLASSIC:
                case FLUID: drawFluid(canvas); break;
                case ELASTIC: drawElastic(canvas); break;
                case RIBBON: drawRibbon(canvas); break;
                case ARROW: drawArrow(canvas); break;
                case PLASMA: drawPlasma(canvas); break;
                case VORTEX: drawVortex(canvas); break;
                case MAGNET: drawMagnet(canvas); break;
                case CHAIN: drawChain(canvas); break;
                case BEAM: drawBeam(canvas); break;
                case PIXEL: drawPixel(canvas); break;
                case JELLY: drawJelly(canvas); break;
                case GHOST: drawGhost(canvas); break;
                case LAVA: drawLava(canvas); break;
                case TAFFY: drawTaffy(canvas); break;
                case SNAKE: drawSnake(canvas); break;
                case WORM: drawWorm(canvas); break;
                case GUM: drawGum(canvas); break;
                case ROOTS: drawRoots(canvas); break;
                case LIGHTNING: drawLightning(canvas); break;
                case BLOB: drawBlob(canvas); break;


                case VENOM: drawVenom(canvas); break;
                case TESLA_COIL: drawTeslaCoil(canvas); break;
                case ZIPPER: drawZipper(canvas); break;
                case PORTAL: drawPortal(canvas); break;
                case ORIGAMI: drawOrigami(canvas); break;
                case SONAR: drawSonar(canvas); break;
                case BLACKHOLE: drawBlackHole(canvas); break;
                case HEAVY_CHAIN: drawHeavyChain(canvas); break;
                case MATRIX: drawMatrix(canvas); break;
                case ARROW_CLUSTER: drawArrowCluster(canvas); break;


                case CYBERPUNK: drawCyberpunk(canvas); break;
                case LIQUID_GLASS: drawLiquidGlass(canvas); break;
                case MECHANICAL_RGB: drawMechanicalRGB(canvas); break;
                case MAGMA_EMBER: drawMagmaEmber(canvas); break;
                case INK_PARCHMENT: drawInkParchment(canvas); break;
                case COSMIC_NEBULA: drawCosmicNebula(canvas); break;
                case SAKURA_GARDEN: drawSakuraGarden(canvas); break;
                case RETRO_8BIT: drawRetro8Bit(canvas); break;
                case GOLDEN_ERA: drawGoldenEra(canvas); break;
                case DEEP_OCEAN: drawDeepOcean(canvas); break;
                case NEON_RAIN: drawNeonRain(canvas); break;
                case CANDY_CRUSH: drawCandyCrush(canvas); break;
                case STEAMPUNK: drawSteampunk(canvas); break;
                case HOLOGRAPHIC: drawHolographic(canvas); break;
                case SPIRIT_REALM: drawSpiritRealm(canvas); break;
                case GOLDEN_LUXURY: drawGoldenLuxury(canvas); break;
                case SAKURA_BREEZE: drawSakuraBreeze(canvas); break;
                case BIOLUMINESCENCE: drawBioluminescence(canvas); break;
                case RETRO_ARCADE: drawRetroArcade(canvas); break;
                case CRYSTAL_PRISM: drawCrystalPrism(canvas); break;
                case VAPORWAVE: drawVaporwave(canvas); break;
                case NOIR_RAIN: drawNoirRain(canvas); break;
                case PAPER_CUTOUT: drawPaperCutout(canvas); break;
                case STAR_FIELD: drawStarField(canvas); break;
                case GEARS: drawGears(canvas); break;

                default: drawFluid(canvas);
            }
        }




        private void drawFluid(Canvas canvas) {
            Path path = new Path();
            path.moveTo(anchorX, anchorY - drag * 0.2f);
            path.quadTo(anchorX, anchorY, tipX, tipY - 20);
            path.arcTo(new RectF(tipX - 20, tipY - 20, tipX + 20, tipY + 20), -90, 180);
            path.quadTo(anchorX, anchorY, anchorX, anchorY + drag * 0.2f);
            path.close();
            canvas.drawPath(path, paint);
        }


        private void drawElastic(Canvas canvas) { paint.setShader(null); paint.setColor(triggered ? 0xFFFF0055 : 0xFFFFFFFF); paint.setStrokeWidth(Math.max(2, 20 - drag / 10)); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); canvas.drawCircle(tipX, tipY, 15, paint); }
        private void drawRibbon(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10); Path p = new Path(); p.moveTo(anchorX, anchorY); p.cubicTo(anchorX + (tipX - anchorX) / 2, anchorY, anchorX + (tipX - anchorX) / 2, tipY, tipX, tipY); canvas.drawPath(p, paint); paint.setStyle(Paint.Style.FILL); }
        private void drawArrow(Canvas canvas) { paint.setShader(null); paint.setColor(0xFFFFFFFF); paint.setStrokeWidth(5); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); Path head = new Path(); head.moveTo(tipX, tipY); head.lineTo(tipX - 15, tipY - 10); head.lineTo(tipX - 15, tipY + 10); head.close(); double angle = Math.atan2(tipY - anchorY, tipX - anchorX); canvas.save(); canvas.rotate((float)Math.toDegrees(angle), tipX, tipY); canvas.drawPath(head, paint); canvas.restore(); }
        private void drawPlasma(Canvas canvas) { paint.setStrokeWidth(3); for (int i = 0; i < 5; i++) { float offX = (random.nextFloat() - 0.5f) * 20; float offY = (random.nextFloat() - 0.5f) * 20; canvas.drawLine(anchorX, anchorY, tipX + offX, tipY + offY, paint); } }
        private void drawVortex(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); float radius = drag / 2; for(int i=0; i<5; i++) { RectF oval = new RectF(tipX - radius + i*5, tipY - radius + i*5, tipX + radius - i*5, tipY + radius - i*5); canvas.drawArc(oval, tick * 10 + i * 45, 270, false, paint); } }
        private void drawMagnet(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5); Path p = new Path(); p.moveTo(anchorX, anchorY - 50); p.quadTo(tipX, tipY, anchorX, anchorY + 50); canvas.drawPath(p, paint); }
        private void drawChain(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); float dx = tipX - anchorX; float dy = tipY - anchorY; float len = (float) Math.hypot(dx, dy); int count = (int) (len / 20); for(int i=0; i<count; i++) { float t = i / (float)count; canvas.drawCircle(anchorX + dx*t, anchorY + dy*t, 5, paint); } }
        private void drawBeam(Canvas canvas) { paint.setShader(null); paint.setColor(0xAAFFFFFF); paint.setStrokeWidth(drag / 5); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); paint.setColor(0x44FFFFFF); paint.setStrokeWidth(drag / 2); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); }
        private void drawPixel(Canvas canvas) { paint.setShader(null); paint.setColor(0xFF00FF00); float dx = tipX - anchorX; float dy = tipY - anchorY; float steps = 10; for(int i=0; i<steps; i++) { float t = i/steps; canvas.drawRect(anchorX + dx*t - 5, anchorY + dy*t - 5, anchorX + dx*t + 5, anchorY + dy*t + 5, paint); } }
        private void drawJelly(Canvas canvas) { float wobble = (float) Math.sin(tick * 0.5) * 5; canvas.drawOval(new RectF(tipX - 20 + wobble, tipY - 20 - wobble, tipX + 20 - wobble, tipY + 20 + wobble), paint); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); }
        private void drawGhost(Canvas canvas) { paint.setAlpha(100); canvas.drawCircle(tipX, tipY, 30, paint); paint.setAlpha(50); canvas.drawCircle(tipX - (tipX-anchorX)*0.2f, tipY - (tipY-anchorY)*0.2f, 25, paint); paint.setAlpha(255); }
        private void drawLava(Canvas canvas) { canvas.drawCircle(anchorX, anchorY, 40, paint); canvas.drawCircle(tipX, tipY, 30, paint); canvas.drawCircle((anchorX+tipX)/2, (anchorY+tipY)/2, 20 + (float)Math.sin(tick*0.1)*5, paint); }
        private void drawTaffy(Canvas canvas) { Path p = new Path(); p.moveTo(anchorX, anchorY - 30); p.quadTo((anchorX+tipX)/2, (anchorY+tipY)/2, tipX, tipY - 10); p.lineTo(tipX, tipY + 10); p.quadTo((anchorX+tipX)/2, (anchorY+tipY)/2, anchorX, anchorY + 30); canvas.drawPath(p, paint); }
        private void drawSnake(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(8); Path p = new Path(); p.moveTo(anchorX, anchorY); float dx = tipX - anchorX; float dy = tipY - anchorY; int waves = 3; for(int i=0; i<=10; i++) { float t = i/10f; float perp = (float)Math.sin(t * Math.PI * waves + tick*0.2) * 10; p.lineTo(anchorX + dx*t + perp, anchorY + dy*t + perp); } canvas.drawPath(p, paint); paint.setStyle(Paint.Style.FILL); }
        private void drawWorm(Canvas canvas) { drawChain(canvas); }
        private void drawGum(Canvas canvas) { paint.setColor(0xFFFF69B4); drawTaffy(canvas); }
        private void drawRoots(Canvas canvas) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); canvas.drawLine(anchorX, anchorY, tipX, tipY, paint); canvas.drawLine(anchorX, anchorY, tipX + 20, tipY + 20, paint); canvas.drawLine(anchorX, anchorY, tipX - 20, tipY + 20, paint); }
        private void drawLightning(Canvas canvas) { paint.setColor(0xFFFFFF00); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); Path p = new Path(); p.moveTo(anchorX, anchorY); float currX = anchorX, currY = anchorY; while(Math.hypot(tipX-currX, tipY-currY) > 10) { currX += (tipX-currX)*0.2 + (random.nextFloat()-0.5)*20; currY += (tipY-currY)*0.2 + (random.nextFloat()-0.5)*20; p.lineTo(currX, currY); } p.lineTo(tipX, tipY); canvas.drawPath(p, paint); }
        private void drawBlob(Canvas canvas) { drawLava(canvas); }

        private void drawVenom(Canvas canvas) { paint.setShader(null); paint.setColor(0xFF000000); canvas.drawCircle(tipX, tipY, 25 + (drag*0.1f), paint); paint.setStyle(Paint.Style.STROKE); for(int i=0; i<8; i++) { float yOffset = (i - 4) * 15; Path p = new Path(); p.moveTo(anchorX, anchorY + yOffset*2); float cp1x = anchorX + (tipX-anchorX)*0.3f + (float)Math.sin(tick*0.2 + i)*10; float cp1y = anchorY + yOffset + (float)Math.cos(tick*0.3)*10; p.quadTo(cp1x, cp1y, tipX, tipY); paint.setStrokeWidth(Math.max(0, 15 - (drag/10)) * ((i%2)+0.5f)); canvas.drawPath(p, paint); } paint.setStyle(Paint.Style.FILL); }
        private void drawTeslaCoil(Canvas canvas) { paint.setShader(null); paint.setColor(triggered ? 0xFFFFFFFF : 0xFF00EAFF); paint.setStyle(Paint.Style.STROKE); for(int b=0; b<3; b++) { paint.setStrokeWidth((3-b)+1); Path p = new Path(); p.moveTo(anchorX, anchorY); float currX = anchorX, currY = anchorY; for(int i=0; i<8; i++) { float progress = (i+1)/8f; float nextX = anchorX + (tipX-anchorX)*progress; float nextY = anchorY + (tipY-anchorY)*progress; float jitter = (drag * 0.2f) * (1-progress); currX = nextX + (float)(random.nextDouble()*jitter*2 - jitter); currY = nextY + (float)(random.nextDouble()*jitter*2 - jitter); p.lineTo(currX, currY); } p.lineTo(tipX, tipY); canvas.drawPath(p, paint); } paint.setStyle(Paint.Style.FILL); canvas.drawCircle(tipX, tipY, 10, paint); }
        private void drawZipper(Canvas canvas) { paint.setShader(null); paint.setColor(0xFF111111); Path track = new Path(); track.moveTo(anchorX, anchorY - 120); track.lineTo(tipX, tipY); track.lineTo(anchorX, anchorY + 120); canvas.drawPath(track, paint); paint.setColor(triggered ? 0xFFFFEB3B : 0xFFCCCCCC); canvas.drawRect(tipX-10, tipY-15, tipX+10, tipY+15, paint); }
        private void drawPortal(Canvas canvas) { float radius = Math.min(60, drag); canvas.save(); canvas.translate(tipX, tipY); canvas.rotate(tick * 5); paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setColor(triggered ? 0xFFFF8800 : 0xFFFFAA00); for(int i=0; i<30; i++) { double angle = (i/30.0) * Math.PI*2; float rOff = (float)(random.nextFloat()*10 - 5); float x1 = (float)Math.cos(angle) * (radius+rOff); float y1 = (float)Math.sin(angle) * (radius+rOff); float x2 = (float)Math.cos(angle+0.2) * (radius+rOff+10); float y2 = (float)Math.sin(angle+0.2) * (radius+rOff+10); paint.setStrokeWidth(1 + random.nextFloat()*2); canvas.drawLine(x1, y1, x2, y2, paint); } paint.setStyle(Paint.Style.FILL); canvas.restore(); }
        private void drawOrigami(Canvas canvas) { paint.setShader(null); paint.setColor(0xFFCCCCCC); Path p = new Path(); p.moveTo(anchorX, anchorY - 100); p.lineTo(tipX, tipY); p.lineTo(anchorX, anchorY + 100); canvas.drawPath(p, paint); paint.setShader(new LinearGradient(anchorX, anchorY, tipX, tipY, 0xFFEEEEEE, 0xFFFFFFFF, Shader.TileMode.CLAMP)); p.reset(); p.moveTo(tipX, tipY - 50); p.lineTo(tipX + drag*0.2f, tipY); p.lineTo(tipX, tipY + 50); canvas.drawPath(p, paint); }
        private void drawSonar(Canvas canvas) { paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setColor(triggered ? 0xFF00FF00 : 0xFFFFFFFF); paint.setStrokeWidth(3); for(int i=0; i<3; i++) { float r = (drag*0.5f) + (i*20); int alpha = (int)((1 - i*0.3f) * 255); paint.setAlpha(alpha); RectF oval = new RectF(tipX-r, tipY-r, tipX+r, tipY+r); canvas.drawArc(oval, 135, 90, false, paint); } paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(tipX, tipY, 6, paint); }
        private void drawBlackHole(Canvas canvas) { float rad = drag * 0.6f; paint.setShader(new RadialGradient(tipX, tipY, rad*1.5f, new int[]{triggered?0xFFFF0000:0xFF4400FF, 0x00000000}, null, Shader.TileMode.CLAMP)); canvas.drawCircle(tipX, tipY, rad*2, paint); paint.setShader(null); paint.setColor(0xFF000000); canvas.drawCircle(tipX, tipY, rad, paint); paint.setStyle(Paint.Style.STROKE); paint.setColor(0x4CFFFFFF); paint.setStrokeWidth(1); for(int i=-2; i<=2; i++) { canvas.drawLine(anchorX, anchorY+i*20, tipX, tipY, paint); } }
        private void drawHeavyChain(Canvas canvas) { paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setColor(0xFFAAAAAA); paint.setStrokeWidth(3); float dx = tipX - anchorX; float dy = tipY - anchorY; float len = (float)Math.hypot(dx, dy); int links = (int)(len / 15); for(int i=0; i<links; i++) { float t = i/(float)links; canvas.drawCircle(anchorX + dx*t, anchorY + dy*t, 6, paint); } paint.setColor(triggered ? 0xFFFFFFFF : 0xFFAAAAAA); paint.setStrokeWidth(4); canvas.drawCircle(tipX, tipY, 10, paint); }
        private void drawMatrix(Canvas canvas) { paint.setShader(null); paint.setColor(triggered ? 0xFFFFFFFF : 0xFF00FF00); paint.setTextSize(30); paint.setTypeface(Typeface.MONOSPACE); for(int i=0; i<6; i++) { float t = i/6f; float x = anchorX + (tipX-anchorX)*t; float y = anchorY + (tipY-anchorY)*t; if(Math.random()>0.5) canvas.drawText(Math.random()>0.5?"1":"0", x, y, paint); } paint.setColor(0xFFCCFFCC); canvas.drawRect(tipX, tipY-10, tipX+8, tipY+10, paint); }
        private void drawArrowCluster(Canvas canvas) { paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); for(int i=0; i<5; i++) { float lag = i * 15; float myDrag = Math.max(0, drag - lag); if(myDrag > 0) { float scale = 1 - (i*0.15f); int alpha = (int)((1 - i*0.15f) * 255); paint.setColor(triggered ? 0xFFFF0055 : 0xFFFFFFFF); paint.setAlpha(alpha); canvas.save(); float t = myDrag/drag; float x = anchorX + (tipX-anchorX)*t; float y = anchorY + (tipY-anchorY)*t; canvas.translate(x, y); canvas.scale(scale, scale); Path p = new Path(); p.moveTo(0, -10); p.lineTo(10, 0); p.lineTo(0, 10); canvas.drawPath(p, paint); canvas.restore(); } } }



        private void drawCyberpunk(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF00FF88);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setShadowLayer(10, 0, 0, 0xFF00FF88);


            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);


            paint.setStyle(Paint.Style.FILL);
            float jitterX = (random.nextFloat() - 0.5f) * 10;
            float jitterY = (random.nextFloat() - 0.5f) * 10;
            canvas.drawRect(tipX - 15 + jitterX, tipY - 15 + jitterY, tipX + 15 + jitterX, tipY + 15 + jitterY, paint);

            paint.setShadowLayer(0,0,0,0);
        }

        private void drawLiquidGlass(Canvas canvas) {
            spawnParticles(1, 0xFFFFFFFF, 10, 2);

            paint.setShader(null);
            paint.setColor(0x88FFFFFF);
            drawFluid(canvas);
        }

        private void drawMechanicalRGB(Canvas canvas) {
            paint.setShader(null);

            int color = Color.HSVToColor(new float[]{(tick * 5) % 360, 1f, 1f});
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8);
            paint.setShadowLayer(15, 0, 0, color);

            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(tipX, tipY, 15, paint);
            paint.setShadowLayer(0,0,0,0);
        }

        private void drawMagmaEmber(Canvas canvas) {
            spawnParticles(2, 0xFFFF5500, 5, 4);
            paint.setShader(null);
            paint.setColor(0xFFFF5500);
            paint.setShadowLayer(10, 0, 0, 0xFFFF0000);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
            canvas.drawCircle(tipX, tipY, 20, paint);
            paint.setShadowLayer(0,0,0,0);
        }

        private void drawInkParchment(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF111111);
            paint.setStrokeWidth(12);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
            paint.setStyle(Paint.Style.FILL);
            spawnParticles(1, 0xFF000000, 8, 1);
            canvas.drawCircle(tipX, tipY, 15 + (float)Math.sin(tick)*2, paint);
        }

        private void drawCosmicNebula(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFFA000FF);
            paint.setShadowLayer(20, 0, 0, 0xFFFFFFFF);
            canvas.drawCircle(tipX, tipY, 20, paint);


            if (tick % 3 == 0) {
                spawnParticles(1, 0xFFFFFFFF, 4, 0);
            }
        }

        private void drawSakuraGarden(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFFFFB7C5);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);

            if (tick % 5 == 0) {

                Particle p = new Particle();
                p.x = tipX; p.y = tipY; p.vx = (random.nextFloat()-0.5f)*2; p.vy = 2; p.life = 1f; p.size = 10; p.color = 0xFFFFB7C5;
                particles.add(p);
            }
        }

        private void drawRetro8Bit(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF00FF00);
            paint.setStrokeWidth(10);

            float dx = tipX - anchorX;
            float dy = tipY - anchorY;
            float steps = 10;
            for(int i=0; i<steps; i++) {
                float x = anchorX + (dx * (i/steps));
                float y = anchorY + (dy * (i/steps));
                canvas.drawRect(x, y, x+10, y+10, paint);
            }
            canvas.drawRect(tipX-15, tipY-15, tipX+15, tipY+15, paint);
        }

        private void drawGoldenEra(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFFFFD700);
            spawnParticles(1, 0xFFFFD700, 4, 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(tipX, tipY, 15, paint);
        }

        private void drawDeepOcean(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF00FFFF);
            spawnParticles(1, 0x8800FFFF, 8, 1);
            canvas.drawCircle(tipX, tipY, 20, paint);
        }

        private void drawNeonRain(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF00AAFF);
            paint.setShadowLayer(10, 0, 0, 0xFF00AAFF);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);


            for(int i=0; i<3; i++) {
                float rx = tipX + (random.nextFloat()-0.5f)*40;
                float ry = tipY + (random.nextFloat()-0.5f)*40;
                canvas.drawLine(rx, ry, rx, ry+20, paint);
            }
        }

        private void drawCandyCrush(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(Color.HSVToColor(new float[]{random.nextFloat()*360, 1f, 1f}));
            canvas.drawCircle(tipX, tipY, 25, paint);
            spawnParticles(2, Color.HSVToColor(new float[]{random.nextFloat()*360, 1f, 1f}), 8, 5);
        }

        private void drawSteampunk(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFF8B4513);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);


            paint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.translate(tipX, tipY);
            canvas.rotate(tick * 5);
            for(int i=0; i<8; i++) {
                canvas.drawRect(-5, -30, 5, 30, paint);
                canvas.rotate(45);
            }
            canvas.drawCircle(0, 0, 20, paint);
            canvas.restore();
        }

        private void drawHolographic(Canvas canvas) {
            paint.setShader(new LinearGradient(anchorX, anchorY, tipX, tipY,
                new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF}, null, Shader.TileMode.CLAMP));
            paint.setStrokeWidth(8);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
            paint.setShader(null);
        }

        private void drawSpiritRealm(Canvas canvas) {
            paint.setShader(null);
            paint.setColor(0xFFAAFFFF);
            paint.setAlpha(100);
            canvas.drawCircle(tipX, tipY, 30 + (float)Math.sin(tick*0.2)*10, paint);
            canvas.drawCircle(tipX, tipY, 15, paint);
        }

        private void drawGoldenLuxury(Canvas canvas) {
            paint.setColor(0xFFD4AF37);
            canvas.save();
            canvas.translate(tipX, tipY);
            canvas.rotate(tick * 2);
            canvas.drawRect(-15, -15, 15, 15, paint);
            canvas.restore();
            paint.setStrokeWidth(2);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
        }

        private void drawSakuraBreeze(Canvas canvas) {
            drawSakuraGarden(canvas);
        }

        private void drawBioluminescence(Canvas canvas) {
            paint.setColor(0xFF00FFCC);
            paint.setShadowLayer(20, 0, 0, 0xFF00FFCC);
            canvas.drawCircle(tipX, tipY, 20, paint);
            spawnParticles(1, 0xFF00FFCC, 4, -2);
        }

        private void drawRetroArcade(Canvas canvas) {
            paint.setColor(0xFFFF0055);
            canvas.drawRect(tipX-20, tipY-20, tipX+20, tipY+20, paint);
            if (tick%5==0) spawnParticles(1, 0xFFFF0055, 10, 0);
        }

        private void drawCrystalPrism(Canvas canvas) {
            paint.setColor(0xAAFFFFFF);
            Path p = new Path();
            p.moveTo(tipX, tipY-20);
            p.lineTo(tipX+20, tipY+10);
            p.lineTo(tipX-20, tipY+10);
            p.close();
            canvas.drawPath(p, paint);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);
        }

        private void drawVaporwave(Canvas canvas) {
            paint.setColor(0xFF00FFFF);
            paint.setStrokeWidth(4);

            for(int i=0; i<5; i++) {
                canvas.drawLine(tipX-20, tipY-20 + i*10, tipX+20, tipY-20 + i*10, paint);
            }
        }

        private void drawNoirRain(Canvas canvas) {
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(tipX, tipY, 5, paint);
            paint.setStrokeWidth(1);
            canvas.drawLine(anchorX, anchorY, tipX, tipY, paint);

            spawnParticles(2, 0xFFCCCCCC, 2, 5);
        }

        private void drawPaperCutout(Canvas canvas) {
            paint.setColor(0xFFFFFFFF);
            paint.setShadowLayer(5, 5, 5, 0x88000000);
            canvas.drawCircle(tipX, tipY, 25, paint);
            paint.setShadowLayer(0,0,0,0);
        }

        private void drawStarField(Canvas canvas) {
            drawCosmicNebula(canvas);
        }

        private void drawGears(Canvas canvas) {
            drawSteampunk(canvas);
        }
    }
}
