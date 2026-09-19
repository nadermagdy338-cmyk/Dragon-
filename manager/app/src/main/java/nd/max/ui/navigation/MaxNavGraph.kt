package nd.max.ui.navigation

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import nd.max.ui.activitylauncher.ActivityLauncherScreen
import nd.max.ui.flasher.KernelFlasherScreen
import nd.max.ui.terminal.TerminalScreen
import nd.max.ui.mainscreens.*
import nd.max.ui.subscreens.*
import nd.max.ui.subscreens.hubs.*

/**
 * Registers every destination declared in [MaxDestination.All] (ADR-02).
 * No route literal may appear here; every entry references the registry.
 */
fun NavGraphBuilder.maxNavGraph(navController: NavHostController) {
    // Onboarding + primary destinations
    composable(MaxDestination.GetStarted.route) { GetStartedScreen(navController) }
    composable(MaxDestination.Now.route) { HomeScreen(navController) }
    composable(MaxDestination.Control.route) { ControlScreen(navController) }
    composable(MaxDestination.Apps.route) { ApplistScreen(navController) }
    composable(MaxDestination.MaxAi.route) { MaxAiScreen(navController) }
    composable(MaxDestination.MaxLive.route) { MaxLiveScreen(navController) }
    composable(MaxDestination.Settings.route) { SettingsScreen(navController) }

    // Control domain hubs: one parameterized entry per domain (F-07).
    composable(MaxDestination.CpuHub.route) { MaxDomainHubScreen(navController, MaxDestination.CpuHub) }
    composable(MaxDestination.GpuHub.route) { MaxDomainHubScreen(navController, MaxDestination.GpuHub) }
    composable(MaxDestination.MemoryHub.route) { MaxDomainHubScreen(navController, MaxDestination.MemoryHub) }
    composable(MaxDestination.DisplayHub.route) { MaxDomainHubScreen(navController, MaxDestination.DisplayHub) }
    composable(MaxDestination.ResponsivenessHub.route) { MaxDomainHubScreen(navController, MaxDestination.ResponsivenessHub) }
    composable(MaxDestination.ThermalHub.route) { MaxDomainHubScreen(navController, MaxDestination.ThermalHub) }
    composable(MaxDestination.PowerHub.route) { MaxDomainHubScreen(navController, MaxDestination.PowerHub) }
    composable(MaxDestination.StorageHub.route) { MaxDomainHubScreen(navController, MaxDestination.StorageHub) }
    composable(MaxDestination.NetworkHub.route) { MaxDomainHubScreen(navController, MaxDestination.NetworkHub) }

    // Feature screens
    composable(MaxDestination.CpuCoreControl.route) { CpuCoreControlScreen(navController) }
    composable(MaxDestination.GovernorSettings.route) { GovSettings(navController) }
    composable(MaxDestination.PreferenceTweaks.route) { PreferenceTweakScreen(navController) }
    composable(MaxDestination.GpuStudio.route) { GpuStudioScreen(navController) }
    composable(MaxDestination.ZramManager.route) { ZramManagerScreen(navController) }
    composable(MaxDestination.DisplayStudio.route) { DisplayStudioScreen(navController) }
    composable(MaxDestination.Resolution.route) { ResolutionScreen(navController) }
    composable(MaxDestination.TouchBoost.route) { TouchBoostScreen(navController) }
    composable(MaxDestination.FpsGo.route) { FpsGoSettings(navController) }
    composable(MaxDestination.Fas.route) { FasScreen(navController) }
    composable(MaxDestination.FpsOverlay.route) { FpsOverlayScreen(navController) }
    composable(MaxDestination.ThermalDetail.route) { ThermalDetailScreen(navController) }
    composable(MaxDestination.Charging.route) { ChargingScreen(navController) }
    composable(MaxDestination.BypassCharging.route) { BypassChargeScreen(navController) }
    composable(MaxDestination.BypassChargingCheck.route) { BypassChargeCheckScreen(navController) }
    composable(MaxDestination.DozeMode.route) { DozeModeScreen(navController) }
    composable(MaxDestination.Dex2oat.route) { Dex2oatScreen(navController) }
    composable(MaxDestination.StorageDetail.route) { StorageDetailScreen(navController) }
    composable(MaxDestination.NetworkScheduler.route) { NetworkSchedulerScreen(navController) }
    composable(MaxDestination.NetworkDetail.route) { NetworkDetailScreen(navController) }
    composable(MaxDestination.ProcessManager.route) { ProcessManagerScreen(navController) }
    composable(MaxDestination.DebloatFreeze.route) { DebloatFreezeScreen(navController) }
    composable(
        route = MaxDestination.AppSettings.route,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType })
    ) { entry ->
        AppSettingsScreen(navController, entry.arguments?.getString("pkg"))
    }

    // Settings children
    composable(MaxDestination.ColorPalette.route) { ColorPaletteScreen(navController) }
    composable(MaxDestination.ColorScheme.route) { ColorSchemeSettings(navController) }
    composable(MaxDestination.Diagnostics.route) { DiagnosticsScreen(navController) }
    composable(MaxDestination.Logs.route) { LogsViewerScreen(navController) }
    composable(MaxDestination.ConfigBackup.route) { ConfigBackupScreen(navController) }
    composable(MaxDestination.Plugins.route) { PluginsScreen(navController) }
    composable(
        route = MaxDestination.MaxBackup.route,
        arguments = listOf(navArgument("pkg") { type = NavType.StringType; defaultValue = "" })
    ) { entry ->
        MaxBackupScreen(
            navController = navController,
            // نمط مسارنا ليس حزمة: قيمة محفوظة من نسخة سبقت الإصلاح قد تحمل `{pkg}`،
            // فتُفتح على تفصيل حزمة لا وجود لها — وهي الحالة المُبلَّغ عنها. الرفض هنا
            // يُسقط إلى قائمة التطبيقات، وهي الشاشة الرئيسية المقصودة لهذا المدخل.
            packageName = packageArgumentOrNull(LocalContext.current, entry.arguments?.getString("pkg")),
        )
    }
    composable(
        route = MaxDestination.Permissions.route,
        // `pkg` اختياري: بلا معرّف تُفتح الشاشة على قائمة التطبيقات (فهي شاشة رئيسية
        // مستقلة بذاتها)، وبه تُفتح على تطبيق واحد. وهذا نفس عقد `Max Backup` حرفيًّا.
        arguments = listOf(navArgument("pkg") { type = NavType.StringType; defaultValue = "" })
    ) { entry ->
        PermissionsScreen(
            navController = navController,
            pkg = packageArgumentOrNull(LocalContext.current, entry.arguments?.getString("pkg")),
        )
    }
    composable(MaxDestination.About.route) { AboutScreen(navController) }
    composable(MaxDestination.Privilege.route) { PrivilegeScreen(navController) }
    composable(MaxDestination.ModuleHealth.route) { ModuleHealthScreen(navController) }

    // Settings - Advanced tools (risk-gated in SettingsScreen, ADR-16)
    composable(MaxDestination.Terminal.route) { TerminalScreen() }
    composable(MaxDestination.SetEdit.route) { SetEditScreen(navController) }
    composable(MaxDestination.ActivityLauncher.route) { ActivityLauncherScreen(navController) }
    composable(MaxDestination.FileManager.route) { FileManagerScreen(navController) }
    composable(MaxDestination.KernelFlasher.route) { KernelFlasherScreen(navController) }
}

