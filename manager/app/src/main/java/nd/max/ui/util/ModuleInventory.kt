/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.ui.util

/** Installation metadata, not evidence that code is running in an app process. */
data class InstalledModule(
    val directory: String,
    val id: String?,
    val name: String?,
    val version: String?,
    val disabled: Boolean?,
    val removalPending: Boolean?,
    val updatePending: Boolean?,
)

data class ModuleInventory(val complete: Boolean, val modules: List<InstalledModule>) {
    companion object {
        private val safeDirectory = Regex("[A-Za-z0-9_.-]+")

        /** A successful, framed listing is required even for an empty inventory. */
        fun parse(lines: List<String>, success: Boolean): ModuleInventory {
            if (!success || lines.firstOrNull() != "MAX_MODULES_BEGIN" ||
                lines.lastOrNull() != "MAX_MODULES_END") return ModuleInventory(false, emptyList())
            val modules = mutableListOf<InstalledModule>()
            var directory: String? = null
            var fields = mutableMapOf<String, String>()
            var flags: List<String>? = null
            fun finish(): Boolean {
                val dir = directory ?: return false
                val state = flags ?: return false
                if (state.size != 3 || state.any { it != "0" && it != "1" }) return false
                if (modules.any { it.directory == dir }) return false
                modules += InstalledModule(dir, fields["id"], fields["name"], fields["version"],
                    state[0] == "1", state[1] == "1", state[2] == "1")
                directory = null
                return true
            }
            for (line in lines.drop(1).dropLast(1)) {
                when {
                    line.startsWith("MAX_MODULE_BEGIN:") -> {
                        if (directory != null) return ModuleInventory(false, emptyList())
                        directory = line.substringAfter(':').takeIf {
                            it != "." && it != ".." && safeDirectory.matches(it)
                        } ?: return ModuleInventory(false, emptyList())
                        fields = mutableMapOf()
                        flags = null
                    }
                    line.startsWith("MAX_MODULE_FLAGS:") -> {
                        if (directory == null || flags != null) return ModuleInventory(false, emptyList())
                        flags = line.substringAfter(':').split(':')
                    }
                    line == "MAX_MODULE_END" -> {
                        if (!finish()) return ModuleInventory(false, emptyList())
                    }
                    directory != null -> {
                        val key = line.substringBefore('=').trim()
                        if (key in setOf("id", "name", "version") && '=' in line) {
                            val value = line.substringAfter('=').trim()
                            if (key in fields || value.isEmpty()) return ModuleInventory(false, emptyList())
                            fields[key] = value
                        }
                    }
                    else -> return ModuleInventory(false, emptyList())
                }
            }
            return if (directory == null) ModuleInventory(true, modules)
                else ModuleInventory(false, emptyList())
        }
    }
}
