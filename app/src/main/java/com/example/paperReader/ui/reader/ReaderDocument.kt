package com.example.paperReader.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import com.example.paperReader.R
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.ui.components.AccessibleIconButton
import com.example.paperReader.ui.theme.spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The pages, top to bottom, at the current zoom. Zoom is a page *width*: the list is laid out that
 * wide inside a horizontal scroll, and the window renders pages for that width, so a zoomed page
 * is a sharper render rather than a stretched one.
 *
 * The place in the document is kept as a page and a fraction of it, not a pixel offset, and the
 * list is scrolled back to it whenever the page width changes — after a rotation, or a zoom — since
 * the same pixel offset would then point somewhere else.
 */
@Composable
internal fun DocumentPages(
    content: ReaderContent.Reading,
    pageImages: Map<Int, PageImage>,
    onViewport: (ReaderViewport) -> Unit,
    onPositionChanged: (ReadingPosition) -> Unit,
    onZoomChanged: (Float) -> Unit,
) {
    val pages = content.pages
    var zoom by rememberSaveable { mutableFloatStateOf(content.initialZoom) }
    var anchorPage by rememberSaveable { mutableIntStateOf(content.initialPosition.pageIndex) }
    var anchorFraction by rememberSaveable { mutableFloatStateOf(content.initialPosition.pageOffsetFraction) }
    var goToPageOpen by rememberSaveable { mutableStateOf(false) }
    val listState = remember { LazyListState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val zoomLabel = stringResource(R.string.reader_zoom_state, (zoom * 100).roundToInt())
    val zoomIn = stringResource(R.string.reader_zoom_in)
    val zoomOut = stringResource(R.string.reader_zoom_out)
    val zoomReset = stringResource(R.string.reader_zoom_reset)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val currentOnViewport by rememberUpdatedState(onViewport)

    fun setZoom(value: Float) {
        zoom = value.coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
        onZoomChanged(zoom)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    // Two fingers zoom; one finger is left to the scrolling below.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val change = event.calculateZoom()
                                if (change != 1f) {
                                    setZoom(zoom * change)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .semantics {
                    stateDescription = zoomLabel
                    customActions = listOf(
                        CustomAccessibilityAction(zoomIn) { setZoom(zoom + ZOOM_STEP); true },
                        CustomAccessibilityAction(zoomOut) { setZoom(zoom - ZOOM_STEP); true },
                        CustomAccessibilityAction(zoomReset) { setZoom(ReaderViewModel.MIN_ZOOM); true },
                    )
                },
        ) {
            val viewportWidthPx = constraints.maxWidth
            val viewportHeightPx = constraints.maxHeight
            val pageWidthPx = (viewportWidthPx * zoom).roundToInt()
            val pageWidth = with(density) { pageWidthPx.toDp() }

            // Captured during composition, before the list lays out at the new width and its old
            // pixel offset starts to mean a different fraction.
            val anchorAtThisWidth = remember(pageWidthPx) { ReadingPosition(anchorPage, anchorFraction) }
            LaunchedEffect(pageWidthPx) {
                val heightPx = pageWidthPx * pages[anchorAtThisWidth.pageIndex].heightPerWidth
                listState.scrollToItem(anchorAtThisWidth.pageIndex, (anchorAtThisWidth.pageOffsetFraction * heightPx).roundToInt())
            }

            LaunchedEffect(listState, pageWidthPx) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { (index, offset) ->
                        val heightPx = pageWidthPx * pages[index].heightPerWidth
                        val fraction = if (heightPx > 0) (offset / heightPx).coerceIn(0f, 1f) else 0f
                        anchorPage = index
                        anchorFraction = fraction
                        currentOnPositionChanged(ReadingPosition(index, fraction))
                    }
            }

            LaunchedEffect(listState, viewportWidthPx, viewportHeightPx) {
                var reportedZoom = zoom
                snapshotFlow {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    ReaderViewport(
                        firstVisiblePage = visible.firstOrNull()?.index ?: anchorPage,
                        lastVisiblePage = visible.lastOrNull()?.index ?: anchorPage,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        zoom = zoom,
                    )
                }
                    .distinctUntilChanged()
                    .collectLatest { viewport ->
                        // Scrolling renders at once; a pinch renders once it settles, rather than
                        // at every intermediate width it passes through.
                        if (viewport.zoom != reportedZoom) delay(ZOOM_SETTLE_MILLIS)
                        reportedZoom = viewport.zoom
                        currentOnViewport(viewport)
                    }
            }

            Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(pageWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
                ) {
                    itemsIndexed(pages) { index, page ->
                        PageView(
                            index = index,
                            pageCount = pages.size,
                            image = pageImages[index],
                            height = with(density) { (pageWidthPx * page.heightPerWidth).toDp() },
                        )
                    }
                }
            }
        }

        PageControls(
            currentPage = anchorPage,
            pageCount = pages.size,
            onGoTo = { page -> scope.launch { listState.scrollToItem(page.coerceIn(0, pages.lastIndex)) } },
            onOpenGoToPage = { goToPageOpen = true },
        )
    }

    if (goToPageOpen) {
        GoToPageDialog(
            pageCount = pages.size,
            onGoTo = { page ->
                goToPageOpen = false
                scope.launch { listState.scrollToItem(page) }
            },
            onDismiss = { goToPageOpen = false },
        )
    }
}

@Composable
private fun PageView(index: Int, pageCount: Int, image: PageImage?, height: Dp) {
    val label = stringResource(R.string.reader_page_of, index + 1, pageCount)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().height(height).semantics { contentDescription = label },
    ) {
        when (image) {
            is PageImage.Ready -> {
                val bitmap = remember(image.bitmap) { image.bitmap.asImageBitmap() }
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.FillBounds)
            }

            // Said in words, never left blank: a blank page could pass for one in the paper.
            is PageImage.Unavailable -> Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.reader_page_unavailable, index + 1),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            null -> Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.reader_page_loading, index + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageControls(
    currentPage: Int,
    pageCount: Int,
    onGoTo: (Int) -> Unit,
    onOpenGoToPage: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.control),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccessibleIconButton(
                icon = Icons.Outlined.KeyboardArrowUp,
                contentDescription = stringResource(R.string.reader_previous_page),
                onClick = { onGoTo(currentPage - 1) },
                enabled = currentPage > 0,
            )
            val position = stringResource(R.string.reader_page_of, currentPage + 1, pageCount)
            val goToPage = stringResource(R.string.reader_go_to_page)
            TextButton(
                onClick = onOpenGoToPage,
                modifier = Modifier.semantics { contentDescription = "$goToPage, $position" },
            ) {
                Text(position)
            }
            AccessibleIconButton(
                icon = Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.reader_next_page),
                onClick = { onGoTo(currentPage + 1) },
                enabled = currentPage < pageCount - 1,
            )
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onGoTo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
                label = { Text(stringResource(R.string.reader_go_to_page_label, pageCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let { onGoTo(it - 1) } }, enabled = page != null) {
                Text(stringResource(R.string.reader_go_to_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_go_to_page_dismiss)) }
        },
    )
}

private const val ZOOM_STEP = 0.5f
private const val ZOOM_SETTLE_MILLIS = 150L
private const val MAX_PAGE_DIGITS = 5
