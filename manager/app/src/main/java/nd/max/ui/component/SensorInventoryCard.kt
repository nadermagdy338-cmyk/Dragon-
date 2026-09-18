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

package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricReadout
import nd.max.ui.design.MaxMetricSize
import nd.max.ui.mainscreens.IconBadge
import nd.max.ui.util.SensorInventory

/**
 * `GAP-11` — **جرد المستشعرات**: ما تُعلنه المنصّة، وما غاب، وقراءة ضوء لا تكذب.
 *
 * والفرق الجوهري عن أي «فاحص مستشعرات»: هذه البطاقة **لا تُعطي درجة** ولا تقول «المستشعر
 * سليم». تعرض ما تعلنه المنصّة عن العتاد، وقراءة واحدة بمهلة — وثلاث حالات لا ثنتان، فلا يصير
 * «لم أقرأ» صفرًا ولا «مظلم».
 *
 * وهي في ملفها هذا لا في شاشة التشخيص: الفصل يمنع الشاشة من تجاوز حدّ الحجم، ويجعل البطاقة
 * مشمولة بالعقد **في مكان واحد** ([SensorInventory] للنموذج وهذا للعرض).
 */
/** عدد المستشعرات المعروضة قبل الاختصار — التشخيص يحتاج الأسماء، لا جدولًا لا ينتهي. */
private const val SENSOR_PREVIEW_LIMIT = 12

@Composable
fun SensorInventoryCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<SensorInventory.Report?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) {
            nd.max.ui.util.SensorMonitorUtil.report(context)
        }
        loaded = true
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Sensors, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_sensor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_sensor_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val current = report
        when {
            !loaded -> SensorDetailRow(stringResource(R.string.max_boot_reading), null)

            current == null -> SensorDetailRow(
                stringResource(R.string.max_sensor_service),
                stringResource(R.string.max_sensor_service_unavailable)
            )

            else -> {
                // الوسوم تُحلّ هنا لأن `joinToString` ليست inline فلا تصلح فيها دالة مركّبة.
                val kindLabels = SensorInventory.Kind.entries.associateWith { kindText(it) }
                SensorDetailRow(stringResource(R.string.max_sensor_count), current.count.toString())
                SensorDetailRow(
                    stringResource(R.string.max_sensor_kinds),
                    current.kindsReported.joinToString(" · ") { kindLabels.getValue(it) }
                )
                if (current.wakeUpCount > 0) {
                    SensorDetailRow(
                        stringResource(R.string.max_sensor_wakeup),
                        current.wakeUpCount.toString()
                    )
                }
                // قراءة الضوء تُعرض بعقد نظام التصميم نفسه (`MaxMetric`) لا بمفردات موازية:
                // حالتا `Unreadable`/`Unsupported` هناك هما **بالضبط** حالتانا، والعارض
                // يرفض طباعة قيمة حين تقول الثقة إن لا شيء حقيقيًّا يُطبع — فلا يصير
                // «لم أقرأ» صفرًا بحكم المكوّن لا بحكم انتباه كاتب الشاشة.
                SensorLightReadout(current.light)
                current.items.take(SENSOR_PREVIEW_LIMIT).forEach { item ->
                    SensorDetailRow(
                        label = item.name.ifBlank { kindLabels.getValue(item.kind) },
                        value = buildString {
                            append(kindLabels.getValue(item.kind))
                            SensorInventory.powerLabel(item.powerMilliAmp)?.let {
                                append(" · ").append(it).append(" mA")
                            }
                            SensorInventory.delayLabel(item.minDelayUs)?.let {
                                append(" · ").append(it).append(" us")
                            }
                        }
                    )
                }
                if (current.count > SENSOR_PREVIEW_LIMIT) {
                    Text(
                        text = stringResource(
                            R.string.max_sensor_more,
                            (current.count - SENSOR_PREVIEW_LIMIT).toString()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.max_sensor_no_score),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * قراءة الضوء كـ`MaxMetric`: القيمة تُطبع **فقط** إن كانت الثقة تسمح، وإلا تُطبع بديلة
 * «غير متاح» من نظام التصميم — وبشرح صريح للسبب.
 */
@Composable
private fun SensorLightReadout(light: SensorInventory.LightReading) {
    val trust = when (light.state) {
        SensorInventory.ReadingState.REPORTED -> MaxDataTrust.Live
        SensorInventory.ReadingState.UNREADABLE -> MaxDataTrust.Unreadable
        SensorInventory.ReadingState.ABSENT -> MaxDataTrust.Unsupported
    }
    val note = when (light.state) {
        SensorInventory.ReadingState.REPORTED -> null
        SensorInventory.ReadingState.UNREADABLE -> stringResource(R.string.max_sensor_light_unreadable)
        SensorInventory.ReadingState.ABSENT -> stringResource(R.string.max_sensor_light_absent)
    }
    MaxMetricReadout(
        metric = MaxMetric(
            label = stringResource(R.string.max_sensor_light),
            value = light.lux?.let { String.format(Locale.US, "%.1f", it) },
            unit = stringResource(R.string.max_sensor_unit_lux),
            trust = trust,
            note = note,
        ),
        size = MaxMetricSize.Small,
    )
}

/** سطر تفاصيل محلي — الشاشة تُخفي `DetailRow` الخاصة بها، فالبطاقة تحمل سطرها. */
@Composable
private fun SensorDetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** اسم الصنف — من مورد لا من نصّ صلب. */
@Composable
private fun kindText(kind: SensorInventory.Kind): String = stringResource(
    when (kind) {
        SensorInventory.Kind.MOTION -> R.string.max_sensor_kind_motion
        SensorInventory.Kind.ENVIRONMENT -> R.string.max_sensor_kind_environment
        SensorInventory.Kind.POSITION -> R.string.max_sensor_kind_position
        SensorInventory.Kind.BODY -> R.string.max_sensor_kind_body
        SensorInventory.Kind.UNPOSITIONED -> R.string.max_sensor_kind_unpositioned
        SensorInventory.Kind.OTHER -> R.string.max_sensor_kind_other
    }
)
