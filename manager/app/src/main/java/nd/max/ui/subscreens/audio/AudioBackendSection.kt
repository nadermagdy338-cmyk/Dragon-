/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **قسم المحرّك والسلّم** (تكملة ٢٣٠): «ما الذي يقود صوتك؟» ثمّ «ما الذي نعرفه عن
 * Dolby على هذا الجهاز؟».
 *
 * **وثلاثة قواعد تحكم هذا الملفّ كلّه:**
 *
 * 1. **لا حالة تُصنع هنا.** السلّم يُبنى في `audioBackendLadder` (صافٍ، مُقاس على JVM)، والاختيار في
 *    `audioBackendSelection`، والحكم في `vendorAudioVerdict`. هذه الشاشة ترسم ما وصلها وتُترجم رموزه
 *    — ولا تحكم (ADR-11 · قاعدة مصفوفة القدرات نفسها).
 * 2. **الحالة تُقال بسببها.** كل رِفادة تُعرض بوسمها **و** بجملة سببها: «تحتاج محوّلًا» بلا سبب لا
 *    تُخبر المستخدم بشيء، و«موجودة ولا مسار» بلا سبب تُوهمه أنّنا أهملناها.
 * 3. **لا مقبض قبل جلسة نملكها.** مقابض مؤثّر المصنّع لا تظهر إلّا بعد فتح جلسة، لأنّ الكتابة قبلها
 *    مستحيلة — وزرٌّ يعرف أنّه سيفشل أسوأ من زرٍّ غائب.
 *
 * **وما لا يُحرَّر هنا بقصد — ويُسجَّل:** معادل الـDAP ذو العشرين نطاقًا **يُقرأ ويُعرض ولا يُكتب**
 * بعد، لأنّه يحتاج محرّر منحنى على عشرين نطاقًا (والمنحنى المنصّيّ بُني في `MaxCurvePlot`). ويُعرض
 * كقيمةٍ مقروءة بدل أن يُوعد بمقبضٍ غير مبنيّ.
 */
package nd.max.ui.subscreens.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.audio.AudioBackendAvailability
import nd.max.core.audio.AudioBackendCandidate
import nd.max.core.audio.AudioBackendId
import nd.max.core.audio.AudioBackendRole
import nd.max.core.audio.AudioBackendSelection
import nd.max.core.audio.AudioKnobVerdict
import nd.max.core.audio.AudioWriteOutcome
import nd.max.core.audio.DolbyDapParam
import nd.max.core.audio.VendorAccessRoute
import nd.max.core.audio.VendorAttachProbe
import nd.max.core.audio.VendorAudioPresence
import nd.max.core.audio.VendorAudioSnapshot
import nd.max.core.audio.VendorAudioVerdict
import nd.max.core.audio.VendorDiscoverySource
import nd.max.core.audio.audioBackendRoleOf
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone

// ────────────────────────────────── السلّم: أيّ محرّك يقود؟ ──────────────────────────────────

/**
 * السلّم كما قِيس، والمحرّك المختار، وبدائله — ثمّ سطرٌ يقول ما نعرفه عن Dolby.
 *
 * @param ladder `null` = **لم يُقس** ⇒ «قيد القياس» ولا تُعرض قائمة فارغة (ADR-07).
 * @param onRemeasure إعادة القياس — **تُترك لقرار المستخدم** لأنّ فيها محاولة إرفاقٍ حقيقيّة.
 */
