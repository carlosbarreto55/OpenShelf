package com.example.paperReader.ui.navigation

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.components.DemoDataNotice
import com.example.paperReader.ui.downloads.DownloadsRoute
import com.example.paperReader.ui.library.LibraryRoute
import com.example.paperReader.ui.paperdetail.PaperDetailRoute
import com.example.paperReader.ui.search.SearchRoute
import com.example.paperReader.ui.theme.spacing

@Composable
fun PaperReaderApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDemoData = remember { BackendConfiguration.fromBuildConfig().mode == BackendMode.FAKE }

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
        Column(modifier = Modifier.padding(innerPadding)) {
            if (isDemoData) {
                DemoDataNotice(
                    message = stringResource(R.string.demo_data_notice),
                    modifier = Modifier.padding(MaterialTheme.spacing.compact),
                )
            }
            NavHost(
                navController = navController,
                startDestination = PaperReaderRoute.Search,
                modifier = Modifier.weight(1f),
            ) {
                composable<PaperReaderRoute.Search> {
                    SearchRoute(
                        onOpenPaper = { paperId: PaperId ->
                            navController.navigate(PaperReaderRoute.PaperDetail(paperId.value))
                        },
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
                    PaperDetailRoute(onBack = { navController.popBackStack() })
                }
            }
        }
    }
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
