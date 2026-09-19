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

/**
 * قواعد قراءة الحرارة — خالصة بلا Android.
 *
 * سبب وجود الملف: العتبات كانت أرقامًا داخل الشاشة (٥٠ ثم ٧٠)، ونقاط التخفيف كانت
 * تُقرأ في `ThermalUtil` ولا تُعرض أصلًا. فنقلها هنا يجعل «هل هذا ساخن؟» و«هل بدأ
 * النظام يخفّض؟» قرارين يُقاسان في JVM عادي، بدل شرط داخل تركيبة واجهة.
 *
 * نصّ صريح: [throttleStart] يعيد `null` حين لا تُقرأ نقاط تخفيف — ولا يعني `null`
 * «لا تخفيف»، بل «لا نعرف». من يعرض `null` كأنه «آمن» يرتكب الخطأ الذي يمنعه ADR-07.
 */
enum class ThermalLevel {
    Nominal,
    Warm,
    Hot
}

/** نقطة قرار واحدة كما تُقرأ من النواة، بلا تبعية على Android. */
data class TripReading(
    val temperatureC: Int,
    val kind: String
)

enum class ThrottleState {
    /** لا نقاط مقروءة: مجهول، لا آمن. */
    Unknown,

    /** قُرئت النقطة والحرارة دونها. */
    Headroom,

    /** بلغت الحرارة نقطة التخفيف أو تجاوزتها. */
    Reached
}

object ThermalModel {

    /** عتبة «دافئ» — نفس قيمة الشاشة السابقة، لم تُغيَّر. */
    const val WARM_C = 50

    /** عتبة «ساخن» — نفس قيمة الشاشة السابقة، لم تُغيَّر. */
    const val HOT_C = 70

    fun levelOf(temperatureC: Int): ThermalLevel = when {
        temperatureC >= HOT_C -> ThermalLevel.Hot
        temperatureC >= WARM_C -> ThermalLevel.Warm
        else -> ThermalLevel.Nominal
    }

    /**
     * أدنى نقطة يبدأ عندها التخفيف السلبي.
     *
     * تُستثنى `critical` و`hot`: هما إنذار إغلاق لا تخفيف أداء. ومن يعرضهما كبداية تخفيف
     * يقول للمستخدم إن معالجه سيُخفَّض عند ٩٥° بينما النظام يغلقه هناك.
     */
    fun throttleStart(trips: List<TripReading>): Int? = trips
        .filter { it.kind.equals("passive", true) || it.kind.equals("active", true) }
        .map { it.temperatureC }
        .filter { it > 0 }
        .minOrNull()

    /** أدنى نقطة إغلاق/حرجة، أو `null` إن لم تُعلن. */
    fun shutdownPoint(trips: List<TripReading>): Int? = trips
        .filter { it.kind.equals("critical", true) || it.kind.equals("hot", true) }
        .map { it.temperatureC }
        .filter { it > 0 }
        .minOrNull()

    fun throttleState(temperatureC: Int, trips: List<TripReading>): ThrottleState {
        val start = throttleStart(trips) ?: return ThrottleState.Unknown
        return if (temperatureC >= start) ThrottleState.Reached else ThrottleState.Headroom
    }

    /** مقدار متبقٍّ قبل التخفيف، أو `null` إن كانت النقطة مجهولة. قابل للسالب: تجاوزها. */
    fun headroomC(temperatureC: Int, trips: List<TripReading>): Int? =
        throttleStart(trips)?.let { it - temperatureC }
}
