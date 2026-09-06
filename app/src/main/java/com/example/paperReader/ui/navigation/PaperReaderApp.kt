package com.example.paperReader.ui.navigation

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
import com.example.paperReader.ui.screens.DownloadsPlaceholderScreen
import com.example.paperReader.ui.screens.LibraryPlaceholderScreen
import com.example.paperReader.ui.screens.SearchPlaceholderScreen

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
        NavHost(
            navController = navController,
            startDestination = PaperReaderRoute.Search,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<PaperReaderRoute.Search> {
                SearchPlaceholderScreen()
            }
            composable<PaperReaderRoute.Library> {
                LibraryPlaceholderScreen()
            }
            composable<PaperReaderRoute.Downloads> {
                DownloadsPlaceholderScreen()
            }
        }
    }
}

private fun NavDestination?.isSelected(route: PaperReaderRoute): Boolean {
    return when (route) {
        PaperReaderRoute.Search -> this?.hierarchyMatches<PaperReaderRoute.Search>() == true
        PaperReaderRoute.Library -> this?.hierarchyMatches<PaperReaderRoute.Library>() == true
        PaperReaderRoute.Downloads -> this?.hierarchyMatches<PaperReaderRoute.Downloads>() == true
    }
}

private inline fun <reified T : Any> NavDestination.hierarchyMatches(): Boolean {
    return hierarchy.any { it.hasRoute<T>() }
}
