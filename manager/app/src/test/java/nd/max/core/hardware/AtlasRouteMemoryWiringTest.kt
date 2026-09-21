package nd.max.core.hardware

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source guards only: no Android classes loaded, no claim about Context behavior on a device. */
class AtlasRouteMemoryWiringTest {
    private val sourceRoot = listOf(
        File("src/main/java/nd/max"),
        File("app/src/main/java/nd/max"),
        File("manager/app/src/main/java/nd/max"),
    ).firstOrNull { it.isDirectory } ?: error("Cannot locate nd.max sources; wiring guard cannot run")

    private fun source(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    @Test
    fun `app and companion both construct route memory through the shared factory`() {
        listOf("core/di/DataModule.kt", "AppMonitor.kt").forEach { path ->
            val text = source(path)
            assertTrue("$path must call the shared factory", Regex("AtlasRouteMemoryFactory\\s*\\.\\s*create\\s*\\(").containsMatchIn(text))
            assertFalse("$path must not construct independent route memory", Regex("\\bAtlasRouteMemory\\s*\\(").containsMatchIn(text))
        }
    }

    @Test
    fun `factory uses shared non backed up storage and kernel boot identity`() {
        val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
        assertTrue(text.contains("AtlasFileStoreIo.directoryFor(context.noBackupFilesDir)"))
        assertTrue(text.contains("AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME"))
        assertTrue(text.contains("AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())"))
        assertFalse("Framework restart must not look like a kernel reboot", text.contains("BOOT_COUNT"))
        assertTrue(Regex("bootGeneration\\s*=\\s*\\{\\s*bootGeneration\\s*}").containsMatchIn(text))
        assertTrue(Regex("privilegeGeneration\\s*=\\s*\\{\\s*0L\\s*}").containsMatchIn(text))
    }

    @Test
    fun `factory never opens a privilege transport to obtain boot identity`() {
        val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
        listOf(
            "RootFileAccess", "RootIpcManager", "Shell", "ProcessBuilder",
            "Runtime.getRuntime", "requestRoot", "requestPermission", "ensureRoot",
            "PrivilegeManager", "com.topjohnwu", "rikka.shizuku",
        ).forEach { forbidden ->
            assertFalse("Factory must not request privilege through $forbidden", text.contains(forbidden))
        }
    }
}
