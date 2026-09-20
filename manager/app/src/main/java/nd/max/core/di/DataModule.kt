package nd.max.core.di

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nd.max.core.atlas.AtlasDeviceIdentity
import nd.max.core.atlas.AtlasDiscovery
import nd.max.core.atlas.AtlasEvidenceStore
import nd.max.core.atlas.AtlasFailureLedger
import nd.max.core.atlas.AtlasFileReadTransport
import nd.max.core.atlas.AtlasFileStoreIo
import nd.max.core.atlas.AtlasRepository
import nd.max.core.atlas.AtlasResolver
import nd.max.core.atlas.AtlasReviewedSeeds
import nd.max.core.atlas.AtlasStoreIo
import nd.max.core.hardware.AndroidContextDataSource
import nd.max.core.hardware.AtlasReadBudget
import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.HardwareDataSource
import nd.max.core.threading.DispatcherProvider
import nd.max.data.datasources.HardwareDataSourceImpl
import java.nio.file.Paths
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

    // ---- Max Atlas (`P5`/`T5.6`) -------------------------------------------------------------------
    //
    // Every Atlas binding is constructor injection with the process singletons wired once here. Two
    // properties matter more than the wiring itself:
    //  - **Nothing scans on construction.** Creating these objects reads nothing; a scan starts only
    //    when a caller asks for one, so building the graph cannot perform I/O on the main thread.
    //  - **The evidence directory is app-private and not backed up.** It is reconstructible, so it has
    //    no business in a backup, and a restored cache from another device would be evidence about a
    //    machine the user no longer has.

    // `Paths.get` لا `Path.of`: الآخيرة أُضيفت في API 34 (أو تحتاج core library desugaring) و`minSdk`
    // هنا 29، فكان النداء يرمي `NoSuchMethodError` على 29–33. `Paths.get` هي الطريق المتاح من API 26
    // لنفس النوع `java.nio.file.Path` — فالإصلاح استبدال النداء لا إسكات التحذير.
    @Provides
    @Singleton
    fun provideAtlasStoreIo(@ApplicationContext context: Context): AtlasStoreIo =
        AtlasFileStoreIo(Paths.get(AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath))

    @Provides
    @Singleton
    fun provideAtlasEvidenceStore(io: AtlasStoreIo): AtlasEvidenceStore = AtlasEvidenceStore(io)

    /**
     * The identity, from what the platform **declares**.
     *
     * `Build.SOC_MODEL` and friends are the vendor's own statement; they are read here rather than
     * guessed from a file, and an empty value stays `null` instead of becoming a placeholder that
     * later looks like a real device name.
     */
    @Provides
    @Singleton
    fun provideAtlasDeviceIdentity(@ApplicationContext context: Context): AtlasDeviceIdentity {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        // `Build.SOC_MANUFACTURER` و`Build.SOC_MODEL` أُضيفا في API 31، و`minSdk` هنا 29 — وهما
        // **ليسا ثابتين يُدمجان** وقت الترجمة (قيمتهما تُقرأ من خصائص النظام في مُهيّئ الحقل)، فقراءتهما
        // على 29/30 ترمي `NoSuchFieldError`. والحماية صريحة هنا، ومجهولهما `null` لا نصًّا بديلًا
        // يشبه اسم جهاز حقيقي (وهو نفس المبدأ الذي يشرحه التعليق أعلاه).
        return AtlasDeviceIdentity(
            socManufacturer =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.ifBlank { null }
                else null,
            socModel =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.ifBlank { null }
                else null,
            hardware = Build.HARDWARE.ifBlank { null },
            board = Build.BOARD.ifBlank { null },
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            apiLevel = Build.VERSION.SDK_INT,
            kernelRelease = System.getProperty("os.version"),
            isLowRamDevice = activityManager?.isLowRamDevice,
            memoryClassMb = activityManager?.memoryClass,
        )
    }

    /**
     * Monotonic milliseconds. `elapsedRealtime` does not jump when the wall clock is corrected, which
     * is what evidence ages must be measured against.
     */
    @Provides
    @Singleton
    fun provideAtlasClockMs(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @Singleton
    fun provideAtlasFailureLedger(clockMs: () -> Long): AtlasFailureLedger =
        AtlasFailureLedger(clockMs = clockMs)

    @Provides
    @Singleton
    fun provideAtlasResolver(store: AtlasEvidenceStore, clockMs: () -> Long): AtlasResolver =
        AtlasResolver(store = store, clockMs = clockMs)

    /** The real read path: rootless file reads, no shell, no module, no prompt. */
    @Provides
    @Singleton
    fun provideAtlasReadTransport(): AtlasReadTransport = AtlasFileReadTransport()

    /**
     * The two knowledge banks, in the order they are consulted: reviewed knowledge first, the
     * accumulated community vocabulary second. Both pass the same validator, and the order is the
     * whole design — the second bank exists to complete what the first could not answer, never to
     * overrule it.
     */
    @Provides
    @Singleton
    fun provideAtlasDiscovery(
        identity: AtlasDeviceIdentity,
        transport: AtlasReadTransport,
        clockMs: () -> Long,
    ): AtlasDiscovery = AtlasDiscovery(
        sources = AtlasDiscovery.defaultSources(),
        identity = identity,
        transport = transport,
        budget = AtlasReadBudget.DEFAULT,
        clockMs = clockMs,
    )

    @Provides
    @Singleton
    fun provideAtlasRepository(
        store: AtlasEvidenceStore,
        resolver: AtlasResolver,
        ledger: AtlasFailureLedger,
        identity: AtlasDeviceIdentity,
        dispatchers: DispatcherProvider,
    ): AtlasRepository = AtlasRepository(
        store = store,
        resolver = resolver,
        ledger = ledger,
        identity = identity,
        catalog = AtlasReviewedSeeds.catalog(),
        dispatcher = dispatchers.io,
    )
}
