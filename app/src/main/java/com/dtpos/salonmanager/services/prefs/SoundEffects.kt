package com.dtpos.salonmanager.services.prefs

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.dtpos.salonmanager.R

/** Short UI sounds (Settings > Sound effects). Used for meaningful actions only, not every tap. */
class SoundEffects(context: Context, private val preferences: UiPreferences) {

    private val pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    } catch (e: Exception) {
        null
    }

    private val tapId = pool?.load(context, R.raw.ui_tap, 1) ?: 0
    private val successId = pool?.load(context, R.raw.ui_success, 1) ?: 0

    fun tap() = play(tapId, 0.55f)

    fun success() = play(successId, 0.7f)

    private fun play(id: Int, volume: Float) {
        if (!preferences.soundEffects.value || id == 0) return
        try {
            pool?.play(id, volume, volume, 1, 0, 1f)
        } catch (e: Exception) {
            // Sound is decoration; never fail an action because of it.
        }
    }
}
