/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **أقسام النظام** (`AQ-06` · `AQ-07` · `AQ-08` · `AQ-09`): طبقة مؤثّرات النظام،
 * وتوجيه المكالمة، والمحلّل الطيفيّ، والبصمات.
 *
 * **وأمر المالك في الطيف منفَّذ حرفيًّا:** «`RECORD_AUDIO` — يُطلب مع **شرح صريح داخل القسم**». فالقسم
 * يُعرض دائمًا (لا يُخفى)، وقبل الإذن يقول **لماذا** نطلبه وأنّه للرسم وحده ولا يُرفع منه شيء — ولا
 * يُطلب من تلقاء نفسه عند فتح الشاشة.
 *
 * **والحذف بخطوتين لا بخطوة:** البصمة تُحذف بلمسةٍ ثانية تُصرّح («تأكيد الحذف») بعد لمسةِ اختيار —
 * فلا يُمحى نمطٌ بنقرةٍ عابرة، ولا نحتاج نافذةً جديدة في نظام التصميم.
 */
package nd.max.ui.subscreens.audio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.MaterialTheme
import nd.max.core.audio.AudioEffectLibraryState
import nd.max.core.audio.audioEffectLibraryState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.audio.AudioEffectsDocument
import nd.max.core.audio.AudioKnobVerdict
import nd.max.core.audio.AudioProfileV2
import nd.max.core.audio.AudioRouteDevice
import nd.max.core.audio.AudioRouteSnapshot
import nd.max.core.audio.AudioSpectrumFrame
import nd.max.core.audio.AudioSystemLayerStatus
import nd.max.core.audio.AudioSystemSnapshot
import nd.max.core.audio.AudioWriteOutcome
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone

// ──────────────────────────────────── ‏AQ-06: توجيه المكالمة ────────────────────────────────────

