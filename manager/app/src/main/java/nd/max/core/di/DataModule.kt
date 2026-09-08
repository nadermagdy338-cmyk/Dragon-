package nd.max.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nd.max.core.hardware.AndroidContextDataSource
import nd.max.core.hardware.HardwareDataSource
import nd.max.data.datasources.HardwareDataSourceImpl
import javax.inject.Singleton

/**
 * روابط طبقة البيانات للمحرك الموحد.
 *
 *  - HardwareDataSource يغذي لوحة القياسات الحية في الشاشة الرئيسية
 *    (HomeDashboardViewModel) بقياسات عتاد فعلية.
 *  - AndroidContextDataSource يغذي محرك MAX AI بسياق الجهاز (التطبيق
 *    الأمامي، البطارية، الحرارة، مستشعر الإضاءة...).
 *
 * مستودع التنبؤ القديم (IPredictorRepository/PredictorRepositoryImplV2)
 * حُذف مع شاشات التنبؤ المتفرقة — منطق التنبؤ الحي يعمل الآن داخل
 * محرك MAX AI عبر المتنبئ الأصلي (PredictorBridge) بلا وسيط مستودع.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideHardwareDataSource(
        impl: HardwareDataSourceImpl
    ): HardwareDataSource = impl

    @Provides
    @Singleton
    fun provideAndroidContextDataSource(
        @ApplicationContext context: Context
    ): AndroidContextDataSource = AndroidContextDataSource(context)
}
