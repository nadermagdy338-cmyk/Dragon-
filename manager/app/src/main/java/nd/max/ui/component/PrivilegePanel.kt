/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.component

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * لوحة الامتياز والوصول (`AR-20`) — **السطح الواحد** للجذر وShizuku معًا.
 *
 * قبل هذا التصميم كان للجذر بابان يظهران منفصلين (صفحة في شاشة البداية وصفّ في
 * الإعدادات) وباب ثالث لShizuku، فيقرأ المستخدم الشيء نفسه في ثلاثة أماكن. صار هنا:
 * طبقة واحدة معروضة (الحالة الحالية) ثم الطبقتان (الجذر · Shizuku) في البطاقة نفسها،
 * ثم فهرس ما يعمل عند كل طبقة — وتُزرع هذه اللوحة في شاشة البداية وفي الإعدادات معًا،
 * فلا تختلف العبارة بين سطحين.
 *
 * **الاكتشاف تلقائي:** تُقرأ الطبقتان عند فتح الشاشة وعند العودة إليها (بلا استدعاء
 * صلاحية — القراءة السلبية أولًا في [PrivilegeManager])، ويبقى «أعِد الفحص» وزرّا
 * الجذر/Shizuku فعلين صريحين لمن أراد أن يطلب بنفسه. القراءة بلا سؤال، والطلب بضغطة.
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
    var scanning by remember { mutableStateOf(true) }
    // هل حاول المستخدم طلب الجذر من هنا؟ يحدّد نوع السطر التوضيحي تحت الزرّ: شرحٌ قبل
    // المحاولة، ونتيجةٌ صريحة (منح/لم يظهر الطلب) بعدها — كما كان الصفّ القديم يقول.
    var rootAttempted by remember { mutableStateOf(false) }

    // ── الاكتشاف التلقائي ────────────────────────────────────────────────────────
    // يُسجّل المستمعين على الخيط الرئيسي (كما يشترط جسر Shizuku) ثم يقرأ الطبقتين على
    // خيط الإدخال/الإخراج، فلا تتجمّد الواجهة ولا يسأل التطبيق صلاحيةً من تلقاء نفسه.
    LaunchedEffect(Unit) {
        PrivilegeManager.start()
        withContext(Dispatchers.IO) { PrivilegeManager.refresh() }
        scanning = false
    }

    // عند العودة إلى الواجهة (بعد منح إذن من نافذة النظام مثلًا) نعيد القراءة تلقائيًا.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) PrivilegeManager.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** إعادة الفحص يدويًّا — اختيارية: التلقائي يكفي، وهذا لمن أراد أن يتأكّد الآن. */
    fun rescan() {
        if (scanning) return
        scope.launch {
            scanning = true
            withContext(Dispatchers.IO) { PrivilegeManager.refresh() }
            scanning = false
        }
    }

    LaunchedEffect(snapshot.level) { onLevelChanged(snapshot.level) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

            Spacer(Modifier.height(18.dp))

            // ── الحالة الحالية: أول ما يُقرأ في الشاشة ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = levelContainer(snapshot.level),
                contentColor = levelContent(snapshot.level),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.max_privilege_current),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(snapshot.level.labelRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    if (scanning || busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = levelContent(snapshot.level),
                        )
                    } else {
                        Icon(
                            imageVector = if (snapshot.level == PrivilegeLevel.NONE) {
                                Icons.Rounded.Info
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (scanning) {
                        stringResource(R.string.max_privilege_scanning)
                    } else {
                        stringResource(R.string.max_privilege_auto_scan)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { rescan() },
                    enabled = !scanning && !busy,
                ) {
                    Text(stringResource(R.string.max_privilege_rescan))
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.max_privilege_layers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            // ── الجذر ──
            PrivilegeLayer(
                title = stringResource(R.string.max_privilege_root_row),
                state = if (snapshot.rootGranted) {
                    stringResource(R.string.max_privilege_root_granted)
                } else {
                    stringResource(R.string.max_privilege_root_denied)
                },
                detail = stringResource(R.string.max_privilege_root_desc),
                ok = snapshot.rootGranted,
                // الشرح والنتيجة كانا في صفّ الإعدادات المستقلّ، وقد دُمجا هنا لأنهما يخصّان
                // هذا الزرّ بعينه: لماذا لا يظهر التطبيق في مدير الروت حتى يُطلَب الإذن مرّة،
                // وماذا حدث بعد الطلب (وهو ما كان يُبلّغ بالـsnackbar سابقًا).
                hint = when {
                    rootAttempted && snapshot.rootGranted -> stringResource(R.string.root_grant_ok)
                    rootAttempted -> stringResource(R.string.root_grant_denied)
                    else -> stringResource(R.string.root_grant_desc)
                },
            ) {
                OutlinedButton(
                    enabled = !busy && !scanning,
                    onClick = {
                        busy = true
                        rootAttempted = true
                        scope.launch {
                            withContext(Dispatchers.IO) { PrivilegeManager.requestRoot() }
                            withContext(Dispatchers.IO) { PrivilegeManager.refresh() }
                            busy = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    // قبل المنح الزرّ "طلب"، وبعده "فحص": نفس الفعل لكن بصدق المرحلة.
                    Text(
                        stringResource(
                            if (snapshot.rootGranted) {
                                R.string.max_privilege_check_root
                            } else {
                                R.string.root_grant_title
                            }
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Shizuku ──
            PrivilegeLayer(
                title = stringResource(R.string.max_privilege_shizuku_row),
                state = when {
                    snapshot.shizuku.ready -> stringResource(R.string.max_privilege_shizuku_granted)
                    snapshot.shizuku.available -> stringResource(R.string.max_privilege_shizuku_denied)
                    else -> stringResource(R.string.max_privilege_shizuku_unavailable)
                },
                detail = stringResource(R.string.max_privilege_shizuku_desc),
                ok = snapshot.shizuku.ready,
            ) {
                Button(
                    enabled = !busy && !scanning && snapshot.shizuku.available,
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
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.max_privilege_shizuku_connect))
                }
            }

            Spacer(Modifier.height(22.dp))

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
private fun levelContainer(level: PrivilegeLevel): Color = when (level) {
    PrivilegeLevel.ROOT -> MaterialTheme.colorScheme.primaryContainer
    PrivilegeLevel.SHIZUKU -> MaterialTheme.colorScheme.tertiaryContainer
    PrivilegeLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun levelContent(level: PrivilegeLevel): Color = when (level) {
    PrivilegeLevel.ROOT -> MaterialTheme.colorScheme.onPrimaryContainer
    PrivilegeLevel.SHIZUKU -> MaterialTheme.colorScheme.onTertiaryContainer
    PrivilegeLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * طبقة واحدة (جذر أو Shizuku): العنوان، ثم الحالة بصيغة جواب صريح، ثم شرح ما تمنحه،
 * ثم زرّ الفعل — وحيث لزم سطر توضيحي صغير تحت الزرّ.
 */
@Composable
private fun PrivilegeLayer(
    title: String,
    state: String,
    detail: String,
    ok: Boolean,
    hint: String? = null,
    action: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = state,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            action()
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
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