/** التوجيه: أجهزة المكالمة التي تُعلنها المنصّة الآن، والساري منها، وإلغاء التوجيه. */
@Composable
internal fun AudioRoutingSection(
    snapshot: AudioRouteSnapshot?,
    onRoute: (AudioRouteDevice) -> Unit,
    onClear: () -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_route_title),
        description = stringResource(R.string.max_audio_route_description),
        collapsible = true,
    ) {
        if (snapshot == null || !snapshot.supported) {
            UnavailableRow(
                title = stringResource(R.string.max_audio_route_title),
                detail = stringResource(R.string.max_audio_route_unavailable),
            )
            return@MaxSection
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_route_active),
                subtitle = snapshot.active?.let { routeName(it) }
                    ?: stringResource(R.string.max_audio_route_none),
                icon = Icons.Rounded.Waves,
                trailing = {
                    if (snapshot.active != null) {
                        MaxCapsule(text = stringResource(R.string.max_audio_route_routed), tone = MaxTone.Accent)
                    }
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_route_clear),
                subtitle = stringResource(R.string.max_audio_route_clear_note),
                enabled = snapshot.active != null,
                onClick = { onClear() },
            )
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_route_available),
                subtitle = stringResource(R.string.max_audio_route_available_note),
            )
            snapshot.available.forEach { device ->
                MaxGroupDivider()
                MaxRow(
                    title = routeName(device),
                    enabled = device.id != snapshot.active?.id,
                    onClick = { if (device.id != snapshot.active?.id) onRoute(device) },
                    trailing = {
                        if (device.id == snapshot.active?.id) {
                            MaxCapsule(
                                text = stringResource(R.string.max_audio_route_in_use),
                                tone = MaxTone.Positive,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** اسم الجهاز: الاسم المُعلَن، وإلا نوعه مترجمًا، وإلا معرّفه — ولا سطرٌ بلا اسم. */
@Composable
private fun routeName(device: AudioRouteDevice): String =
    device.productName?.takeIf { it.isNotBlank() }
        ?: device.typeToken?.let { stringResource(routeTypeTitle(it)) }
        ?: stringResource(R.string.max_audio_route_device, device.id)

private fun routeTypeTitle(token: String): Int = when (token) {
    "builtin_earpiece" -> R.string.max_audio_device_earpiece
    "builtin_speaker" -> R.string.max_audio_device_speaker
    "wired_headset" -> R.string.max_audio_device_wired_headset
    "wired_headphones" -> R.string.max_audio_device_wired_headphones
    "bluetooth_sco" -> R.string.max_audio_device_bluetooth_sco
    "bluetooth_a2dp" -> R.string.max_audio_device_bluetooth_a2dp
    "usb_device" -> R.string.max_audio_device_usb
    "usb_headset" -> R.string.max_audio_device_usb_headset
    "hearing_aid" -> R.string.max_audio_device_hearing_aid
    "hdmi" -> R.string.max_audio_device_hdmi
    else -> R.string.max_audio_device_unknown
}

// ───────────────────────────────────── ‏AQ-08: الطيف ─────────────────────────────────────

/**
 * الطيف — **والقسم يُعرض دائمًا**: قبل الإذن يشرح لماذا نحتاجه، وبعده يبدأ الرسم بطلبٍ من المستخدم.
 *
 * **والإذن يُطلب من هنا لا من تلقاء الشاشة** (أمر المالك: طلبٌ بشرح داخل القسم): لا نداء لنافذة
 * نظام إلا بلمسٍ صريح على «امنح الإذن»، فلا يفاجأ المستخدم بنافذة عند فتح الشاشة.
 */
@Composable
internal fun AudioSpectrumSection(
    permissionGranted: Boolean,
    running: Boolean,
    frame: AudioSpectrumFrame?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onPermissionResult(granted) }
    MaxSection(
        title = stringResource(R.string.max_audio_spectrum_title),
        description = stringResource(R.string.max_audio_spectrum_description),
        collapsible = true,
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_spectrum_permission_title),
                subtitle = stringResource(R.string.max_audio_spectrum_permission_body),
                icon = Icons.Rounded.Mic,
                iconTone = if (permissionGranted) MaxTone.Positive else MaxTone.Caution,
                trailing = {
                    MaxCapsule(
                        text = stringResource(
                            if (permissionGranted) R.string.max_audio_spectrum_granted
                            else R.string.max_audio_spectrum_not_granted,
                        ),
                        tone = if (permissionGranted) MaxTone.Positive else MaxTone.Caution,
                    )
                },
            )
        }
        if (!permissionGranted) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_spectrum_grant),
                    subtitle = stringResource(R.string.max_audio_spectrum_locked),
                    icon = Icons.Rounded.Mic,
                    iconTone = MaxTone.Caution,
                    onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }
            return@MaxSection
        }
        MaxGroup {
            MaxSwitchRow(
                title = stringResource(R.string.max_audio_spectrum_toggle),
                subtitle = stringResource(R.string.max_audio_spectrum_toggle_note),
                checked = running,
                icon = Icons.Rounded.Waves,
                onCheckedChange = { on ->
                    if (on) onStart() else onStop()
                },
            )
        }
        MaxGroup {
            SpectrumGraph(frame)
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_spectrum_peak),
                subtitle = frame?.peakMb?.let { levelText(it) } ?: MAX_VALUE_UNAVAILABLE,
                trailing = {
                    MaxCapsule(
                        text = if (running) {
                            stringResource(R.string.max_audio_spectrum_live)
                        } else {
                            stringResource(R.string.max_audio_spectrum_idle)
                        },
                        tone = if (running) MaxTone.Accent else MaxTone.Neutral,
                    )
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_spectrum_rms),
                subtitle = frame?.rmsMb?.let { levelText(it) } ?: MAX_VALUE_UNAVAILABLE,
                trailing = {
                    MaxCapsule(
                        text = stringResource(R.string.max_audio_spectrum_bands, frame?.bands?.size ?: 0),
                        tone = MaxTone.Neutral,
                    )
                },
            )
        }
    }
}

/** الملّي‑ديسيبل ← ديسيبل (١٠٠ mB = ١ dB) — الوحدة المنصوصة للمنصّة، لا تقريب من عندنا. */
private fun levelText(mb: Int): String = "${mb / 100} dB"

