/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.util

import android.content.Context
import nd.max.core.gamespace.PanelSide
import nd.max.core.gamespace.validGamePackages

/**
 * تفضيلات اللوحة الجانبية للألعاب — **لكلّ لعبة، لا مفتاح عامّ واحد**.
 *
 * ### ولماذا لكلّ لعبة
 *
 * اللوحة تُفتح فوق لعبة تُقاس، فمفتاح عامّ واحد يعني أن من فعّلها لِلعبة FPS يجدها تظهر فوق
 * لعبة بلا فائدة — والتراكب الذي يظهر حيث لا يُطلب هو نفسه العطب الذي مُنع في `HudSession`
 * («قارئ واحد لا قارئان» بمعنى: لا سطح يظهر بلا طلب). فالمجموعة **أسماء حزم مُتحقَّق منها**
 * ([validGamePackages])، فلا يُكتب في المخزن اسم لم يُقرأ من مصدر حقيقي.
 *
 * ### والأسماء لا الأرقام
 *
 * كما في `FpsOverlayPrefs`/`ProcessOverlayPrefs`: [PanelSide] يُخزَّن **اسمًا**، وقراءته تُسقط
 * إلى الافتراضيّ عند أي اسم لا يُعرف — فلا يُحوَّل جهاز محفوظ من جانب إلى آخر بإعادة ترتيب.
 *
 * والحدّ المعلن: مجموعة فارغة تعني «لا لعبة مُفعَّلة»، وهي الحالة الافتراضية — ولا تُقرأ خطأً
 * كـ«مفعَّل للجميع».
 */
object GamePanelPrefs {

    /** نفس مخزن `GameSpaceRepository` (مكتبة الألعاب)، فالبيانات تُدار من موضع واحد. */
    private const val PREFS_NAME = "settings"

    private const val KEY_ENABLED = "game_panel_enabled"
    private const val KEY_SNAP = "game_panel_snap"
    private const val KEY_SIDE = "game_panel_side"

    /** الحافة الافتراضية: يمين الشاشة — وهي الموضع الذي تعتاده ألواح الألعاب. */
    val DEFAULT_SIDE: PanelSide = PanelSide.End

    data class State(
        val enabledPackages: Set<String> = emptySet(),
        val snapEdges: Boolean = true,
        val side: PanelSide = DEFAULT_SIDE
    )

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val packages = runCatching {
            validGamePackages(p.getStringSet(KEY_ENABLED, emptySet())?.toSet().orEmpty())
        }.getOrDefault(emptySet())
        return State(
            enabledPackages = packages,
            snapEdges = p.getBoolean(KEY_SNAP, true),
            side = enumOr(p.getString(KEY_SIDE, null), PanelSide.entries, DEFAULT_SIDE)
        )
    }

    /** يُفعِّل/يُطفئ لعبة. ويعود `false` بلا ادّعاء نجاح إن رفض المخزن أو كان الاسم غير صالح. */
    fun setEnabled(context: Context, pkg: String, enabled: Boolean): Boolean = runCatching {
        val current = load(context)
        val next = validGamePackages(if (enabled) current.enabledPackages + pkg else current.enabledPackages - pkg)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_ENABLED, next)
            .commit()
    }.getOrDefault(false)

    fun saveSide(context: Context, side: PanelSide) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SIDE, side.name).apply()
    }

    private fun <T> enumOr(raw: String?, values: List<T>, fallback: T): T where T : Enum<T> =
        values.firstOrNull { it.name == raw } ?: fallback
}