@Composable
internal fun AudioBackendLadderSection(
    vendor: VendorAudioSnapshot?,
    probe: VendorAttachProbe?,
    ladder: List<AudioBackendCandidate>?,
    backend: AudioBackendSelection?,
    onRemeasure: () -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_backend_title),
        description = stringResource(R.string.max_audio_backend_description),
        collapsible = true,
    ) {
        if (ladder == null || backend == null) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_backend_title),
                    subtitle = stringResource(R.string.max_audio_backend_reading),
                    icon = Icons.Rounded.Memory,
                )
            }
            return@MaxSection
        }

        val primary = backend.primary?.let { id -> ladder.firstOrNull { it.id == id } }
        // والأسماء تُحلّ **قبل** بناء الجملة: `joinToString` ليست `inline`، فلا يصحّ نداء `stringResource`
        // داخل لامداها — و`map` عليه هي `inline` فيصحّ. (عطبٌ لا يمسكه إلّا المُصرّف، وقد أمسكه.)
        val fallbackNames = backend.fallbacks.map { stringResource(backendTitle(it)) }.joinToString("  ·  ")
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_backend_primary),
                subtitle = listOfNotNull(
                    primary?.let { engineReasonText(it.reason) },
                    primary?.detail?.takeIf { it.isNotBlank() },
                    fallbackNames.takeIf { it.isNotEmpty() }
                        ?.let { names -> stringResource(R.string.max_audio_backend_fallbacks, names) },
                ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_backend_none) },
                icon = Icons.Rounded.Bolt,
                iconTone = if (primary != null) MaxTone.Positive else MaxTone.Inactive,
                trailing = {
                    if (primary != null) {
                        MaxCapsule(
                            text = stringResource(backendStateText(primary.availability)),
                            tone = backendStateTone(primary.availability),
                        )
                    }
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_backend_remeasure),
                subtitle = stringResource(R.string.max_audio_backend_remeasure_note),
                icon = Icons.Rounded.Tune,
                onClick = onRemeasure,
            )
        }

        MaxGroup {
            ladder.forEachIndexed { index, candidate ->
                if (index > 0) MaxGroupDivider()
                BackendRow(candidate)
            }
        }

        // وسطر Dolby في التبويب نفسه: من يقرأ السلّم يجب أن يعرف فورًا ما جرى لمؤثّر المصنّع — فلا
        // يكفي أن يكون له رِفادةٌ في القائمة بلا تفصيل المعرّف والمسار.
        VendorSummaryGroup(vendor, probe)
    }
}

/** رِفادة واحدة: اسم المحرّك، ووسم حالته، وجملة سببها. */
@Composable
private fun BackendRow(candidate: AudioBackendCandidate) {
    MaxRow(
        title = stringResource(backendTitle(candidate.id)),
        subtitle = listOfNotNull(
            engineReasonText(candidate.reason),
            candidate.detail?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  "),
        icon = backendIcon(candidate.id),
        iconTone = backendStateTone(candidate.availability),
        trailing = {
            MaxCapsule(
                text = stringResource(backendStateText(candidate.availability)),
                tone = backendStateTone(candidate.availability),
            )
        },
    )
}

/** سطر Dolby المُوجز: الحضور، ثمّ المعرّف، ثمّ المسار المُجرَّب. */
@Composable
private fun VendorSummaryGroup(vendor: VendorAudioSnapshot?, probe: VendorAttachProbe?) {
    val verdict = vendor?.verdict
    MaxGroup {
        MaxRow(
            title = stringResource(R.string.max_audio_vendor_title),
            subtitle = listOfNotNull(
                verdict?.let { engineReasonText(it.reason) },
                verdict?.let { it.detail ?: it.effectUuid },
                verdict?.source?.let { source -> stringResource(vendorSourceText(source)) },
                probe?.evidence?.route?.let { route -> stringResource(vendorRouteText(route)) },
            ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_vendor_not_measured) },
            icon = Icons.Rounded.Waves,
            iconTone = vendorPresenceTone(verdict?.presence),
            trailing = {
                MaxCapsule(
                    text = stringResource(vendorPresenceText(verdict?.presence)),
                    tone = vendorPresenceTone(verdict?.presence),
                )
            },
        )
    }
}

// ──────────────────── من يقود الآن: سطر يربط الاختيار بالمقابض التي تحته ────────────────────

/**
 * **سطر «من يقود»** في تبويب المحرّك — والمقابض كلها تحته.
 *
 * **ولماذا هذا ليس تزيينًا:** كان الاختيار يُقاس ويُعرض في تبويب `SYSTEM` وحده، بينما المقابض في
 * `ENGINE` تعمل بلا علاقةٍ به — فيمكن أن يقود المنصّة ويُظنّ أنّ مؤثّر المصنّع يقود، أو العكس. وهذا
 * يربط **الحكم** بالمكان الذي يُكتب فيه، من قياسٍ واحد (`state.backend`) لا من حسابٍ ثانٍ هنا.
 */
@Composable
internal fun AudioEngineLeadGroup(
    backend: AudioBackendSelection?,
    ladder: List<AudioBackendCandidate>?,
) {
    val primary = backend?.primary?.let { id -> ladder?.firstOrNull { it.id == id } }
    MaxGroup {
        MaxRow(
            title = stringResource(R.string.max_audio_backend_lead),
            subtitle = listOfNotNull(
                primary?.let { stringResource(backendTitle(it.id)) },
                primary?.let { engineReasonText(it.reason) },
                primary?.detail?.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_backend_none) },
            icon = Icons.Rounded.Bolt,
            iconTone = if (primary != null) MaxTone.Positive else MaxTone.Inactive,
        )
    }
}