@Composable
private fun SpectrumGraph(frame: AudioSpectrumFrame?) {
    val bars = frame?.bands ?: emptyList()
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = MaxSpace.rowPaddingHorizontal),
    ) {
        if (bars.isEmpty()) {
            drawLine(empty, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
            return@Canvas
        }
        val slot = size.width / bars.size
        val width = (slot * 0.7f).coerceAtLeast(1f)
        bars.forEachIndexed { index, level ->
            val barHeight = (level.coerceIn(0f, 1f)) * size.height
            drawRect(
                color = color,
                topLeft = Offset(index * slot, size.height - barHeight),
                size = Size(width, barHeight),
            )
        }
    }
}

// ──────────────────────────────── ‏AQ-09: طبقة مؤثّرات النظام ────────────────────────────────

/**
 * طبقة مؤثّرات النظام — **القسم يعرض ما قِيس، ثمّ يفعل بخطوتين**.‏
 *
 * **وثلاث قواعد تحكم هذا القسم، وكلّها من عطبٍ متخيّل واقعيّ:**
 *
 * ١. **التحذير قبل الفعل لا بعده**: الطبقة تحلّ محلّ ملفّ يقرؤه `audioserver` — فعطبها يعني مؤثّراتٍ
 *    لا تُحمّل أو صوتًا لا يعمل. فالتحذير سطرٌ ثابت في القسم، ونافذة تأكيد قبل الكتابة، **وطريق
 *    الرجوع مذكور**: حذف الوحدة من Magisk أو زرّ الإلغاء هنا.
 * ٢. **الرجوع تامّ ومُعلَن:** ملفّ النظام لا يُلمس قطّ؛ كل ما نكتبه في مجلّد وحدة مستقلّة، وحذفه
 *    يعيد الحالة الأولى بلا استرجاعٍ جزئيّ.
 * ٣. **كل سبب يُسمّى:** لا زرٌّ معطّل بلا تفسير — يُعرض رمز السبب وحكمه المفصّل من `engineReasonText`.
 *
 * **ومسارات الملفّات تُعرض بنصّها لا وصفًا:** المستخدم يرى مسار المصدر وبصمة ما سيُكتب، فإن أراد
 * التحقّق بنفسه في مدير ملفّات الجذر فالمعلومة أمامه.
 */
/**
 * **صفّ مكتبة المؤثّر** — **والحالة تُقاس في نواةٍ نقيّة** ([`audioEffectLibraryState`]) ولا تُقرأ من
 * شروطٍ مكتوبة هنا: العطب المقيس لم يكن «المكتبة غائبة» بل **مكتبةٌ ٠٦٠٠ تُركّب ولا يقرؤها المصنع**
 * فيُطبع «can't find libmaxfx.so» وتُقرأ العلّة عقدًا وهي صلاحية. فالواجهة تُترجم الخمس حالات وترسمها،
 * والفرق بينها يُقاس في `kverify-audio`.
 */
private fun libraryStateOf(snapshot: AudioSystemSnapshot): AudioEffectLibraryState =
    audioEffectLibraryState(
        hasPlan = snapshot.libraryPlan != null,
        installed = snapshot.libraryInstalled,
        mode = snapshot.libraryMode,
    )

/** **والمسار يُعرض في الحالتين** — فالقارئ يعرف أين يُبحث لا «مفقود» فقط. */
@Composable
private fun librarySubtitle(snapshot: AudioSystemSnapshot): String {
    val plan = snapshot.libraryPlan
        ?: return stringResource(R.string.max_audio_layer_library_not_shipped)
    return if (snapshot.libraryInstalled == true) {
        plan.devicePath
    } else {
        stringResource(R.string.max_audio_layer_library_missing_at, plan.devicePath)
    }
}

private fun libraryTone(state: AudioEffectLibraryState): MaxTone = when (state) {
    AudioEffectLibraryState.READABLE -> MaxTone.Positive
    AudioEffectLibraryState.UNMEASURED -> MaxTone.Neutral
    else -> MaxTone.Caution
}

