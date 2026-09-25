/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.navigation

import androidx.navigation.NavHostController

/**
 * Typed navigation helpers handed to screens instead of a raw [NavHostController]
 * (ADR-02). Every route is derived from [MaxDestination]; no hand-written route
 * string may appear here or anywhere else outside the registry.
 */
data class MaxNavActions(private val navController: NavHostController) {
    /**
     * فتح وجهة بلا نيّة سابقة. والمسار من [MaxDestination.launchRoute] لا من
     * [MaxDestination.route]: نقلُ النمط `?pkg={pkg}` كما هو يُطابق الوجهة ويُمرّر
     * `"{pkg}"` قيمةً للمعامل — نص غير فارغ، فتفتح `Max Backup` و`Permissions &
     * App ops` تفصيلَ حزمة لا وجود لها بدل قائمتهما (عطب مُبلَّغ عنه).
     *
     * و`check` مقصود: وجهة معاملها في **مسارها** (`app_settings/{pkg}`) لا قيمة
     * افتراضية لها، فبثُّها من قائمة خطأ برمجي — والصمت عنه هو نفسه شكل العطب
     * المُبلَّغ عنه (ضغطة لا تُنتج شيئًا).
     */
    fun navigateTo(dest: MaxDestination) {
        check(!dest.needsLaunchArgument) {
            "${dest.route} needs an argument in its path; open it with the typed helper"
        }
        navController.navigate(dest.launchRoute)
    }

    /** Single-top primary navigation with state saved (ADR-01, ADR-03). */
    fun navigateToPrimary(dest: MaxDestination) = navController.navigate(dest.launchRoute) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    /**
     * فتح مسار سجلّي يصل كـ**نص** لا كـ[MaxDestination].
     *
     * هذا هو المدخل الوحيد المسموح لشاشة تحمل المسارات كنصوص — لوحة `Now` مثلًا
     * تعلن `onNavigate: (String) -> Unit`. ووجودها هنا لا يحرّرها من القاعدة: تمرّ
     * ب**نفس** [launchRouteOf] التي تمرّ بها [navigateTo]، فلا يمكن أن ينشأ فيها
     * العطب نفسه (`?pkg={pkg}` يُنقل نمطًا فيُمرَّر `"{pkg}"` قيمةً).
     *
     * ⇒ لوحة `Now` كانت تمرّر `navController::navigate` مباشرةً، فوصل المسار الخام
     * إلى الـNavigator بلا نزع. ولم يُبلَّغ عنه عطبًا لأن كل مسارات تلك اللوحة بلا
     * استعلام اختياري — أي أنها كانت محصّنة بالحظّ لا بالقاعدة، والفرق يظهر أول
     * مسار `?pkg` يُضاف إليها.
     */
    fun navigateRoute(route: String) = navController.navigate(launchRouteOf(route))

    /** Opens an app's settings workspace; route built from the registry. */
    fun openApp(pkg: String) = navController.navigate(
        MaxDestination.AppSettings.route.replace("{pkg}", pkg)
    )

    fun back() = navController.popBackStack()
}
