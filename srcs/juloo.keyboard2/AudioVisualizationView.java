package juloo.keyboard2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AudioVisualizationView extends View {

    private static final int WAVE_COUNT = 12;
    private static final int POINT_COUNT = 60;

    private final Paint paint = new Paint();
    private final Path path = new Path();
    private float phase = 0f;
    private float amplitude = 0f;
    private float targetAmplitude = 0f;
    private int waveColor = 0xFF00D9FF;


    private static final int PARTICLE_COUNT = 50;
    private final float[] particleX = new float[PARTICLE_COUNT];
    private final float[] particleY = new float[PARTICLE_COUNT];
    private final float[] particleSpeed = new float[PARTICLE_COUNT];
    private final float[] particleTargetX = new float[PARTICLE_COUNT];
    private final float[] particleTargetY = new float[PARTICLE_COUNT];
    private final Random random = new Random();
    private final Paint particlePaint = new Paint();


    private boolean isFrozen = false;
    private boolean isExploding = false;
    private float explodeFactor = 0f;

    public AudioVisualizationView(Context context) {
        this(context, null);
    }

    public AudioVisualizationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setAntiAlias(true);


        particlePaint.setColor(Color.WHITE);
        particlePaint.setAntiAlias(true);
        particlePaint.setAlpha(150);

        for (int i=0; i<PARTICLE_COUNT; i++) {
            particleX[i] = random.nextFloat();
            particleY[i] = random.nextFloat();
            particleSpeed[i] = 0.002f + random.nextFloat() * 0.005f;
            particleTargetX[i] = 0.5f;
            particleTargetY[i] = 0.5f;
        }
    }

    public void setWaveColor(int color) {
        this.waveColor = color;

        paint.setShadowLayer(15f, 0f, 0f, color);
        particlePaint.setShadowLayer(5f, 0f, 0f, Color.WHITE);
        invalidate();
    }

    public void setFrozen(boolean frozen) {
        this.isFrozen = frozen;
        if (!frozen) {

             isExploding = false;
             explodeFactor = 0f;
        }
    }

    public void explode() {
        this.isFrozen = false;
        this.isExploding = true;
        this.explodeFactor = 1.0f;
    }

    public void addAmplitude(float rmsdB) {

        float normalized = Math.max(0f, (rmsdB + 2f) / 10f);
        targetAmplitude = normalized;
    }

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {

            phase += 0.08f;
            amplitude += (targetAmplitude - amplitude) * 0.1f;


            targetAmplitude *= 0.95f;

            invalidate();
            postOnAnimation(this);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            postOnAnimation(animator);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(animator);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            removeCallbacks(animator);
            postOnAnimation(animator);
        } else {
            removeCallbacks(animator);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int midY = height / 2;















        int startColor = waveColor;
        int endColor = (waveColor & 0x00FFFFFF) | 0x88000000;

        paint.setColor(waveColor);
        paint.setShader(null);


        for (int i=0; i<PARTICLE_COUNT; i++) {
            if (isFrozen) {

                float tx = particleTargetX[i];
                float ty = particleTargetY[i];

                particleX[i] += (tx - particleX[i]) * 0.05f;
                particleY[i] += (ty - particleY[i]) * 0.05f;
            } else if (isExploding) {

                float dx = particleX[i] - 0.5f;
                float dy = particleY[i] - 0.5f;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist < 0.01f) { dx = (random.nextFloat()-0.5f); dy = (random.nextFloat()-0.5f); }

                particleX[i] += dx * 0.1f * explodeFactor;
                particleY[i] += dy * 0.1f * explodeFactor;


                if (particleX[i] > 1.0f) particleX[i] = 0f;
                if (particleX[i] < 0f) particleX[i] = 1.0f;
                if (particleY[i] > 1.0f) particleY[i] = 0f;
                if (particleY[i] < 0f) particleY[i] = 1.0f;

                explodeFactor *= 0.99f;
                if (explodeFactor < 0.1f) isExploding = false;
            } else {

                particleX[i] += particleSpeed[i];
                if (particleX[i] > 1.0f) particleX[i] = 0f;


                if (random.nextFloat() < 0.01f) particleY[i] = random.nextFloat();
            }

            float px = particleX[i] * width;
            float py = particleY[i] * height;


            float radius = 2f + (amplitude * 3f);
            if (isFrozen) radius = 3f;

            canvas.drawCircle(px, py, radius, particlePaint);
        }





        float baseAmp = height * 0.4f;
        float currentAmp = baseAmp * (0.2f + (amplitude * 0.8f));

        for (int w = 0; w < WAVE_COUNT; w++) {
            path.reset();


            float wavePhase = phase + (w * 0.5f);
            float freq = 2f + (w * 0.2f);
            float waveAlpha = 1.0f - ((float)w / WAVE_COUNT);
            paint.setAlpha((int)(255 * waveAlpha * 0.8f));

            for (int i = 0; i <= POINT_COUNT; i++) {
                float t = (float) i / POINT_COUNT;
                float x = t * width;







                float envelope = (float) Math.sin(t * Math.PI);

                float y = midY + envelope * currentAmp * (float)Math.sin(t * Math.PI * freq + wavePhase);

                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
        }
    }
}
