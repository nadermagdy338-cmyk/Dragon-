package nd.max.ui.subscreens.hubs
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import nd.max.ui.navigation.*
@Composable fun NetworkHubScreen(navController: NavHostController) = MaxDomainHubScreen(navController, MaxDestination.NetworkHub)
