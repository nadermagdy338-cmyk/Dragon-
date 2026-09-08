/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package nd.max.ui.util

import android.content.Context

/** Persisted appearance/metrics settings for [nd.max.service.FpsOverlayService]. */
object FpsOverlayPrefs {
    private const val PREFS_NAME = "maxmanager_fps_overlay"

    private const val KEY_ENABLED = "overlay_enabled"
    private const val KEY_STYLE = "style_mode"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_COLOR = "color_hex"
    private const val KEY_SIZE = "text_size_sp"
    private const val KEY_ALPHA = "bg_alpha"
    private const val KEY_WIDTH_SCALE = "width_scale"
    private const val KEY_SHOW_FPS = "show_fps"
    private const val KEY_SHOW_CPU = "show_cpu"
    private const val KEY_SHOW_WATT = "show_watt"
    private const val KEY_SHOW_TEMP = "show_temp"
    private const val KEY_SHOW_RAM = "show_ram"
    private const val KEY_SHOW_RENDER = "show_render"

    data class State(
        val enabled: Boolean = false,
        val styleMode: Int = 0, // 0=Android list, 1=PC/monospace, 2=Mini
        val orientation: Int = 0, // 0=vertical, 1=horizontal (style 0 only)
        val colorHex: String = "#00E676",
        val textSizeSp: Float = 14f,
        val bgAlpha: Float = 0.5f,
        val widthScale: Float = 1f,
        val showFps: Boolean = true,
        val showCpu: Boolean = true,
        val showWatt: Boolean = false,
        val showTemp: Boolean = true,
        val showRam: Boolean = true,
        val showRender: Boolean = false
    )

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            enabled = p.getBoolean(KEY_ENABLED, false),
            styleMode = p.getInt(KEY_STYLE, 0),
            orientation = p.getInt(KEY_ORIENTATION, 0),
            colorHex = p.getString(KEY_COLOR, "#00E676") ?: "#00E676",
            textSizeSp = p.getFloat(KEY_SIZE, 14f),
            bgAlpha = p.getFloat(KEY_ALPHA, 0.5f),
            widthScale = p.getFloat(KEY_WIDTH_SCALE, 1f),
            showFps = p.getBoolean(KEY_SHOW_FPS, true),
            showCpu = p.getBoolean(KEY_SHOW_CPU, true),
            showWatt = p.getBoolean(KEY_SHOW_WATT, false),
            showTemp = p.getBoolean(KEY_SHOW_TEMP, true),
            showRam = p.getBoolean(KEY_SHOW_RAM, true),
            showRender = p.getBoolean(KEY_SHOW_RENDER, false)
        )
    }

    fun save(context: Context, state: State) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, state.enabled)
            putInt(KEY_STYLE, state.styleMode)
            putInt(KEY_ORIENTATION, state.orientation)
            putString(KEY_COLOR, state.colorHex)
            putFloat(KEY_SIZE, state.textSizeSp)
            putFloat(KEY_ALPHA, state.bgAlpha)
            putFloat(KEY_WIDTH_SCALE, state.widthScale)
            putBoolean(KEY_SHOW_FPS, state.showFps)
            putBoolean(KEY_SHOW_CPU, state.showCpu)
            putBoolean(KEY_SHOW_WATT, state.showWatt)
            putBoolean(KEY_SHOW_TEMP, state.showTemp)
            putBoolean(KEY_SHOW_RAM, state.showRam)
            putBoolean(KEY_SHOW_RENDER, state.showRender)
            apply()
        }
    }
}
