/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Source guard for the owner's order: the «Snapshot» trust label is hidden across the app and
 * shown only under Advanced Mode. Every renderer of the label or its caption must pass through
 * the single gate `provenanceVisible`, and the default Device Info help must not name the label.
 *
 * Scope: reads the code and the strings, not a rendered screen. Whether the result looks right
 * on a device still needs a device.
 */
class MaxProvenanceGateTest {

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

    /** Source with comments removed, so a comment that mentions a name cannot satisfy a guard. */
    private fun code(relative: String): String = File(sourceRoot, relative).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    /** Text of one top-level function: from its declaration to the next top-level declaration. */
    private fun function(source: String, name: String): String {
        val declaration = Regex("(?m)^(?:private )?fun\\s+${Regex.escape(name)}\\s*\\(")
            .find(source) ?: error("Missing function $name")
        val next = Regex("(?m)^(?:private )?fun\\s+").find(source, declaration.range.last + 1)
        return source.substring(declaration.range.first, next?.range?.first ?: source.length)
    }

    @Test
    fun `every renderer of the trust label or its caption passes through the one gate`() {
        val design = code("ui/design/MaxMetric.kt")
        assertTrue(
            "MaxTrustChip must check the gate",
            function(design, "MaxTrustChip").contains("provenanceVisible(trust)"),
        )
        assertTrue(
            "the caption must check the gate itself",
            function(design, "MaxMetricProvenance").contains("provenanceVisible(metric.trust)"),
        )
        assertTrue(
            "the readout's spoken label must follow the gate",
            function(design, "MaxMetricReadout").contains("trustAudible"),
        )
        assertTrue(
            "the line's spoken label must follow the gate",
            function(design, "MaxMetricLine").contains("trustAudible"),
        )
    }

    @Test
    fun `the gate hides snapshot only and reads the advanced flag`() {
        val gate = function(code("ui/design/MaxMetric.kt"), "provenanceVisible")
        assertTrue("the gate must test Snapshot", gate.contains("MaxDataTrust.Snapshot"))
        assertTrue("the gate must read Advanced Mode", gate.contains("rememberAdvancedMode()"))
        assertFalse("Stale is a warning and is never hidden by this gate", gate.contains("Stale"))
    }

    @Test
    fun `the default device info help text never names the snapshot label`() {
        val screen = code("ui/subscreens/DeviceInfoScreen.kt")
        assertTrue(
            "the help body must choose between the two texts by the advanced flag",
            screen.contains("R.string.devinfo_help_desc_basic"),
        )

        val english = File(sourceRoot, "../../../res/values/strings.xml").readText()
        val basicEn = requireNotNull(helpText(english, "devinfo_help_desc_basic")) {
            "devinfo_help_desc_basic is missing from values/strings.xml"
        }
        assertFalse(basicEn.contains("snapshot", ignoreCase = true))

        val arabic = File(sourceRoot, "../../../res/values-ar/strings.xml").readText()
        val basicAr = requireNotNull(helpText(arabic, "devinfo_help_desc_basic")) {
            "devinfo_help_desc_basic is missing from values-ar/strings.xml"
        }
        assertFalse(basicAr.contains("لقط"))
    }

    private fun helpText(xml: String, key: String): String? =
        Regex("<string name=\"${Regex.escape(key)}\">([^<]*)</string>").find(xml)?.groupValues?.get(1)
}
