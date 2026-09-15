package nd.max.ui.mainscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import nd.max.R
import nd.max.ui.design.MaxDomainCard
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.subscreens.hubs.maxHubDescription

/**
 * The Control primary destination: the nine device-domain hubs (ADR-04)
 * plus the legacy flat tweaks workspace while its toggle rows await a home.
 */
@Composable
fun ControlScreen(navController: NavHostController) {
    val actions = MaxNavActions(navController)
    val hubs = MaxDestination.All.filter { it.parent == MaxDestination.Control && it != MaxDestination.AllTweaks }

    MaxListScreen(
        title = stringResource(R.string.max_nav_control),
        onBack = actions::back,
        accentIcon = MaxDestination.Control.icon,
    ) {
        hubs.forEach { hub ->
            item(key = hub.route) {
                MaxDomainCard(destination = hub, subtitle = stringResource(maxHubDescription(hub)), onClick = { actions.navigateTo(hub) })
            }
        }
        item(key = MaxDestination.AllTweaks.route) {
            MaxSection(title = stringResource(R.string.max_nav_all_tweaks_section)) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_nav_all_tweaks),
                        subtitle = stringResource(R.string.max_nav_all_tweaks_desc),
                        icon = MaxDestination.AllTweaks.icon,
                        onClick = { actions.navigateTo(MaxDestination.AllTweaks) },
                    )
                }
            }
        }
    }
}
