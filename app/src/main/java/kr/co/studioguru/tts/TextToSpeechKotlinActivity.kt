package kr.co.studioguru.tts

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import kr.co.studioguru.tts.databinding.ActivityTextToSpeechTestBinding
import org.json.JSONObject

class TextToSpeechKotlinActivity: AppCompatActivity() {

    private var vb: ActivityTextToSpeechTestBinding? = null
    private val textToSpeechKotlin: TextToSpeechKotlin by lazy {
        TextToSpeechKotlin(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityTextToSpeechTestBinding.inflate(layoutInflater)
        textToSpeechKotlin.speakStatusListener(callback = callback)
        vb?.run {
            setContentView(this.root)
            // setWebView
            vb!!.webview.loadUrl("file:///android_asset/webview_content.html")
            vb!!.webview.addJavascriptInterface(
                TreasureComicsJavascript(tts = textToSpeechKotlin),
                "treasureComics"
            )
            val webSettings = vb!!.webview.settings
            // ---------- 필수 ---------- //
            webSettings.domStorageEnabled = true // DOM 스토리지 활성화
            webSettings.javaScriptEnabled = true // JavaScript 사용 가능
            webSettings.javaScriptCanOpenWindowsAutomatically = true // JavaScript에서 새 창 열기 허용
            webSettings.setSupportMultipleWindows(true) // 다중 창 지원
            webSettings.mediaPlaybackRequiresUserGesture = false // 사용자 제스처 없이 미디어 재생 허용

            // ---------- 옵션 ---------- //
            webSettings.databaseEnabled = true // 데이터베이스 사용 가능
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT // 기본 캐시 모드 설정
            webSettings.textZoom = 100 // 텍스트 확대/축소 비율 설정
            webSettings.setSupportZoom(false) // 확대/축소 지원 비활성화
            webSettings.displayZoomControls = false // 확대/축소 컨트롤 비활성화
            webSettings.defaultTextEncodingName = "utf-8" // 기본 텍스트 인코딩 설정
            webSettings.loadWithOverviewMode =
                true // 콘텐츠를 웹뷰에 맞게 축소하여 전체 내용을 한눈에 볼 수 있도록 설정 // 개요 모드로 로드 설정
            webSettings.mixedContentMode =
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 혼합 컨텐츠 허용 (HTTPS 페이지에서 HTTP 컨텐츠 로드 가능)


            // WebSettings.MIXED_CONTENT_NEVER_ALLOW: 보안상의 이유로 HTTPS 페이지에서 HTTP 컨텐츠 로드를 차단
            // WebSettings.MIXED_CONTENT_ALWAYS_ALLOW: 모든 HTTP 및 HTTPS 컨텐츠 로드를 허용
            // WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE: 기본적으로 HTTPS를 유지하지만 일부 HTTP 컨텐츠 로드를 허용 // 혼합 컨텐츠 허용
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                // 콘텐츠를 단일 열로 정렬하여 화면 너비에 맞게 표시
                webSettings.layoutAlgorithm =
                    WebSettings.LayoutAlgorithm.SINGLE_COLUMN // 레이아웃 알고리즘 설정 (오래된 버전 지원)
            }
        }
    }

    val callback: (String, String, TextToSpeechKotlin.SpeakStatus) -> Unit = { utteranceId, callbackName, status ->
        Log.e(
            "TextToSpeechHelperTest",
            "{utteranceId: $utteranceId, callbackName: $callbackName, speakStatus: ${status.value} }"
        )
        try {
            val param = JSONObject()
            param.put("speakId", utteranceId)
            param.put("speakStatus", status.value)
            postResponse(callbackName, param)
        } catch (e: Exception) {
            Log.e("TextToSpeechHelperTest", e.message ?: "")
        }
    }

    fun postResponse(callbackName: String, params: JSONObject?) {
        runOnUiThread {
            val paramString =
                if (params != null) "'" + params.toString().replace("\"", "\\\"") + "'" else ""
            val script = "(function(){$callbackName($paramString);})();"
            vb!!.webview.evaluateJavascript(script, null)
        }
    }

    class TreasureComicsJavascript internal constructor(private val tts: TextToSpeechKotlin?) {
        @JavascriptInterface
        fun postMessage(message: String?) {
            try {
                val param = message?.let { JSONObject(it) }
                val request = param?.getString("request")
                val callbackName = param?.getString("callback") ?: ""
                if (request == "postSpeak") {
                    val action = param.getString("action")
                    when (action) {
                        "start" -> {
                            val innerParameter = param.getJSONObject("parameter")
                            val speakId = innerParameter.getString("speakId")
                            val speakText = innerParameter.getString("speakText")
                            val speechRate = innerParameter.getDouble("speechRate").toFloat()
                            val pitch = innerParameter.getDouble("pitch").toFloat()
                            // speak
                            val entity = TextToSpeechKotlin.SpeakEntity(
                                speakId,
                                speakText,
                                speechRate,
                                pitch
                            )
                            tts?.speak(speakEntity = entity, callbackName = callbackName)
                            return
                        }

                        "pause" -> {
                            tts?.speakPause(callbackName = callbackName)
                            return
                        }

                        "stop" -> {
                            tts?.speakStop(callbackName = callbackName)
                            return
                        }

                        "resume" -> {
                            tts?.speakResume(callbackName = callbackName)
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TextToSpeechHelperTest", e.message.toString())
            }
        }
    }
}