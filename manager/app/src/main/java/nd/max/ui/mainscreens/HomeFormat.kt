/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * تنسيق الأرقام في الشاشة الرئيسية — في ملف واحد يقرأه كل من يرسم.
 *
 * ولماذا فُصلت: كانت داخل `LegendaryHomeDashboard` وحدها، فلما انقسم الكرت إلى ملفين صار
 * اللازم إمّا تكرارها أو مشاركتها؛ والتكرار في تنسيق «1.6 GHz» يعني رقمين مختلفين لنفس
 * المعلومة بعد جولة أو جولتين. وهنا مصدر واحد، والمسافات بوحدة `Locale.US` لأنها أرقام
 * تقنية تُقرأ بترتيبها اللاتيني في كل لغة.
 */
package nd.max.ui.mainscreens
import kotlin.math.roundToInt
import nd.max.R
import nd.max.ui.viewmodel.DashboardState

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import nd.max.ui.component.neuralPalette
import java.util.Locale

internal fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

internal fun compactFrequency(mhz: Int?): String = when {
    mhz == null || mhz <= 0 -> "\u2014"
    mhz >= 1000 -> "${(mhz / 1000f).oneDecimal()} GHz"
    else -> "$mhz MHz"
}

internal fun compactUptime(minutes: Long): String = when {
    minutes <= 0 -> "\u2014"
    minutes >= 1440 -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

internal fun gigabytes(mb: Int): String = when {
    mb <= 0 -> "\u2014"
    mb >= 1024 -> "${(mb / 1024f).oneDecimal()} GB"
    else -> "$mb MB"
}

internal fun netSpeed(kbps: Long): String = when {
    kbps <= 0 -> "0 KB/s"
    kbps >= 1024 -> "${(kbps / 1024f).oneDecimal()} MB/s"
    else -> "$kbps KB/s"
}

internal fun fractionOf(used: Int, total: Int): Float =
    if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)

/** لون الحرارة يُقرأ بالعين قبل الرقم: هادئ · انتباه · خطر. */
@Composable
internal fun temperatureAccent(value: Int?): Color {
    val p = neuralPalette()
    return when {
        value == null -> p.muted
        value >= 45 -> p.danger
        value >= 40 -> p.warn
        else -> p.ok
    }
}

/** حرارة الجهاز للبطاقة الأولى: حسّاس البطارية أولًا ثم المعالج. والصفر = غير مقروء. */
internal fun deviceHeatC(dashboard: DashboardState): Int? =
    dashboard.batteryTempC.takeIf { it > 0f }?.roundToInt()
        ?: dashboard.cpuTempC.takeIf { it > 0 }

/** كلمة حكم الحرارة، بالعتبات التي يطابقها لونها `temperatureAccent` حرفيًّا. */
internal fun heatWordRes(heat: Int?): Int = when {
    heat == null -> R.string.max_home_unavailable
    heat >= HEAT_HOT_C -> R.string.home_vital_temp_hot
    heat >= HEAT_WARM_C -> R.string.home_vital_temp_warm
    else -> R.string.home_vital_temp_cool
}

private const val HEAT_HOT_C = 45
private const val HEAT_WARM_C = 40