// ───────────────────────────── التحكّم: مقابض مؤثّر المصنّع ─────────────────────────────

/**
 * مقابض مؤثّر المصنّع — **ولا تظهر إلّا بجلسةٍ مفتوحة**.
 *
 * @param values المعاملات المقروءة — والقيمة التي لم تُقرأ تُعرض «—» ويُعطَّل مفتاحها بسببها.
 * @param onValue كتابة معامل بقيمةٍ واحدة — والقيم بطولها المُعلَن تُبنى في الطبقة الصافية.
 * @param role **دور مؤثّر المصنّع في الاختيار الجاري** — فتُقال موضعَه بلا كلام: أهو يقود؟ أم بديل؟
 *   أم في السلّم ولا يقود. وهذا ما يمنع أن يُقرأ القسم «عاملًا» بجانب محرّكٍ آخر يقود فعلًا.
 */
@Composable
internal fun VendorEffectSection(
    verdict: VendorAudioVerdict?,
    probe: VendorAttachProbe?,
    role: AudioBackendRole,
    open: Boolean,
    dapEnabled: Int?,
    effectEnabled: Boolean?,
    profile: Int?,
    values: Map<DolbyDapParam, IntArray>,
    knob: AudioKnobVerdict?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDapEnabled: (Boolean) -> Unit,
    onEffectEnabled: (Boolean) -> Unit,
    onProfile: (Int) -> Unit,
    onValue: (DolbyDapParam, IntArray) -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_vendor_controls_title),
        description = stringResource(R.string.max_audio_vendor_controls_description),
        collapsible = true,
    ) {
        // بلا اكتشاف: يُقال السبب ولا يُبنى قسمٌ فارغ (القاعدة نفسها في كل المستودع).
        if (verdict == null) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_vendor_title),
                    subtitle = stringResource(R.string.max_audio_vendor_not_measured),
                    icon = Icons.Rounded.Waves,
                    iconTone = MaxTone.Caution,
                )
            }
            return@MaxSection
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_vendor_title),
                subtitle = listOfNotNull(
                    engineReasonText(verdict.reason),
                    verdict.effectUuid,
                    verdict.source?.let { source -> stringResource(vendorSourceText(source)) },
                    probe?.evidence?.route?.let { route -> stringResource(vendorRouteText(route)) },
                ).joinToString("  ·  "),
                icon = Icons.Rounded.Waves,
                iconTone = vendorPresenceTone(verdict.presence),
                trailing = {
                    MaxCapsule(
                        text = stringResource(vendorPresenceText(verdict.presence)),
                        tone = vendorPresenceTone(verdict.presence),
                    )
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_backend_role),
                subtitle = stringResource(vendorRoleText(role)),
                icon = Icons.Rounded.Bolt,
                iconTone = when (role) {
                    AudioBackendRole.PRIMARY -> MaxTone.Positive
                    AudioBackendRole.FALLBACK -> MaxTone.Caution
                    AudioBackendRole.OTHER -> MaxTone.Inactive
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = if (open) {
                    stringResource(R.string.max_audio_vendor_close)
                } else {
                    stringResource(R.string.max_audio_vendor_open)
                },
                subtitle = if (open) {
                    stringResource(R.string.max_audio_vendor_close_note)
                } else {
                    stringResource(R.string.max_audio_vendor_open_note)
                },
                icon = Icons.Rounded.LockOpen,
                iconTone = if (open) MaxTone.Positive else MaxTone.Neutral,
                // وبلا مسارٍ مقيس لا يُعرض زرّ فتح: الفتح محاولةُ إرفاق، ولا وعدَ بما لم يُقس.
                // وبلا مسارٍ مقيس لا يُعرض زرّ فتح — وسببُ المنع في سطر الوصف نفسه، لأنّ `MaxRow`
                // لا يحمل حقل «سبب القفل» (بخلاف `MaxSwitchRow`/`MaxSliderRow`).
                enabled = open || verdict.isControllable,
                onClick = { if (open) onClose() else onOpen() },
            )
        }

        if (knob != null) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_vendor_last_write),
                    subtitle = listOfNotNull(
                        knob.expected?.let { stringResource(R.string.max_audio_vendor_write_expected, it) },
                        knob.actual?.let { stringResource(R.string.max_audio_vendor_write_actual, it) },
                        engineReasonText(knob.reason),
                    ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_vendor_write_ok) },
                    icon = Icons.Rounded.Tune,
                    iconTone = when {
                        knob.isApplied -> MaxTone.Positive
                        knob.outcome == AudioWriteOutcome.BLOCKED -> MaxTone.Caution
                        else -> MaxTone.Inactive
                    },
                )
            }
        }

        // وما دون هذا لا يُعرض بلا جلسة: كلّه كتابة.
        if (!open) return@MaxSection

        MaxGroup {
            MaxSwitchRow(
                title = stringResource(R.string.max_audio_vendor_dap_enabled),
                subtitle = stringResource(R.string.max_audio_vendor_dap_enabled_note),
                checked = dapEnabled == 1,
                enabled = dapEnabled != null,
                lockedReason = if (dapEnabled == null) stringResource(R.string.max_audio_vendor_unread) else null,
                onCheckedChange = { onDapEnabled(it) },
                icon = Icons.Rounded.Bolt,
            )
            MaxGroupDivider()
            MaxSwitchRow(
                title = stringResource(R.string.max_audio_vendor_effect_enabled),
                subtitle = stringResource(R.string.max_audio_vendor_effect_enabled_note),
                checked = effectEnabled == true,
                enabled = effectEnabled != null,
                lockedReason = if (effectEnabled == null) stringResource(R.string.max_audio_vendor_unread) else null,
                onCheckedChange = { onEffectEnabled(it) },
                icon = Icons.Rounded.Memory,
            )
            MaxGroupDivider()
            ProfileRow(profile, onProfile)
        }

        // ومعاملات المعالج المُعلَنة: **تُبنى من المفردة الصافية**، فمعاملٌ يُضاف هناك يظهر هنا.
        MaxGroup {
            DolbyDapParam.entries.forEachIndexed { index, param ->
                if (index > 0) MaxGroupDivider()
                DapParamRow(
                    param = param,
                    value = values[param],
                    onWrite = { written -> onValue(param, written) },
                )
            }
        }
    }
}

