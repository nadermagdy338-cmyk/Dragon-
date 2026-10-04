/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.util

import android.content.Context
import nd.max.core.platform.ProcessScope
import nd.max.core.platform.ProcessSort

/**
 * ما يُحفظ لمراقب المهام — تقرأه الشاشة والخدمة معًا.
 *
 * ### ولماذا صار له إعداد أصلًا
 *
 * التراكب السابق كان **ثوابت في الشيفرة**: ثمانية صفوف، وكل ثانيتين، وترتيب بالمعالج. ومن أراد
 * غير ذلك لم يكن له طريق — لا خيار في الشاشة ولا ملفّ تفضيلات. وهو في متناول المالك (تراكب فوق
 * لعبة تُقاس): قليل من الصفوف أنفع من ثمانية، والفاصل أقصر يرتجف والقيمة تتحرّك.
 *
 * ### والأسماء لا الأرقام
 *
 * كما في `FpsOverlayPrefs`: المخزون **اسم النوع**، وإعادة ترتيب الاختيارات لا تُحوّل جهازًا محفوظًا
 * من شكل إلى آخر — أسوأ ما يحدث هو الرجوع إلى الافتراضيّ.
 *
 * ### والحدّ المعلن
 *
 * المفاتيح `proc_*` جديدة كلها، والقديمة في الملفّ نفسه بلا قارئ ⇒ **مرّة واحدة** يعود المستخدم
 * القائم إلى الافتراضيّ. وهذه أسوأ من الترحيل الصامت: مجموعة الخيارات نفسها تغيّرت (نطاق ·
 * ترتيب · فاصل · تسمية ثقيل)، فترحيل رقم قديم إلى معنى جديد كان سيُنشئ اختيارًا لم يخترْه أحد.
 */
object ProcessOverlayPrefs {

    private const val PREFS_NAME = "maxmanager_process_overlay"

    private const val KEY_ENABLED = "proc_enabled"
    private const val KEY_ROWS = "proc_rows"
    private const val KEY_INTERVAL = "proc_interval_seconds"
    private const val KEY_SORT = "proc_sort"
    private const val KEY_SCOPE = "proc_scope"
    private const val KEY_MARK_HEAVY = "proc_mark_heavy"
    private const val KEY_TALLY = "proc_show_tally"
    private const val KEY_SNAP = "proc_snap_edges"
    private const val KEY_TEXT_SIZE = "proc_text_size_sp"
    private const val KEY_BACKGROUND = "proc_background_alpha"

    /** أعداد الصفوف المسموحة — ثلاثة لا حقل حرّ: نافذة صفٍّ واحد أو ثلاثين ليست نافذة تُقاس بها. */
    val ROW_CHOICES: List<Int> = listOf(3, 5, 8)

    /** فواصل التحديث بالثواني. ما دون الثانية يستهلك بطارية بلا معلومة جديدة. */
    val INTERVAL_CHOICES: List<Int> = listOf(1, 2, 5)

    /** أحجام الخطّ المسموحة (sp) — من يقرأ التراكب على شاشة عالية الكثافة يحتاج أكبر. */
    val TEXT_SIZE_CHOICES: List<Float> = listOf(11f, 13f, 15f)

    /**
     * عتبة «ثقيل» — نسبة يتحوّل عندها لون الصفّ.
     *
     * وثابت لا خيار: القيمة **وصف** («هذا يستهلك نصف معالجك») لا ذوق، وثلاثة أرقام في مكان واحد
     * تجعل المقارنة بين شاشتين تعتمد على إعداد كل واحدة لا على الجهاز.
     */
    const val HEAVY_PERCENT: Float = 50f

    data class State(
        val enabled: Boolean = false,
        val rows: Int = 5,
        val intervalSeconds: Int = 2,
        val sort: ProcessSort = ProcessSort.Cpu,
        val scope: ProcessScope = ProcessScope.All,
        val markHeavy: Boolean = true,
        val showTally: Boolean = true,
        val snapEdges: Boolean = true,
        val textSizeSp: Float = 13f,
        val backgroundAlpha: Float = 0.72f
    )

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            enabled = p.getBoolean(KEY_ENABLED, false),
            rows = p.getInt(KEY_ROWS, 5).takeIf { it in ROW_CHOICES } ?: 5,
            intervalSeconds = p.getInt(KEY_INTERVAL, 2).takeIf { it in INTERVAL_CHOICES } ?: 2,
            sort = enumOr(p.getString(KEY_SORT, null), ProcessSort.entries, ProcessSort.Cpu),
            scope = enumOr(p.getString(KEY_SCOPE, null), ProcessScope.entries, ProcessScope.All),
            markHeavy = p.getBoolean(KEY_MARK_HEAVY, true),
            showTally = p.getBoolean(KEY_TALLY, true),
            snapEdges = p.getBoolean(KEY_SNAP, true),
            textSizeSp = p.getFloat(KEY_TEXT_SIZE, 13f)
                .takeIf { it in TEXT_SIZE_CHOICES } ?: 13f,
            backgroundAlpha = p.getFloat(KEY_BACKGROUND, 0.72f)
        )
    }

    fun save(context: Context, state: State) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, state.enabled)
            putInt(KEY_ROWS, state.rows)
            putInt(KEY_INTERVAL, state.intervalSeconds)
            putString(KEY_SORT, state.sort.name)
            putString(KEY_SCOPE, state.scope.name)
            putBoolean(KEY_MARK_HEAVY, state.markHeavy)
            putBoolean(KEY_TALLY, state.showTally)
            putBoolean(KEY_SNAP, state.snapEdges)
            putFloat(KEY_TEXT_SIZE, state.textSizeSp)
            putFloat(KEY_BACKGROUND, state.backgroundAlpha)
            apply()
        }
    }

    private fun <T> enumOr(raw: String?, values: List<T>, fallback: T): T where T : Enum<T> =
        values.firstOrNull { it.name == raw } ?: fallback
}
