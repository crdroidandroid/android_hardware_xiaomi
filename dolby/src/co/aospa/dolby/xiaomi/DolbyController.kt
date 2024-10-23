/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.R

internal class DolbyController private constructor(
    private val context: Context
) {
    private var dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(context.mainLooper)

    // Restore current profile on every media session
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val isPlaying = configs.any {
                it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            }
            dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
            if (isPlaying)
                setCurrentProfile()
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            setCurrentProfile()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            setCurrentProfile()
        }
    }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            } else {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        }

    var dsOn: Boolean
        get() =
            dolbyEffect.dsOn.also {
                dlog(TAG, "getDsOn: $it")
            }
        set(value) {
            dlog(TAG, "setDsOn: $value")
            checkEffect()
            dolbyEffect.dsOn = value
            registerCallbacks = value
            if (value)
                setCurrentProfile()
        }

    var profile: Int
        get() =
            dolbyEffect.profile.also {
                dlog(TAG, "getProfile: $it")
            }
        set(value) {
            dlog(TAG, "setProfile: $value")
            checkEffect()
            dolbyEffect.profile = value
        }

    var preset: String
        get() {
            val gains = dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS)
            return gains.joinToString(separator = ",").also {
                dlog(TAG, "getPreset: $it")
            }
        }
        set(value) {
            dlog(TAG, "setPreset: $value")
            checkEffect()
            val gains = value.split(",")
                    .map { it.toInt() }
                    .toIntArray()
            dolbyEffect.setDapParameter(DsParam.GEQ_BAND_GAINS, gains)
        }

    var headphoneVirtEnabled: Boolean
        get() =
            dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER).also {
                dlog(TAG, "getHeadphoneVirtEnabled: $it")
            }
        set(value) {
            dlog(TAG, "setHeadphoneVirtEnabled: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value)
        }

    var speakerVirtEnabled: Boolean
        get() =
            dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER).also {
                dlog(TAG, "getSpeakerVirtEnabled: $it")
            }
        set(value) {
            dlog(TAG, "setSpeakerVirtEnabled: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value)
        }

    var bassEnhancerEnabled: Boolean
        get() =
            dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE).also {
                dlog(TAG, "getBassEnhancerEnabled: $it")
            }
        set(value) {
            dlog(TAG, "setBassEnhancerEnabled: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value)
        }

    var volumeLevelerEnabled: Boolean
        get() {
            val enabled = dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE)
            val amount = dolbyEffect.getDapParameterInt(DsParam.VOLUME_LEVELER_AMOUNT)
            dlog(TAG, "getVolumeLevelerEnabled: enabled=$enabled amount=$amount")
            return enabled && amount > 0
        }
        set(value) {
            dlog(TAG, "setVolumeLevelerEnabled: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value)
            dolbyEffect.setDapParameter(
                DsParam.VOLUME_LEVELER_AMOUNT,
                if (value) VOLUME_LEVELER_AMOUNT else 0
            )
        }

    var stereoWideningAmount: Int
        get() =
            dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT).also {
                dlog(TAG, "getStereoWideningAmount: $it")
            }
        set(value) {
            dlog(TAG, "setStereoWideningAmount: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value)
        }

    var dialogueEnhancerAmount: Int
        get() {
            val enabled = dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE)
            val amount = if (enabled) {
                dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT)
            } else 0
            dlog(TAG, "getDialogueEnhancerAmount: enabled=$enabled amount=$amount")
            return amount
        }
        set(value) {
            dlog(TAG, "setDialogueEnhancerAmount: $value")
            checkEffect()
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, (value > 0))
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value)
        }

    init {
        dlog(TAG, "initialized")
    }

    fun onBootCompleted() {
        dlog(TAG, "onBootCompleted")

        // Restore current profile now and on certain audio changes.
        val on = dsOn
        dolbyEffect.enabled = on
        registerCallbacks = on
        if (on)
            setCurrentProfile()
    }

    private fun checkEffect() {
        if (!dolbyEffect.hasControl()) {
            Log.w(TAG, "lost control, recreating effect")
            dolbyEffect.release()
            dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
        }
    }

    private fun setCurrentProfile() {
        if (!dsOn) {
            dlog(TAG, "setCurrentProfile: skip, dolby is off")
            return
        }
        dlog(TAG, "setCurrentProfile")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        profile = prefs.getString(DolbyConstants.PREF_PROFILE, "0" /*dynamic*/).toInt()
    }

    fun getProfileName(): String? {
        val profile = dolbyEffect.profile.toString()
        val profiles = context.resources.getStringArray(R.array.dolby_profile_values)
        val profileIndex = profiles.indexOf(profile)
        dlog(TAG, "getProfileName: profile=$profile index=$profileIndex")
        return if (profileIndex == -1) null else context.resources.getStringArray(
            R.array.dolby_profile_entries
        )[profileIndex]
    }

    fun resetProfileSpecificSettings() {
        checkEffect()
        dolbyEffect.resetProfileSpecificSettings()
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val VOLUME_LEVELER_AMOUNT = 2

        @Volatile
        private var instance: DolbyController? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context).also { instance = it }
            }
    }
}
