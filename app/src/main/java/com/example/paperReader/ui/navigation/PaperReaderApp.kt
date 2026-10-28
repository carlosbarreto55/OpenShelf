package com.example.paperReader.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
        NavHost(
            navController = navController,
            // Switching tabs is not forward navigation, so it does not animate. The default
            // crossfade kept both destinations composed and half-opaque for its whole duration,
            // and rapid taps stacked a new fade on one still running, overlapping the text of two
            // or three screens. Even a transition of no length keeps the outgoing screen composed
            // for a frame or two, so it is hidden from the first of them rather than left drawn
            // under the new one — except beneath a detail push, which slides over it.
            enterTransition = { EnterTransition.None },
            exitTransition = {
                if (targetState.destination.hasRoute<PaperReaderRoute.PaperDetail>()) {
                    ExitTransition.None
                } else {
                    hiddenAtOnce
                }
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { hiddenAtOnce },
            startDestination = PaperReaderRoute.Search,
            modifier = Modifier.padding(innerPadding),
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
            // A slide and no fade: detail draws an opaque surface, so while it moves it covers the
            // screen beneath instead of blending its text with it. Leaving detail for a tab is a
            // tab switch and stays instant; only push and back move.
            composable<PaperReaderRoute.PaperDetail>(
                enterTransition = {
                    slideIntoContainer(SlideDirection.Start, tween(DETAIL_SLIDE_MILLIS))
                },
                popExitTransition = {
                    slideOutOfContainer(SlideDirection.End, tween(DETAIL_SLIDE_MILLIS))
                },
            ) {
                PaperDetailRoute(
                    onBack = { navController.popBackStack() },
                    onNavigateTo = navigateToErrorRoute,
                )
            }
        }
    }
}

/** Long enough to read as movement, short enough that a quick back-and-forth never waits on it. */
private const val DETAIL_SLIDE_MILLIS = 250

/** Gone from the first frame it is leaving on; see the NavHost's transitions. */
private val hiddenAtOnce = fadeOut(snap())

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
