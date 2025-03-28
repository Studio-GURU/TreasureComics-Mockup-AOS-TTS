package kr.co.studioguru.tts;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Objects;

import kr.co.studioguru.tts.databinding.ActivityTextToSpeechTestBinding;

public class TextToSpeechJavaActivity extends AppCompatActivity implements TextToSpeechJava.ITextToSpeechListener {

    private ActivityTextToSpeechTestBinding vb;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextToSpeechJava textToSpeechJava = new TextToSpeechJava(this);
        textToSpeechJava.setTextToSpeechListener(this);
        vb = ActivityTextToSpeechTestBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());
        // setWebView
        vb.webview.loadUrl("file:///android_asset/webview_content.html");
        vb.webview.addJavascriptInterface(new TreasureComicsJavascript(textToSpeechJava), "treasureComics");
        WebSettings webSettings = vb.webview.getSettings();
        // ---------- 필수 ---------- //
        webSettings.setDomStorageEnabled(true); // DOM 스토리지 활성화
        webSettings.setJavaScriptEnabled(true); // JavaScript 사용 가능
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true); // JavaScript에서 새 창 열기 허용
        webSettings.setSupportMultipleWindows(true); // 다중 창 지원
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 사용자 제스처 없이 미디어 재생 허용
        // ---------- 옵션 ---------- //
        webSettings.setDatabaseEnabled(true); // 데이터베이스 사용 가능
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT); // 기본 캐시 모드 설정
        webSettings.setTextZoom(100); // 텍스트 확대/축소 비율 설정
        webSettings.setSupportZoom(false); // 확대/축소 지원 비활성화
        webSettings.setDisplayZoomControls(false); // 확대/축소 컨트롤 비활성화
        webSettings.setDefaultTextEncodingName("utf-8"); // 기본 텍스트 인코딩 설정
        webSettings.setLoadWithOverviewMode(true); // 콘텐츠를 웹뷰에 맞게 축소하여 전체 내용을 한눈에 볼 수 있도록 설정 // 개요 모드로 로드 설정
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // 혼합 컨텐츠 허용 (HTTPS 페이지에서 HTTP 컨텐츠 로드 가능)
        // WebSettings.MIXED_CONTENT_NEVER_ALLOW: 보안상의 이유로 HTTPS 페이지에서 HTTP 컨텐츠 로드를 차단
        // WebSettings.MIXED_CONTENT_ALWAYS_ALLOW: 모든 HTTP 및 HTTPS 컨텐츠 로드를 허용
        // WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE: 기본적으로 HTTPS를 유지하지만 일부 HTTP 컨텐츠 로드를 허용 // 혼합 컨텐츠 허용
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            // 콘텐츠를 단일 열로 정렬하여 화면 너비에 맞게 표시
            webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN); // 레이아웃 알고리즘 설정 (오래된 버전 지원)
        }
    }

    @Override
    public void onCallback(String utteranceId, String callbackName, TextToSpeechJava.SpeakStatus speakStatus) {
        Log.e("TextToSpeechHelperTest", "{utteranceId : " + utteranceId + ", speakStatus : " + speakStatus.getValue() + " }");
        try {
            JSONObject param = new JSONObject();
            param.put("speakId", utteranceId);
            param.put("speakStatus", speakStatus.getValue());
            postResponse(callbackName, param);
        } catch (Exception e) {
            Log.e("TextToSpeechHelperTest", Objects.requireNonNull(e.getMessage()));
        }
    }

    public void postResponse(String callbackName, JSONObject params) {
        runOnUiThread(() -> {
            String paramString = (params != null) ? "'" + params.toString().replace("\"", "\\\"") + "'" : "";
            String script = "(function(){" + callbackName + "(" + paramString + ");})();";
            vb.webview.evaluateJavascript(script, null);
        });
    }

    static class TreasureComicsJavascript {
        private final TextToSpeechJava tts;

        TreasureComicsJavascript(TextToSpeechJava textToSpeechJava) {
            this.tts = textToSpeechJava;
        }

        @JavascriptInterface
        public void postMessage(String message) {
            try {
                JSONObject param = new JSONObject(message);
                String request = param.getString("request");
                String callbackName = param.getString("callback");
                if (request.equals("postSpeak")) {
                    String action = param.getString("action");
                    switch (action) {
                        case "start":
                            JSONObject innerParameter = param.getJSONObject("parameter");
                            String speakId = innerParameter.getString("speakId");
                            String speakText = innerParameter.getString("speakText");
                            float speechRate = (float) innerParameter.getDouble("speechRate");
                            float pitch = (float) innerParameter.getDouble("pitch");
                            // speak
                            TextToSpeechJava.SpeakEntity entity = new TextToSpeechJava.SpeakEntity(speakId, speakText, speechRate, pitch);
                            tts.speak(entity, callbackName);
                            return;
                        case "pause":
                            tts.speakPause(callbackName);
                            return;
                        case "stop":
                            tts.speakStop(callbackName);
                            return;
                        case "resume":
                            tts.speakResume(callbackName);
                            return;
                    }
                }
            } catch (Exception e) {
                Log.e("TextToSpeechHelperTest", Objects.requireNonNull(e.getMessage()));
            }
        }
    }
}


