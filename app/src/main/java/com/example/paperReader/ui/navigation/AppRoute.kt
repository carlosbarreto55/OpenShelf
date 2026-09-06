package com.example.paperReader.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.paperReader.R
import kotlinx.serialization.Serializable

@Serializable
sealed interface PaperReaderRoute {
    @Serializable
    data object Search : PaperReaderRoute

    @Serializable
    data object Library : PaperReaderRoute

    @Serializable
    data object Downloads : PaperReaderRoute
}

data class TopLevelDestination(
    val route: PaperReaderRoute,
    @param:StringRes val labelResId: Int,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(PaperReaderRoute.Search, R.string.nav_search, Icons.Outlined.Search),
    TopLevelDestination(PaperReaderRoute.Library, R.string.nav_library, Icons.Outlined.BookmarkBorder),
    TopLevelDestination(PaperReaderRoute.Downloads, R.string.nav_downloads, Icons.Outlined.Download),
)
