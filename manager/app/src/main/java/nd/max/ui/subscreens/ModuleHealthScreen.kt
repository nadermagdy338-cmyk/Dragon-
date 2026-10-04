/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxCardSpec
import nd.max.R
import nd.max.ui.design.MaxScreen
import nd.max.ui.util.ModuleHealth
import nd.max.ui.util.ModuleHealthUtil
import nd.max.ui.util.VersionIdentity

/**
 * صحة الوحدة والإنقاذ (`AR-05` + `AR-18`) — **قراءة فقط**، وتعمل **بلا شبكة**.
 *
 * تجيب السؤال الذي يبقى بلا جواب في أغلب تطبيقات الجذر: «هل الوحدة سليمة؟ وإن
 * انهار الجهاز، ماذا أفعل الآن؟» — من داخل التطبيق نفسه بدل مواقع موزّعة تحتاج حاسوبًا.
 */
@Composable
fun ModuleHealthScreen(navController: NavController) {
    var health by remember { mutableStateOf<ModuleHealth?>(null) }

    LaunchedEffect(Unit) {
        health = withContext(Dispatchers.IO) { ModuleHealthUtil.read() }
    }

    MaxScreen(
        title = stringResource(R.string.max_module_title),
        onBack = { navController.navigateUp() },
    ) {
        SectionCard(
                title = stringResource(R.string.max_module_health_title),
                subtitle = stringResource(R.string.max_module_health_desc),
            ) {
                val snapshot = health
                if (snapshot == null) {
                    InfoRow(stringResource(R.string.max_module_reading), null)
                } else if (!snapshot.installed) {
                    InfoRow(stringResource(R.string.max_module_not_detected), null)
                } else {
                    InfoRow(
                        stringResource(R.string.max_module_boot_count),
                        snapshot.bootCount?.toString() ?: stringResource(R.string.status_unknown),
                    )
                    InfoRow(
                        stringResource(R.string.max_module_disabled),
                        when (snapshot.disabled) {
                            true -> stringResource(R.string.max_yes)
                            false -> stringResource(R.string.max_no)
                            null -> stringResource(R.string.status_unknown)
                        },
                    )
                    InfoRow(
                        stringResource(R.string.max_module_rescue_triggered),
                        when (snapshot.rescueTriggered) {
                            true -> stringResource(R.string.max_yes)
                            false -> stringResource(R.string.max_no)
                            null -> stringResource(R.string.status_unknown)
                        },
                    )
                    InfoRow(
                        stringResource(R.string.max_module_update_pending),
                        when (snapshot.updatePending) {
                            true -> stringResource(R.string.max_yes)
                            false -> stringResource(R.string.max_no)
                            null -> stringResource(R.string.status_unknown)
                        },
                    )
                    InfoRow(
                        stringResource(R.string.max_module_version_code),
                        if (snapshot.versionCode >= 0) {
                            snapshot.versionCode.toString()
                        } else {
                            stringResource(R.string.status_unknown)
                        },
                    )
                    InfoRow(
                        stringResource(R.string.max_module_id),
                        snapshot.id ?: stringResource(R.string.status_unknown),
                    )
                    InfoRow(
                        stringResource(R.string.max_module_version_label),
                        snapshot.version ?: stringResource(R.string.status_unknown),
                    )
                }
            }

            // ---- AR-04: هوية الإصدار من مصدر واحد ------------------------------------
            SectionCard(
                title = stringResource(R.string.max_version_identity_title),
                subtitle = stringResource(R.string.max_version_identity_desc),
            ) {
                val context = LocalContext.current
                val snapshot = health
                val versionReport = remember(snapshot) {
                    snapshot?.let { VersionIdentity.read(context, it) }
                }
                InfoRow(
                    stringResource(R.string.max_version_identity_app),
                    versionReport?.app?.display ?: stringResource(R.string.max_module_reading),
                )
                InfoRow(
                    stringResource(R.string.max_version_identity_module),
                    versionReport?.module?.display ?: stringResource(R.string.max_module_reading),
                )
                InfoRow(
                    stringResource(R.string.max_version_identity_agreement),
                    when (versionReport?.agreement) {
                        VersionIdentity.Agreement.MATCH -> stringResource(R.string.max_version_identity_match)
                        VersionIdentity.Agreement.MISMATCH -> stringResource(R.string.max_version_identity_mismatch)
                        else -> stringResource(R.string.status_unknown)
                    },
                )
            }

            SectionCard(
                title = stringResource(R.string.max_module_recovery_title),
                subtitle = stringResource(R.string.max_module_recovery_desc),
            ) {
                val snapshot = health
                if (snapshot == null || !snapshot.installed) {
                    InfoRow(stringResource(R.string.max_module_recovery_none), null)
                } else if (snapshot.hasRecoveryHistory) {
                    InfoRow(
                        stringResource(R.string.max_module_recovery_count),
                        snapshot.recoveryEventCount?.toString()
                            ?: stringResource(R.string.status_unknown),
                    )
                    snapshot.lastRecoveryEvent?.let { last ->
                        Text(
                            text = last,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    InfoRow(stringResource(R.string.max_module_recovery_none), null)
                }
            }

            SectionCard(
                title = stringResource(R.string.max_module_rescue_title),
                subtitle = stringResource(R.string.max_module_rescue_desc),
            ) {
                RescueStep(1, stringResource(R.string.max_module_rescue_step1))
                RescueStep(2, stringResource(R.string.max_module_rescue_step2))
                RescueStep(3, stringResource(R.string.max_module_rescue_step3))
                RescueStep(4, stringResource(R.string.max_module_rescue_step4))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.max_module_rescue_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    // القشرة من `MaxCardShell` لا `Card` محلّية بـ`RoundedCornerShape(MaxCardSpec.radius)`: الطلب (§٢) يعدّ
    // نصف القطر والحدّ والخلفية والحشو **واحدًا لكل بطاقة في التطبيق**، و24 كانت قيمة رابعة
    // بعد 22 (العقد) و28 (الورقة) و12 (التحكّم) — وقارئ لا يفرّق بصره بين 22 و24، فالاختلاف
    // ضجيج لا تصميم. والعنوان صار `titleSmall` لأن سلّم §٩ يسمّي عنوان البطاقة به (و`titleMedium`
    // محفوظ لعنوان القسم).
    MaxCardShell(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MaxCardSpec.iconGlyph),
            )
            Spacer(Modifier.width(MaxCardSpec.gap))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RescueStep(index: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
