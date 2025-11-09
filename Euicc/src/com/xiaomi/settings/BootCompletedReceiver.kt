/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.xiaomi.settings.telephony.EsimController

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "XiaomiEuiccBootReceiver"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "Received boot completed intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> onBootCompleted(context)
        }
    }

    private fun onBootCompleted(context: Context) {
        // Telephony
        EsimController.getInstance(context).onBootCompleted()
    }
}
