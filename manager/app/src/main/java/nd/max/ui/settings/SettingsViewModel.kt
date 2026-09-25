/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * عارضة [SettingsPreference] لشاشة الإعدادات: تمرّر حالتها وتكتب فيه.
 *
 * وجودها بسيط عن قصد: المنطق كله في [SettingsPreference] و[AppLanguage] (لأن الإقلاع يحتاجهما)،
 * ودور هذه الطبقة أن تكون نقطة الاشتراك التي تنتظرها الـComposable، لا أن تكرّر الحالة.
 * [[ADR-11]]: لا كتابة مباشرة من الواجهة إلى مخزن؛ الكتابة هنا تمرّ بـ[SettingsPreference].
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreference.getInstance(application)

    /** وسم اللغة الحالي كما يختاره المستخدم. */
    val currentLanguage: StateFlow<String> = preferences.currentLanguageCode

    /** حالة زرّ «الخيارات المتقدّمة». */
    val isAdvancedMode: StateFlow<Boolean> = preferences.isAdvancedMode

    /** يُحفظ العلم فورًا؛ لا يحتاج coroutine لأنه كتابة صغيرة على الذاكرة والملف. */
    fun setAdvancedMode(enabled: Boolean) = preferences.setAdvancedModeEnabled(enabled)

    /**
     * يحفظ اللغة ويطبّقها فورًا على النشاطات القائمة.
     *
     * الكتابة في الملف **قبل** التطبيق: [AppLanguage.apply] قد يُعيد إنشاء النشاط، فيجب أن يكون
     * الاختيار محفوظًا قبل ذلك كي يقرأه الإقلاع نفسه.
     */
    fun setAppLanguage(code: String) {
        preferences.setLanguageCode(code)
        AppLanguage.apply(code)
    }
}
