/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.preference

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_ENABLE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_RESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_STEREO
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class DolbySettingsFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    private val appContext: Context
        get() = requireContext().applicationContext

    private val switchBar by lazy { findPreference<MainSwitchPreference>(PREF_ENABLE)!! }
    private val profilePref by lazy { findPreference<ListPreference>(PREF_PROFILE)!! }
    private val presetPref by lazy { findPreference<Preference>(PREF_PRESET)!! }
    private val ieqPref by lazy { findPreference<DolbyIeqPreference>(PREF_IEQ)!! }
    private val stereoPref by lazy { findPreference<ListPreference>(PREF_STEREO)!! }
    private val dialoguePref by lazy { findPreference<ListPreference>(PREF_DIALOGUE)!! }
    private val bassPref by lazy { findPreference<SwitchPreferenceCompat>(PREF_BASS)!! }
    private val hpVirtPref by lazy { findPreference<SwitchPreferenceCompat>(PREF_HP_VIRTUALIZER)!! }
    private val spkVirtPref by lazy { findPreference<SwitchPreferenceCompat>(PREF_SPK_VIRTUALIZER)!! }
    private val volumePref by lazy { findPreference<SwitchPreferenceCompat>(PREF_VOLUME)!! }
    private val resetPref by lazy { findPreference<Preference>(PREF_RESET)!! }

    private val dolbyController by lazy(LazyThreadSafetyMode.NONE) {
        DolbyController.getInstance(appContext)
    }
    private val audioManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(AudioManager::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())

    private var isOnSpeaker = true
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setIsOnSpeaker($value)")
            updateProfileSpecificPrefs()
        }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            updateSpeakerState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            updateSpeakerState()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        dlog(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.dolby_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val profile = dolbyController.profile
        preferenceManager.preferenceDataStore = DolbyPreferenceStore(appContext).also {
            it.profile = profile
        }

        val dsOn = dolbyController.dsOn
        switchBar.addOnSwitchChangeListener(this)
        switchBar.isChecked = dsOn

        profilePref.onPreferenceChangeListener = this
        updateProfileIcon(profile)
        profilePref.isEnabled = dsOn
        profilePref.apply {
            if (entryValues.contains(profile.toString())) {
                summary = "%s"
                value = profile.toString()
            } else {
                summary = getString(R.string.dolby_unknown)
            }
        }

        hpVirtPref.onPreferenceChangeListener = this
        spkVirtPref.onPreferenceChangeListener = this
        stereoPref.onPreferenceChangeListener = this
        dialoguePref.onPreferenceChangeListener = this
        bassPref.onPreferenceChangeListener = this
        volumePref.onPreferenceChangeListener = this
        ieqPref.onPreferenceChangeListener = this

        resetPref.setOnPreferenceClickListener {
            dolbyController.resetProfileSpecificSettings()
            updateProfileSpecificPrefs()
            Toast.makeText(
                appContext,
                getString(R.string.dolby_reset_profile_toast, profilePref.summary),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)
        updateSpeakerState()
        updateProfileSpecificPrefs()
    }

    override fun onDestroyView() {
        dlog(TAG, "onDestroyView")
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateProfileSpecificPrefs()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        dlog(TAG, "onPreferenceChange: key=${preference.key} value=$newValue")
        when (preference.key) {
            PREF_PROFILE -> {
                val profile = newValue.toString().toInt()
                dolbyController.profile = profile
                (preferenceManager.preferenceDataStore as DolbyPreferenceStore).profile = profile
                updateProfileIcon(profile)
                updateProfileSpecificPrefs()
            }
            PREF_SPK_VIRTUALIZER -> dolbyController.setSpeakerVirtEnabled(newValue as Boolean)
            PREF_HP_VIRTUALIZER -> dolbyController.setHeadphoneVirtEnabled(newValue as Boolean)
            PREF_STEREO -> dolbyController.setStereoWideningAmount(newValue.toString().toInt())
            PREF_DIALOGUE -> dolbyController.setDialogueEnhancerAmount(newValue.toString().toInt())
            PREF_BASS -> dolbyController.setBassEnhancerEnabled(newValue as Boolean)
            PREF_VOLUME -> dolbyController.setVolumeLevelerEnabled(newValue as Boolean)
            PREF_IEQ -> dolbyController.setIeqPreset(newValue.toString().toInt())
            else -> return false
        }
        return true
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        dlog(TAG, "onCheckedChanged($isChecked)")
        dolbyController.dsOn = isChecked
        profilePref.isEnabled = isChecked
        updateProfileSpecificPrefs()
    }

    private fun updateSpeakerState() {
        val devices = audioManager
            ?.getDevicesForAttributes(ATTRIBUTES_MEDIA)
            .orEmpty()
        val firstType = devices.firstOrNull()?.type
        isOnSpeaker = (firstType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    }

    private fun updateProfileSpecificPrefs() {
        val unknownRes = getString(R.string.dolby_unknown)
        val headphoneRes = getString(R.string.dolby_connect_headphones)
        val dsOn = dolbyController.dsOn
        val currentProfile = dolbyController.profile

        dlog(
            TAG, "updateProfileSpecificPrefs: dsOn=$dsOn currentProfile=$currentProfile"
                    + " isOnSpeaker=$isOnSpeaker"
        )

        val enable = dsOn && (currentProfile != -1)
        presetPref.isEnabled = enable
        spkVirtPref.isEnabled = enable
        ieqPref.isEnabled = enable
        dialoguePref.isEnabled = enable
        volumePref.isEnabled = enable
        bassPref.isEnabled = enable
        resetPref.isEnabled = enable
        hpVirtPref.isEnabled = enable && !isOnSpeaker
        stereoPref.isEnabled = enable && !isOnSpeaker

        if (!enable) return

        presetPref.summary = dolbyController.getPresetName()

        val ieqValue = dolbyController.getIeqPreset(currentProfile)
        ieqPref.apply {
            if (entryValues.contains(ieqValue.toString())) {
                summary = "%s"
                value = ieqValue.toString()
            } else {
                summary = unknownRes
            }
        }

        val deValue = dolbyController.getDialogueEnhancerAmount(currentProfile).toString()
        dialoguePref.apply {
            if (entryValues.contains(deValue)) {
                summary = "%s"
                value = deValue
            } else {
                summary = unknownRes
            }
        }

        spkVirtPref.isChecked = dolbyController.getSpeakerVirtEnabled(currentProfile)
        volumePref.isChecked = dolbyController.getVolumeLevelerEnabled(currentProfile)
        bassPref.isChecked = dolbyController.getBassEnhancerEnabled(currentProfile)

        // below prefs are not enabled on loudspeaker
        if (isOnSpeaker) {
            stereoPref.summary = headphoneRes
            hpVirtPref.summary = headphoneRes
            return
        }

        val swValue = dolbyController.getStereoWideningAmount(currentProfile).toString()
        stereoPref.apply {
            if (entryValues.contains(swValue)) {
                summary = "%s"
                value = swValue
            } else {
                summary = unknownRes
            }
        }

        hpVirtPref.apply {
            isChecked = dolbyController.getHeadphoneVirtEnabled(currentProfile)
            summary = null
        }
    }

    private fun updateProfileIcon(profile: Int) {
        when (profile) {
            0 -> profilePref.setIcon(R.drawable.ic_profile_dynamic)
            1 -> profilePref.setIcon(R.drawable.ic_profile_movie)
            2 -> profilePref.setIcon(R.drawable.ic_profile_music)
            3 -> profilePref.setIcon(R.drawable.ic_profile_custom)
            else -> profilePref.setIcon(R.drawable.ic_dolby)
        }
    }

    companion object {
        private const val TAG = "DolbySettingsFragment"
        private val ATTRIBUTES_MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
}
