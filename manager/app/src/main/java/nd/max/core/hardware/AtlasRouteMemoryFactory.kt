package nd.max.core.hardware

import android.content.Context
import nd.max.core.atlas.AtlasFileStoreIo
import java.nio.file.Paths

/** The app and companion must agree on both the store and the device boot, not the process lifetime. */
object AtlasRouteMemoryFactory {
    fun create(context: Context, clockMs: () -> Long): AtlasRouteMemory {
        // Deferred until the first memory operation on the caller's worker. Never prompt for root.
        val bootGeneration by lazy {
            // Android's BOOT_COUNT also changes on framework-only restarts. Only the kernel's
            // boot_id identifies the reset that makes an old hardware quarantine safe to expire.
            AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())
        }
        return AtlasRouteMemory(
            io = AtlasFileStoreIo(
                Paths.get(
                    AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath,
                    AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,
                ).toAbsolutePath(),
            ),
            clockMs = clockMs,
            bootGeneration = { bootGeneration },
            // Permission changes do not establish that an unknown physical state was restored.
            // Both processes keep quarantine until a different, known boot has rebuilt vendor state.
            privilegeGeneration = { 0L },
        )
    }
}
