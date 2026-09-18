/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import androidx.annotation.StringRes
import com.topjohnwu.superuser.Shell
import nd.max.R

/**
 * حاوية الخمول التي وضعت المنصّة فيها التطبيق (`AR-14`).
 *
 * الأرقام من `AppStandbyController` الرسمية: `5` معفى · `10` نشط · `20` مجموعة عمل ·
 * `30` متكرّر · `40` نادر · `45` مقيَّد · `50` أبدًا. وأي رقم غير معروف يبقى `UNKNOWN`
 * لا يُدمج في خانة قريبة (ADR-07).
 */
enum class StandbyBucket(@StringRes val labelRes: Int) {
    EXEMPTED(R.string.max_bucket_exempted),
    ACTIVE(R.string.max_bucket_active),
    WORKING_SET(R.string.max_bucket_working_set),
    FREQUENT(R.string.max_bucket_frequent),
    RARE(R.string.max_bucket_rare),
    RESTRICTED(R.string.max_bucket_restricted),
    NEVER(R.string.max_bucket_never),
    UNKNOWN(R.string.max_bucket_unknown),
}

/**
 * ما تفعله **المنصّة** بهذا التطبيق في الخلفية — لا ما نعتقده (`AR-14`).
 *
 * يستبدل فولكلور «اقتل الخلفية» بآلية النظام الحقيقية (App Standby + Doze). وقراءة فقط:
 * لا تغيير حاوية ولا إعفاء.
 */
data class BackgroundGovernance(
    /** الحاوية الخام كما أعلنها النظام، أو `null` إن تعذّرت القراءة. */
    val rawBucket: Int?,
    /** هل التطبيق معفى من Doze؟ `null` = تعذّرت القراءة. */
    val dozeWhitelisted: Boolean?,
) {
    val bucket: StandbyBucket get() = BackgroundGovernanceUtil.bucketOf(rawBucket)

    /** مقيَّد: حاوية `RESTRICTED` أو `NEVER`. */
    val restricted: Boolean?
        get() = rawBucket?.let { bucket == StandbyBucket.RESTRICTED || bucket == StandbyBucket.NEVER }

    val readable: Boolean get() = rawBucket != null || dozeWhitelisted != null
}

object BackgroundGovernanceUtil {

    fun bucketOf(raw: Int?): StandbyBucket = when (raw) {
        5 -> StandbyBucket.EXEMPTED
        10 -> StandbyBucket.ACTIVE
        20 -> StandbyBucket.WORKING_SET
        30 -> StandbyBucket.FREQUENT
        40 -> StandbyBucket.RARE
        45 -> StandbyBucket.RESTRICTED
        50 -> StandbyBucket.NEVER
        else -> StandbyBucket.UNKNOWN
    }

    /** يحلّل مخرج `am get-standby-bucket` — رقم صحيح غير سالب، وإلا `null`. */
    fun parseBucket(raw: String?): Int? {
        val value = raw?.trim()?.toIntOrNull() ?: return null
        return value.takeIf { it >= 0 }
    }

    /**
     * هل الحزمة في قائمة Doze البيضاء؟ يعود بـ`null` إن لم نقرأ المخرج أصلًا،
     * وبـ`false` إذا قُرئ ولم تُوجد — وهو فرق مقصود (ADR-07).
     */
    fun whitelistContains(dump: String?, pkg: String): Boolean? {
        if (dump == null) return null
        return dump.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .any { line -> line.substringAfterLast(',').trim() == pkg }
    }

    fun read(pkg: String): BackgroundGovernance {
        val bucket = readBucket(pkg)
        val whitelist = readWhitelist(pkg)
        return BackgroundGovernance(rawBucket = bucket, dozeWhitelisted = whitelist)
    }

    private fun readBucket(pkg: String): Int? = runCatching {
        val result = Shell.cmd("am get-standby-bucket ${quote(pkg)}").exec()
        if (!result.isSuccess) null else parseBucket(result.out.firstOrNull())
    }.getOrNull()

    private fun readWhitelist(pkg: String): Boolean? = runCatching {
        val result = Shell.cmd("dumpsys deviceidle whitelist").exec()
        if (!result.isSuccess) null else whitelistContains(result.out.joinToString("\n"), pkg)
    }.getOrNull()

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
