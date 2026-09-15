package nd.max.ui.navigation

import androidx.navigation.NavHostController

/**
 * Typed navigation helpers handed to screens instead of a raw [NavHostController]
 * (ADR-02). Every route is derived from [MaxDestination]; no hand-written route
 * string may appear here or anywhere else outside the registry.
 */
data class MaxNavActions(private val navController: NavHostController) {
    fun navigateTo(dest: MaxDestination) = navController.navigate(dest.route)

    /** Single-top primary navigation with state saved (ADR-01, ADR-03). */
    fun navigateToPrimary(dest: MaxDestination) = navController.navigate(dest.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    /** Opens an app's settings workspace; route built from the registry. */
    fun openApp(pkg: String) = navController.navigate(
        MaxDestination.AppSettings.route.replace("{pkg}", pkg)
    )

    fun back() = navController.popBackStack()
}
