/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **طبقة XML عامّة** لملفّ مؤثّرات النظام (`AQ-09`): تحليلٌ **لا يُسقط المجهول**، وتسلسلٌ
 * ثابت البايتات.
 *
 * **ولماذا شجرةٌ عامّة لا أصناف مُسمّاة لكل عنصر:** هذا الملفّ يُقرأ من نظام الجهاز ثمّ يُعاد كتابته
 * إلى طبقة نظاميّة (وحدة Magisk) **تحلّ محلّ ملفّ النظام كاملًا**. فضياع عنصرٍ لا نفهمه — مكتبةُ
 * مؤثّر مصنّع، أو قسمٌ من نسخة أحدث — يعني **إسكات صوت الجهاز** لا خطأً في شاشة. ولا يكفي أن نعرف
 * العناصر التي نعرفها: يجب أن نحفظ **كل** ما قُرئ، بترتيبه وسماته.
 *
 * **وما يُسقَط معلَنٌ لا مسكوتٌ عنه:** التعليقات وتعليمات المعالجة لا تُحفظ (لا معنى لها في ملفّ
 * تهيئة)، وفراغات النصّ تُقصّ. والباقي يُحفظ: **ترتيب العناصر** حرفيًّا، و**كل سمّةٍ** بقيمتها —
 * ومنها البادئات (`xmlns:xi` سمّةٌ عاديّة لأنّ التحليل **بلا أسماء نطاقات**، فلا يُعاد بناء اسمٍ
 * مركّب ولا يُسقط تصريحٌ لا نفهمه).
 *
 * **وأمّا ترتيب السمات داخل العنصر فليس ترتيب الملفّ — وهذا مقيسٌ لا مُفترَض:** مُحلِّل المنصّة
 * (Xerces في الـJVM) يُبلّغ عن سمات العنصر **مرتَّبةً بالاسم**: `<library path="p" name="n"/>`
 * يعود `name` قبل `path`، و`<root b a c zz aa/>` يعود `a aa b c zz`. ولا يعني ذلك عطبًا: ترتيب
 * السمات في XML **لا دلالة له** ولا يقرؤه `audioserver`. والمهمّ — وهو ما يقيسه الاختبار — أنّ الخرج
 * **ثابت البايتات لنفس الدخل** (فالبصمة التي يقارنها المحكِّم لا تهتزّ بين كتابةٍ وقراءة).
 *
 * **ولا كيان خارجيّ:** ملفٌّ يُقرأ من نظام الجهاز لا يجوز أن يجلب مرجعًا من الشبكة، فالمُحلِّل يُغلق
 * `DOCTYPE` ويُشعل المعالجة الآمنة — فالقراءة لا تصير مسار هجوم.
 *
 * **وصافٍ تمامًا:** لا `android.*` هنا، فيُقاس على JVM وحده بلا جهاز ولا `android.jar`.
 */
package nd.max.core.audio

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * عنصر XML واحد — **وترتيب الأبناء محفوظ كما قُرئ**، وسماته محفوظة **كاملةً** بأسمائها وقيمها
 * (وترتيبها ترتيب المُحلِّل: مرتَّبًا بالاسم — كما هو مُعلَن في `AudioEffectsXml`).
 *
 * و`text` هو النصّ المباشر للعنصر (مقصوصًا)، و`null` حين لا نصّ — فالغياب غياب (ADR-07).
 */
data class AudioXmlNode(
    val name: String,
    val attributes: List<Pair<String, String>> = emptyList(),
    val children: List<AudioXmlNode> = emptyList(),
    val text: String? = null,
) {
    /** قيمة سمّة — والمفتاح غير الموجود يعود `null`، والقيمة الفارغة تعود `null` كذلك. */
    fun attribute(key: String): String? =
        attributes.firstOrNull { it.first == key }?.second?.takeIf { it.isNotBlank() }

    /** الأبناء باسمٍ واحد، بترتيبهم. */
    fun childrenNamed(key: String): List<AudioXmlNode> = children.filter { it.name == key }
}

