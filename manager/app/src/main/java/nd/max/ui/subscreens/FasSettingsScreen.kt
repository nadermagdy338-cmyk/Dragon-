/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.subscreens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.MaxManagerInsight
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.design.MaxScreen

/**
 * إعدادات جدولة FAS (`frame-aware scheduling`) — صفحة شرح واحدة.
 *
 * تعتمد هيكل الصفحة المشترك [MaxScreen] بدل `Scaffold` خاصّ بها، فالشريط العلوي والهوامش
 * وحالة التعطيل كلها من مكان واحد.
 */
@Composable
fun FasScreen(navController: NavController) {
    ScreenAccentProvider(MaterialTheme.colorScheme.secondary) {
        MaxScreen(
            title = stringResource(R.string.str_frame_aware_scheduling),
            onBack = { navController.popBackStack() },
        ) {
            MaxManagerInsight(
                text = stringResource(R.string.str_fas_is_a_user_space_implementa),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