/** صفّ الملفّ الشخصيّ: يُقرأ، ويُكتب بإدخالٍ صريح — **ولا نطاق يُخترع** لأنّ المنصّة لا تُعلنه. */
@Composable
private fun ProfileRow(profile: Int?, onProfile: (Int) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(profile?.toString().orEmpty()) }

    MaxRow(
        title = stringResource(R.string.max_audio_vendor_profile),
        subtitle = listOfNotNull(
            stringResource(R.string.max_audio_vendor_profile_value, profile?.toString() ?: MAX_VALUE_UNAVAILABLE),
            stringResource(R.string.max_audio_vendor_profile_note),
        ).joinToString("  ·  "),
        icon = Icons.Rounded.Tune,
        enabled = profile != null,
        onClick = {
            text = profile?.toString().orEmpty()
            visible = true
        },
    )
    MaxInputDialog(
        visible = visible,
        title = stringResource(R.string.max_audio_vendor_profile),
        fieldLabel = stringResource(R.string.max_audio_vendor_profile_field),
        value = text,
        onValueChange = { text = it },
        confirmLabel = stringResource(R.string.max_audio_vendor_write),
        onConfirm = {
            text.trim().toIntOrNull()?.let(onProfile)
            visible = false
        },
        onDismiss = { visible = false },
    )
}

/**
 * معامل معالج: يُعرض المقروء (كلّه أو أوّله مع عدد النطاقات)، ويُكتب بإدخالٍ صريح.
 *
 * **ولماذا إدخالٌ لا شريط:** المدى الحقيقي لهذه المعاملات **لا تُعلنه المنصّة**، وشريطٌ بمدًى مُخترع
 * يكتب قيمًا لا معنى لها. فالمستخدم يُدخل ما يعرفه، والقراءة بعده تقول ما صار.
 *
 * **والقيم المتعدّدة (عشرون نطاقًا) تُكتب كاملةً**: النصّ يُحلّل إلى أعداد بقدر الطول المُعلَن — وطلبٌ
 * ناقصٌ أو زائد يُردّ في الطبقة الصافية بسببٍ مكتوب، لا بكتابةٍ مشوّهة.
 */