/** تحليلٌ وتسلسلٌ لشجرة XML — الطبقة الوحيدة التي تعرف الصيغة. */
object AudioEffectsXml {

    /**
     * يحلّل نصًّا إلى شجرة، أو `null` إن لم يكن XML صالحًا.
     *
     * **و`null` ليست «ملفّ فارغ»:** التحليل الفاشل يعني «لم نقرأ»، والحكم عليه يكون في الطبقة
     * التي تعرف السياق (`source-unparsable`) — ولا يُخترع هنا ملفٌّ فارغ يُكتب مكان ملفّ النظام.
     */
    fun parse(text: String): AudioXmlNode? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        // بلا أسماء نطاقات: البادئة (`xmlns:xi`) تُقرأ سمّةً عاديّة فتُحفظ كما هي.
        factory.isNamespaceAware = false
        factory.isValidating = false
        factory.isExpandEntityReferences = false
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val builder = factory.newDocumentBuilder()
        // ومُبلِّغٌ صامت: العطب مقيسٌ ومُسمّى في الطبقة التي تعرف السياق (`source-unparsable`)، فلا
        // حاجة أن يطبع المُحلِّل معه سطرًا إنجليزيًّا في `logcat` — وهو سجلٌّ يُطلبه مستخدم الجذر.
        builder.setErrorHandler(DefaultHandler())
        val document = builder.parse(InputSource(StringReader(text)))
        val root = document.documentElement ?: return null
        toNode(root)
    }.getOrNull()

    /** شجرة ← نصّ. **والخرج ثابت البايتات** لنفس الشجرة، فيقارن المحكِّم بصمةً لا ترتيبًا. */
    fun serialize(node: AudioXmlNode): String {
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        appendNode(out, node, 0)
        return out.toString()
    }

    private fun toNode(element: Element): AudioXmlNode {
        val attributeMap = element.attributes
        val attributes = (0 until attributeMap.length).map { index ->
            val item = attributeMap.item(index)
            item.nodeName to item.nodeValue.orEmpty()
        }
        val children = mutableListOf<AudioXmlNode>()
        val text = StringBuilder()
        var child = element.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.ELEMENT_NODE -> children += toNode(child as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(child.nodeValue.orEmpty())
                // والتعليقات وتعليمات المعالجة تُسقَط **معلَنةً** — لا معنى لها في ملفّ تهيئة،
                // وإبقاؤها يحتاج نموذجًا أعقد بلا فائدة تُقاس.
                else -> Unit
            }
            child = child.nextSibling
        }
        return AudioXmlNode(
            name = element.tagName,
            attributes = attributes,
            children = children,
            text = text.toString().trim().takeIf { it.isNotEmpty() },
        )
    }

    private fun appendNode(out: StringBuilder, node: AudioXmlNode, depth: Int) {
        val indent = "    ".repeat(depth)
        out.append(indent).append('<').append(node.name)
        node.attributes.forEach { (key, value) ->
            out.append(' ').append(key).append("=\"").append(escapeAttribute(value)).append('"')
        }
        val text = node.text
        if (node.children.isEmpty() && text == null) {
            out.append("/>\n")
            return
        }
        out.append('>')
        if (text != null) out.append(escapeText(text))
        if (node.children.isNotEmpty()) {
            out.append('\n')
            node.children.forEach { appendNode(out, it, depth + 1) }
            out.append(indent)
        }
        out.append("</").append(node.name).append(">\n")
    }

    /** سمّة ← نصّ آمن. **والمحارف البيضاء تُطوى فراغًا**، فلا يُكسَر الملفّ بقيمة فيها سطر جديد. */
    private fun escapeAttribute(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\n', '\r', '\t' -> append(' ')
                else -> append(char)
            }
        }
    }

    private fun escapeText(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }
}
