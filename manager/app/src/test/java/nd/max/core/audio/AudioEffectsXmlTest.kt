/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الطبقة الصافية لـXML — **مقيسةٌ على JVM وحدها**.
 *
 * **والسؤال الذي يقيسه هذا الملفّ وليس سؤالًا جماليًّا:** وحدة الطبقة النظاميّة تحلّ محلّ ملفّ
 * `audio_effects.xml` **كاملًا**؛ فإن لم يُحفظ ما لا نفهمه، صار أوّل تشغيل على جهازٍ بمكتبة مؤثّر
 * مصنّع **إسكاتًا للصوت**. فالاختبارات هنا **لا تقيس ما نعرفه، بل ما جهلناه**: قسمًا لا نعرفه ·
 * سمّة لا نعرفها (`xmlns:xi`) · ترتيبًا · نصًّا يبدو XML ولا يُحلَّل.
 *
 * **والحدّ المُعلن:** لا `android.jar` في هذه البيئة، ولذلك لا يوجد هنا استيراد Android واحد — وهو
 * مقصود: هذه الطبقة صافية أصلًا، فقياسها **حقيقيّ لا مُحاكى**.
 */
class AudioEffectsXmlTest {

    // ── ما لا نفهمه يُحفظ ──────────────────────────────────────────────

    @Test
    fun `a section we do not know survives the round trip in its own place`() {
        val source = """
            <?xml version="1.0" encoding="utf-8"?>
            <audio_effects version="2.0" xmlns:xi="http://www.w3.org/2001/XInclude">
                <libraries>
                    <library name="bundle" path="libbundlewrapper.so"/>
                </libraries>
                <postprocess>
                    <stream type="AUDIO_STREAM_MUSIC" path="libpost.so"/>
                </postprocess>
            </audio_effects>
        """.trimIndent()

        val parsed = AudioEffectsXml.parse(source)
        assertNotNull(parsed)
        val root = parsed!!

        assertEquals("audio_effects", root.name)
        // والبـادئة محفوظة سمّةً عاديّة: التحليل **بلا أسماء نطاقات**، فلا يُعاد بناء اسمٍ مركّب ولا يُسقط تصريح.
        assertEquals(listOf("version", "xmlns:xi"), root.attributes.map { it.first })
        assertEquals("http://www.w3.org/2001/XInclude", root.attribute("xmlns:xi"))
        assertEquals(listOf("libraries", "postprocess"), root.children.map { it.name })

        val serialized = AudioEffectsXml.serialize(root)
        assertTrue(serialized.contains("xmlns:xi=\"http://www.w3.org/2001/XInclude\""))
        assertTrue(serialized.contains("<postprocess>"))
        assertTrue(serialized.contains("libpost.so"))

        // ودورةٌ كاملة: النصّ المُعاد كتابته يعطي الشجرة نفسها.
        assertEquals(root, AudioEffectsXml.parse(serialized))
    }

    @Test
    fun `every attribute survives, in the order the platform parser reports`() {
        val parsed = AudioEffectsXml.parse(
            "<library path=\"p\" name=\"n\" uuid=\"u\" library=\"l\" xmlns:xi=\"http://x\"/>",
        )!!
        // المقيس: المُحلِّل يُبلّغ عنها **مرتَّبةً بالاسم** لا بترتيب الملفّ — فلا يُدَّعى ترتيبٌ لم يُقس.
        assertEquals(
            listOf("library", "name", "path", "uuid", "xmlns:xi"),
            parsed.attributes.map { it.first },
        )
        // والمهمّ أنّ شيئًا لم يُسقط: كل اسمٍ وقيمته — ومنها البادئة التي لا نفهمها.
        assertEquals(
            mapOf(
                "library" to "l",
                "name" to "n",
                "path" to "p",
                "uuid" to "u",
                "xmlns:xi" to "http://x",
            ),
            parsed.attributes.toMap(),
        )
    }

    @Test
    fun `the same tree always writes the same bytes`() {
        // وهذا ما يعتمد عليه المحكِّم فعلًا: البصمة تُقارَن على نصّ، فنصٌّ يتغيّر بين كتابتين يجعل
        // كل قراءة "انحرافًا" كاذبًا.
        val source = """
            <audio_effects version="2.0">
                <libraries><library path="lib.so" name="x"/></libraries>
                <postprocess><stream type="AUDIO_STREAM_MUSIC" path="libpost.so"/></postprocess>
            </audio_effects>
        """.trimIndent()
        val once = AudioEffectsXml.serialize(AudioEffectsXml.parse(source)!!)
        val twice = AudioEffectsXml.serialize(AudioEffectsXml.parse(source)!!)
        val afterRoundTrip = AudioEffectsXml.serialize(AudioEffectsXml.parse(once)!!)
        assertEquals(once, twice)
        assertEquals(once, afterRoundTrip)
    }

