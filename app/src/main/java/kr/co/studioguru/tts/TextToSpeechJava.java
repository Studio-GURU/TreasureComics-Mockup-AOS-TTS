package kr.co.studioguru.tts;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class TextToSpeechJava {
    private final Context context;

    public TextToSpeechJava(Context context) {
        this.context = context;
    }

    public interface ITextToSpeechListener {
        public void onCallback(String utteranceId, String callbackName, SpeakStatus speakStatus);
    }

    public static class SpeakEntity {
        public String speakId;
        public String speakText;
        public float speechRate = 1.0f;
        public float pitch = 1.0f;

        public SpeakEntity(String speakId, String speakText, float speechRate, float pitch) {
            this.speakId = speakId;
            this.speakText = speakText;
            this.speechRate = speechRate;
            this.pitch = pitch;
        }

        public SpeakEntity(String speakId, String speakText, float speechRate) {
            this.speakId = speakId;
            this.speakText = speakText;
            this.speechRate = speechRate;
        }

        public SpeakEntity(String speakId, String speakText) {
            this.speakId = speakId;
            this.speakText = speakText;
        }
    }

    public enum SpeakStatus {
        START(1),
        PAUSE(2),
        RESUME(3),
        STOP(4),
        DONE(5),
        PLAYING(-100),
        MUTED(-200),
        ERROR(-999);

        private final int value;

        SpeakStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static SpeakStatus from(int from) {
            for (SpeakStatus status : SpeakStatus.values()) {
                if (status.value == from) {
                    return status;
                }
            }
            return ERROR;
        }
    }

    private boolean isReady = false;
    private boolean isPaused = false;
    private boolean isResumed = false;
    private SpeakEntity currentSpeak = null;
    private TextToSpeech behavior = null;
    private ITextToSpeechListener listener = null;

    public void setTextToSpeechListener(ITextToSpeechListener listener) {
        this.listener = listener;
    }

    private void initialize(Runnable callback) {
        if (isReady && behavior != null) {
            callback.run();
        } else {
            behavior = new TextToSpeech(context, status -> {
                isReady = (status == TextToSpeech.SUCCESS);
                callback.run();
            });
        }
    }

    public void speak(SpeakEntity speakEntity, String callbackName) {
        initialize(() -> {
            if (behavior != null && behavior.isSpeaking() || isPaused) {
                if (listener != null) {
                    listener.onCallback(speakEntity.speakId, callbackName, SpeakStatus.PLAYING);
                }
            } else if (getCurrentVolume(context) == 0) {
                listener.onCallback(speakEntity.speakId, callbackName, SpeakStatus.MUTED);
            } else {
                if (!isReady) {
                    listener.onCallback(speakEntity.speakId, callbackName, SpeakStatus.ERROR);
                } else {
                    if (behavior != null) {
                        behavior.setPitch(speakEntity.pitch);
                        behavior.setSpeechRate(speakEntity.speechRate);
                        behavior.setLanguage(Locale.KOREAN);
                        behavior.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                if (isResumed) {
                                    isResumed = false;
                                    if (listener != null) {
                                        listener.onCallback(utteranceId, callbackName, SpeakStatus.RESUME);
                                    }
                                } else {
                                    if (listener != null) {
                                        listener.onCallback(utteranceId, callbackName, SpeakStatus.START);
                                    }
                                }
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                isPaused = false;
                                isResumed = false;
                                if (listener != null) {
                                    listener.onCallback(utteranceId, callbackName, SpeakStatus.DONE);
                                }
                                speakDestroy();
                            }

                            @Override
                            public void onError(String utteranceId) {
                                isPaused = false;
                                isResumed = false;
                                if (listener != null) {
                                    listener.onCallback(utteranceId, callbackName, SpeakStatus.ERROR);
                                }
                                speakDestroy();
                            }
                        });
                        this.currentSpeak = speakEntity;
                        behavior.speak(speakEntity.speakText, TextToSpeech.QUEUE_FLUSH, null, speakEntity.speakId);
                    }
                }
            }
        });
    }

    public void speakPause(String callbackName) {
        initialize(() -> {
            if (behavior != null && behavior.isSpeaking() && !isPaused) {
                isPaused = true;
                behavior.stop();
                if (currentSpeak != null && listener != null) {
                    listener.onCallback(currentSpeak.speakId, callbackName, SpeakStatus.PAUSE);
                }
            }
        });
    }

    public void speakResume(String callbackName) {
        initialize(() -> {
            if (isPaused) {
                isPaused = false;
                isResumed = true;
                if (currentSpeak != null) {
                    speak(currentSpeak, callbackName);
                }
            }
        });
    }

    public void speakStop(String callbackName) {
        if (currentSpeak != null) {
            if (listener != null) {
                listener.onCallback(currentSpeak.speakId, callbackName, SpeakStatus.STOP);
            }
            isPaused = false;
            currentSpeak = null;
            if (behavior != null) {
                behavior.stop();
            }
        }
    }

    private void speakDestroy() {
        try {
            isReady = false;
            isResumed = false;
            isPaused = false;
            if (behavior != null) {
                behavior.stop();
                behavior.shutdown();
                behavior = null;
            }
        } catch (Exception e) {
            Log.e("TextToSpeechManager", "Speak => Destroy => Failure($it)");
        }
    }

    private int getCurrentVolume(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
}
