package nd.max.ui.util

import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiVerdict
import nd.max.core.maxai.SafetyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بطاقة «ما يحدث الآن؟» — القواعد التي تحمي صدقها، لا شكلها.
 *
 * وكل اختبار هنا يسأل سؤالًا واحدًا: **هل يدّعي هذا الإسقاط شيئًا لم يقله المصدر؟**
 * لأن أخطاء هذا الملف لا تظهر كعطب، بل كجملة واثقة كاذبة على الشاشة الرئيسية.
 */
class HomeActivityModelTest {

    private val now = 1_000_000L

    private fun signals(
        aiEnabled: Boolean = false,
        profileRequestInFlight: Boolean = false,
        safetyLevel: SafetyLevel = SafetyLevel.NORMAL,
        safetyEngaged: Boolean = false,
        safetyReason: String? = null,
        verdict: MaxAiVerdict? = null,
        result: DecisionResult? = null,
        detail: String? = null,
        atMs: Long = 0L,
        exploration: Boolean = false,
        appContext: String? = null,
        appSinceMs: Long = 0L,
        missing: Set<ActivityReading> = emptySet(),
    ) = ActivitySignals(
        aiEnabled = aiEnabled,
        profileRequestInFlight = profileRequestInFlight,
        safetyLevel = safetyLevel,
        safetyEngaged = safetyEngaged,
        safetyReason = safetyReason,
        lastVerdict = verdict,
        lastResult = result,
        lastKnobLabel = "policy4/max",
        lastValue = "1800000",
        lastDetail = detail,
        lastAtMs = atMs,
        lastExploration = exploration,
        appContext = appContext,
        appSinceMs = appSinceMs,
        missingReadings = missing,
    )

    private fun project(s: ActivitySignals) = HomeActivityModel.project(s, now)

    // --- ١ الأمان يسبق كل شيء ---

    @Test fun safetyOutranksAFreshSuccessAndAnAppSwitch() {
        val a = project(
            signals(
                safetyEngaged = true,
                safetyReason = "78°C متوقعة بعد 30 ثانية",
                verdict = MaxAiVerdict.IMPROVED,
                atMs = now - 500L,
                appContext = "com.tencent.ig",
                appSinceMs = now - 200L,
            )
        )
        assertEquals(ActivityState.SAFETY, a.state)
        assertEquals(ActivityTone.DANGER, a.tone)
        assertFalse(a.transient)
        assertEquals("78°C متوقعة بعد 30 ثانية", a.detail)
    }

    @Test fun anEngagedLevelAloneIsEnoughEvenWithoutTheFlag() {
        val a = project(signals(safetyLevel = SafetyLevel.ENGAGED, aiEnabled = true))
        assertEquals(ActivityState.SAFETY, a.state)
    }

    @Test fun safetyWithoutAReasonDoesNotInventOne() {
        val a = project(signals(safetyEngaged = true))
        assertEquals(ActivityState.SAFETY, a.state)
        assertEquals(null, a.detail)
    }

    // --- ٢ الجاري الآن لا يُدّعى نجاحه ---

    @Test fun anInFlightProfileRequestIsApplyingNotVerified() {
        val a = project(signals(profileRequestInFlight = true, verdict = MaxAiVerdict.IMPROVED, atMs = now - 100L))
        assertEquals(ActivityState.APPLYING, a.state)
        assertEquals(ActivityTone.WORKING, a.tone)
    }

    // --- ٣ الأحكام: الفرق بين «تحسّن» و«طُبِّق» و«لم تثبت» ---

    @Test fun aMeasuredImprovementIsTheOnlyImprovement() {
        val a = project(signals(verdict = MaxAiVerdict.IMPROVED, atMs = now - 1_000L))
        assertEquals(ActivityState.VERIFIED, a.state)
        assertTrue(a.measured)
        assertTrue(a.transient)
        assertEquals(now - 1_000L + HomeActivityModel.EVENT_TTL_MS, a.expiresAtMs)
    }

    @Test fun aVerifiedWriteWithoutAMeasuredEffectIsNotCalledAnImprovement() {
        val a = project(signals(verdict = MaxAiVerdict.UNMEASURED, atMs = now - 1_000L))
        assertEquals(ActivityState.VERIFIED, a.state)
        assertFalse(a.measured)
    }

    @Test fun aWriteThatNeverHeldIsRefused() {
        val a = project(signals(verdict = MaxAiVerdict.WRITE_FAILED, atMs = now - 1_000L))
        assertEquals(ActivityState.REFUSED, a.state)
        assertEquals(ActivityTone.ATTENTION, a.tone)
    }

    @Test fun noCandidateIsACalmNoActionNotAFailure() {
        val a = project(signals(verdict = MaxAiVerdict.NO_ACTION, atMs = now - 1_000L))
        assertEquals(ActivityState.REFUSED, a.state)
        assertEquals(ActivityTone.CALM, a.tone)
    }

    @Test fun aRollbackThatProvedItselfIsCalmAndOneThatDidNotIsAttention() {
        assertEquals(
            ActivityTone.CALM,
            project(signals(verdict = MaxAiVerdict.REGRESSED_ROLLED_BACK, atMs = now - 1_000L)).tone,
        )
        assertEquals(
            ActivityTone.ATTENTION,
            project(signals(verdict = MaxAiVerdict.REGRESSED_STUCK, atMs = now - 1_000L)).tone,
        )
        assertEquals(
            ActivityState.ROLLED_BACK,
            project(signals(verdict = MaxAiVerdict.REGRESSED_STUCK, atMs = now - 1_000L)).state,
        )
    }

