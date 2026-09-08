package nd.max.core.hardware

/** Vendor-neutral adaptive policy. It only decides a profile; a caller owns application. */
class AdaptiveProfileEngine(
    private val coolThresholdC: Float = 38f,
    private val warmThresholdC: Float = 44f,
    private val hotThresholdC: Float = 48f,
    private val hysteresisC: Float = 1.5f,
) {
    enum class Profile { POWER, BALANCED, GAMING, PERFORMANCE }
    data class Input(val temperatureC: Float?, val batteryPct: Int?, val charging: Boolean, val loadPct: Int?)
    data class Decision(val profile: Profile, val reason: String, val changed: Boolean)

    private var last: Profile? = null

    @Synchronized
    fun decide(input: Input): Decision {
        val t = input.temperatureC
        val battery = input.batteryPct
        val load = input.loadPct
        val next = when {
            t != null && t >= hotThresholdC -> Profile.POWER
            battery != null && battery <= 12 && !input.charging -> Profile.POWER
            t != null && t >= warmThresholdC -> Profile.BALANCED
            load != null && load >= 80 && (input.charging || (battery ?: 100) >= 30) -> Profile.PERFORMANCE
            load != null && load >= 55 -> Profile.GAMING
            t != null && t <= coolThresholdC - hysteresisC && load != null && load < 45 -> Profile.BALANCED
            else -> last ?: Profile.BALANCED
        }
        val changed = next != last
        last = next
        val reason = when {
            t != null && t >= hotThresholdC -> "temperature-hot"
            battery != null && battery <= 12 && !input.charging -> "battery-critical"
            t != null && t >= warmThresholdC -> "temperature-warm"
            load != null && load >= 80 -> "high-load"
            load != null && load >= 55 -> "medium-load"
            else -> "stable"
        }
        return Decision(next, reason, changed)
    }

    @Synchronized fun reset() { last = null }
}
