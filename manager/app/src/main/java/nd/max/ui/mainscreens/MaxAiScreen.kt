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

package nd.max.ui.mainscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.maxai.DecisionResult
import nd.max.core.maxai.MaxAiController
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.PendingManualStore
import nd.max.core.maxai.SafetyEnforcement
import nd.max.core.maxai.SafetyLevel
import nd.max.core.maxai.SafetyStatus
import nd.max.core.hardware.ProfileApplier
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.MaxSurface
import nd.max.ui.component.MaxSwitch
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.viewmodel.MaxAiViewModel

/**
 * شاشة MAX AI — الواجهة الوحيدة للمحرك الموحد.
 *
 * القاعدة الصارمة: كل رقم معروض هنا عداد أو قياس حقيقي من المحرك
 * (قرارات/ناجحة/معدلة/محجوبة/حرارة/حمل). لا رسوم بيانية زخرفية،
 * لا ثقة/عصر/مكافأة — هذه أرقام تشخيصية لا تخص المستخدم.
 *
 * الحالتان:
 *  - AI مطفأ (الافتراضي): الملفات الثلاثة قابلة للاختيار وتُطبق فورًا.
 *  - AI مفعل: الملفات مقفلة (🔒) وMax AI يدير الأداء تلقائيًا؛ أي طلب
 *    يدوي يُحفظ "معلقًا" ويُطبق لحظة الإيقاف — لا يضيع شيء.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxAiScreen(
    navController: NavController,
    viewModel: MaxAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val safety by viewModel.safety.collectAsStateWithLifecycle()
    val profileRequest by viewModel.profileRequest.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val colors = MaterialTheme.colorScheme

    // دورة فورية عند دخول الشاشة: القياسات المعروضة حالية لا قديمة.
    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenAccentProvider(colors.tertiary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = colors.background,
            topBar = {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = stringResource(R.string.maxai_screen_title),
                    onBack = { navController.popBackStack() },
                    accentIcon = Icons.Rounded.Psychology,
                    accent = colors.tertiary
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = 16.dp, end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { MasterSwitchCard(state, viewModel) }
                item { ProfileModeCard(state, profileRequest, viewModel) }
                item { EngineStatusCard(state) }
                item { SafetyCard(safety) }
                item { ActivityCountersCard(state) }
                if (state.aiEnabled && state.pendingChanges.isNotEmpty()) {
                    item { PendingChangesCard(state) }
                }
            }
        }
    }
}

// ── المفتاح الرئيسي ─────────────────────────────────────────────────

@Composable
private fun MasterSwitchCard(state: MaxAiState, viewModel: MaxAiViewModel) {
    val colors = MaterialTheme.colorScheme
    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Psychology, null,
                tint = if (state.aiEnabled) colors.tertiary else colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.maxai_master_switch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (state.aiEnabled) stringResource(R.string.maxai_managing_auto)
                    else stringResource(R.string.maxai_manual_control),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.aiEnabled) colors.tertiary else colors.onSurfaceVariant
                )
            }
            MaxSwitch(
                checked = state.aiEnabled,
                onCheckedChange = { viewModel.setAiEnabled(it) }
            )
        }
    }
}

// ── الملفات: حرة عند الإيقاف، مقفلة عند التفعيل ─────────────────────

@Composable
private fun ProfileModeCard(
    state: MaxAiState,
    profileRequest: nd.max.core.maxai.ProfileRequestState,
    viewModel: MaxAiViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val profiles = listOf(
        Triple(ProfileApplier.PROFILE_PERFORMANCE, stringResource(R.string.profile_performance), Icons.Rounded.Bolt),
        Triple(ProfileApplier.PROFILE_BALANCED, stringResource(R.string.profile_balanced), Icons.Rounded.Memory),
        Triple(ProfileApplier.PROFILE_ECO, stringResource(R.string.profile_powersave), Icons.Rounded.HealthAndSafety),
    )

    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.maxai_profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.aiEnabled) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Lock, null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                if (state.aiEnabled) stringResource(R.string.maxai_profiles_locked_desc)
                else stringResource(R.string.maxai_profiles_free_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            profiles.forEach { (id, label, icon) ->
                val selected = state.currentProfile == id
                val pending = state.aiEnabled &&
                    state.pendingChanges.any { it.key == PendingManualStore.KEY_PROFILE && it.value == id }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon, null,
                        tint = if (selected) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (state.aiEnabled) colors.onSurfaceVariant else colors.onSurface
                    )
                    when {
                        pending -> Text(
                            stringResource(R.string.maxai_pending_tag),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.tertiary
                        )
                        state.aiEnabled -> Icon(
                            Icons.Rounded.Lock, null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        selected -> Icon(
                            Icons.Rounded.Verified, null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // عند الإيقاف: أزرار اختيار مباشرة (تطبيق فوري بلا انتظار)
            if (!state.aiEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { (id, label, _) ->
                        val selected = state.currentProfile == id
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.requestProfile(id, label) },
                            enabled = !profileRequest.inFlight,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) colors.tertiary.copy(alpha = 0.12f) else colors.surface,
                                contentColor = if (selected) colors.tertiary else colors.onSurfaceVariant
                            )
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ── حالة المحرك: استراتيجية/متحكم/آخر إجراء/سبب ────────────────────

@Composable
private fun EngineStatusCard(state: MaxAiState) {
    val controllerLabel = when (state.controller) {
        MaxAiController.MANUAL -> stringResource(R.string.maxai_controller_manual)
        MaxAiController.MAX_AI -> stringResource(R.string.maxai_controller_ai)
        MaxAiController.APP_PROFILE -> stringResource(R.string.maxai_controller_app)
        MaxAiController.SAFETY_OVERRIDE -> stringResource(R.string.maxai_controller_safety)
    }

    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.maxai_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            StatusRow(stringResource(R.string.maxai_strategy), state.strategyLabel)
            StatusRow(stringResource(R.string.maxai_controller), controllerLabel)
            if (state.lastDecision != null) {
                StatusRow(
                    stringResource(R.string.maxai_last_action),
                    "${state.lastDecision!!.label} · ${resultLabel(state.lastDecision!!.result)}"
                )
                StatusRow(stringResource(R.string.maxai_reason), state.lastDecision!!.reason)
            }
            // المراقبة الحية: قياسات لحظية فعلية لا تقديرات
            StatusRow(
                stringResource(R.string.maxai_monitoring),
                "CPU ${state.cpuLoadPercent}% · ${state.thermalC.toInt()}°C · " +
                    stringResource(R.string.maxai_battery_pct, state.batteryPercent) +
                    " · " +
                    (
                        if (state.screenOn) stringResource(R.string.maxai_screen_on)
                        else stringResource(R.string.maxai_screen_off)
                        )
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── الأمان ──────────────────────────────────────────────────────────

@Composable
private fun SafetyCard(safety: SafetyStatus) {
    val colors = MaterialTheme.colorScheme
    val danger = when (safety.level) {
        SafetyLevel.NORMAL -> colors.secondary
        SafetyLevel.ENGAGED -> Color(0xFFE6A100)
        SafetyLevel.CRITICAL -> colors.error
    }

    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.HealthAndSafety, null,
                    tint = danger, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.maxai_safety_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when (safety.level) {
                        SafetyLevel.NORMAL -> stringResource(R.string.maxai_safety_normal)
                        SafetyLevel.ENGAGED -> stringResource(R.string.maxai_safety_engaged)
                        SafetyLevel.CRITICAL -> stringResource(R.string.maxai_safety_critical)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = danger,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (safety.lastReason.isNotBlank()) {
                Text(
                    safety.lastReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (safety.engaged) {
                val enforcement = when (safety.enforcement) {
                    SafetyEnforcement.APPLIED -> "مطبق ومتحقق"
                    SafetyEnforcement.PARTIAL -> "مطبق جزئيًا"
                    SafetyEnforcement.FAILED -> "فشل التحقق — ستُعاد المحاولة تلقائيًا"
                    SafetyEnforcement.UNAVAILABLE -> "غير متاح على هذا الجهاز"
                    SafetyEnforcement.NOT_REQUIRED -> "غير مطلوب"
                }
                Text(
                    "$enforcement${safety.enforcementDetail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (safety.enforcement == SafetyEnforcement.APPLIED) colors.secondary else danger
                )
            }
            // شريط الموقع الحراري من 30 إلى 60 درجة — قياس فعل واحد
            LinearProgressIndicator(
                progress = { ((safety.thermalC - 30f) / 30f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = danger,
                trackColor = colors.surfaceVariant
            )
            Text(
                stringResource(R.string.maxai_safety_interventions, safety.interventions),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// ── عدادات النشاط الحقيقية ─────────────────────────────────────────

@Composable
private fun ActivityCountersCard(state: MaxAiState) {
    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.maxai_activity_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CounterCell(
                    stringResource(R.string.maxai_decisions, state.totalDecisions),
                    Modifier.weight(1f)
                )
                CounterCell(
                    stringResource(R.string.maxai_successful, state.successfulDecisions),
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CounterCell(
                    stringResource(R.string.maxai_adjusted, state.adjustedDecisions),
                    Modifier.weight(1f)
                )
                CounterCell(
                    stringResource(R.string.maxai_blocked, state.blockedForSafety),
                    Modifier.weight(1f)
                )
            }
            if (state.rlSteps > 0) {
                Text(
                    stringResource(R.string.maxai_learning_steps, state.rlSteps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CounterCell(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── التعديلات المعلقة ──────────────────────────────────────────────

@Composable
private fun PendingChangesCard(state: MaxAiState) {
    val colors = MaterialTheme.colorScheme
    MaxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.maxai_pending_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.maxai_pending_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            state.pendingChanges.forEach { change ->
                Text(
                    "• ${change.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.tertiary
                )
            }
        }
    }
}

@Composable
private fun resultLabel(result: DecisionResult): String = when (result) {
    DecisionResult.VERIFIED -> stringResource(R.string.maxai_result_verified)
    DecisionResult.EXECUTED -> stringResource(R.string.maxai_result_executed)
    DecisionResult.ADJUSTED -> stringResource(R.string.maxai_result_adjusted)
    DecisionResult.BLOCKED_FOR_SAFETY -> stringResource(R.string.maxai_result_blocked)
    DecisionResult.SKIPPED -> stringResource(R.string.maxai_result_skipped)
    DecisionResult.FAILED -> stringResource(R.string.maxai_result_failed)
}