@Composable
private fun libraryCapsule(snapshot: AudioSystemSnapshot) {
    // والحالة `NOT_READABLE` لا تُنتَج إلّا بصلاحيةٍ **مقروءة** (`mode != null`، مقيس في النواة)،
    // والحرس `?:"?"` صريحٌ هنا فلا يُطبع فراغٌ لو تغيّر الثابت يومًا (ولا يُقال «٠٦٠٠» ظنًّا).
    val mode = snapshot.libraryMode ?: "?"
    when (libraryStateOf(snapshot)) {
        AudioEffectLibraryState.NOT_SHIPPED -> MaxCapsule(
            text = stringResource(R.string.max_audio_layer_library_not_shipped_capsule),
            tone = MaxTone.Caution,
            icon = Icons.Rounded.Warning,
        )
        AudioEffectLibraryState.NOT_INSTALLED -> MaxCapsule(
            text = stringResource(R.string.max_audio_layer_library_missing),
            tone = MaxTone.Caution,
        )
        AudioEffectLibraryState.READABLE -> MaxCapsule(
            text = stringResource(R.string.max_audio_layer_library_readable),
            tone = MaxTone.Positive,
        )
        // والصلاحية المقروءة بعينها تُعرض — فلا تُقرأ العلّة عقدًا وهي صلاحية.
        AudioEffectLibraryState.NOT_READABLE -> MaxCapsule(
            text = stringResource(R.string.max_audio_layer_library_mode, mode),
            tone = MaxTone.Caution,
        )
        AudioEffectLibraryState.UNMEASURED -> MaxCapsule(
            text = stringResource(R.string.max_audio_layer_library_mode_unread),
            tone = MaxTone.Neutral,
        )
    }
}

