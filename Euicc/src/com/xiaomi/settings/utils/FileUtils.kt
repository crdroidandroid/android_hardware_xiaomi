/*
 * SPDX-FileCopyrightText: 2022-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.util.Log
import java.io.File

private const val TAG = "FileUtils"

/*
 * Writes the given String value into the given file
 *
 * @return true on success, false on failure
 */
fun writeLine(fileName: String, value: String): Boolean =
    runCatching { File(fileName).writeText(value) }
        .onFailure { e -> Log.e(TAG, "Could not write to file $fileName", e) }
        .isSuccess

/*
 * Writes the given Int value into the given file
 *
 * @return true on success, false on failure
 */
fun writeLine(fileName: String, value: Int): Boolean =
    writeLine(fileName, value.toString())