    // ── ما يبدو XML ولا يُحلَّل ⇒ `null` لا «ملفّ فارغ» ────────────────

    @Test
    fun `an unparsable text is a null and never an empty tree`() {
        assertNull(AudioEffectsXml.parse(""))
        assertNull(AudioEffectsXml.parse("ليس XML أصلًا"))
        assertNull(AudioEffectsXml.parse("<audio_effects><libraries></audio_effects>"))
        assertNull(AudioEffectsXml.parse("<audio_effects>&undefined_entity;</audio_effects>"))
        assertNull(AudioEffectsXml.parse("<audio_effects>\u0000</audio_effects>"))
    }

    @Test
    fun `an empty but valid root is an empty root, not an unreadable file`() {
        val parsed = AudioEffectsXml.parse("<audio_effects/>")
        assertNotNull(parsed)
        assertEquals("audio_effects", parsed!!.name)
        assertEquals(emptyList<AudioXmlNode>(), parsed.children)
        assertNull(parsed.text)
    }

    @Test
    fun `a doctype is refused so a system file cannot pull a reference from the outside`() {
        val withDoctype =
            "<?xml version=\"1.0\"?><!DOCTYPE audio_effects [<!ENTITY external SYSTEM \"file:///etc/hostname\">]>" +
                "<audio_effects>&external;</audio_effects>"
        assertNull(AudioEffectsXml.parse(withDoctype))
        // ومرجعٌ داخليّ غير مُعرَّف يُسقِط التحليل كذلك: لا كيان خارجيّ ولا داخليّ يدخل الشجرة.
        assertNull(AudioEffectsXml.parse("<audio_effects>&undefined_entity;</audio_effects>"))
    }

    // ── التعليقات والتعليمات تُسقَط معلَنةً ────────────────────────────

    @Test
    fun `comments and processing instructions are dropped, elements are not`() {
        val parsed = AudioEffectsXml.parse(
            "<audio_effects><!-- تعليق من النسخة --><?pi something?><libraries/></audio_effects>",
        )!!
        assertEquals(listOf("libraries"), parsed.children.map { it.name })
        assertNull(parsed.text)
    }

    // ── النصّ والسمات: قصٌّ وتهريب ─────────────────────────────────────

    @Test
    fun `text is trimmed and escaped, and escapes come back as text`() {
        val node = AudioXmlNode(
            name = "device",
            attributes = listOf("type" to "AUDIO_DEVICE_OUT_SPEAKER"),
            text = "a & b < c",
        )
        val xml = AudioEffectsXml.serialize(node)
        assertTrue(xml.contains("a &amp; b &lt; c"))
        assertFalse(xml.contains("a & b"))
        assertEquals(node, AudioEffectsXml.parse(xml))
    }

    @Test
    fun `an attribute value never carries a newline into the file`() {
        val xml = AudioEffectsXml.serialize(AudioXmlNode("library", listOf("path" to "bad\nvalue")))
        assertFalse(xml.contains("bad\nvalue"))
        assertTrue(xml.contains("path=\"bad value\""))
    }

    @Test
    fun `a node without children and without text closes on itself`() {
        val empty = AudioEffectsXml.serialize(AudioXmlNode("apply", listOf("effect" to "bassboost")))
        assertTrue(empty.contains("<apply effect=\"bassboost\"/>"))
        val withText = AudioEffectsXml.serialize(AudioXmlNode("apply", text = "x"))
        assertTrue(withText.contains("<apply>x</apply>"))
    }

    @Test
    fun `a blank attribute is absent, not empty`() {
        val node = AudioXmlNode("library", listOf("name" to "", "path" to "   ", "uuid" to "u"))
        assertNull(node.attribute("name"))
        assertNull(node.attribute("path"))
        assertNull(node.attribute("missing"))
        assertEquals("u", node.attribute("uuid"))
    }

    @Test
    fun `childrenNamed keeps only that name in file order`() {
        val node = AudioEffectsXml.parse(
            "<root><library name=\"a\"/><effect name=\"b\"/><library name=\"c\"/></root>",
        )!!
        assertEquals(listOf("a", "c"), node.childrenNamed("library").map { it.attribute("name") })
        assertEquals(listOf("b"), node.childrenNamed("effect").map { it.attribute("name") })
        assertEquals(emptyList<String>(), node.childrenNamed("absent").map { it.name })
    }
}