    @Test fun anExplorationProbeIsNotPresentedAsAWantedImprovement() {
        val a = project(signals(verdict = MaxAiVerdict.IMPROVED, atMs = now - 1_000L, exploration = true))
        assertTrue(a.exploration)
        assertEquals(ActivityTone.CALM, a.tone)
        assertTrue(a.measured)
    }

    @Test fun theEngineDetailIsCarriedVerbatim() {
        val a = project(
            signals(
                verdict = MaxAiVerdict.WRITE_FAILED,
                detail = "live value differs: wrote 1800000 read 1400000",
                atMs = now - 1_000L,
            )
        )
        assertEquals("live value differs: wrote 1800000 read 1400000", a.detail)
    }

    // --- ٣ب ملخّص القرار حين لا حلقة مسجّلة (طلب ملف يدوي) ---

    @Test fun aManualProfileRequestShowsTheEngineSummaryInsteadOfDroppingTheEvent() {
        val a = project(signals(result = DecisionResult.EXECUTED, atMs = now - 500L))
        assertEquals(ActivityState.VERIFIED, a.state)
        assertFalse(a.measured)
        assertTrue(a.transient)
    }

    @Test fun aBlockedManualRequestIsASafetyInterventionNotASilentSkip() {
        val a = project(signals(result = DecisionResult.BLOCKED_FOR_SAFETY, atMs = now - 500L))
        assertEquals(ActivityState.SAFETY, a.state)
        assertEquals(ActivityTone.ATTENTION, a.tone)
    }

    @Test fun aFailedRequestWithNoEpisodeIsRefused() {
        val a = project(signals(result = DecisionResult.FAILED, atMs = now - 500L))
        assertEquals(ActivityState.REFUSED, a.state)
        assertEquals(ActivityTone.ATTENTION, a.tone)
    }

    @Test fun aRecordWithoutAnEpisodeStillExpiresBackToTheBaseState() {
        val a = project(
            signals(aiEnabled = true, result = DecisionResult.EXECUTED, atMs = now - HomeActivityModel.EVENT_TTL_MS)
        )
        assertEquals(ActivityState.MONITORING, a.state)
    }

    // --- ٤ العبور: الحدث ينتهي فتعود الحالة ---

    @Test fun anEventPastItsWindowFallsBackToTheBaseState() {
        val a = project(signals(aiEnabled = true, verdict = MaxAiVerdict.IMPROVED, atMs = now - HomeActivityModel.EVENT_TTL_MS))
        assertEquals(ActivityState.MONITORING, a.state)
        assertFalse(a.transient)
    }

    @Test fun oneMillisecondInsideTheWindowIsStillTheEvent() {
        val a = project(
            signals(aiEnabled = true, verdict = MaxAiVerdict.IMPROVED, atMs = now - HomeActivityModel.EVENT_TTL_MS + 1)
        )
        assertEquals(ActivityState.VERIFIED, a.state)
    }

    // --- ٥ التطبيق في المقدمة ---

    @Test fun anAppOpenedAfterTheDecisionOutranksTheDecision() {
        val a = project(
            signals(
                aiEnabled = true,
                verdict = MaxAiVerdict.IMPROVED,
                atMs = now - 5_000L,
                appContext = "com.tencent.ig",
                appSinceMs = now - 1_000L,
            )
        )
        assertEquals(ActivityState.APP_SWITCH, a.state)
        assertEquals("com.tencent.ig", a.appPackage)
    }

    @Test fun aDecisionMadeAfterTheAppSwitchOutranksTheSwitch() {
        val a = project(
            signals(
                verdict = MaxAiVerdict.IMPROVED,
                atMs = now - 1_000L,
                appContext = "com.tencent.ig",
                appSinceMs = now - 6_000L,
            )
        )
        assertEquals(ActivityState.VERIFIED, a.state)
    }

    @Test fun anAppSwitchWithNoKnownTimeIsNeverShown() {
        val a = project(signals(aiEnabled = true, appContext = "com.tencent.ig", appSinceMs = 0L))
        assertEquals(ActivityState.MONITORING, a.state)
    }

    // --- ٦ نقص القدرة يقال صريحًا ---

    @Test fun missingLiveReadingsAreNamedInsteadOfFakedWithZeros() {
        val a = project(signals(aiEnabled = true, missing = setOf(ActivityReading.GPU, ActivityReading.THERMAL)))
        assertEquals(ActivityState.UNSUPPORTED, a.state)
        assertEquals(setOf(ActivityReading.GPU, ActivityReading.THERMAL), a.missingReadings)
    }

    @Test fun anEventStillOutranksAnUnsupportedReading() {
        val a = project(
            signals(
                verdict = MaxAiVerdict.IMPROVED,
                atMs = now - 500L,
                missing = setOf(ActivityReading.GPU),
            )
        )
        assertEquals(ActivityState.VERIFIED, a.state)
    }

    // --- ٧ الحالة الأساسية مفيدة في الحالين ---

    @Test fun withTheEngineOnTheBaseStateIsMonitoringAndWithItOffItIsIdle() {
        assertEquals(ActivityState.MONITORING, project(signals(aiEnabled = true)).state)
        assertEquals(ActivityState.IDLE, project(signals()).state)
        assertEquals(ActivityTone.CALM, project(signals(aiEnabled = true)).tone)
    }

    @Test fun aVerdictWithoutATimestampIsNeverShownAsFresh() {
        val a = project(signals(aiEnabled = true, verdict = MaxAiVerdict.IMPROVED, atMs = 0L))
        assertEquals(ActivityState.MONITORING, a.state)
    }
}
