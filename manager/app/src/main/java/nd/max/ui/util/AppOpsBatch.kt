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
 * `AppOps` — **الإجراء الجماعي**: وضع عملية واحدة على مجموعة تطبيقات في خطوة واحدة.
 *
 * **ولماذا هذا الملف خالص بلا Android:** هو المكان الذي يقرّر **كم كتابةً ستُرسل إلى الجهاز،
 * وإلى من**. الكتابة على `AppOps` تمرّ بامتياز، وكل نداءٍ منها قد يُرفض أو يُتجاهَل؛ فقرار
 * «من يُكتب عليه» ليس تفصيلًا واجهيًّا بل السؤال الذي يحدّد ضرر الإجراء. وقاعدةٌ كهذه لا
 * يصحّ أن تُقاس إلا بتشغيل فعلي — ولذلك لا يستورد هذا الملف شيئًا من Android ولا من `org.json`،
 * فيُترجم ويُختبر في JVM عادي بلا جهاز (انظر `AppOpsBatchTest`).
 *
 * وثلاثة قرارات تفصل هذا الإجراء عن «حلقة على قائمة»:
 *
 *  1. **لا كتابة لما هو مكتوب.** تطبيق وضعه الحالي هو الوضع المطلوب **يُتخطّى** لا يُعاد كتابته:
 *     كتابةٌ لا تُغيّر شيئًا تستهلك امتيازًا ووقتًا، وقد يُظهرها الجهاز «فشلًا» لأنها لم تُغيّر
 *     قراءةً. فيُحصى في `already` ويُقال للمستخدم، ولا يُرسَل له أمر.
 *  2. **لا كتابة على عملية لا يملكها التطبيق.** الأوضاع تُقرأ من الجهاز لكل محدَّد، والعملية
 *     التي لا تظهر في قراءة تطبيق **لا تُكتب عليه** (`absent`) — بدل أن تُرسَل أمرًا مصيره
 *     «تعذّر التحقّق» ثم يُقال للمستخدم إن الإجراء «نجح» أو «فشل» وهو لم يكن ممكنًا أصلًا.
 *  3. **فشل واحد لا يُسقط الباقي.** الكتابة تُمرَّر كدالة (`write`) لا كنداء داخلي، فيبقى التتابع
 *     والعدّ والصمود في مكانٍ يمكن قياسه: أي استثناء من الكتابة يُحصى `FAILED` ويُكمَل.
 */
package nd.max.ui.util

object AppOpsBatch {

    /**
     * ما قرأه الجهاز لتطبيق واحد.
     *
     * `modes == null` ليست «بلا أوضاع» بل **«تعذّرت القراءة»** — لا امتياز، أو مخرج لم نُعرَبه.
     * والتمييز مقصود: `mapOf()` تعني «قرأت فلم أجد»، وهذا لا يُقال إلا إن قُرئ فعلًا.
     */
    data class Reading(val pkg: String, val modes: Map<String, PermissionPolicy.OpMode>?)

    /** عملية واحدة، والمحدَّدون الذين **يملكونها فعلًا** — من قراءاتهم لا من افتراضنا. */
    data class Operation(val op: String, val holders: List<String>)

    /**
     * العمليات المتاحة عبر المحدَّدين — **اتحاد** لا تقاطع.
     *
     * التقاطع كان سيُخفي العملية التي يملكها تسعة عشر من عشرين، وهي العملية التي جاء المستخدم
     * من أجلها. والاتحاد يبقيها معروضة مع **عدد مالكيها**، فيُختار الإجراء على حقيقته،
     * والثلاثة الذين لا يملكونها يُسمَّون `absent` في الخطة.
     *
     * والترتيب: الأوسع انتشارًا أولًا، ثم الأبجدي — فالأول في القائمة هو الأكثر معنى للاختيار.
     */
    fun operations(readings: List<Reading>): List<Operation> {
        val holders = LinkedHashMap<String, MutableList<String>>()
        readings.forEach { reading ->
            reading.modes?.keys?.forEach { op ->
                holders.getOrPut(op) { mutableListOf() }.add(reading.pkg)
            }
        }
        return holders
            .map { (op, packages) -> Operation(op = op, holders = packages.toList()) }
            .sortedWith(compareByDescending<Operation> { it.holders.size }.thenBy { it.op })
    }