@Composable
internal fun AudioSystemLayerSection(
    snapshot: AudioSystemSnapshot?,
    verdict: AudioKnobVerdict?,
    onInstall: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var addition by rememberSaveable { mutableStateOf("") }

    MaxSection(
        title = stringResource(R.string.max_audio_layer_title),
        description = stringResource(R.string.max_audio_layer_description),
        collapsible = true,
    ) {
        if (snapshot == null) {
            UnavailableRow(
                title = stringResource(R.string.max_audio_layer_title),
                detail = stringResource(R.string.max_audio_layer_unmeasured),
            )
            return@MaxSection
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_layer_source),
                subtitle = listOfNotNull(
                    snapshot.sourcePath,
                    // والتشخيص يُقال بلسانه: «الجذر هو كذا» بدل «لا يُحلَّل» — فأوّل نظرة تعرف
                    // هل الملف فاسدٌ أم بصيغةٍ لا نعرفها بعد (وهو القياس الذي يُبنى عليه الإصلاح).
                    snapshot.sourceRoot?.let { root ->
                        stringResource(R.string.max_audio_layer_root_is, root)
                    },
                ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_layer_no_source) },
                icon = Icons.Rounded.Folder,
                iconTone = if (snapshot.sourcePath != null) MaxTone.Neutral else MaxTone.Caution,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_layer_root),
                subtitle = stringResource(
                    if (snapshot.verdict.status != AudioSystemLayerStatus.NEEDS_ROOT) {
                        R.string.max_audio_layer_root_granted
                    } else {
                        R.string.max_audio_layer_root_missing
                    },
                ),
                icon = Icons.Rounded.Warning,
                iconTone = if (snapshot.verdict.status != AudioSystemLayerStatus.NEEDS_ROOT) {
                    MaxTone.Positive
                } else {
                    MaxTone.Caution
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_layer_module),
                subtitle = snapshot.moduleRoot,
                icon = Icons.Rounded.Folder,
                trailing = {
                    MaxCapsule(
                        text = stringResource(
                            if (snapshot.installed) R.string.max_audio_layer_installed
                            else R.string.max_audio_layer_not_installed,
                        ),
                        tone = if (snapshot.installed) MaxTone.Positive else MaxTone.Neutral,
                    )
                },
            )
            MaxGroupDivider()
            // **صفّ المكتبة (تكملة ٢٤٢):** الطبقةُ وحدها لا تُسمع. وهذا الصفّ يقول أين المكتبة
            // بالضبط: أخرجت من الحزمة؟ نُسخت إلى مسار البحث؟ بأيّ صلاحية؟ — **فيسقط التخمين**،
            // ولو قُرئت الحالة من غير قياس لعُرض «غير مشحونة» وهي مشحونة أو العكس (ADR-07).
            MaxRow(
                title = stringResource(R.string.max_audio_layer_library),
                subtitle = librarySubtitle(snapshot),
                icon = Icons.Rounded.Folder,
                iconTone = libraryTone(libraryStateOf(snapshot)),
                trailing = { libraryCapsule(snapshot) },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_layer_writable),
                subtitle = stringResource(
                    if (snapshot.moduleWritable) R.string.max_audio_layer_writable_yes
                    else R.string.max_audio_layer_writable_no,
                ),
                icon = Icons.Rounded.Folder,
                iconTone = if (snapshot.moduleWritable) MaxTone.Positive else MaxTone.Caution,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_layer_contents),
                subtitle = contentsText(snapshot.document),
                trailing = {
                    if (snapshot.verdict.requiresReboot) {
                        MaxCapsule(
                            text = stringResource(R.string.max_audio_layer_reboot),
                            tone = MaxTone.Caution,
                            icon = Icons.Rounded.RestartAlt,
                        )
                    }
                },
            )
        }

        // والسبب الأسبق يُقال بجملته: من لا جذر عنده لا يُشتَّت بـ«المسار غير قابل للكتابة».
        if (!snapshot.verdict.isReady) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_layer_blocked_title),
                    subtitle = engineReasonText(snapshot.verdict.reason),
                    icon = Icons.Rounded.Warning,
                    iconTone = MaxTone.Caution,
                )
            }
        }

        // والتحذير دائم الظهور لا يُخفى بعد نجاح: خطر الطبقة لا ينتهي بتركيبها.
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_layer_warning_title),
                subtitle = stringResource(R.string.max_audio_layer_warning_body),
                icon = Icons.Rounded.Warning,
                iconTone = MaxTone.Caution,
            )
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_layer_add),
                subtitle = stringResource(R.string.max_audio_layer_add_note),
                icon = Icons.Rounded.Add,
                onClick = { editing = true },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_layer_remove),
                subtitle = stringResource(R.string.max_audio_layer_remove_note),
                icon = Icons.Rounded.Delete,
                // والحذف لا يُعرض فعلًا حين لا وحدةَ — ولا يُقرأ «فشل».
                enabled = snapshot.installed,
                onClick = onRemove,
            )
        }

        verdict?.let { SystemLayerVerdict(it) }
    }

    if (editing) {
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_audio_layer_input_title),
            fieldLabel = stringResource(R.string.max_audio_layer_input_label),
            value = addition,
            onValueChange = { addition = it },
            confirmLabel = stringResource(R.string.max_audio_layer_input_confirm),
            onConfirm = {
                editing = false
                confirming = true
            },
            onDismiss = { editing = false },
            placeholder = stringResource(R.string.max_audio_layer_input_placeholder),
            supportingText = stringResource(R.string.max_audio_layer_input_support),
        )
    }

    // والتأكيد فنّيًّا لا تخويفًا: **المسار المطلق الذي سيُكتب** أمام المستخدم ليقرأه بنفسه.
    if (confirming) {
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_audio_layer_confirm_title),
            message = stringResource(R.string.max_audio_layer_confirm_body),
            confirmLabel = stringResource(R.string.max_audio_layer_confirm_action),
            onConfirm = {
                confirming = false
                onInstall(addition)
            },
            onDismiss = { confirming = false },
            destructive = true,
            icon = Icons.Rounded.Warning,
            technicalDetail = snapshot?.overlayPath,
        )
    }
}

