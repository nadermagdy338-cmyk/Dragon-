/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.emuhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSystemsTest {
    private fun file(name: String, parent: String = "root", size: Long? = 1024L) =
        RomFile("uri:$parent/$name", name, parent, size)

    // ───────────────────────────── جدول الأنظمة ─────────────────────────────

    @Test fun knownExtensionsMapToTheirSystem() {
        assertEquals(RomSystem.NES, RomSystems.systemFor("Zelda.nes"))
        assertEquals(RomSystem.SNES, RomSystems.systemFor("Mario.sfc"))
        assertEquals(RomSystem.GB, RomSystems.systemFor("Tetris.gb"))
        assertEquals(RomSystem.GBA, RomSystems.systemFor("العاب.GBA"))
        assertEquals(RomSystem.N64, RomSystems.systemFor("a.z64"))
        assertEquals(RomSystem.NDS, RomSystems.systemFor("b.nds"))
        assertEquals(RomSystem.N3DS, RomSystems.systemFor("c.3ds"))
    }

    @Test fun unknownExtensionIsNullAndNotAGuess() {
        assertNull(RomSystems.systemFor("notes.txt"))
        assertNull(RomSystems.systemFor("noextension"))
        assertNull(RomSystems.systemFor(""))
        assertNull(RomSystems.systemFor("archive.tar.gz"))
    }

    @Test fun ambiguousExtensionsClaimNoSystem() {
        for (name in listOf("a.bin", "a.iso", "a.chd", "a.zip", "a.7z", "a.m3u", "a.cue")) {
            assertTrue(name, RomSystems.isAmbiguous(name))
            assertNull(name, RomSystems.systemFor(name))
        }
        assertFalse(RomSystems.isAmbiguous("a.gba"))
    }

    @Test fun onlyKnownOrAmbiguousExtensionsAreIndexed() {
        // المعروف والملتبس يُفهرسان…
        for (name in listOf("a.nes", "a.gba", "a.z64", "a.bin", "a.iso", "a.cue", "a.chd")) {
            assertTrue(name, RomSystems.isCandidate(name))
        }
        // …وما ليس لعبة لا يزحم الرفّ ولا يأكل سقف العناصر.
        for (name in listOf("photo.jpg", "song.mp3", "doc.pdf", "notes.txt", "noextension", "")) {
            assertFalse(name, RomSystems.isCandidate(name))
        }
    }

    @Test fun baseNameStripsExtensionAndPath() {
        assertEquals("Final Fantasy VII", RomSystems.baseName("Final Fantasy VII.cue"))
        assertEquals("Game", RomSystems.baseName("roms/psx/Game.bin"))
        assertEquals("Game", RomSystems.baseName("C:\\roms\\Game.bin"))
        assertEquals("noext", RomSystems.baseName("noext"))
    }

    // ───────────────────────────── تجميع مجموعات الأقراص ─────────────────────────────

    @Test fun cueAndBinBecomeOneEntryWithTheCueAsItsUri() {
        val entries = groupDiscSets(listOf(file("FF7.bin"), file("FF7.cue")))
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("FF7", entry.name)
        assertEquals("uri:root/FF7.cue", entry.uri)
        assertEquals(2, entry.parts.size)
        assertTrue(entry.discSet)
        assertTrue(entry.ambiguous)
        assertNull(entry.system)
    }

    @Test fun aBinWithoutItsCueIsNotSwallowed() {
        val entries = groupDiscSets(listOf(file("Loose.bin"), file("Other.gba")))
        assertEquals(2, entries.size)
        val loose = entries.first { it.name == "Loose.bin" }
        assertEquals(1, loose.parts.size)
        assertFalse(loose.discSet)
    }

    @Test fun aNonDiscSiblingIsNeverFoldedIntoADiscSet() {
        // `Game.gba` و`Game.sav` يتشاركان الاسم بلا امتداد، و`sav` ليس قرصًا ⇒ عنصران لا واحد.
        val entries = groupDiscSets(listOf(file("Game.gba"), file("Game.sav")))
        assertEquals(2, entries.size)
    }

    @Test fun sameNameInDifferentFoldersStaysTwoGames() {
        val entries = groupDiscSets(listOf(file("Game.gba", "a"), file("Game.gba", "b")))
        assertEquals(2, entries.size)
    }

    @Test fun sizeIsTheSumOfKnownPartsAndNullWhenNothingIsKnown() {
        val known = groupDiscSets(listOf(file("A.bin", size = 100L), file("A.cue", size = 4L))).single()
        assertEquals(104L, known.sizeBytes)
        val unknown = groupDiscSets(listOf(file("B.gba", size = null))).single()
        assertNull(unknown.sizeBytes)
    }

    @Test fun knownSystemsSortBeforeUnknownOnes() {
        val entries = groupDiscSets(listOf(file("zzz.unknownext"), file("aaa.gba")))
        assertEquals(RomSystem.GBA, entries.first().system)
        assertNull(entries.last().system)
    }

    @Test fun emptyInputGivesEmptyShelf() {
        assertTrue(groupDiscSets(emptyList()).isEmpty())
    }

    // ───────────────────────────── الرفّ والترشيح ─────────────────────────────

    private val shelf = groupDiscSets(
        listOf(file("Zelda.nes"), file("Mario.sfc"), file("Tetris.gb"), file("Mystery.xyz"))
    )

    @Test fun nullSystemMeansEveryShelfRow() {
        assertEquals(4, shelfRows(shelf, null, "").size)
    }

    @Test fun systemChipFiltersToItsOwnRows() {
        val nes = shelfRows(shelf, RomSystem.NES, "")
        assertEquals(listOf("Zelda.nes"), nes.map { it.name })
    }

    @Test fun queryMatchesCaseInsensitivelyAndKeepsUnknownRowsFindable() {
        assertEquals(listOf("Zelda.nes"), shelfRows(shelf, null, "zel").map { it.name })
        assertEquals(listOf("Mystery.xyz"), shelfRows(shelf, null, "MYSTERY").map { it.name })
        assertTrue(shelfRows(shelf, null, "nothing").isEmpty())
    }

    @Test fun countsAreBuiltFromWhatWasActuallyFound() {
        val counts = systemCounts(shelf)
        assertEquals(1, counts[RomSystem.NES])
        assertEquals(1, counts[RomSystem.SNES])
        assertNull(counts[RomSystem.N64])
    }

    // ───────────────────────────── مخزن الفهرس ─────────────────────────────

    @Test fun aStoredFileSurvivesEncodeAndDecodeWithoutInventingNumbers() {
        val original = RomFile("content://x/1", "Game.bin", "content://x", 2048L)
        assertEquals(original, RomIndexStore.decode(RomIndexStore.encode(original)))
        val unknown = RomFile("content://x/2", "Weird.xyz", "content://x", null)
        assertEquals(unknown, RomIndexStore.decode(RomIndexStore.encode(unknown)))
    }

    @Test fun aBrokenLineIsDroppedAndDoesNotPoisonTheIndex() {
        assertNull(RomIndexStore.decode("garbage"))
        assertNull(RomIndexStore.decode(""))
        assertNull(RomIndexStore.decode("\u0001\u0001\u0001\u0001"))
        val lines = setOf(RomIndexStore.encode(file("A.gba")), "garbage")
        assertEquals(1, RomIndexStore.decodeAll(lines).size)
    }

    @Test fun duplicateUrisCollapseToTheLastWrittenOne() {
        val lines = setOf(RomIndexStore.encode(file("A.gba", size = 1L)), RomIndexStore.encode(file("A.gba", size = 2L)))
        assertEquals(1, RomIndexStore.decodeAll(lines).size)
    }
}
