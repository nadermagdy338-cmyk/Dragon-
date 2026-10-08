/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * هامش الخنق الحراري للرئيسية: أقرب مسافة من حرارة المعالج والرسوم إلى نقطة التخفيف المعلنة في الجهاز.
 *
 * نقاط التخفيف ثابتة في الجهاز، فتُقرأ مرة لكل منطقة ثم تُخزَّن. والحرارة تأتي من القراءة الجارية
 * نفسها (`ThermalUtil.readThermalZones`) فلا قراءة إضافية في كل نبضة. ولا يُعرض رقم لا أساس له:
 * إن لم تُعلن أيّ منطقة نقطة تخفيف فالنتيجة [NO_THROTTLE_HEADROOM]، وتُكتب شرطة في الواجهة.
 */
package nd.max.ui.util

import nd.max.core.platform.ThermalUtil
import nd.max.core.platform.ThermalZoneInfo
import java.util.concurrent.ConcurrentHashMap

/** لا نقطة تخفيف مقروءة: قيمة لا تُعرض رقمًا أبدًا، ولا صفرًا. */
internal const val NO_THROTTLE_HEADROOM = Int.MIN_VALUE

internal object HomeLiveReadings {
    /** نقاط التخفيف لكل منطقة حرارية، ومفتاحها مسار المنطقة في `sysfs`. */
    private val tripCache = ConcurrentHashMap<String, List<TripReading>>()

    /**
     * أقرب مسافة (°م) إلى نقطة التخفيف بين مناطق المعالج والرسوم المفعّلة والمقروءة، أو
     * [NO_THROTTLE_HEADROOM] إن لم تُعلن أيٌّ منها نقطة تخفيف. وتكون سالبة حين تجاوزتها منطقة.
     */
    internal fun throttleHeadroomOf(zones: List<ThermalZoneInfo>): Int =
        zones.asSequence()
            .filter { it.isEnabled && it.temperatureC > 0 && (it.category == "CPU" || it.category == "GPU") }
            .mapNotNull { zone -> ThermalModel.headroomC(zone.temperatureC, tripsOf(zone.sysfsPath)) }
            .minOrNull() ?: NO_THROTTLE_HEADROOM

    private fun tripsOf(sysfsPath: String): List<TripReading> =
        tripCache.getOrPut(sysfsPath) {
            ThermalUtil.readTripPoints(sysfsPath).map { TripReading(temperatureC = it.temperatureC, kind = it.kind) }
        }
}
