package com.dergoogler.mmrl.ui.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination

@Composable
fun HomeNavigationButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val navigator = LocalDestinationsNavigator.current
    val label = stringResource(R.string.page_home)

    TextButton(
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        onClick = {
            navigator.navigate(HomeScreenDestination) {
                popUpTo(NavGraphs.root) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        Text(label)
    }
}
