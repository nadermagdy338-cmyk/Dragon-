/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import java.util.Base64

enum class SpoofRecoveryPhase { PREPARED, CONFIG_VERIFIED, RESTORED, FAILED, CONFLICT }
enum class SpoofRecoveryDecision { ORIGINAL_PRESENT, TARGET_PRESENT, UNREADABLE, EXTERNAL_CHANGE }

/** Sensitive config content is private recovery data, never developer/user log output or export. */
data class SpoofRecoveryRecord(
    val engineId: String,
    val transactionId: String,
    val createdAtMs: Long,
    val original: String,
    val target: String,
    val phase: SpoofRecoveryPhase,
    val reason: String,
) {
    init {
        require(engineId in setOf(SpoofCopgContract.MODULE_ID, SpoofGlobalContract.MODULE_ID))
        require(transactionId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(createdAtMs >= 0)
        require(original.isNotBlank() && target.isNotBlank())
        require(original.toByteArray().size <= MAX_CONFIG_BYTES && target.toByteArray().size <= MAX_CONFIG_BYTES)
        require(reason.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    }
    val originalSignature get() = SpoofCopgContract.signature(original)
    val targetSignature get() = SpoofCopgContract.signature(target)
    fun decision(live: String?): SpoofRecoveryDecision = when {
        live == null -> SpoofRecoveryDecision.UNREADABLE
        SpoofCopgContract.signature(live) == originalSignature -> SpoofRecoveryDecision.ORIGINAL_PRESENT
        SpoofCopgContract.signature(live) == targetSignature -> SpoofRecoveryDecision.TARGET_PRESENT
        else -> SpoofRecoveryDecision.EXTERNAL_CHANGE
    }
    companion object { const val MAX_CONFIG_BYTES = 512 * 1024 }
}

/** Preserve the original recovery point only while the previous verified target is still live. */
fun spoofRecoveryBaseline(previous: SpoofRecoveryRecord?, live: String): String =
    if (previous?.phase == SpoofRecoveryPhase.CONFIG_VERIFIED && previous.targetSignature == SpoofCopgContract.signature(live))
        previous.original else live

object SpoofRecoveryCodec {
    const val MAX_BYTES = 3 * 1024 * 1024
    private const val HEADER = "MAX_IDENTITY_RECOVERY\t1"
    private fun encodeValue(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeValue(value: String): String {
        val decoded = Base64.getDecoder().decode(value).toString(Charsets.UTF_8)
        require(encodeValue(decoded) == value)
        return decoded
    }
    fun encode(records: List<SpoofRecoveryRecord>): String {
        require(records.size <= 2 && records.map { it.engineId }.distinct().size == records.size)
        return buildString {
            append(HEADER).append('\n')
            records.sortedBy { it.engineId }.forEach {
                append(listOf(it.engineId, it.transactionId, it.createdAtMs.toString(), it.phase.name,
                    it.reason, encodeValue(it.original), encodeValue(it.target)).joinToString("\t")).append('\n')
            }
        }.also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }
    fun decode(raw: String): List<SpoofRecoveryRecord> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val lines = raw.removeSuffix("\n").split('\n')
        require(lines.first() == HEADER && lines.size <= 3)
        return lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 7)
            SpoofRecoveryRecord(fields[0], fields[1], fields[2].toLong(), decodeValue(fields[5]),
                decodeValue(fields[6]), SpoofRecoveryPhase.valueOf(fields[3]), fields[4])
        }.also { require(it.map { record -> record.engineId }.distinct().size == it.size) }
    }
}
