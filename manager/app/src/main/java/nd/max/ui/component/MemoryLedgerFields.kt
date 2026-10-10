/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.util.MemoryLedger

/** صفّ واحد في دفتر الذاكرة: عنوانه وقيمته، بعد الترجمة — الشاشات ترسمه بأسلوبها. */
@Immutable
data class MemoryLedgerField(val label: String, val value: String)

/**
 * `AR-24` — حقول دفتر الذاكرة (PSS · طريقة القياس · المحفوظ · المقارنة) **من مصدر واحد**.
 *
 * كان التحويل من [MemoryLedger.Report] إلى (عنوان، قيمة) مكتوبًا داخل بطاقة شاشة التشخيص وحدها،
 * وصارت الشاشات تعرضه في مواضعها: «معلومات الجهاز» (قسم الذاكرة) و«Kernel Facts» في Zram —
 * والحقول هناك تُبنى بهذه الدالة لا بنسخة ثانية، فنسختان لنفس الصياغة تتباعدان حتمًا
 * (راجع `ADR-02`: للشيء الواحد مدخل واحد).
 *
 * @param persist `true` في شاشة التشخيص **وحدها**: هي التي تقيس وتُسجّل لقطة جديدة.
 *   والافتراض `false` لكل من يعرض الدفتر عرضًا فقط — القياس يحدث، والكتابة لا.
 */
@Composable
fun rememberMemoryLedgerFields(persist: Boolean = false): List<MemoryLedgerField> {
    val context = LocalContext.current
    var report by remember { mutableStateOf<MemoryLedger.Report?>(null) }
    LaunchedEffect(context, persist) {
        report = withContext(Dispatchers.IO) {
            MemoryLedger.observeOwn(context, persist = persist)
        }
    }

    val loaded = report
    val current = loaded?.current
    if (loaded == null || current == null) {
        // «جارٍ القراءة…» مع شرطة القيمة الموحّدة: لا يظهر صفّ بلا قيمة ولا قيمة بلا مصدر.
        return listOf(
            MemoryLedgerField(
                label = stringResource(R.string.max_memory_reading),
                value = MAX_VALUE_UNAVAILABLE,
            )
        )
    }

    return listOf(
        MemoryLedgerField(
            label = stringResource(R.string.max_memory_current),
            value = stringResource(R.string.max_memory_kb_format, current.totalPssKb.toString()),
        ),
        MemoryLedgerField(
            label = stringResource(R.string.max_memory_method),
            value = when (current.method) {
                MemoryLedger.Method.OWN_PROCESS -> stringResource(R.string.max_memory_method_own)
                MemoryLedger.Method.DUMPSYS -> stringResource(R.string.max_memory_method_dumpsys)
            },
        ),
        MemoryLedgerField(
            label = stringResource(R.string.max_memory_snapshots),
            value = loaded.snapshots.count { it.key == current.key }.toString(),
        ),
        MemoryLedgerField(
            label = stringResource(R.string.max_memory_trend),
            value = when (val delta = loaded.delta) {
                MemoryLedger.MemoryDelta.Insufficient ->
                    stringResource(R.string.max_memory_trend_insufficient)

                is MemoryLedger.MemoryDelta.Stable ->
                    stringResource(R.string.max_memory_trend_stable, delta.previousKb.toString())

                is MemoryLedger.MemoryDelta.Changed ->
                    if (delta.deltaKb > 0) {
                        stringResource(
                            R.string.max_memory_trend_grew,
                            delta.percent.toString(),
                            delta.deltaKb.toString(),
                        )
                    } else {
                        stringResource(
                            R.string.max_memory_trend_shrank,
                            delta.percent.toString(),
                            (-delta.deltaKb).toString(),
                        )
                    }
            },
        ),
    )
}