/**
 * معامل الحزمة كما تعنيه الوجهتان ذواتا المعامل الاختياري: `null` إلا أن تكون القيمة
 * **شكلها** معرّف حزمة و**مثبّتة فعلًا** على الجهاز.
 *
 * ولماذا الفحصان معًا، وهما ليسا تكرارًا:
 *
 * - الشكل يرفض `{pkg}` — نمط مسارنا إن وصل قيمةً. واسم حزمة Java لا يقبل قوسًا أصلًا،
 *   فالرفض ليس ترجيحًا. وهذا هو الفحص الذي يعمل **حتى لو جاءت القيمة من حالة تنقّل
 *   محفوظة من نسخة سبقت إصلاح مسار الإطلاق** — وهي الحالة المُبلَّغ عنها («تظهر `{pkg}`
 *   … لا توجد حزمة بهذا الاسم على الجهاز»).
 * - والوجود يرفض حزمة حقيقية **أُبطل تثبيتها** بعد إنشاء المدخل، فلم يُبقِ حارس الشكل عليها.
 *
 * وفائدة الاثنين واحدة: الشاشتان تعتبران `null` «بلا نيّة سابقة» فتُفتحان على **قائمة
 * التطبيقات**، وهي الشاشة الرئيسية المقصودة لهما بطلب صريح من المالك — لا تفصيل حزمة
 * لا وجود لها. فالطريق المسدود (رسالة + إعادة محاولة بلا نفع) لا يبقى منه شيء.
 */
private fun packageArgumentOrNull(context: Context, raw: String?): String? =
    raw.takeIf { isPackageArgument(it) }?.takeIf { name ->
        runCatching { context.packageManager.getApplicationInfo(name, 0) }.isSuccess
    }