/** كتابة الطبقة ومؤثّرها وسطرها — نصًّا واحدًا، و`—` حين لا قراءة (ADR-07). */
@Composable
private fun contentsText(document: AudioEffectsDocument?): String = when (document) {
    null -> MAX_VALUE_UNAVAILABLE
    else -> stringResource(
        R.string.max_audio_layer_contents_value,
        document.libraries.size,
        document.effects.size,
        document.deviceAttachments.size,
    )
}

/** حكم آخر محاولة كتابة على الطبقة — والسبب يُنقل حرفيًّا مترجمًا لا مُصنّفًا من جديد. */
@Composable
private fun SystemLayerVerdict(verdict: AudioKnobVerdict) {
    val titleRes = when (verdict.outcome) {
        AudioWriteOutcome.APPLIED -> R.string.max_audio_layer_applied
        AudioWriteOutcome.BLOCKED -> R.string.max_audio_layer_blocked
        AudioWriteOutcome.FAILED -> R.string.max_audio_layer_failed
        AudioWriteOutcome.NOT_ATTEMPTED -> R.string.max_audio_layer_not_attempted
    }
    MaxGroup {
        MaxRow(
            title = stringResource(titleRes),
            subtitle = verdict.reason?.let { engineReasonText(it) }
                ?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.max_audio_write_reason_none),
            icon = if (verdict.isApplied) Icons.Rounded.GraphicEq else Icons.Rounded.Warning,
            iconTone = if (verdict.isApplied) MaxTone.Positive else MaxTone.Caution,
        )
    }
}

// ───────────────────────────────────── ‏AQ-07: البصمات ─────────────────────────────────────

/** البصمات: حفظٌ من القراءة الحالية، وتصدير، وحذفٌ بخطوتين. */
@Composable
internal fun AudioProfilesSection(
    profiles: List<AudioProfileV2>,
    dropped: List<String>,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    MaxSection(
        title = stringResource(R.string.max_audio_profiles_title),
        description = stringResource(R.string.max_audio_profiles_description),
        collapsible = true,
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_profiles_save),
                subtitle = stringResource(R.string.max_audio_profiles_save_note),
                icon = Icons.Rounded.Save,
                onClick = onSave,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_profiles_export),
                subtitle = stringResource(R.string.max_audio_profiles_export_note),
                onClick = onExport,
            )
        }
        if (dropped.isNotEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_profiles_dropped_title),
                    subtitle = stringResource(R.string.max_audio_profiles_dropped, dropped.size),
                    trailing = { MaxCapsule(text = "${dropped.size}", tone = MaxTone.Caution) },
                )
            }
        }
        if (profiles.isEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_profiles_empty),
                    subtitle = stringResource(R.string.max_audio_profiles_empty_note),
                )
            }
            return@MaxSection
        }
        MaxGroup {
            profiles.forEachIndexed { index, profile ->
                if (index > 0) MaxGroupDivider()
                MaxRow(
                    title = profile.name,
                    subtitle = listOfNotNull(
                        stringResource(R.string.max_audio_profiles_entries, profile.entries.size),
                        profile.deviceFingerprint,
                    ).joinToString("  ·  "),
                    icon = Icons.Rounded.Save,
                    onClick = {
                        // لمسةٌ أولى تُصرّح، وثانيةٌ تنفّذ — فلا يُمحى نمطٌ بنقرةٍ عابرة.
                        if (pendingDelete == profile.id) {
                            pendingDelete = null
                            onDelete(profile.id)
                        } else {
                            pendingDelete = profile.id
                        }
                    },
                    trailing = {
                        if (pendingDelete == profile.id) {
                            MaxCapsule(
                                text = stringResource(R.string.max_audio_profiles_confirm_delete),
                                tone = MaxTone.Critical,
                            )
                        } else {
                            MaxCapsule(
                                text = stringResource(R.string.max_audio_profiles_schema, profile.schemaVersion),
                                tone = MaxTone.Neutral,
                            )
                        }
                    },
                )
            }
        }
    }
}
