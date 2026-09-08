package nd.max.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MaxManager's shared transient feedback surface.
 *
 * All screens use the same floating treatment so success/error/info messages
 * feel like one product instead of a mixture of stock Material snackbars and
 * legacy Toasts. The host deliberately stays neutral; the message itself is
 * the source of truth and existing callers keep their current behavior.
 *
 * v2 floats the pill higher above the gesture area and adds a soft shadow so
 * it reads as an overlay rather than a strip pinned to the content.
 */
@Composable
fun MaxSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(
            start = MaxUiMetrics.screenHorizontalPadding,
            end = MaxUiMetrics.screenHorizontalPadding,
            bottom = 16.dp + bottomInset
        )
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(MaxUiMetrics.smallRadius + 4.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionContentColor = MaterialTheme.colorScheme.inversePrimary,
            dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}
