/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قفل فهرس OPP على MediaTek: **التثبيت** و**التحرير** — ولا شيء غيرهما.
 *
 * وفُصل هذا المسار في ملفه لأنه أخطر ما في طبقة GPU وأصعب ما يُكتشف خطؤه:
 *
 * - التثبيت يكتب فهرسًا واحدًا في `fix_target_opp_index`، والتردد الذي يجري عليه الجهاز بعدها
 *   يحدّده **جدول النواة** لا فهرستنا. فخطأ في الفهرسة لا يُخطئ الهدف فقط، بل يُعلن نجاحًا لأن
 *   التحقّق من صدى الفهرس لا من التردد (انظر `GpuCeilingPolicy.PinVerdict` الذي يقيس التردد).
 * - والتثبيت يوقف DVFS، فبقاؤه بلا تحرير يجمّد التردد على درجة واحدة: «عالق على ٦٥٠» — وهو أثر
 *   لا يظهر في أي شاشة ولا في أي قراءة تردد.
 *
 * وما لا يفعله هذا الملف: لا يقرّر إن كان الطلب سقفًا أم تثبيتًا (ذلك `GpuCeilingPolicy`)، ولا
 * يقرأ جدول OPP (ذلك `MtkGpuOppTable`)، ولا يختار الجهاز (ذلك `GpuHardwareBackend`).
 */
package nd.max.core.hardware

internal object MtkGpuFixedIndex {

    /**
     * يُعيد التوسّع الديناميكي بعد تحرير قفل OPP — والتثبيت هو ما يوقف DVFS، فزواله يُعيده.
     *
     * و`release = false`: هذه ليست إعادة سقف المصنّع بل إعادة تشغيل الحاكم. ولو مرّرنا `true` لصار
     * كل خروج من تثبيت يرفع حماية حراريّة وَضعها المصنّع بلا سبب يخصّها.
     */
    fun restoreDynamicScaling(io: GpuHardwareBackend.Io) {
        io.permitVendorCeiling(lock = false, release = false)
    }

    /** يحرّر قفل الفهرس الثابت (يكتب `-1`)، ويُرجع خط الأساس عند الفشل. */
    fun release(live: GpuHardwareBackend.Device, request: GpuHardwareBackend.Request, io: GpuHardwareBackend.Io): GpuHardwareBackend.TransactionResult {
        val path = live.mtkFixedIndexPath
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "fixed-lock-unavailable")
        val baselineIndex = io.read(path)?.let(MtkGpuOppTable::parseIndex)
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "baseline-unreadable")
        if (baselineIndex == "-1") {
            restoreDynamicScaling(io)
            return GpuHardwareBackend.TransactionResult(request, GpuHardwareBackend.refresh(live.path, io), true, true)
        }
        val wrote = io.write(path, "-1")
        val verified = wrote && io.read(path)?.let(MtkGpuOppTable::parseIndex) == "-1"
        if (verified) {
            restoreDynamicScaling(io)
            return GpuHardwareBackend.TransactionResult(request, GpuHardwareBackend.refresh(live.path, io), true, true)
        }
        val rollbackWrote = io.write(path, baselineIndex)
        val rollbackVerified = rollbackWrote && io.read(path)?.let(MtkGpuOppTable::parseIndex) == baselineIndex
        return GpuHardwareBackend.TransactionResult(
            request, GpuHardwareBackend.refresh(live.path, io), wrote, false, true, rollbackVerified,
            if (rollbackVerified) "apply-not-verified-baseline-restored" else "apply-and-rollback-failed",
        )
    }

    /**
     * يثبّت درجة واحدة بفهرسها — التثبيت يعني «ثبّت هذه القيمة».
     *
     * وفيه يوقف DVFS ويُرفع سقف المصنّع **إن طلبه المنادي** (`releaseVendorCeiling`): تثبيت درجة
     * أعلى من قمع المصنّع لا يُثبّت شيئًا بلا هذا الرفع. ومن كتب سقفًا أدنى من قدرة الجهاز (بروفايل
     * تبريد: `release = false`) لا يُلمس سقف المصنّع ولا يوقف DVFS — يكفيه أن الفهرس يقصّ.
     */
    fun pin(live: GpuHardwareBackend.Device, request: GpuHardwareBackend.Request, io: GpuHardwareBackend.Io): GpuHardwareBackend.TransactionResult {
        val path = live.mtkFixedIndexPath
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "fixed-lock-unavailable")
        val frequency = request.minFreq
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "incomplete-range")
        val targetIndex = live.mtkOppIndexByFrequency[frequency]
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "unsupported-frequency")
        val baselineIndex = io.read(path)?.let(MtkGpuOppTable::parseIndex)
            ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "baseline-unreadable")
        if (request.releaseVendorCeiling) {
            io.permitVendorCeiling(lock = true, release = true)
        }
        val touchesGovernor = request.governor != null
        val baselineGovernor = if (touchesGovernor) {
            live.governor
                ?: return GpuHardwareBackend.TransactionResult(request, live, false, false, error = "baseline-unreadable")
        } else null
        val wroteLock = io.write(path, targetIndex)
        val wroteGovernor = !touchesGovernor || (wroteLock && io.write("${live.path}/governor", request.governor))
        val actual = GpuHardwareBackend.refresh(live.path, io)
        val verified = wroteLock && wroteGovernor &&
            io.read(path)?.let(MtkGpuOppTable::parseIndex) == MtkGpuOppTable.parseIndex(targetIndex) &&
            (request.governor == null || actual?.governor.equals(request.governor, true))
        if (verified) return GpuHardwareBackend.TransactionResult(request, actual, true, true)
        val rollbackLock = io.write(path, baselineIndex) && io.read(path)?.let(MtkGpuOppTable::parseIndex) == baselineIndex
        val rollbackGovernor = baselineGovernor == null ||
            (io.write("${live.path}/governor", baselineGovernor) &&
                GpuHardwareBackend.refresh(live.path, io)?.governor == baselineGovernor)
        val rollbackVerified = rollbackLock && rollbackGovernor
        // وإعادة التوسّع الديناميكي **جزء من التراجع** لا زينة بعده.
        //
        // فالتثبيت أوقف DVFS (إن كان قد طلب رفع سقف المصنّع)، وتراجعٌ يُعيد الفهرس ويُبقي DVFS
        // موقوفًا لا يُعيد شيئًا: الجهاز يبقى على الدرجة التي صادف وجودها وقت الطلب — أي يُنتج
        // العطب الذي جاء التراجع لإلغائه. و`applyDevfreq` يفعل هذا في تراجعه، فيتبع مسار القفل
        // القاعدة نفسها بدل أن يكون استثناءً لها.
        restoreDynamicScaling(io)
        return GpuHardwareBackend.TransactionResult(
            request, GpuHardwareBackend.refresh(live.path, io), wroteLock && wroteGovernor, false, true, rollbackVerified,
            if (rollbackVerified) "apply-not-verified-baseline-restored" else "apply-and-rollback-failed",
        )
    }
}
