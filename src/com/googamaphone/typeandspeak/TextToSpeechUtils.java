
package com.googamaphone.typeandspeak;

import com.googamaphone.typeandspeak.utils.LogUtils;

import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class TextToSpeechUtils {
    public static Set<Locale> loadTtsLanguages(TextToSpeech tts, Intent data) {
        if (data == null) {
            LogUtils.log(TextToSpeechUtils.class, Log.ERROR, "Received null intent");
            return Collections.emptySet();
        }

        final TreeSet<Locale> availableLangs = new TreeSet<Locale>(LOCALE_COMPARATOR);

        if (getAvailableVoicesICS(availableLangs, data)
                || getAvailableVoicesFallback(availableLangs, data)
                || getAvailableVoicesBruteForce(availableLangs, tts)) {
            return availableLangs;
        }

        return Collections.emptySet();
    }

    private static boolean getAvailableVoicesICS(TreeSet<Locale> supportedLocales, Intent intent) {
        final ArrayList<String> availableLangs = intent
                .getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
        if (availableLangs == null) {
            return false;
        }

        for (String availableLang : availableLangs) {
            final Locale locale = parseLocale(availableLang);
            if (locale == null) {
                continue;
            }

            supportedLocales.add(locale);
        }

        return (!supportedLocales.isEmpty());
    }

    private static boolean getAvailableVoicesFallback(TreeSet<Locale> langsList, Intent extras) {
        final String root = extras.getStringExtra(Engine.EXTRA_VOICE_DATA_ROOT_DIRECTORY);
        final String[] files = extras.getStringArrayExtra(Engine.EXTRA_VOICE_DATA_FILES);
        final String[] langs = extras.getStringArrayExtra(Engine.EXTRA_VOICE_DATA_FILES_INFO);
        if ((root == null) || (files == null) || (langs == null)) {
            LogUtils.log(TextToSpeechUtils.class, Log.ERROR, "Missing data on available voices");
            return false;
        }

        for (int i = 0; i < files.length; i++) {
            final File file = new File(root, files[i]);
            if (!file.canRead()) {
                LogUtils.log(TextToSpeechUtils.class, Log.ERROR,
                        "Cannot read file for " + langs[i]);
                continue;
            }

            final Locale locale = parseLocale(langs[i]);
            if (locale == null) {
                LogUtils.log(TextToSpeechUtils.class, Log.ERROR,
                        "Failed to parse locale for " + langs[i]);
                continue;
            }

            langsList.add(locale);
        }

        return (!langsList.isEmpty());
    }

    private static boolean getAvailableVoicesBruteForce(TreeSet<Locale> langsList, TextToSpeech tts) {
        final Locale[] systemLocales = Locale.getAvailableLocales();

        // Check every language supported by the system against the TTS.
        for (Locale systemLocale : systemLocales) {
            final int status = tts.isLanguageAvailable(systemLocale);
            if ((status != TextToSpeech.LANG_AVAILABLE)
                    && (status != TextToSpeech.LANG_COUNTRY_AVAILABLE)
                    && (status != TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)) {
                continue;
            }

            langsList.add(systemLocale);
        }

        return (!langsList.isEmpty());
    }

    private static Locale parseLocale(String language) {
        final String[] langCountryVariant = language.split("-");

        if (langCountryVariant.length == 1) {
            return new Locale(langCountryVariant[0]);
        } else if (langCountryVariant.length == 2) {
            return new Locale(langCountryVariant[0], langCountryVariant[1]);
        } else if (langCountryVariant.length == 3) {
            return new Locale(langCountryVariant[0], langCountryVariant[1], langCountryVariant[2]);
        }

        return null;
    }

    private static final Comparator<Locale> LOCALE_COMPARATOR = new Comparator<Locale>() {
        @Override
        public int compare(Locale lhs, Locale rhs) {
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
        }
    };
}
