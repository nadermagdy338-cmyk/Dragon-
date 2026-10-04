/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nd.max.R
import nd.max.core.gamespace.GameApp
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.LobbyPalette
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

private val ManageDialogMaxWidth = 560.dp

/** هل اللعبة في اللوبي الآن؟ — الاستبعاد الصريح يغلب الكشف الآلي (نفس قاعدة `gameLibrary`). */
internal fun isInLobby(app: GameApp, manual: Set<String>, excluded: Set<String>): Boolean =
    app.packageName !in excluded && (app.detectedGame || app.packageName in manual)

/**
 * إدارة الألعاب: كل تطبيق قابل للتشغيل مع مفتاح إدراج/إزالة.
 *
 * هذا هو ما كان «وضع التحرير» في `GameSpaceScreen` القديمة، وقد نُقل إلى هنا حين أُزيلت تلك الشاشة
 * (طلب المالك) كي لا تضيع القدرة على إضافة لعبة لم تُكتشف أو إزالة تطبيق اكتُشف خطأً. والكتابة تمرّ
 * بـ`GameSpaceRepository.membership` وحدها — لا مخزن ثانٍ.
 */
@Composable
internal fun ManageGamesDialog(
    apps: List<GameApp>,
    manual: Set<String>,
    excluded: Set<String>,
    onToggle: (pkg: String, include: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(apps, manual, excluded, query) {
        apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedWith(
                compareByDescending<GameApp> { isInLobby(it, manual, excluded) }
                    .thenBy { it.label.lowercase() }
            )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .widthIn(max = ManageDialogMaxWidth)
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(MaxRadius.sheet))
                .background(LobbyPalette.Surface)
                .padding(MaxSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.lobby_manage_title),
                    color = LobbyPalette.Ink,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MaxSize.minTouchTarget)) {
                    Text(text = stringResource(R.string.lobby_manage_done), color = LobbyPalette.RedBright)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MaxSize.minTouchTarget)
                    .clip(RoundedCornerShape(MaxRadius.control))
                    .background(LobbyPalette.Panel)
                    .padding(horizontal = MaxSpace.md),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(text = stringResource(R.string.spoof_search), color = LobbyPalette.Muted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = LobbyPalette.Ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(LobbyPalette.RedBright),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                items(rows, key = { it.packageName }) { app ->
                    val included = isInLobby(app, manual, excluded)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MaxSize.minTouchTarget)
                            .padding(vertical = MaxSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
                    ) {
                        AppIconImage(packageName = app.packageName, size = 40.dp, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                color = LobbyPalette.Ink,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                color = LobbyPalette.Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = included,
                            onCheckedChange = { onToggle(app.packageName, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = LobbyPalette.Red,
                                checkedThumbColor = LobbyPalette.Ink,
                                uncheckedTrackColor = LobbyPalette.PanelRaised,
                                uncheckedThumbColor = LobbyPalette.Muted
                            )
                        )
                    }
                }
            }
        }
    }
}
