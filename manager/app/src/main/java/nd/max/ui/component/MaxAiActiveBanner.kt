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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.viewmodel.MaxAiViewModel

/**
 * لافتة توعية موحّدة: "Max AI يدير الأداء الآن" — تظهر أعلى شاشات
 * التعديل اليدوي التي تتقاطع مع ما يتحكم به المحرك (سقف تردد CPU،
 * الحاكم، الملفات...). الغرض ترابطي بحت: المستخدم في شاشة الحاكم لم
 * يكن يعلم أن Max AI مفعّل وقد يتجاوز تغييره — الآن يعلم، ويصله رابط
 * مباشر لإدارة المحرك بدل مواجهة "لماذا لا يثبت إعدادي؟" بصمت.
 *
 * تحقن ViewModel المحرك ذاتيًا وتقرأ الحالة الحيّة، فلا تحتاج الشاشة
 * المستضيفة أي توصيل — يكفي وضع MaxAiActiveBanner(navController) أعلاها.
 * لا تُعرض إطلاقًا حين يكون Max AI مطفأً (التحكم اليدوي كامل).
 */
@Composable
fun MaxAiActiveBanner(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MaxAiViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = state.aiEnabled,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.tertiaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(11.dp), color = colors.tertiary.copy(alpha = 0.15f)) {
                    Icon(
                        Icons.Rounded.Psychology, null,
                        tint = colors.tertiary,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.maxai_banner_active_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.maxai_banner_active_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                TextButton(onClick = { navController.navigate("maxai") }) {
                    Text(stringResource(R.string.maxai_banner_open), color = colors.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