    /**
     * خطة الكتابة لعملية ووضع على مجموعة محدَّدة.
     *
     * والأربع لا اثنتان: `writes` و`already` هما المحدَّدون الذين يملكون العملية، و`absent`
     * و`unreadable` هما من لا يصلح معه الأمر — والثاني ليس ذنب التطبيق بل ذنب امتيازنا.
     */
    data class Plan(
        val op: String,
        val mode: PermissionPolicy.OpMode,
        val writes: List<String>,
        val already: List<String>,
        val absent: List<String>,
        val unreadable: List<String>,
    ) {
        /** كل من اختير، أيًّا كان مصيره — الرقم الذي يقارنه المستخدم بعدد ما حدّده. */
        val selected: Int get() = writes.size + already.size + absent.size + unreadable.size

        /** من كانت الكتابة عليه ممكنة ومفيدة (مكتوب أو مكتوب عليه أصلًا). */
        val reachable: Int get() = writes.size + already.size

        /** لا شيء يُنفَّذ: لا أن الأمر غير مسموح، بل أن الخطة لا تحمل كتابة واحدة. */
        val idle: Boolean get() = writes.isEmpty()
    }

    fun plan(
        readings: List<Reading>,
        op: String,
        mode: PermissionPolicy.OpMode,
    ): Plan {
        val writes = mutableListOf<String>()
        val already = mutableListOf<String>()
        val absent = mutableListOf<String>()
        val unreadable = mutableListOf<String>()

        readings.forEach { reading ->
            val current = reading.modes?.get(op)
            when {
                reading.modes == null -> unreadable += reading.pkg
                current == null -> absent += reading.pkg
                current == mode -> already += reading.pkg
                else -> writes += reading.pkg
            }
        }

        return Plan(
            op = op,
            mode = mode,
            writes = writes,
            already = already,
            absent = absent,
            unreadable = unreadable,
        )
    }

    /**
     * حصيلة الإجراء الجماعي — **لكل تطبيق حكمه**.
     *
     * ولا رقم واحد يُسمّى «نجاحًا»: `APPLIED_AS_DEFAULT` تغييرٌ حدث بأثر آخر، و`IGNORED_BY_DEVICE`
     * ليس فشلنا، و`UNVERIFIABLE` ليس نجاحًا. جمعها في عدّاد واحد هو بالضبط ما تجنّبه هذه الشاشة
     * في كل موضع آخر، فلا يُفعَل هنا.
     */
    class Tally {
        var applied: Int = 0
            private set
        var asDefault: Int = 0
            private set
        var ignored: Int = 0
            private set
        var failed: Int = 0
            private set
        var unverifiable: Int = 0
            private set

        /** المعرّفات التي قُرئت بعد كتابتها على الوضع المطلوب — أي أن التغيير وقع فعلًا. */
        val changed: MutableList<String> = mutableListOf()

        val changedCount: Int get() = applied + asDefault

        fun record(pkg: String, verdict: PermissionPolicy.WriteVerdict) {
            when (verdict) {
                PermissionPolicy.WriteVerdict.APPLIED -> {
                    applied++
                    changed += pkg
                }

                PermissionPolicy.WriteVerdict.APPLIED_AS_DEFAULT -> {
                    asDefault++
                    changed += pkg
                }

                PermissionPolicy.WriteVerdict.IGNORED_BY_DEVICE -> ignored++
                PermissionPolicy.WriteVerdict.FAILED -> failed++
                PermissionPolicy.WriteVerdict.UNVERIFIABLE -> unverifiable++
            }
        }
    }

    /**
     * ينفّذ الخطة، ويكتب **فقط** على `plan.writes` وبهذا الترتيب.
     *
     * ودالة الكتابة تُمرَّر من الخارج: الشاشة تُمرّر `AppOpsUtil.setOp` (المسار الوحيد للكتابة
     * بحسب `ADR-11`)، والاختبار يُمرّر نتيجة مُصطنَعة. وبذلك يصير «هل توقّف عند أول فشل؟»
     * و«هل كُتب على من قال إنه مكتوب؟» سؤالين قابلين للإجابة بتشغيل، لا بقراءة.
     *
     * و`onStep` اختياري: يُبلَّغ قبل كل كتابة برقمها من المجموع وبمعرّفها، فتُعرض «٣ من ١٨»
     * لا شريط يدور بلا معنى.
     */
    fun apply(
        plan: Plan,
        write: (String) -> PermissionPolicy.WriteVerdict,
        onStep: ((index: Int, pkg: String) -> Unit)? = null,
    ): Tally {
        val tally = Tally()
        plan.writes.forEachIndexed { index, pkg ->
            onStep?.invoke(index, pkg)
            // الكتابة لا تُسقط الإجراء: استثناء من تطبيق واحد كان سيوقف الباقي ويترك مجموعةً
            // نصفَ مكتوبة بلا حصيلة تُشرح.
            val verdict = runCatching { write(pkg) }.getOrDefault(PermissionPolicy.WriteVerdict.FAILED)
            tally.record(pkg, verdict)
        }
        return tally
    }
}
