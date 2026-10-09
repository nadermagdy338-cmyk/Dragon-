/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

/**
 * مجموعة «الانتحال» كما في COPG — **الخيارات المجانية فقط**: انتحال المعالج وحظر المعالج.
 *
 * ما لا يُنقل عمدًا: `cow` و`aid` وبقية وسوم PRO في COPG (انظر [CopgTag.tier]). هي خارج هذه المجموعة،
 * فلا تُعرض ولا تُكتب من هذه الواجهة، والاختبار يتحقّق من أن الوسمين المعروضين مجانيان.
 *
 * نقيّ: يعمل على مجموعة الوسوم كما تُحفظ في سياسة التطبيق، ولا يكتب شيئًا.
 */
object SpoofImpersonation {
    private const val CPU_KEY = "cpu"
    private val BLOCK: String = CopgTag.BlockCpu.render()

    /** مفتاح المعالج المنتحَل لهذا التطبيق، أو `null` إن كان المعالج الحقيقي. */
    fun cpuKey(tags: Set<String>): String? =
        tags.firstOrNull { keyOf(it) == CPU_KEY }?.substringAfter('=', "")?.ifEmpty { null }

    /** هل حُظر انتحال المعالج لهذا التطبيق؟ */
    fun blocksCpu(tags: Set<String>): Boolean = BLOCK in tags

    /**
     * يضبط انتحال المعالج. `model = null` يمحوه ويُبقي باقي الوسوم بما فيها الحظر.
     * أمّا `model` فيلغي الحظر المتعارض (وسمان متنافيان في [CopgTagRules]) ثم يضيف `cpu=<model>`.
     */
    fun withCpu(tags: Set<String>, model: String?): Set<String> {
        if (model == null) return tags.filterNot { keyOf(it) == CPU_KEY }.toSet()
        val rendered = CopgTag.Cpu(model).render()
        require(CopgTagRules.valid(rendered)) { "unsafe CPU model" }
        return (tags - BLOCK).filterNot { keyOf(it) == CPU_KEY }.toSet() + rendered
    }

    /** يضبط حظر المعالج. تفعيله يلغي انتحال المعالج المتعارض، وإلغاؤه يُبقي الباقي كما هو. */
    fun withBlock(tags: Set<String>, blocked: Boolean): Set<String> {
        if (!blocked) return tags - BLOCK
        return tags.filterNot { keyOf(it) == CPU_KEY }.toSet() + BLOCK
    }

    private fun keyOf(tag: String): String = tag.substringBefore('=')
}
