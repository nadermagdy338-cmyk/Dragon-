package nd.max.ui.navigation

/**
 * مسارات التنقّل **الداخلي** لشاشة مُشغّل الأنشطة.
 *
 * ADR-02: كل مسار يعيش في حزمة `ui/navigation`، وملف الشاشة لا يحمل حرفًا نصّيًا للمسار.
 * قبل هذا الملف كانت `"app_list"` مكتوبة مرتين (ثابت خاص + `startDestination`)، و`"app_detail/"`
 * مع `"packageName"` مكتوبتين داخل الشاشة — وهي ثغرة لم تكن بوابة §5(b) تراها لأنها تبحث عن
 * `navigate("...")` فقط، لا عن ثوابت مسار خارج حزمة التنقّل.
 */
internal object ActivityLauncherRoutes {
    /** مسار قائمة التطبيقات داخل الشاشة. */
    const val LIST = "app_list"

    /** بادئة مسار تفاصيل تطبيق (يُضاف إليها اسم الحزمة). */
    const val DETAIL = "app_detail/"

    /** اسم وسيط اسم الحزمة في مسار التفاصيل. */
    const val PACKAGE_ARG = "packageName"

    /** نمط المسار كما يُسجَّل في `composable(...)`. */
    const val DETAIL_PATTERN = DETAIL + "{$PACKAGE_ARG}"

    /** مسار تفاصيل حزمة معيّنة. */
    fun detail(packageName: String) = DETAIL + packageName
}
