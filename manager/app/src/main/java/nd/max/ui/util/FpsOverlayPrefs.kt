/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package nd.max.ui.util

import android.content.Context
import nd.max.core.platform.HudArrangement
import nd.max.core.platform.HudField
import nd.max.core.platform.HudForm

/**
 * ما تُحفظه لوحة الأداء. تُقرأ من الشاشة ومن الخدمة معًا، فتغيّر إعداد وأنت داخل لعبة.
 *
 * ### ولماذا أسماء الأنواع لا أرقامها
 *
 * النسخة السابقة خزّنت `style_mode` و`orientation` **أرقامًا** (`0/1/2`) وتركت معناها في
 * تعليق داخل الشاشة. ورقم لا معنى له في المخزن يُقرأ خطأً بمجرّد إعادة ترتيب الاختيارات: قائمة
 * تُقدَّم فيها «الحلقة» خطوة واحدة تُحوّل كل جهاز محفوظ من «شريط» إلى «حلقة» صامتًا. فصار
 * المخزون **اسم النوع** وقراءته تُسقط إلى الأصل عند أي اسم لا يُعرف — والحالة السيئة الوحيدة
 * الممكنة هي الرجوع إلى الافتراضيّ، لا إلى شكل آخر بالغلط.
 *
 * ### والحدّ المعلن
 *
 * المفاتيح **جديدة كلها** (`hud_*`)، والقديمة (`style_mode` · `show_fps` …) تبقى في ملفّ
 * التفضيلات بلا قارئ ⇒ **مرّة واحدة** يرجع المستخدم القائم إلى الافتراضيّ. قُبل ذلك لأنّ
 * مجموعة الخيارات نفسها أُعيد تصميمها (شكل جديد، حقول متعدّدة الاختيار، منحنى بمدة، التصاق)،
 * وترحيل رقم قديم إلى معنى جديد كان سيُنشئ شكلًا لم يخترْه أحد.
 */
object FpsOverlayPrefs {

    private const val PREFS_NAME = "maxmanager_fps_overlay"

    private const val KEY_ENABLED = "hud_enabled"
    private const val KEY_FORM = "hud_form"
    private const val KEY_ARRANGEMENT = "hud_arrangement"
    private const val KEY_FIELDS = "hud_fields"
    private const val KEY_COLOR = "hud_color_hex"
    private const val KEY_TEXT_SIZE = "hud_text_size_sp"
    private const val KEY_BACKGROUND = "hud_background_alpha"
    private const val KEY_WIDTH_SCALE = "hud_width_scale"
    private const val KEY_GRAPH = "hud_graph"
    private const val KEY_GRAPH_SPAN = "hud_graph_span"
    private const val KEY_SNAP = "hud_snap_edges"

    /** الحقول التي تظهر قبل أن يختار المستخدم: الثلاثة التي تُسأل عنها أوّلًا. */
    val DEFAULT_FIELDS: Set<HudField> = setOf(HudField.Frames, HudField.Cpu, HudField.Heat)

    /** مدد المنحنى المسموحة — ثلاثة لا حقل حرّ، فمدّة عشوائية تعني رسمًا لا يُقارَن. */
    val GRAPH_SPANS: List<Int> = listOf(10, 20, 40)

    data class State(
        val enabled: Boolean = false,
        val form: HudForm = HudForm.Strip,
        val arrangement: HudArrangement = HudArrangement.Line,
        val fields: Set<HudField> = DEFAULT_FIELDS,
        val colorHex: String = "#00E676",
        val textSizeSp: Float = 14f,
        val backgroundAlpha: Float = 0.5f,
        val widthScale: Float = 1f,
        val showGraph: Boolean = false,
        val graphSpan: Int = 20,
        val snapEdges: Boolean = true
    ) {
        /** الحقول بترتيب العرض: ترتيب النوع نفسه، فلا صفّ يُعاد ترتيبه بالتخزين. */
        val orderedFields: List<HudField> get() = HudField.entries.filter { it in fields }
    }

    /**
     * مفاتيح التنقّل العائمة داخل الخدمة — تُقرأ مرّة كل دورة قراءة، فاختيار يُغيّر شكل اللوحة
     * يظهر في النافذة فورًا بلا إعادة تشغيلها (وهو ما كان يستلزم إطفاءها وتشغيلها).
     */
    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            enabled = p.getBoolean(KEY_ENABLED, false),
            form = enumOr(p.getString(KEY_FORM, null), HudForm.entries, HudForm.Strip),
            arrangement = enumOr(
                p.getString(KEY_ARRANGEMENT, null),
                HudArrangement.entries,
                HudArrangement.Line
            ),
            fields = readFields(p.getString(KEY_FIELDS, null)),
            colorHex = p.getString(KEY_COLOR, "#00E676") ?: "#00E676",
            textSizeSp = p.getFloat(KEY_TEXT_SIZE, 14f),
            backgroundAlpha = p.getFloat(KEY_BACKGROUND, 0.5f),
            widthScale = p.getFloat(KEY_WIDTH_SCALE, 1f),
            showGraph = p.getBoolean(KEY_GRAPH, false),
            graphSpan = p.getInt(KEY_GRAPH_SPAN, 20).takeIf { it in GRAPH_SPANS } ?: 20,
            snapEdges = p.getBoolean(KEY_SNAP, true)
        )
    }

    fun save(context: Context, state: State) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, state.enabled)
            putString(KEY_FORM, state.form.name)
            putString(KEY_ARRANGEMENT, state.arrangement.name)
            putString(KEY_FIELDS, state.fields.joinToString(",") { it.name })
            putString(KEY_COLOR, state.colorHex)
            putFloat(KEY_TEXT_SIZE, state.textSizeSp)
            putFloat(KEY_BACKGROUND, state.backgroundAlpha)
            putFloat(KEY_WIDTH_SCALE, state.widthScale)
            putBoolean(KEY_GRAPH, state.showGraph)
            putInt(KEY_GRAPH_SPAN, state.graphSpan)
            putBoolean(KEY_SNAP, state.snapEdges)
            apply()
        }
    }

    private fun readFields(raw: String?): Set<HudField> {
        if (raw.isNullOrBlank()) return DEFAULT_FIELDS
        val names = raw.split(",").mapNotNull { entry ->
            HudField.entries.firstOrNull { it.name == entry.trim() }
        }
        // مجموعة فارغة تعني «لوحة لا تُظهر شيئًا» — وهي حالة صحيحة قصدها المستخدم، لكنّها
        // أيضًا ما تُنتجه قراءة ملفّ قديم فارغ. والفارق أنّ الفارغ الملفّ **غياب مفتاح** لا
        // مجموعة فرّغها المستخدم، ولذلك يُميَّز هنا بالنصّ لا بالطول.
        return names.toSet()
    }

    private fun <T> enumOr(raw: String?, values: List<T>, fallback: T): T where T : Enum<T> =
        values.firstOrNull { it.name == raw } ?: fallback
}
