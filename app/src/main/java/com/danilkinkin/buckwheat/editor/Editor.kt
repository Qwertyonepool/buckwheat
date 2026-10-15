package com.danilkinkin.buckwheat.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import com.danilkinkin.buckwheat.util.observeLiveData
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.editor.dateTimeEdit.DateTimeEditPill
import com.danilkinkin.buckwheat.editor.tagging.TaggingToolbar
import com.danilkinkin.buckwheat.editor.toolbar.EditorToolbar
import com.danilkinkin.buckwheat.ui.BuckwheatTheme

enum class AnimState { EDITING, COMMIT, IDLE, RESET }

@Composable
fun Editor(
    modifier: Modifier = Modifier,
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    onOpenHistory: (() -> Unit)? = null,
) {
    val focusController = remember { FocusController() }
    val mode by editorViewModel.mode.observeAsState(EditMode.ADD)

    val spendFlightState = remember { SpendFlightState() }
    var spendFlight by remember { mutableStateOf<SpendFlight?>(null) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    var lastSeenCommit by remember { mutableIntStateOf(editorViewModel.spentCommitted.value ?: 0) }

    observeLiveData(editorViewModel.spentCommitted) {
        if (it <= lastSeenCommit) return@observeLiveData
        lastSeenCommit = it

        // Called synchronously from the commit, before the field is cleared, so the flight
        // starts on the same frame the amount disappears from the field
        val captured = spendFlightState.captured ?: return@observeLiveData
        val target = spendFlightState.barTarget ?: return@observeLiveData

        // committed faster than the capture after the last key press: skip rather than show
        // an outdated amount
        if (captured.rawValue != editorViewModel.rawSpentValue.value) return@observeLiveData

        val barValueHeight = spendFlightState.barValueHeight
        val endScale = if (barValueHeight > 0f) {
            (barValueHeight / captured.image.height).coerceIn(0.15f, 0.6f)
        } else {
            0.25f
        }

        spendFlight = SpendFlight(captured, target, endScale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
    ) {
        CompositionLocalProvider(LocalSpendFlight provides spendFlightState) {
        Column(
            Modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusController.focus() }
        ) {
            EditorToolbar()
            if (mode == EditMode.EDIT) {
                DateTimeEditPill()
            }
            CurrentSpendEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                focusController = focusController,
            )
            TaggingToolbar(editorFocusController = focusController)
            Spacer(Modifier.height(24.dp))
        }
        }

        SpendFlightOverlay(flight = spendFlight, rootOffset = rootOffset)
    }
}

@Preview
@Composable
fun EditorPreview() {
    BuckwheatTheme {
        Editor()
    }
}
