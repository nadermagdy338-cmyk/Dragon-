package nd.max.ui.navigation

import androidx.navigation.NavHostController

data class MaxNavActions(private val navController: NavHostController) {
    fun navigateTo(dest: MaxDestination) = navController.navigate(dest.route)
    fun navigateToPrimary(dest: MaxDestination) = navController.navigate(dest.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun openApp(pkg: String) = navController.navigate("app_settings/" + pkg)
    fun back() = navController.popBackStack()
    fun openActivityDetail(packageName: String) = navController.navigate("app_detail/" + packageName)
}