@Composable
private fun DapParamRow(param: DolbyDapParam, value: IntArray?, onWrite: (IntArray) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value?.joinToString(" ").orEmpty()) }

    val read = value != null
    MaxRow(
        title = stringResource(dapParamTitle(param)),
        subtitle = listOfNotNull(
            stringResource(R.string.max_audio_vendor_param_note, param.id, param.length),
            value?.joinToString(" ")?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.max_audio_vendor_unread),
        ).joinToString("  ·  "),
        icon = if (param.length > 1) Icons.Rounded.GraphicEq else Icons.Rounded.Extension,
        enabled = read,
        onClick = {
            text = value?.joinToString(" ").orEmpty()
            visible = true
        },
    )
    MaxInputDialog(
        visible = visible,
        title = stringResource(dapParamTitle(param)),
        fieldLabel = stringResource(R.string.max_audio_vendor_value_field),
        value = text,
        onValueChange = { text = it },
        confirmLabel = stringResource(R.string.max_audio_vendor_write),
        onConfirm = {
            // ولا كتابة بلا قيمة: النصّ الفارغ ليس صفرًا (ADR-07).
            parseDapValues(text, param.length)?.let(onWrite)
            visible = false
        },
        onDismiss = { visible = false },
    )
}

/**
 * تحليل النصّ إلى أعداد **بالطول المُعلَن بالضبط** — والفاصل فراغ أو فاصلة، فما يكتبه المستخدم عادةً
 * يُقبل، وما لا يطابق الطول **يُرفض ولا يُكمَّل بأصفار**.
 */
private fun parseDapValues(text: String, expected: Int): IntArray? {
    val parts = text.trim().split(' ', ',').filter { it.isNotBlank() }
    if (parts.size != expected) return null
    val values = IntArray(expected)
    parts.forEachIndexed { index, part ->
        values[index] = part.trim().toIntOrNull() ?: return null
    }
    return values
}

// ─────────────────────────────── الخرائط: رمز ← نصّه في الموارد ───────────────────────────────

/** اسم المحرّك — والرموز في [`AudioBackendId`] هي المفاتيح، لا الصياغة. */
private fun backendTitle(id: AudioBackendId): Int = when (id) {
    AudioBackendId.VENDOR_EFFECT -> R.string.max_audio_backend_name_vendor
    AudioBackendId.PLATFORM_DYNAMICS -> R.string.max_audio_backend_name_dynamics
    AudioBackendId.PLATFORM_EQUALIZER -> R.string.max_audio_backend_name_equalizer
    AudioBackendId.PLATFORM_SIMPLE -> R.string.max_audio_backend_name_simple
    AudioBackendId.SYSTEM_LAYER -> R.string.max_audio_backend_name_system_layer
}

/** حالة الرِفادة ← نصّها. ولا سادس لهذه الخمسة — وهي مفردات المصفوفة نفسها. */
private fun backendStateText(state: AudioBackendAvailability): Int = when (state) {
    AudioBackendAvailability.AVAILABLE -> R.string.max_audio_caps_status_writable
    AudioBackendAvailability.NEEDS_ADAPTER -> R.string.max_audio_caps_status_needs_adapter
    AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE -> R.string.max_audio_backend_state_detected_unavailable
    AudioBackendAvailability.UNAVAILABLE -> R.string.max_audio_caps_status_unavailable
    AudioBackendAvailability.UNKNOWN -> R.string.max_audio_caps_status_unknown
}

/** والنبرة تتبع الحالة ولا تحمل المعنى وحدها: الوسم مكتوب والرمز مرسوم. */
private fun backendStateTone(state: AudioBackendAvailability): MaxTone = when (state) {
    AudioBackendAvailability.AVAILABLE -> MaxTone.Positive
    AudioBackendAvailability.NEEDS_ADAPTER -> MaxTone.Caution
    AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE -> MaxTone.Inactive
    AudioBackendAvailability.UNAVAILABLE -> MaxTone.Inactive
    AudioBackendAvailability.UNKNOWN -> MaxTone.Caution
}

private fun backendIcon(id: AudioBackendId): ImageVector = when (id) {
    AudioBackendId.VENDOR_EFFECT -> Icons.Rounded.Waves
    AudioBackendId.PLATFORM_DYNAMICS -> Icons.Rounded.Bolt
    AudioBackendId.PLATFORM_EQUALIZER -> Icons.Rounded.GraphicEq
    AudioBackendId.PLATFORM_SIMPLE -> Icons.Rounded.Tune
    AudioBackendId.SYSTEM_LAYER -> Icons.Rounded.Extension
}

