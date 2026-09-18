/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.privilege.PrivilegeCatalog
import nd.max.core.privilege.PrivilegeLevel
import nd.max.core.privilege.PrivilegeManager
import nd.max.core.privilege.PrivilegeTier

/**
 * لوحة الامتياز والوصول (`AR-20`).
 *
 * تجيب ثلاثة أسئلة في مكان واحد: ما الطبقة الحالية؟ كيف أوصّلها؟ وما الذي يعمل عند كل
 * طبقة؟ وهي **مصدر الحقيقة نفسه** الذي يُستخدم في شاشة البداية وفي الإعدادات، فلا
 * تختلف العبارة بين سطحين.
 *
 * تُبنى على واجهات Material3 وحدها بلا تبعيات شاشة أخرى، لتُزرع في أي مكان.
 */
@Composable
fun PrivilegePanel(
    modifier: Modifier = Modifier,
    onLevelChanged: (PrivilegeLevel) -> Unit = {},
) {
    val snapshot by PrivilegeManager.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { PrivilegeManager.start() }

    // عند العودة إلى الواجهة: نعيد القراءة فقط (لا نطلب صلاحية تلقائيًا).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) PrivilegeManager.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snapshot.level) { onLevelChanged(snapshot.level) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.max_privilege_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.max_privilege_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            LevelBadge(level = snapshot.level)

            Spacer(Modifier.height(16.dp))

            // ── Shizuku ──
            PrivilegeRow(
                title = stringResource(R.string.max_privilege_shizuku_row),
                detail = when {
                    snapshot.shizuku.ready -> stringResource(R.string.max_privilege_shizuku_granted)
                    snapshot.shizuku.available -> stringResource(R.string.max_privilege_shizuku_denied)
                    else -> stringResource(R.string.max_privilege_shizuku_unavailable)
                },
                ok = snapshot.shizuku.ready,
            ) {
                Button(
                    enabled = !busy && snapshot.shizuku.available,
                    onClick = {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (snapshot.shizuku.permissionGranted) {
                                    PrivilegeManager.refresh()
                                } else {
                                    nd.max.core.privilege.ShizukuGateway.requestPermission()
                                }
                            }
                            PrivilegeManager.refresh()
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.max_privilege_shizuku_connect))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── الجذر ──
            PrivilegeRow(
                title = stringResource(R.string.max_privilege_root_row),
                detail = if (snapshot.rootGranted) {
                    stringResource(R.string.max_privilege_root_granted)
                } else {
                    stringResource(R.string.max_privilege_root_denied)
                },
                ok = snapshot.rootGranted,
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { PrivilegeManager.requestRoot() }
                            PrivilegeManager.refresh()
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.max_privilege_check_root))
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.max_privilege_features_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.max_privilege_honesty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            TierSection(PrivilegeTier.NO_ROOT, snapshot.level)
            Spacer(Modifier.height(10.dp))
            TierSection(PrivilegeTier.SHIZUKU, snapshot.level)
            Spacer(Modifier.height(10.dp))
            TierSection(PrivilegeTier.ROOT, snapshot.level)
        }
    }
}

@Composable
private fun LevelBadge(level: PrivilegeLevel) {
    val container = when (level) {
        PrivilegeLevel.ROOT -> MaterialTheme.colorScheme.primaryContainer
        PrivilegeLevel.SHIZUKU -> MaterialTheme.colorScheme.tertiaryContainer
        PrivilegeLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (level) {
        PrivilegeLevel.ROOT -> MaterialTheme.colorScheme.onPrimaryContainer
        PrivilegeLevel.SHIZUKU -> MaterialTheme.colorScheme.onTertiaryContainer
        PrivilegeLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.max_privilege_current),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(level.labelRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

@Composable
private fun PrivilegeRow(
    title: String,
    detail: String,
    ok: Boolean,
    action: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        action()
    }
}

@Composable
private fun TierSection(tier: PrivilegeTier, level: PrivilegeLevel) {
    val features = PrivilegeCatalog.byTier(tier)
    if (features.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(tier.labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        features.forEach { feature ->
            val available = PrivilegeCatalog.availableAt(feature, level)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (available) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(feature.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                )
            }
        }
    }
}
