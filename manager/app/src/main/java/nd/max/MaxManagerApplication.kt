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

package nd.max

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import nd.max.core.maxai.MaxAiEngine
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.SharedHardwareOwnershipStore

/**
 * نقطة إقلاع التطبيق: تُنشئ مكون Hilt وتُقلع بمحرك MAX AI.
 *
 * المحرك يبدأ مع العملية (مرة واحدة) حتى وهو مطفأ — فمحرك الأمان
 * يعمل دائمًا بصرف النظر عن حالة AI، ودورة الإيقاف لا تكتب شيئًا على
 * العتاد. تفعيل Max AI نفسه يظل قرار المستخدم وحده (افتراضيًا مطفأ).
 */
@HiltAndroidApp
class MaxManagerApplication : Application() {

    @Inject lateinit var maxAiEngine: MaxAiEngine

    override fun onCreate() {
        super.onCreate()
        SharedHardwareOwnershipStore.configure(filesDir, applicationInfo.uid, android.os.Process.myPid())
        // Same directory as the journal: the UI writes locks here, the companion
        // process reads them, and both must agree on one durable lock set.
        ManualControlLocks.configure(filesDir)
        maxAiEngine.start()
    }
}
