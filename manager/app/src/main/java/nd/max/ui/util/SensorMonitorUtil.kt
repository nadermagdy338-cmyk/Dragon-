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

package nd.max.ui.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * جرد المستشعرات **كما تُعلنه المنصّة**، وقراءة ضوء واحدة بمهلة محدودة.
 *
 * وهذه الطبقة وُجدت لأن قراءة الضوء في المستودع كانت **تفشل دائمًا**: `registerListener` ثم
 * `unregisterListener` في الكتلة نفسها، فلا يصل حدث قطّ وتبقى القيمة `0f`. فالآن التسجيل
 * ينتظر **أول حدث** بمهلة، وإن لم يصل نقول «لم أقرأ» — ولا نقول «صفر لوكس».
 *
 * والقراءة هنا **بطلب واحد لا بمستشعر دائم**: لا خدمة خلفية ولا تسجيل مستمر. وهذا مقصود — أداة
 * تشخيص تستهلك المستشعرات باستمرار لتقيس المستشعرات ليست أداة تشخيص.
 */
object SensorMonitorUtil {

    /** مهلة قراءة الضوء: أقصر من أن تُزعج، وأطول من دورة مستشعر واحدة. */
    const val LIGHT_TIMEOUT_MS = 1_500L

    /** جرد كامل بلا امتياز. `null` = تعذّر الوصول إلى خدمة المستشعرات. */
    fun inventory(context: Context): List<SensorInventory.Item>? = runCatching {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.getSensorList(Sensor.TYPE_ALL).map { sensor ->
            SensorInventory.Item(
                name = sensor.name ?: "",
                vendor = sensor.vendor ?: "",
                typeId = sensor.type,
                kind = SensorInventory.kindOf(sensor.type),
                powerMilliAmp = sensor.power,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                minDelayUs = sensor.minDelay,
                isWakeUp = runCatching { sensor.isWakeUpSensor }.getOrDefault(false),
            )
        }
    }.getOrNull()

    /**
     * قراءة ضوء واحدة بمهلة.
     *
     * ثلاث نتائج معلَنة: `ABSENT` (لا مستشعر في هذا الجهاز) · `REPORTED` (وصلت قيمة) ·
     * `UNREADABLE` (المستشعر موجود ولم تصل قيمة — أو رُفض التسجيل).
     */
    suspend fun readLight(context: Context, timeoutMs: Long = LIGHT_TIMEOUT_MS): SensorInventory.LightReading =
        runCatching {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
                ?: return@runCatching SensorInventory.lightReading(
                    sensorAbsent = true,
                    timedOut = false,
                    lux = null,
                )

            // قناة مُدمِجة: آخر قيمة تكفي، ولا نُراكم قراءات لا نقرؤها.
            val channel = Channel<Float>(Channel.CONFLATED)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.values.firstOrNull()?.let { channel.trySend(it) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            // رفض التسجيل ليس فشلًا صامتًا: يعني «لم أستطع القراءة» صراحةً.
            val registered = runCatching {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            }.getOrDefault(false)
            if (!registered) {
                return@runCatching SensorInventory.lightReading(
                    sensorAbsent = false,
                    timedOut = true,
                    lux = null,
                )
            }

            val value = try {
                withTimeoutOrNull(timeoutMs) { channel.receive() }
            } finally {
                channel.close()
                runCatching { manager.unregisterListener(listener) }
            }

            SensorInventory.lightReading(
                sensorAbsent = false,
                timedOut = value == null,
                lux = value,
            )
        }.getOrElse {
            // تعذّر الوصول للخدمة أصلًا ⇒ «لم أقرأ»، لا صفر.
            SensorInventory.lightReading(sensorAbsent = false, timedOut = true, lux = null)
        }

    /** تقرير كامل جاهز للعرض. */
    suspend fun report(context: Context): SensorInventory.Report? =
        inventory(context)?.let { SensorInventory.report(it, readLight(context)) }
}
