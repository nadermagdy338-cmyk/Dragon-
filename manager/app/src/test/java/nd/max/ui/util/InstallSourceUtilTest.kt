/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * عقد `AR-34`: تصنيف محافظ — لا «مثبَّت يدويًّا» بلا دليل (ADR-07).
 */
class InstallSourceUtilTest {

    @Test
    fun systemAppWinsRegardlessOfInstaller() {
        assertEquals(
            InstallSourceKind.SYSTEM,
            InstallSourceUtil.classify("com.android.vending", isSystemApp = true)
        )
        assertEquals(
            InstallSourceKind.SYSTEM,
            InstallSourceUtil.classify(null, isSystemApp = true)
        )
    }

    @Test
    fun knownStoresAreNamed() {
        assertEquals(
            InstallSourceKind.PLAY_STORE,
            InstallSourceUtil.classify("com.android.vending", isSystemApp = false)
        )
        assertEquals(
            InstallSourceKind.OTHER_STORE,
            InstallSourceUtil.classify("org.fdroid.fdroid", isSystemApp = false)
        )
    }

    @Test
    fun unknownInstallerIsNotCalledASideload() {
        assertEquals(
            InstallSourceKind.INSTALLED_BY_APP,
            InstallSourceUtil.classify("com.some.filemanager", isSystemApp = false)
        )
    }

    @Test
    fun missingInstallerStaysUnknown() {
        assertEquals(InstallSourceKind.UNKNOWN, InstallSourceUtil.classify(null, isSystemApp = false))
        assertEquals(InstallSourceKind.UNKNOWN, InstallSourceUtil.classify("   ", isSystemApp = false))
    }

    @Test
    fun installerIsTrimmed() {
        assertEquals(
            InstallSourceKind.PLAY_STORE,
            InstallSourceUtil.classify("  com.android.vending  ", isSystemApp = false)
        )
    }
}
