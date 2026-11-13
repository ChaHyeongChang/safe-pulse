package com.safepulse.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.safepulse.core.Prompt;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 음성 안내. 기획서 4장의 원칙대로 핵심 지시는 SDK·네트워크와 무관하게 재생돼야 한다.
 * <ol>
 *   <li>res/raw/vo_{key} 녹음 파일이 있으면 그것을 재생한다(1순위, docs/VOICE_LINES.md 참고).</li>
 *   <li>없으면 Android 기본 TextToSpeech로 읽는다.</li>
 * </ol>
 * 위기 코칭은 재생 중인 안내를 끊고(interrupt) 바로 말하고, 리허설 안내는 순서대로 쌓는다.
 * 재생 전 오디오 포커스를 독점해 음악·라디오를 멈춘다(Pleos Audio Focus 정책 준수).
 */
final class VoiceOutput implements TextToSpeech.OnInitListener {

    private static final String TAG = "SafePulse/Voice";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private final ArrayDeque<Prompt> queue = new ArrayDeque<>();
    private TextToSpeech tts;
    private boolean ttsReady;
    private MediaPlayer player;
    private boolean speaking;
    private float speechRate = 1.0f;

    VoiceOutput(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        this.focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        this.tts = new TextToSpeech(this.context, this);
    }

    @Override
    public void onInit(int status) {
        main.post(() -> {
            if (status != TextToSpeech.SUCCESS || tts == null) {
                Log.w(TAG, "TTS init failed: " + status);
                return;
            }
            int lang = tts.setLanguage(Locale.KOREAN);
            ttsReady = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ttsReady) Log.w(TAG, "Korean TTS voice not available");
            tts.setAudioAttributes(attributes);
            tts.setSpeechRate(speechRate);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(VoiceOutput.this::onUtteranceFinished); }
                @Override public void onError(String id) { main.post(VoiceOutput.this::onUtteranceFinished); }
            });
        });
    }

    /** 고령 모드: 0.85배속. */
    void setSlow(boolean slow) {
        main.post(() -> {
            speechRate = slow ? 0.85f : 1.0f;
            if (tts != null) tts.setSpeechRate(speechRate);
        });
    }

    void speak(Prompt prompt, boolean interrupt) {
        main.post(() -> {
            if (interrupt) {
                queue.clear();
                stopCurrent();
                play(prompt);
            } else if (speaking) {
                queue.addLast(prompt);
            } else {
                play(prompt);
            }
        });
    }

    private void play(Prompt prompt) {
        audioManager.requestAudioFocus(focusRequest);
        speaking = true;
        int resId = context.getResources().getIdentifier("vo_" + prompt.key, "raw", context.getPackageName());
        if (resId != 0 && playClip(resId)) return;
        if (ttsReady && tts != null) {
            tts.speak(prompt.text, TextToSpeech.QUEUE_FLUSH, null, prompt.key + System.nanoTime());
        } else {
            // 음성 수단이 없으면 화면 안내만 남는다. 다음 안내가 막히지 않게 끝난 것으로 처리.
            Log.w(TAG, "no voice output for " + prompt.key);
            main.postDelayed(this::onUtteranceFinished, 1500);
        }
    }

    private boolean playClip(int resId) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attributes);
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(m -> onUtteranceFinished());
            mp.setOnErrorListener((m, what, extra) -> {
                onUtteranceFinished();
                return true;
            });
            mp.prepare();
            mp.start();
            player = mp;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "clip playback failed", e);
            return false;
        }
    }

    private void onUtteranceFinished() {
        releasePlayer();
        Prompt next = queue.pollFirst();
        if (next != null) {
            play(next);
        } else {
            speaking = false;
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    private void stopCurrent() {
        releasePlayer();
        if (tts != null) tts.stop();
        speaking = false;
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
    }

    void shutdown() {
        main.post(() -> {
            stopCurrent();
            audioManager.abandonAudioFocusRequest(focusRequest);
            if (tts != null) {
                tts.shutdown();
                tts = null;
            }
        });
    }
}
