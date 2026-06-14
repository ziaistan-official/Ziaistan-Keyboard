package juloo.keyboard2;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;


public class SoundManager {
    private static final int SAMPLE_RATE = 44100;
    private static final int DURATION_MS = 20;
    private static final int NUM_SAMPLES = SAMPLE_RATE * DURATION_MS / 1000;

    private AudioTrack audioTrack;
    private byte[] soundData;

    public SoundManager() {
        generateClickSound();
        initAudioTrack();
    }

    private void generateClickSound() {
        soundData = new byte[NUM_SAMPLES * 2];


        double frequency = 1000.0;

        for (int i = 0; i < NUM_SAMPLES; ++i) {
            double t = (double) i / SAMPLE_RATE;

            double envelope = Math.exp(-300.0 * t);
            double sampleValue = Math.sin(2.0 * Math.PI * frequency * t) * envelope;


            short val = (short) (sampleValue * 32767);
            soundData[2 * i] = (byte) (val & 0xff);
            soundData[2 * i + 1] = (byte) ((val >> 8) & 0xff);
        }
    }

    private void initAudioTrack() {
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        int bufferSize = Math.max(minBufferSize, soundData.length);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } else {
             audioTrack = new AudioTrack(
                    AudioManager.STREAM_SYSTEM,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC);
        }


        audioTrack.write(soundData, 0, soundData.length);
    }

    public void playClick(int volumePercentage) {
        if (audioTrack == null || volumePercentage <= 0) return;


        if (volumePercentage > 100) volumePercentage = 100;

        float vol = volumePercentage / 100.0f;

        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack.stop();
                audioTrack.reloadStaticData();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioTrack.setVolume(vol);
                } else {
                    audioTrack.setStereoVolume(vol, vol);
                }
                audioTrack.play();
            } catch (Exception e) {

            }
        }
    }

    public void release() {
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }
}
