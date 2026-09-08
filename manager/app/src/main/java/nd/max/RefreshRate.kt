/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class RefreshRateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MaxManager"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "nd.max.SET_FPS") return

        val isReset = intent.getBooleanExtra("reset", false)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isReset) {
                    Log.d(TAG, "Resetting refresh rate to system default")
                    resetRefreshRate()
                } else {
                    val fps = intent.getIntExtra("fps", 60)
                    Log.d(TAG, "Applying refresh rate: ${fps}Hz")
                    applyRefreshRate(context, fps)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Undoes what [applyRefreshRate] persisted, without picking a new forced
     * mode. MaxManager stops enforcing a refresh rate; the display goes back
     * to whatever the vendor/system would normally pick on its own.
     */
    private fun resetRefreshRate() {
        val result = Shell.cmd(
            "settings delete system peak_refresh_rate",
            "settings delete secure user_refresh_rate",
            "settings delete system user_refresh_rate",
            "settings delete system miui_refresh_rate",
            "resetprop -n persist.vendor.display.refresh_rate",
            "resetprop -n persist.sys.display.refresh_rate"
        ).exec()
        Log.d(TAG, if (result.isSuccess) "Refresh rate override cleared" else "Failed to clear refresh rate override: ${result.err}")
    }

    private suspend fun applyRefreshRate(context: Context, fps: Int) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: run {
            Log.w(TAG, "Display not available")
            return
        }
        val supported = display.supportedModes.map { it.refreshRate.toInt() }.distinct()
        if (fps !in supported) {
            Log.w(TAG, "FPS $fps not supported: $supported")
            return
        }

        // Let Android's display policy consume the requested rate, then verify
        // both the persisted preference and the live mode. A settings write
        // alone is not proof that the panel actually switched modes.
        val result = Shell.cmd(
            "settings put system peak_refresh_rate $fps",
            "settings put secure user_refresh_rate $fps",
            "settings put system min_refresh_rate $fps"
        ).exec()
        if (!result.isSuccess) {
            Log.w(TAG, "Refresh rate preference write failed: ${result.err}")
            return
        }
        delay(250)
        val live = display.refreshRate
        val persisted = Shell.cmd("settings get system peak_refresh_rate").exec().out.joinToString("").trim()
        if (persisted == fps.toString() && kotlin.math.abs(live - fps) <= 1.0f) {
            Log.d(TAG, "Refresh rate verified -> ${live}Hz")
        } else {
            Log.w(TAG, "Refresh rate not fully verified: requested=${fps}Hz live=${live}Hz persisted=$persisted")
        }
    }

}