/** حضور مؤثّر المصنّع ← نصّه — **والرمز الذي نصّه المالك محفوظٌ حرفيًّا:** «اكتُشف ولا يُتاح». */
private fun vendorPresenceText(presence: VendorAudioPresence?): Int = when (presence) {
    VendorAudioPresence.DETECTED_CONTROLLABLE -> R.string.max_audio_vendor_presence_controllable
    VendorAudioPresence.DETECTED_BUT_UNAVAILABLE -> R.string.max_audio_vendor_presence_unavailable
    VendorAudioPresence.ABSENT -> R.string.max_audio_vendor_presence_absent
    VendorAudioPresence.UNKNOWN -> R.string.max_audio_caps_status_unknown
    null -> R.string.max_audio_vendor_not_measured
}

private fun vendorPresenceTone(presence: VendorAudioPresence?): MaxTone = when (presence) {
    VendorAudioPresence.DETECTED_CONTROLLABLE -> MaxTone.Positive
    VendorAudioPresence.DETECTED_BUT_UNAVAILABLE -> MaxTone.Caution
    VendorAudioPresence.ABSENT -> MaxTone.Inactive
    VendorAudioPresence.UNKNOWN, null -> MaxTone.Caution
}

/** دور الرِفادة ← نصّه — والقيم الثلاث من [`AudioBackendRole`] ولا زيادة. */
private fun vendorRoleText(role: AudioBackendRole): Int = when (role) {
    AudioBackendRole.PRIMARY -> R.string.max_audio_backend_role_primary
    AudioBackendRole.FALLBACK -> R.string.max_audio_backend_role_fallback
    AudioBackendRole.OTHER -> R.string.max_audio_backend_role_other
}

/**
 * **مصدر الاكتشاف ← نصّه** — فيُعرف هل المُكتشَف «مُحمَّلٌ الآن» أم «مُعرَّفٌ في التهيئة فقط»،
 * وهما ليسا واحدًا: الثاني لا يُعرض عاملًا أبدًا.
 */
private fun vendorSourceText(source: VendorDiscoverySource): Int = when (source) {
    VendorDiscoverySource.EFFECT_LIST -> R.string.max_audio_vendor_source_effect_list
    VendorDiscoverySource.AUDIO_EFFECTS_CONFIG -> R.string.max_audio_vendor_source_config
}

/** المسار ← نصّه — والثلاثة هي أسئلة المالك نفسها (SDK عامّ · واجهة مخفيّة · طبقة نظاميّة). */
private fun vendorRouteText(route: VendorAccessRoute): Int = when (route) {
    VendorAccessRoute.PUBLIC_SDK -> R.string.max_audio_vendor_route_public
    VendorAccessRoute.HIDDEN_API -> R.string.max_audio_vendor_route_hidden
    VendorAccessRoute.SYSTEM_LAYER -> R.string.max_audio_vendor_route_system
}

/** اسم معامل DAP — والمفاتيح هي أسماء المفردة نفسها، فلا تُنسخ أرقامًا في الشاشة. */
private fun dapParamTitle(param: DolbyDapParam): Int = when (param) {
    DolbyDapParam.HEADPHONE_VIRTUALIZER -> R.string.max_audio_vendor_param_headphone
    DolbyDapParam.SPEAKER_VIRTUALIZER -> R.string.max_audio_vendor_param_speaker
    DolbyDapParam.VOLUME_LEVELER_ENABLE -> R.string.max_audio_vendor_param_volume_leveler
    DolbyDapParam.IEQ_PRESET -> R.string.max_audio_vendor_param_ieq
    DolbyDapParam.DIALOGUE_ENHANCER_ENABLE -> R.string.max_audio_vendor_param_dialogue
    DolbyDapParam.DIALOGUE_ENHANCER_AMOUNT -> R.string.max_audio_vendor_param_dialogue_amount
    DolbyDapParam.GEQ_BAND_GAINS -> R.string.max_audio_vendor_param_geq
    DolbyDapParam.BASS_ENHANCER_ENABLE -> R.string.max_audio_vendor_param_bass
    DolbyDapParam.STEREO_WIDENING_AMOUNT -> R.string.max_audio_vendor_param_stereo
}
