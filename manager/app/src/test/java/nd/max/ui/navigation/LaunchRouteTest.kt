package nd.max.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس على مسار **الإطلاق** — أي ما يُنقَر من قائمة بلا نيّة سابقة.
 *
 * العطب المحروس مُبلَّغ عنه من المستخدم: الضغط على `Max Backup` و`Permissions &
 * App ops` في شاشة التحكم يفتح شاشة فارغة. السبب أن التنقّل كان ينقل نمط المسار
 * نفسه (`max_backup?pkg={pkg}`) — يُطابق الوجهة، لكن `pkg` يصل إليه النص
 * `"{pkg}"`، وهو غير فارغ فينجو من الفلترة التي تفصل القائمة عن التفصيل.
 *
 * والمسارات المقيسة هنا تُقرأ من **سجل الوجهات نفسه**، وصفوف التحكم من **نموذج شاشة
 * التحكم نفسه** — لا من نسخة منها — حتى لا يمرّ الاختبار على قيم قديمة بعد تغييرهما.
 */
class LaunchRouteTest {
    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val found = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("Cannot locate nd.max sources; guard not evaluated", found != null)
        sourceRoot = found!!
    }

    /** الكود دون تعليقاته: تُقاس البنية، ولا تُقاس صياغة التعليق. */
    private fun read(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    /** كل مسار مُعلَن في [MaxDestination]، كما هو في السجل، بمفتاح اسم وجهته. */
    private fun registry(): Map<String, String> =
        Regex("data object (\\w+) : MaxDestination\\(\"([^\"]+)\"")
            .findAll(read("ui/navigation/MaxDestinations.kt"))
            .associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * أسماء الوجهات التي تعرضها **شاشة التحكم** في شريط الأدوات المتقدّمة، مقروءة
     * من `ControlToolDestinations`. وهي المقصودة بعطب «أضغط فلا يُفتح شيء».
     */
    private fun controlToolNames(): List<String> =
        Regex("MaxDestination\\.(\\w+)")
            .findAll(
                read("ui/mainscreens/ControlLayoutModel.kt")
                    .substringAfter("ControlToolDestinations: List<MaxDestination> = listOf(")
                    .substringBefore(")")
            )
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `the registry still holds the routes this guard was written for`() {
        val routes = registry()
        assertTrue("registry could not be parsed (${routes.size} routes)", routes.size > 20)
        assertEquals("max_backup?pkg={pkg}", routes["MaxBackup"])
        assertEquals("max_perms?pkg={pkg}", routes["Permissions"])
        assertEquals("app_settings/{pkg}", routes["AppSettings"])
    }

    @Test
    fun `control lists the two tools this guard was written for`() {
        val names = controlToolNames()
        assertTrue("Control tools band could not be parsed: $names", names.size >= 5)
        assertTrue("Max Backup is not on the Control page: $names", names.contains("MaxBackup"))
        assertTrue("AppOps is not on the Control page: $names", names.contains("Permissions"))
    }

    @Test
    fun `every tool row on the control page opens its own screen, never a fake detail`() {
        val registry = registry()
        val offenders = controlToolNames().filter { name ->
            val route = registry[name]
            route == null || launchRouteNeedsArgument(route) || launchRouteOf(route).contains('{')
        }
        assertTrue(
            "These Control rows cannot open their screen from a list; that is the " +
                "reported 'I press and nothing appears': $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `launching drops the optional query instead of passing the pattern as the value`() {
        assertEquals("max_backup", launchRouteOf("max_backup?pkg={pkg}"))
        assertEquals("max_perms", launchRouteOf("max_perms?pkg={pkg}"))
    }

    @Test
    fun `a route needing an argument in its path stays detectable, not silently launched`() {
        assertTrue(launchRouteNeedsArgument("app_settings/{pkg}"))
        assertFalse(launchRouteNeedsArgument("max_backup?pkg={pkg}"))
        assertFalse(launchRouteNeedsArgument("max_perms?pkg={pkg}"))
        assertFalse(launchRouteNeedsArgument("control"))
    }

    @Test
    fun `only the path-argument destination is unlaunchable, and it is opened by its typed helper`() {
        val unlaunchable = registry().filterValues { launchRouteNeedsArgument(it) }.keys
        assertEquals(
            "More destinations became unlaunchable from a list — tools rows would stop opening.",
            setOf("AppSettings"),
            unlaunchable,
        )
        assertTrue(read("ui/navigation/MaxNavActions.kt").contains(".route.replace(\"{pkg}\", pkg)"))
    }

    @Test
    fun `navigateTo launches the base route, never the raw pattern`() {
        val actions = read("ui/navigation/MaxNavActions.kt")
        assertTrue(
            "navigateTo must launch the resolved route",
            actions.contains("navController.navigate(dest.launchRoute)"),
        )
        assertFalse(
            "Navigating dest.route hands the `{pkg}` pattern to the consumer as the argument " +
                "value; that is the reported empty-screen bug.",
            actions.contains("navigate(dest.route)"),
        )
    }

    @Test
    fun `our own route pattern is not a package name`() {
        // لا يمكن أن يحتوي اسم حزمة Java على قوس؛ فهاتان ليستا حزمة بأي حال.
        assertFalse(isPackageArgument("{pkg}"))
        assertFalse(isPackageArgument("max_backup?pkg={pkg}"))
        assertFalse(isPackageArgument(null))
        assertFalse(isPackageArgument(""))
        assertFalse(isPackageArgument("   "))
        assertTrue(isPackageArgument("com.android.settings"))
        assertTrue(isPackageArgument("nd.max"))
    }

    @Test
    fun `both optional-package destinations reject a stale pattern argument`() {
        val graph = read("ui/navigation/MaxNavGraph.kt")
        assertEquals(
            "MaxBackup and Permissions must BOTH resolve `pkg` through packageArgumentOrNull. " +
                "An unfiltered one re-opens the reported dead screen: saved navigation state " +
                "survives an app update, so a `{pkg}` entry created before the fix is " +
                "restored as-is and shown as a real package.",
            2,
            Regex("packageArgumentOrNull\\(LocalContext\\.current").findAll(graph).count(),
        )
        assertTrue(
            "The resolver must reject our own route pattern, not only blank values.",
            graph.contains("isPackageArgument(it)"),
        )
        assertFalse(
            "A destination still accepts any non-blank `pkg`, including `{pkg}`.",
            graph.contains("takeIf { it.isNotBlank() }"),
        )
    }

    @Test
    fun `both optional-package screens open their app list when the argument is rejected`() {
        // هذا هو المقصود النهائي: أي قيمة مرفوضة تُسقط إلى قائمة التطبيقات، لا إلى
        // شاشة تفصيل حزمة لا وجود لها ولا إلى رسالة «غير مثبّت» مع زرّ إعادة محاولة.
        assertTrue(read("ui/subscreens/MaxBackupScreen.kt").contains("if (!packageName.isNullOrBlank())"))
        assertTrue(read("ui/subscreens/PermissionsScreen.kt").contains("if (pkg.isNullOrBlank())"))
    }

    @Test
    fun `the home dashboard reaches screens through that same gateway`() {
        val home = read("ui/mainscreens/HomeScreen.kt")
        assertTrue(
            "The dashboard's links must resolve their route like every other entry",
            home.contains("navActions::navigateRoute"),
        )
        assertFalse(
            "navController::navigate passes the raw route through unresolved.",
            home.contains("navController::navigate"),
        )
        assertFalse(
            "A hand-written route string outside the registry (ADR-02).",
            home.contains("\"gpustudio\""),
        )
    }
}
