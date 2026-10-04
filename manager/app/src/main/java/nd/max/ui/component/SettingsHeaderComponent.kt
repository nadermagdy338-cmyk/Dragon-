/*
 * Copyright (C) 2026-2027 Zexshia
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

package nd.max.ui.component


import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.BuildConfig
import nd.max.R
import nd.max.ui.util.*


@Composable
fun AppInfoHeaderContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var time by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            time = Calendar.getInstance()
        }
    }

    // اللغة من التكوين لا من `Locale.getDefault()`: الأخير غير مراقَب، فلو بدّل المستخدم اللغة بقيت
    // الساعة وجزء التاريخ بلغة قديمة حتى تُعاد الشاشة — والتطبيق يشحن ٨٥ لغة ومبدّل لغة داخلي.
    val locale = LocalConfiguration.current.locales[0]
    val hourFormat = SimpleDateFormat("HH", locale)
    val minuteFormat = SimpleDateFormat("mm", locale)
    val buildDateString = remember(locale) {
        SimpleDateFormat("yyyy-MM-dd", locale).format(Date(BuildConfig.BUILD_TIME))
    }

    val totalSeconds = SystemClock.elapsedRealtime() / 1000
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val uptimeString = if (days > 0) {
        "${days}d ${hours}h ${minutes}m"
    } else {
        "${hours}h ${minutes}m"
    }

    // **ترحيل إلى القشرة:** `0.16f` هنا هي `MaxAlpha.border` (اسم كان مفقودًا لا قيمة مخترعة)،
    // و`1.dp` هو `MaxCardSpec.borderWidth`. والباقي من العقد.
    MaxCardShell(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.border),
        contentPadding = 0.dp,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(MaxRadius.inset))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    WallpaperCache.bitmapState.value?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.cd_wallpaper),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        // 9dp لم تكن على سلّم 4dp؛ وسم صغير يأخذ الخطوة المجاورة.
                        shape = RoundedCornerShape(MaxSpace.sm)
                    ) {
                        Text(
                            text = "${hourFormat.format(time.time)}:${minuteFormat.format(time.time)}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.str_archhaven_devs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(MaxRadius.control),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppInfoTextItem(
                    Icons.Outlined.CalendarMonth,
                    stringResource(R.string.str_build_date),
                    buildDateString,
                    Modifier.weight(1f)
                )
                AppInfoTextItem(
                    Icons.Outlined.Numbers,
                    stringResource(R.string.str_version_code),
                    BuildConfig.VERSION_CODE.toString(),
                    Modifier.weight(0.75f)
                )
                AppInfoTextItem(
                    Icons.Filled.Schedule,
                    stringResource(R.string.str_device_uptime),
                    uptimeString,
                    Modifier.weight(0.9f)
                )
            }
        }
    }
}

@Composable
private fun AppInfoTextItem(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
