package com.appincreible.musicplayer.player.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

final class DjLoquendoEngine {
    private static final String UTTERANCE_ID = "appincreible_dj_loquendo";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private TextToSpeech tts;
    private boolean ready;
    private Runnable completion;

    DjLoquendoEngine(Context context) {
        appContext = context.getApplicationContext();
        tts = new TextToSpeech(appContext, status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (!ready) return;
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setSpeechRate(0.96f);
            tts.setPitch(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { finishCurrent(); }
                @Override public void onError(String utteranceId) { finishCurrent(); }
                @Override public void onError(String utteranceId, int errorCode) { finishCurrent(); }
            });
            applyPreferredVoice();
        });
    }

    boolean shouldAnnounce() {
        if (!AudioPreferences.djEnabled(appContext)) return false;
        List<String> phrases = phrases();
        if (phrases.isEmpty()) return false;
        return random.nextInt(100) < AudioPreferences.djProbability(appContext);
    }

    void speakRandom(@Nullable Runnable onDone) {
        completion = onDone;
        if (!ready || tts == null) {
            finishCurrent();
            return;
        }
        List<String> phrases = phrases();
        if (phrases.isEmpty()) {
            finishCurrent();
            return;
        }
        applyPreferredVoice();
        Voice activeVoice = tts.getVoice();
        if (!isOfflineVoice(activeVoice)) {
            finishCurrent();
            return;
        }
        String phrase = phrases.get(random.nextInt(phrases.size()));
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        int result = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID);
        if (result == TextToSpeech.ERROR) finishCurrent();
    }

    void cancel() {
        completion = null;
        if (tts != null) tts.stop();
    }

    void release() {
        completion = null;
        ready = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private void finishCurrent() {
        Runnable callback = completion;
        completion = null;
        if (callback != null) mainHandler.post(callback);
    }

    private List<String> phrases() {
        String raw = AudioPreferences.djPhrases(appContext);
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        String[] lines = raw.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String clean = line.trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }

    private void applyPreferredVoice() {
        if (!ready || tts == null) return;
        String preferred = AudioPreferences.djVoiceName(appContext);
        Voice selected = findOfflineVoice(preferred);
        if (selected != null) {
            tts.setVoice(selected);
            return;
        }
        int language = tts.setLanguage(new Locale("es", "AR"));
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(new Locale("es"));
        }
        Voice current = tts.getVoice();
        if (current != null && current.isNetworkConnectionRequired()) {
            Voice fallback = firstSpanishOfflineVoice();
            if (fallback != null) tts.setVoice(fallback);
        }
    }

    @Nullable
    private Voice findOfflineVoice(String voiceName) {
        if (voiceName == null || voiceName.isEmpty() || tts == null) return firstSpanishOfflineVoice();
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return null;
        for (Voice voice : voices) {
            if (voiceName.equals(voice.getName()) && isOfflineVoice(voice)) return voice;
        }
        return firstSpanishOfflineVoice();
    }

    @Nullable
    private Voice firstSpanishOfflineVoice() {
        if (tts == null) return null;
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return null;
        List<Voice> available = new ArrayList<>();
        for (Voice voice : voices) {
            if (!isOfflineVoice(voice)) continue;
            Locale locale = voice.getLocale();
            if (locale != null && "es".equalsIgnoreCase(locale.getLanguage())) available.add(voice);
        }
        if (available.isEmpty()) return null;
        available.sort(Comparator.comparingInt((Voice v) -> "AR".equalsIgnoreCase(v.getLocale().getCountry()) ? 0 : 1)
                .thenComparing(Voice::getName));
        return available.get(0);
    }

    static boolean isOfflineVoice(Voice voice) {
        if (voice == null || voice.isNetworkConnectionRequired()) return false;
        Set<String> features = voice.getFeatures();
        return features == null || !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
    }
}
