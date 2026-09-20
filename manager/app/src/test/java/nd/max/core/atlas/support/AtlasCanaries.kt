package nd.max.core.atlas.support

/**
 * Privacy canaries (`P0`).
 *
 * Every value here is **fake and recognisable**. `P6` seeds them into raw evidence, exceptions and
 * diagnostics levels, then asserts that none of them survives into stored evidence, the preview bytes
 * or the shared bytes. The check must be able to fail, which is what [poisonedBlob] is for.
 */
object AtlasCanaries {

    const val FAKE_IMEI = "351234567890123"
    const val FAKE_SERIAL = "SENTINEL-SERIAL-0001"
    const val FAKE_SSID = "SentinelHomeNetwork"
    const val FAKE_MAC = "02:00:SENTINEL:00:00"
    const val FAKE_ANDROID_ID = "sentinelandroidid01"
    const val FAKE_PACKAGE = "com.sentinel.private.app"
    const val FAKE_TOKEN = "sentinel-token-9f3c"
    const val FAKE_USER_PATH = "/storage/emulated/0/Download/sentinel-secret-report.txt"

    val ALL: List<String> = listOf(
        FAKE_IMEI,
        FAKE_SERIAL,
        FAKE_SSID,
        FAKE_MAC,
        FAKE_ANDROID_ID,
        FAKE_PACKAGE,
        FAKE_TOKEN,
        FAKE_USER_PATH,
    )

    /** Which sentinels appear in a blob. Empty means the blob carries none of them. */
    fun leaks(text: String?): List<String> = if (text == null) emptyList() else ALL.filter(text::contains)

    fun leakFree(text: String?): Boolean = leaks(text).isEmpty()

    /** A blob that must be detected, so a passing check proves the checker is not vacuous. */
    fun poisonedBlob(): String = ALL.joinToString(separator = "\n") { sentinel -> "raw=$sentinel" }
}
