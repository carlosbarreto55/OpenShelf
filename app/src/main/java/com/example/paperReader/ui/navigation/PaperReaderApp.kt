package com.example.paperReader.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.components.ErrorRoute
import com.example.paperReader.ui.downloads.DownloadsRoute
import com.example.paperReader.ui.library.LibraryRoute
import com.example.paperReader.ui.paperdetail.PaperDetailRoute
import com.example.paperReader.ui.search.SearchRoute

@Composable
fun PaperReaderApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val label = stringResource(destination.labelResId)
                    NavigationBarItem(
                        selected = currentDestination.isSelected(destination.route),
                        onClick = {
                            navController.navigate(destination.route, navOptions {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            })
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(label) },
                        modifier = Modifier.semantics {
                            contentDescription = label
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        // The second route an error surface offers. Routed like a bottom-bar tap (single top,
        // state restored) so "open your library" from a failed search lands on the same Library the
        // user would otherwise reach, rather than stacking a second copy of it.
        val navigateToErrorRoute: (ErrorRoute) -> Unit = { route ->
            navController.navigate(route.destination(), navOptions {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            })
        }

        // No demo notice here: disclosing placeholder data is owed *once*, not as permanent
        // chrome on every destination. The search screen carries the dismissible explanation and
        // the indicator that reopens it; a simulated download carries its own disclaimer.
        //
        // The padding already covers the status bar and the navigation bar (the bottom bar
        // includes the latter), so it is also marked consumed. Padding alone does not tell a
        // destination's own Scaffold or TopAppBar that, and the detail screen's applied both
        // insets a second time: a status-bar-high strip above its app bar, and another
        // navigation-bar-high one at the foot of its content.
        NavHost(
            navController = navController,
            startDestination = PaperReaderRoute.Search,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<PaperReaderRoute.Search> {
                SearchRoute(
                    onOpenPaper = { paperId: PaperId ->
                        navController.navigate(PaperReaderRoute.PaperDetail(paperId.value))
                    },
                    onNavigateTo = navigateToErrorRoute,
                )
            }
            composable<PaperReaderRoute.Library> {
                LibraryRoute(
                    onOpenPaper = { paperId: PaperId ->
                        navController.navigate(PaperReaderRoute.PaperDetail(paperId.value))
                    },
                )
            }
            composable<PaperReaderRoute.Downloads> {
                DownloadsRoute()
            }
            composable<PaperReaderRoute.PaperDetail> {
                PaperDetailRoute(
                    onBack = { navController.popBackStack() },
                    onNavigateTo = navigateToErrorRoute,
                )
            }
        }
    }
}

/** The one place an [ErrorRoute] becomes a destination, so nothing below navigation needs to know
 * the graph exists. */
private fun ErrorRoute.destination(): PaperReaderRoute = when (this) {
    ErrorRoute.LIBRARY -> PaperReaderRoute.Library
    ErrorRoute.SEARCH -> PaperReaderRoute.Search
    ErrorRoute.DOWNLOADS -> PaperReaderRoute.Downloads
}

private fun NavDestination?.isSelected(route: PaperReaderRoute): Boolean {
    return when (route) {
        PaperReaderRoute.Search -> this?.hierarchyMatches<PaperReaderRoute.Search>() == true
        PaperReaderRoute.Library -> this?.hierarchyMatches<PaperReaderRoute.Library>() == true
        PaperReaderRoute.Downloads -> this?.hierarchyMatches<PaperReaderRoute.Downloads>() == true
        is PaperReaderRoute.PaperDetail -> false
    }
}

private inline fun <reified T : Any> NavDestination.hierarchyMatches(): Boolean {
    return hierarchy.any { it.hasRoute<T>() }
}
