/*
 * Configuration backup / restore.
 *
 * This page is the explained home of the flow: what a backup contains, what is
 * verified before anything is written. The flow itself — file pickers, scope
 * checkboxes, SoC guard, messages — lives once in
 * [nd.max.ui.component.rememberConfigBackupFlow], which the Control screen's
 * top-bar action shares, so the two entry points can never drift apart.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.rememberConfigBackupFlow
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.viewmodel.TweakViewModel

@Composable
fun ConfigBackupScreen(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel(),
) {
    val navActions = MaxNavActions(navController)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backupFlow = rememberConfigBackupFlow(viewModel) { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    MaxScreen(
        title = stringResource(R.string.max_nav_config_backup),
        subtitle = stringResource(R.string.max_config_backup_desc),
        onBack = navActions::back,
        accentIcon = Icons.Rounded.Backup,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_nav_config_backup),
                body = stringResource(R.string.max_config_backup_help),
            )
        },
    ) {
        MaxSection(title = stringResource(R.string.max_config_backup_section)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_config_backup_action),
                    subtitle = stringResource(R.string.max_config_backup_action_desc),
                    icon = Icons.Rounded.Backup,
                    iconTone = MaxTone.Accent,
                    onClick = backupFlow::startBackup,
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_config_restore_action),
                    subtitle = stringResource(R.string.max_config_restore_action_desc),
                    icon = Icons.Rounded.Restore,
                    iconTone = MaxTone.Caution,
                    onClick = backupFlow::startRestore,
                )
            }
        }

        MaxSection(title = stringResource(R.string.max_config_backup_scope_title)) {
            MaxBullets(
                lines = listOf(
                    stringResource(R.string.str_tweak_configuration_settings),
                    stringResource(R.string.str_per_app_applist_settings),
                    stringResource(R.string.max_config_backup_scope_note),
                )
            )
        }
    }
}
