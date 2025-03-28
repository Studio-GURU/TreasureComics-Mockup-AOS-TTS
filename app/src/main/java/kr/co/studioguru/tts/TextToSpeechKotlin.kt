package kr.co.studioguru.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TextToSpeechKotlin(private val context: Context) {

    data class SpeakEntity(
        val speakId: String,
        val speakText: String,
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f
    )

    enum class SpeakStatus(val value: Int) {
        START(value = 1),
        PAUSE(value = 2),
        RESUME(value = 3),
        STOP(value = 4),
        DONE(value = 5),
        PLAYING(value = -100),
        MUTED(value = -200),
        ERROR(value = -999);

        companion object {
            fun from(from: Int): SpeakStatus {
                return when (from) {
                    1 -> START
                    2 -> PAUSE
                    3 -> RESUME
                    4 -> STOP
                    5 -> DONE
                    -100 -> PLAYING
                    -200 -> MUTED
                    else -> ERROR
                }
            }
        }
    }

    private var isReady = false
    private var isPaused = false
    private var isResumed = false
    private var currentSpeak: SpeakEntity? = null
    private var behavior: TextToSpeech? = null
    private var callback: ((String, String, SpeakStatus) -> Unit)? = null

    private fun initialize(callback: () -> Unit) {
        if (isReady && behavior != null) {
            callback.invoke()
        } else {
            behavior = TextToSpeech(context) { status ->
                isReady = status == TextToSpeech.SUCCESS
                callback.invoke()
            }
        }
    }

    fun speak(speakEntity: SpeakEntity, callbackName: String) {
        initialize {
            if (behavior?.isSpeaking == true || isPaused) {
                callback?.invoke(speakEntity.speakId, callbackName, SpeakStatus.PLAYING)
            } else if (getCurrentVolume(context = context) == 0) {
                callback?.invoke(speakEntity.speakId, callbackName, SpeakStatus.MUTED)
            } else {
                if (!isReady) {
                    callback?.invoke(speakEntity.speakId, callbackName, SpeakStatus.ERROR)
                } else {
                    behavior?.setPitch(speakEntity.pitch)
                    behavior?.setSpeechRate(speakEntity.speechRate)
                    behavior?.setLanguage(Locale.KOREAN)
                    behavior?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (isResumed) {
                                isResumed = false
                                callback?.invoke(utteranceId!!, callbackName, SpeakStatus.RESUME)
                            } else {
                                callback?.invoke(utteranceId!!, callbackName, SpeakStatus.START)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            isPaused = false
                            isResumed = false
                            callback?.invoke(utteranceId!!, callbackName, SpeakStatus.DONE)
                            speakDestroy()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isPaused = false
                            isResumed = false
                            callback?.invoke(utteranceId!!, callbackName, SpeakStatus.ERROR)
                            speakDestroy()
                        }
                    })
                    this.currentSpeak = speakEntity
                    behavior?.speak(speakEntity.speakText, TextToSpeech.QUEUE_FLUSH, null, speakEntity.speakId)
                }
            }
        }
    }

    fun speakPause(callbackName: String) {
        initialize {
            if (behavior?.isSpeaking == true && !isPaused) {
                isPaused = true
                behavior?.stop()
                currentSpeak?.run {
                    callback?.invoke(this.speakId, callbackName, SpeakStatus.PAUSE)
                }
            }
        }
    }

    fun speakResume(callbackName: String) {
        initialize {
            if (isPaused) {
                isPaused = false
                isResumed = true
                currentSpeak?.run {
                    speak(speakEntity = this, callbackName = callbackName)
                }
            }
        }
    }

    fun speakStop(callbackName: String) {
        currentSpeak?.run {
            callback?.invoke(this.speakId, callbackName, SpeakStatus.STOP)
            isPaused = false
            currentSpeak = null
            behavior?.stop()
        }
    }

    fun speakStatusListener(callback: (utteranceId: String, callbackName: String, status: SpeakStatus) -> Unit) {
        this.callback = callback
    }

    fun speakDestroy() {
        kotlin.runCatching {
            isReady = false
            isResumed = false
            isPaused = false
            behavior?.stop()
            behavior?.shutdown()
            behavior = null
        }.onSuccess {
            Log.d("TextToSpeechManager", "Speak => Destroy => Success")
        }.onFailure {
            Log.e("TextToSpeechManager", "Speak => Destroy => Failure($it)")
        }
    }

    private fun getCurrentVolume(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
}