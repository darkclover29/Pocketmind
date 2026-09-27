package com.pocketshadow.app.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

/** Tiny built-in tones; no bundled audio assets or extra network/runtime cost. */
class PocketShadowSoundEffects {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 22) }.getOrNull()

    fun send() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
    }

    fun complete() {
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 110)
    }

    fun close() {
        tone?.release()
    }
}
