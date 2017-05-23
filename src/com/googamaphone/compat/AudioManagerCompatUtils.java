
package com.googamaphone.compat;

import java.lang.reflect.Method;

import android.media.AudioManager;

public class AudioManagerCompatUtils {
    private static final Class<?> CLASS_AudioManager = AudioManager.class;
    private static final Class<?> CLASS_OnAudioFocusChangeListener = ReflectUtils
            .getClass("android.media.AudioManager$OnAudioFocusChangeListener");
    private static final Method METHOD_requestAudioFocus = ReflectUtils.getMethod(
            CLASS_AudioManager, "requestAudioFocus", CLASS_OnAudioFocusChangeListener, int.class,
            int.class);
    private static final Method METHOD_abandonAudioFocus = ReflectUtils.getMethod(
            CLASS_AudioManager, "abandonAudioFocus", CLASS_OnAudioFocusChangeListener);

    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;

    public static final int requestAudioFocus(AudioManager receiver, Object l, int streamType,
            int durationHint) {
        return (Integer) ReflectUtils.invoke(receiver, METHOD_requestAudioFocus, -1, l, streamType,
                durationHint);
    }

    public static final int abandonAudioFocus(AudioManager receiver, Object l) {
        return (Integer) ReflectUtils.invoke(receiver, METHOD_abandonAudioFocus, -1, l);
    }
}
