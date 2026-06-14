package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.util.TypedValue;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;

public class VoiceInputView extends FrameLayout {

    private TextView statusText;
    private TextView transcriptionText;
    private ImageButton micButton;
    private ImageButton closeButton;
    private ImageButton undoButton;
    private ImageButton redoButton;
    private ImageButton settingsButton;
    private View pulseBg;
    private AudioVisualizationView waveformView;
    private View suggestionsScroll;
    private android.widget.LinearLayout suggestionsContainer;
    private Callback callback;
    private int themeColor = 0xFF00D9FF;

    public interface Callback {
        void onMicClick();
        void onCloseClick();
        void onUndoClick();
        void onRedoClick();
        void onSettingsClick();
        void onKeyClick(int keyCode);
        void onCommandButtonDown();
        void onSuggestionSelected(int wordIndex, String suggestion);
    }

    public VoiceInputView(Context context) {
        this(context, null);
    }

    public VoiceInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.voice_input_overlay, this, true);

        statusText = findViewById(R.id.voice_status_text);
        transcriptionText = findViewById(R.id.voice_transcription_text);
        transcriptionText.setTextColor(themeColor);
        micButton = findViewById(R.id.voice_mic_button);
        closeButton = findViewById(R.id.voice_close_button);
        undoButton = findViewById(R.id.voice_undo_button);
        redoButton = findViewById(R.id.voice_redo_button);
        settingsButton = findViewById(R.id.voice_settings_button);
        pulseBg = findViewById(R.id.mic_pulse_bg);
        waveformView = findViewById(R.id.voice_waveform_view);
        suggestionsScroll = findViewById(R.id.voice_suggestions_scroll);
        suggestionsContainer = findViewById(R.id.voice_suggestions_container);


        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
            themeColor = typedValue.data;
        } else if (context.getTheme().resolveAttribute(R.attr.colorLabel, typedValue, true)) {
            themeColor = typedValue.data;
        }

        int keyboardColor = 0xFF000000;
        if (context.getTheme().resolveAttribute(R.attr.colorKeyboard, typedValue, true)) {
            keyboardColor = typedValue.data;
        }


        statusText.setTextColor(themeColor);
        micButton.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);

        if (waveformView != null) {
            waveformView.setWaveColor(themeColor);
        }


        undoButton.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);
        redoButton.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);
        settingsButton.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);
        closeButton.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);


        this.setBackgroundColor(keyboardColor);


        String themeName = Config.globalConfig().themeName;
        int keyColor = 0xFFCCCCCC;
        if (context.getTheme().resolveAttribute(R.attr.colorKey, typedValue, true)) {
            keyColor = typedValue.data;
        }
        applyTheme(undoButton, themeName, keyColor);
        applyTheme(redoButton, themeName, keyColor);
        applyTheme(settingsButton, themeName, keyColor);
        applyTheme(closeButton, themeName, keyColor);

        setupKey(R.id.voice_key_backspace, android.view.KeyEvent.KEYCODE_DEL, themeName, keyColor);
        setupKey(R.id.voice_key_delete, android.view.KeyEvent.KEYCODE_FORWARD_DEL, themeName, keyColor);
        setupKey(R.id.voice_key_cursor_left, android.view.KeyEvent.KEYCODE_DPAD_LEFT, themeName, keyColor);
        setupKey(R.id.voice_key_cursor_right, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, themeName, keyColor);

        setupKey(R.id.voice_key_word_back, -101, themeName, keyColor);
        setupKey(R.id.voice_key_word_fwd, -102, themeName, keyColor);

        setupKey(R.id.voice_key_del_word, -201, themeName, keyColor);
        setupKey(R.id.voice_key_del_word_fwd, -202, themeName, keyColor);
        setupKey(R.id.voice_key_replace, -203, themeName, keyColor);
        setupKey(R.id.voice_key_suggest, -204, themeName, keyColor);

        setupKey(R.id.voice_key_sel_back, -301, themeName, keyColor);
        setupKey(R.id.voice_key_sel_curr, -302, themeName, keyColor);
        setupKey(R.id.voice_key_sel_fwd, -303, themeName, keyColor);

        micButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onMicClick();
            }
        });

        micButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (callback != null) callback.onCommandButtonDown();
                return true;
            }
        });

        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onCloseClick();
            }
        });

        undoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onUndoClick();
            }
        });

        redoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onRedoClick();
            }
        });

        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onSettingsClick();
            }
        });


        View spaceBtn = findViewById(R.id.voice_key_space);
        if (spaceBtn != null) {
            applyTheme(spaceBtn, themeName, keyColor);
            spaceBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) callback.onKeyClick(android.view.KeyEvent.KEYCODE_SPACE);
                }
            });
            spaceBtn.setOnTouchListener(new OnTouchListener() {
                private float startX;
                private boolean isSwiping;
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    switch(event.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            isSwiping = false;
                            return false;
                        case android.view.MotionEvent.ACTION_MOVE:
                            float diff = event.getRawX() - startX;
                            if (Math.abs(diff) > 20) {
                                isSwiping = true;
                                if (callback != null) {





                                    int count = (int)(diff / 30);
                                    if (count != 0) {
                                        callback.onKeyClick(count > 0 ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                                        startX = event.getRawX();
                                    }
                                }
                                return true;
                            }
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                            if (isSwiping) return true;
                            break;
                    }
                    return false;
                }
            });
        }
    }

    private void setupKey(int id, final int keyCode, String themeName, int keyColor) {
        View v = findViewById(id);
        if (v != null) {
            applyTheme(v, themeName, keyColor);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) callback.onKeyClick(keyCode);
                }
            });

            v.setOnTouchListener(new RepeatListener(400, 100, new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (callback != null) callback.onKeyClick(keyCode);
                }
            }));
        }
    }


    private static class RepeatListener implements OnTouchListener {
        private android.os.Handler handler = new android.os.Handler();
        private int initialInterval;
        private final int normalInterval;
        private final OnClickListener clickListener;
        private View touchedView;

        private Runnable handlerRunnable = new Runnable() {
            @Override
            public void run() {
                if (touchedView.isEnabled()) {
                    handler.postDelayed(this, normalInterval);
                    clickListener.onClick(touchedView);
                } else {
                    handler.removeCallbacks(handlerRunnable);
                    touchedView.setPressed(false);
                    touchedView = null;
                }
            }
        };

        public RepeatListener(int initialInterval, int normalInterval, OnClickListener clickListener) {
            if (clickListener == null)
                throw new IllegalArgumentException("null runnable");
            if (initialInterval < 0 || normalInterval < 0)
                throw new IllegalArgumentException("negative interval");

            this.initialInterval = initialInterval;
            this.normalInterval = normalInterval;
            this.clickListener = clickListener;
        }

        @Override
        public boolean onTouch(View view, android.view.MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    handler.removeCallbacks(handlerRunnable);
                    handler.postDelayed(handlerRunnable, initialInterval);
                    touchedView = view;
                    touchedView.setPressed(true);
                    clickListener.onClick(view);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(handlerRunnable);
                    if (touchedView != null) {
                        touchedView.setPressed(false);
                        touchedView = null;
                    }
                    return true;
            }
            return false;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setStatus(String status) {
        statusText.setText(status);
    }

    public void setTranscription(String text, boolean isPartial) {
        transcriptionText.setText(text);
        if (isPartial) {
            transcriptionText.setAlpha(0.7f);
            if (waveformView != null) waveformView.setFrozen(false);
        } else {
            transcriptionText.setAlpha(1.0f);
            if (waveformView != null && text != null && !text.isEmpty()) {
                waveformView.setFrozen(true);
            } else if (waveformView != null) {
                waveformView.explode();
            }
        }
    }

    public void showListeningState() {
        micButton.setColorFilter(themeColor);
        startPulseAnimation();
        if (waveformView != null) waveformView.setFrozen(false);
    }

    public void showCommandState() {
        micButton.setColorFilter(0xFF9B59B6);
        statusText.setText("Listening for commands...");
        startPulseAnimation();
    }

    public void showIdleState() {
        micButton.setColorFilter(themeColor);
        stopPulseAnimation();
    }

    public void showErrorState(String error) {
        statusText.setText(error);
        micButton.setColorFilter(0xFFFF0000);
        stopPulseAnimation();
    }

    public void updateAudioLevel(float rmsdB) {
        if (waveformView != null) {
            waveformView.addAmplitude(rmsdB);
        }
    }

    public void showMultiWordSuggestions(java.util.List<String> originalWords, java.util.List<java.util.List<String>> suggestions) {
        if (suggestionsScroll == null || suggestionsContainer == null) return;

        suggestionsContainer.removeAllViews();
        suggestionsScroll.setVisibility(VISIBLE);
        transcriptionText.setVisibility(GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 0; i < originalWords.size() && i < suggestions.size(); i++) {
            String word = originalWords.get(i);
            java.util.List<String> sugs = suggestions.get(i);
            if (sugs == null || sugs.isEmpty()) continue;


            TextView header = new TextView(getContext());
            header.setText("Word " + (i+1) + ": " + word);
            header.setTextColor(themeColor);
            header.setTextSize(14);
            header.setPadding(0, 16, 0, 8);
            suggestionsContainer.addView(header);


            android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(getContext());
            android.widget.LinearLayout row = new android.widget.LinearLayout(getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            hScroll.addView(row);

            final int index = i;
            for (String sug : sugs) {
                TextView chip = new TextView(getContext());
                chip.setText(sug);
                chip.setTextColor(0xFFFFFFFF);
                chip.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

                chip.setPadding(24, 12, 24, 12);
                chip.setOnClickListener(v -> {
                    if (callback != null) callback.onSuggestionSelected(index, sug);
                });
                row.addView(chip);


                View spacer = new View(getContext());
                spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(16, 1));
                row.addView(spacer);
            }
            suggestionsContainer.addView(hScroll);
        }
    }

    public void hideSuggestions() {
        if (suggestionsScroll != null) suggestionsScroll.setVisibility(GONE);
        if (transcriptionText != null) transcriptionText.setVisibility(VISIBLE);
    }

    private void startPulseAnimation() {
        ScaleAnimation pulse = new ScaleAnimation(1.0f, 1.5f, 1.0f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(1000);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        pulseBg.startAnimation(pulse);
    }

    private void stopPulseAnimation() {
        pulseBg.clearAnimation();
    }

    private void applyTheme(View view, String themeName, int color) {
        if (view == null || themeName == null) return;
        float radius = 15f;
        view.setBackground(new ProceduralThemeDrawable(themeName, color, radius));
    }
}
