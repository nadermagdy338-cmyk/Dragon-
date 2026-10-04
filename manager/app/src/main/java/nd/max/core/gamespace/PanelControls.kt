package nd.max.core.gamespace

import com.topjohnwu.superuser.Shell
import nd.max.MaxManagerProps
import nd.max.core.hardware.ProfileApplier
import nd.max.core.hardware.RootFileAccess
import nd.max.core.platform.PropertyUtils

enum class BypassState { Unsupported, Off, On }

/**
 * ما تتحكّم به اللوحة وهو موجود أصلًا في التطبيق: ملف الأداء (نفس `ProfileApplier` لبلاطة الإعدادات
 * السريعة) وتجاوز الشحن (نفس مسار `BypassChgTileService`). [profile] = "1" أداء · "2" متوازن · "3" توفير؛
 * و[auto] = وضع الذكاء مفعّل فيملك هو الملف ولا تغيّره اللوحة.
 */
data class PanelControlState(
    val profile: String? = null,
    val auto: Boolean = false,
    val bypass: BypassState = BypassState.Unsupported
)

object PanelControls {
    private const val PROFILE_FILE = "/data/adb/.config/MaxManager/API/current_profile"
    private const val BYPASS_FILE = "/data/adb/.config/MaxManager/bypasschgconfig/bypasschg"

    /** من خيط IO. */
    fun read(): PanelControlState {
        val auto = PropertyUtils.get(MaxManagerProps.Conf.AI_ENABLED, "0") != "0"
        val profile = RootFileAccess.read(PROFILE_FILE)?.trim()?.takeIf { it in setOf("1", "2", "3") }
        val bypass = when {
            PropertyUtils.get(MaxManagerProps.Conf.BYPASS_PATH, "") == "UNSUPPORTED" -> BypassState.Unsupported
            PropertyUtils.get(MaxManagerProps.Conf.BYPASS_CHARGE, "0") == "1" -> BypassState.On
            else -> BypassState.Off
        }
        return PanelControlState(profile = profile, auto = auto, bypass = bypass)
    }

    /** يرفض صراحةً حين يملك الذكاء الملف؛ يعيد true فقط إن نجحت الخدمة فعلًا. */
    fun applyProfile(id: String): Boolean {
        if (read().auto) return false
        return ProfileApplier.apply(id)
    }

    fun setBypass(on: Boolean) {
        val value = if (on) "1" else "0"
        PropertyUtils.set(MaxManagerProps.Conf.BYPASS_CHARGE, value)
        Shell.cmd("echo $value > $BYPASS_FILE").exec()
    }
}
