/*
 * Copyright (C) 2026-2027 MaxManager contributors
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

package nd.max.core.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.PowerManager
import com.topjohnwu.superuser.io.SuFile
import nd.max.core.jni.PredictorBridge
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.ThermalUtil

/**
 * حالة الجهاز الطبيعية (normalized) للمحرك الذكي — نفس الإحداثيات
 * السبع التي يتوقعها وكيل التعلم المعزز في Rust (DeviceState) والتوأم
 * الرقمي، كلها في النطاق [0, 1] ومن مصادر قياس حقيقية:
 *
 *  - cpuLoad      : دلتا /proc/stat عبر FpsMonitorUtil.getCpuLoad()
 *  - thermal      : أعلى حرارة CPU/GPU من مناطق thermal الحقيقية (°C/100)
 *  - battery      : نسبة البطارية من بث ACTION_BATTERY_CHANGED
 *  - appIntent    : لعبة مكتشفة من ملف الوحدة gameinfo = 1، شاشة مطفأة = 0، استخدام عادي = 0.5
 *  - screenOn     : PowerManager.isInteractive
 *  - memoryUsage  : الذاكرة المستخدمة / الكلية
 *  - networkSpeed : معدل بايتات الشبكة الفعلي منذ آخر قراءة (مطبَّع عند 2MB/s)
 *
 * مصدر واحد مشترك لحلقة التعلم ومحرك التوصيات — حتى لا يتكرر منطق
 * القياس في مكانين فينفرقان (قاعدة المشروع: "متكررش منطق موجود").
 */
object DeviceStateCollector {

    data class DeviceSnapshot(
        val cpuLoad: Float,
        val thermal: Float,
        val battery: Float,
        val appIntent: Float,
        val screenOn: Float,
        val memoryUsage: Float,
        val networkSpeed: Float
    )

    /** السرعة التي يُطبَّع عندها معدل الشبكة إلى 1.0 (2 ميجابايت/ثانية). */
    private const val NETWORK_NORM_BYTES_PER_SEC = 2_000_000f

    private const val GAMEINFO_PATH = "/data/data/nd.max/API/gameinfo"

    // ── حالة دلتا الشبكة (تُحدَّث مع كل قراءة) ────────────────────────
    @Volatile private var lastRxTxBytes = -1L
    @Volatile private var lastTimestampMs = -1L

    /** يقرأ الحالة الكاملة من العتاد. آمن للاستدعاء من أي خيط. */
    fun collect(context: Context): DeviceSnapshot {
        // حمل CPU الفعلي (دلتا بين قراءتين لـ /proc/stat)
        val cpuLoad = FpsMonitorUtil.getCpuLoad().coerceIn(0, 100) / 100f

        // أعلى حرارة CPU/GPU من مناطق thermal الحقيقية
        val thermal = runCatching {
            ThermalUtil.readThermalZones()
                .filter { it.category in setOf("CPU", "GPU") && it.temperatureC > 0 }
                .maxOfOrNull { it.temperatureC }?.toFloat() ?: 0f
        }.getOrDefault(0f) / 100f

        // البطارية من بث النظام
        val battery = readBatteryFraction(context)

        // حالة الشاشة الحقيقية
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = powerManager?.isInteractive ?: true
        val screenOn = if (interactive) 1f else 0f

        // نية الاستخدام: لعبة من ملف الوحدة، وإلا استخدام عادي/خمول
        val appIntent = when {
            isGameDetected() -> 1f
            !interactive -> 0f
            else -> 0.5f
        }

        // الذاكرة المستخدمة فعليًا
        val ram = FpsMonitorUtil.getRamInfo(context)
        val memoryUsage = if (ram.totalMb > 0) {
            (ram.usedMb.toFloat() / ram.totalMb).coerceIn(0f, 1f)
        } else 0f

        // معدل الشبكة الفعلي منذ آخر قراءة (بالزمن الحقيقي المنقضي)
        val networkSpeed = readNetworkSpeedNormalized()

        // تغذية مُتنبئ الطاقة الأصلي بالقياسات (بنسبة مئوية) — التنبؤ
        // الحراري الأمامي يعمل فوق هذا التاريخ الحقيقي ويستهلكه محرك
        // الأمان في كل دورة
        runCatching {
            PredictorBridge.updatePowerPredictor(
                cpuLoad = cpuLoad * 100f,
                thermal = thermal * 100f,
                battery = battery * 100f
            )
        }

        return DeviceSnapshot(
            cpuLoad = cpuLoad,
            thermal = thermal,
            battery = battery,
            appIntent = appIntent,
            screenOn = screenOn,
            memoryUsage = memoryUsage,
            networkSpeed = networkSpeed
        )
    }

    private fun readBatteryFraction(context: Context): Float = runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (scale > 0) (level.toFloat() / scale).coerceIn(0f, 1f) else 0f
    }.getOrDefault(0f)

    /**
     * معدل الشبكة الحقيقي بالبايت/ثانية مطبَّعًا إلى [0, 1].
     *
     * @Synchronized لأن عدة حللات (التعلم المعزز، محرك التوصيات، حاكم
     * الطاقة) تتقاسم نفس عدادات الدلتا — بدون القفل تتنافس
     * قراءة-تعديل-كتابة فتُحتسب فترات متراكبة أو تُفقد.
     */
    @Synchronized
    private fun readNetworkSpeedNormalized(): Float = runCatching {
        val now = System.currentTimeMillis()
        val rxTx = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

        val prevBytes = lastRxTxBytes
        val prevTime = lastTimestampMs
        // حدّث الحالة دائمًا كي تكون القراءة القادمة دلتا عن هذه
        lastRxTxBytes = rxTx
        lastTimestampMs = now

        // أول قراءة أو عدّاد النظام التفّ (إعادة تشغيل): لا معدل بعد
        if (prevBytes < 0L || prevTime < 0L || rxTx < prevBytes) return@runCatching 0f
        val elapsedSec = (now - prevTime) / 1000f
        if (elapsedSec <= 0f) return@runCatching 0f

        val speed = (rxTx - prevBytes) / elapsedSec
        (speed / NETWORK_NORM_BYTES_PER_SEC).coerceIn(0f, 1f)
    }.getOrDefault(0f)

    /** يقرأ ملف الوحدة gameinfo — أول حقل اسم الحزمة أو NULL. */
    private fun isGameDetected(): Boolean = runCatching {
        val file = SuFile(GAMEINFO_PATH)
        if (!file.exists()) return@runCatching false
        val firstLine = file.newInputStream().bufferedReader().use { it.readLine() }
            ?: return@runCatching false
        val pkg = firstLine.split(" ").firstOrNull()
        pkg != null && pkg != "NULL" && pkg.isNotBlank()
    }.getOrDefault(false)
}
