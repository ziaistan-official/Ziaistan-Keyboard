package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.media.AudioManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceTypingManager {

    private static final String TAG = "VoiceTypingManager";
    private SpeechRecognizer speechRecognizer;
    private Intent recognitionIntent;
    private final Context context;
    private VoiceTypingListener listener;
    private boolean isListening = false;
    private boolean isContinuousMode = true;
    private boolean isCommandMode = false;
    private boolean isReplaceMode = false;
    private String currentLanguage = Locale.getDefault().toString();
    private boolean userStopped = false;

    public interface VoiceTypingListener {
        void onVoiceTypingStarted();
        void onVoiceTypingStopped();
        void onVoiceTypingError(String error);
        void onVoiceTypingResult(String text, boolean isPartial);
        void onVoiceCommand(String command);
        void onRmsChanged(float rmsdB);
        void onReplaceModeResult(String text);
    }

    public VoiceTypingManager(Context context, VoiceTypingListener listener) {
        this.context = context;
        this.listener = listener;
        initializeRecognizer();
    }

    private void muteAudio() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {

                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error muting audio", e);
        }
    }

    private void unmuteAudio() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unmuting audio", e);
        }
    }

    private void initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionCallback());
        } else {
            Log.e(TAG, "Speech recognition not available on this device.");
            if (listener != null) {
                listener.onVoiceTypingError("Speech recognition not available");
            }
        }
    }

    public void startListening(Locale locale) {
        if (locale != null) {
            this.currentLanguage = locale.toString();
        }
        startListeningInternal();
    }

    public void startListening() {
        startListeningInternal();
    }

    private void startListeningInternal() {
        if (speechRecognizer == null) {
            initializeRecognizer();
        }
        if (speechRecognizer != null && !isListening) {
            userStopped = false;
            recognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage);



            recognitionIntent.putExtra("android.speech.extra.ENABLE_FORMATTING", true);

            try {
                muteAudio();
                speechRecognizer.startListening(recognitionIntent);
                isListening = true;
                if (listener != null) listener.onVoiceTypingStarted();
            } catch (Exception e) {
                unmuteAudio();
                Log.e(TAG, "Error starting speech recognition", e);
                isListening = false;
                if (listener != null) listener.onVoiceTypingError("Error starting: " + e.getMessage());
            }
        }
    }

    public void stopListening() {
        userStopped = true;
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
        unmuteAudio();

        if (listener != null) listener.onVoiceTypingStopped();
    }

    public void setCommandMode(boolean enabled) {
        this.isCommandMode = enabled;
        this.isReplaceMode = false;
    }

    public void setReplaceMode(boolean enabled) {
        this.isReplaceMode = enabled;
        this.isCommandMode = false;
    }

    public void processCommand(String command) {

        if (listener != null) {
            listener.onVoiceCommand(command);
        }
    }

    public void cancel() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            isListening = false;
            unmuteAudio();
            if (listener != null) listener.onVoiceTypingStopped();
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        unmuteAudio();
    }

    public void setLanguage(String languageCode) {
        this.currentLanguage = languageCode;
    }

    public boolean isListening() {
        return isListening;
    }

    private class RecognitionCallback implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            if (listener != null) {
                listener.onRmsChanged(rmsdB);
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");

        }

        @Override
        public void onError(int error) {
            String errorMessage = getErrorText(error);
            Log.e(TAG, "onError: " + errorMessage);

            isListening = false;


            boolean isMinorError = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT);

            if (isContinuousMode && !userStopped && isMinorError) {

                 startListeningInternal();
            } else {
                 unmuteAudio();

                 isListening = false;
                 if (listener != null) {
                     listener.onVoiceTypingStopped();

                     if (!isMinorError) {
                        listener.onVoiceTypingError(errorMessage);
                     }
                 }
            }
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                if (isCommandMode) {
                     if (listener != null) {
                         processCommand(text);
                     }
                } else if (isReplaceMode) {
                     text = applyPunctuation(text);
                     if (listener != null) {
                         listener.onReplaceModeResult(text);
                     }

                     isReplaceMode = false;

                     userStopped = true;
                } else {
                     text = applyPunctuation(text);
                     if (listener != null) {
                         listener.onVoiceTypingResult(text, false);
                     }
                }
            }


            if (isContinuousMode && !userStopped && !isReplaceMode) {
                startListeningInternal();
            } else {
                if (listener != null) listener.onVoiceTypingStopped();
            }
        }

        private void processCommand(String text) {
             text = text.toLowerCase().trim();

             if (text.contains("delete word") || text.contains("delete last word")) {
                 listener.onVoiceCommand("delete_word");
             } else if (text.contains("delete sentence") || text.contains("delete last sentence")) {
                 listener.onVoiceCommand("delete_sentence");
             } else if (text.contains("delete all") || text.contains("clear all")) {
                 listener.onVoiceCommand("delete_all");
             } else if (text.contains("new line") || text.contains("enter")) {
                 listener.onVoiceCommand("new_line");
             } else if (text.contains("space")) {
                 listener.onVoiceCommand("space");
             } else if (text.contains("tab")) {
                 listener.onVoiceCommand("tab");
             } else if (text.contains("select all")) {
                 listener.onVoiceCommand("select_all");
             } else if (text.contains("copy")) {
                 listener.onVoiceCommand("copy");
             } else if (text.contains("paste")) {
                 listener.onVoiceCommand("paste");
             } else if (text.contains("cut")) {
                 listener.onVoiceCommand("cut");
             } else if (text.contains("undo")) {
                 listener.onVoiceCommand("undo");
             } else if (text.contains("start")) {
                 listener.onVoiceCommand("go_start");
             } else if (text.contains("end")) {
                 listener.onVoiceCommand("go_end");
             } else {





                 if (text.contains("delete")) listener.onVoiceCommand("delete_char");
             }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = applyPunctuation(matches.get(0));
                if (listener != null) {
                    listener.onVoiceTypingResult(text, true);
                }
            }
        }

        private String applyPunctuation(String text) {
            if (text == null) return "";
            String res = text;

            res = res.replace(" period", ".")
                     .replace(" comma", ",")
                     .replace(" question mark", "?")
                     .replace(" exclamation mark", "!")
                     .replace(" new line", "\n")
                     .replace(" new paragraph", "\n\n")
                     .replace(" colon", ":")
                     .replace(" semicolon", ";");

            res = res.replaceAll("(?i)\\bperiod\\b", ".")
                     .replaceAll("(?i)\\bcomma\\b", ",")
                     .replaceAll("(?i)\\bquestion mark\\b", "?")
                     .replaceAll("(?i)\\bexclamation mark\\b", "!")
                     .replaceAll("(?i)\\bnew line\\b", "\n")
                     .replaceAll("(?i)\\bnew paragraph\\b", "\n\n")
                     .replaceAll("(?i)\\bcolon\\b", ":")
                     .replaceAll("(?i)\\bsemicolon\\b", ";");


            if (res.toLowerCase().contains("all caps")) {
                res = res.toUpperCase();
                res = res.replace("ALL CAPS", "");
            }
            if (res.toLowerCase().contains("quote") && res.toLowerCase().contains("end quote")) {
                 res = res.replaceFirst("(?i)quote", "\"");
                 res = res.replaceFirst("(?i)end quote", "\"");
            }

            return res;
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    public static String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER: return "Error from server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown error";
        }
    }
}
