/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * «الوضع المتقدّم» لأي شاشة **غير** شاشة الإعدادات: يُقرأ من [SettingsPreference] ويُعاد تركيبه
 * لحظة تبديله من الإعدادات، بلا وسيط.
 *
 * **ولماذا مصدر واحد:** ما يُخفيه هذا العلم اليوم يخُرَج من طبقة التصميم نفسها (سطر منشأ
 * `Snapshot`) ومن أقسام مشتركة (دفتر الذاكرة في `DeviceInfo` و`Kernel Facts`)، فلا يجوز أن
 * يقرأه كل موضع بطريقته — وإلّا ظهر في شاشة وغاب في أخرى بلا سبب مرئي.
 *
 * وشاشة الإعدادات وحدها تقرأه من عارضتها ([SettingsViewModel]) لأنها **تكتبه** أيضًا؛
 * والقراءة هنا لا تكتب شيئًا: `SettingsPreference` نسخة واحدة لكل عملية.
 */
@Composable
fun rememberAdvancedMode(): Boolean {
    val context = LocalContext.current
    val preference = remember(context) { SettingsPreference.getInstance(context) }
    return preference.isAdvancedMode.collectAsStateWithLifecycle().value
}
