package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.theme.Colors
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlider(
    steps: Int = 0,
    startValue: Int,
    endValue: Int,
    value: Int,
    defaultValue: Int? = null,
    primaryColor: Color = Colors.PrimaryBlue,
    innerCircleColor: Color = Color.White,
    onValueChangeFinished: ((Int) -> Unit)? = null,
    onValueChange: ((Int) -> Unit)? = null,
) {
    // Internal state stays Float because M3 Slider requires Float for smooth dragging
    var internalValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    val safeSteps = max(0, steps)
    var trackWidth by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var previousStep by remember { mutableIntStateOf(-1) }
    val valueRange = endValue.toFloat() - startValue.toFloat()

    // Simplified default check since we are dealing with whole numbers
    val isDefaultValueSelected = defaultValue != null && internalValue.roundToInt() == defaultValue
    val currentInnerCircleColor = if (isDefaultValueSelected) Color.Red else innerCircleColor

    fun updateHaptics(newValue: Float) {
        val progress = if (valueRange > 0f) ((newValue - startValue) / valueRange).coerceIn(0f, 1f) else 0f
        val selectedIndex = if (safeSteps > 0) {
            val totalPositions = safeSteps + 2
            (progress * (totalPositions - 1)).roundToInt().coerceIn(0, totalPositions - 1)
        } else {
            if (progress >= .5f) 1 else 0
        }

        if (selectedIndex != previousStep) {
            if (previousStep != -1) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            previousStep = selectedIndex
        }
    }

    LaunchedEffect(internalValue) {
        updateHaptics(internalValue)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(internalValue, trackWidth) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val downChange = event.changes.firstOrNull { it.changedToDown() }

                        if (downChange != null && trackWidth.width > 0) {
                            val touchX = downChange.position.x
                            val progress = if (valueRange > 0f) ((internalValue - startValue) / valueRange).coerceIn(0f, 1f) else 0f

                            val trackStartPx = (size.width - trackWidth.width) / 2f
                            val thumbCenterPx = trackStartPx + (progress * trackWidth.width)

                            val touchRadiusPx = 24.dp.toPx()

                            if (kotlin.math.abs(touchX - thumbCenterPx) > touchRadiusPx) {
                                downChange.consume()
                            }
                        }
                    }
                }
            }
    ) {
        Slider(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                // Convert back to Int for the public API callback
                onValueChange?.invoke(newValue.roundToInt())
                updateHaptics(newValue)
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke(internalValue.roundToInt())
            },
            valueRange = startValue.toFloat()..endValue.toFloat(),
            steps = safeSteps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = primaryColor,
                activeTrackColor = primaryColor,
                inactiveTrackColor = Color(0xFFBCBCBC)
            ),
            thumb = { CustomSliderThumb(primaryColor, currentInnerCircleColor) },
            track = { sliderState ->
                CustomSliderTrack(
                    sliderState = sliderState,
                    trackWidth = trackWidth,
                    onTrackSized = { trackWidth = it },
                    density = density,
                    startValue = startValue,
                    endValue = endValue,
                    valueRange = valueRange,
                    steps = safeSteps,
                    primaryColor = primaryColor,
                    defaultValue = defaultValue
                )
            }
        )
    }
}

@Composable
private fun CustomSliderThumb(
    primaryColor: Color,
    innerCircleColor: Color
) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(primaryColor)
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(innerCircleColor)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSliderTrack(
    sliderState: SliderState,
    trackWidth: IntSize,
    onTrackSized: (IntSize) -> Unit,
    density: Density,
    startValue: Int,
    endValue: Int,
    valueRange: Float,
    steps: Int,
    primaryColor: Color,
    defaultValue: Int? // Changed to Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(5.dp).height(8.dp).background(primaryColor))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .onGloballyPositioned { onTrackSized(it.size) },
            contentAlignment = Alignment.CenterStart
        ) {
            if (trackWidth != IntSize.Zero) {
                val trackWidthDp = with(density) { trackWidth.width.toDp() }
                val progress = if (valueRange > 0f) ((sliderState.value - startValue) / valueRange).coerceIn(0f, 1f) else 0f

                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFBCBCBC)))
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(primaryColor))

                if (steps in 1..20) {
                    val totalSegments = steps + 1
                    for (step in 1..steps) {
                        val pos = step.toFloat() / totalSegments
                        val offsetX = trackWidthDp * pos - 2.dp
                        Box(
                            Modifier.offset(x = offsetX).size(4.dp, 8.dp).background(Color.Gray)
                        )
                    }
                }

                // Range check simplified for Int bounds
                if (defaultValue != null && defaultValue in startValue..endValue) {
                    val defaultProgress = if (valueRange > 0f) (defaultValue - startValue).toFloat() / valueRange else 0f
                    val defaultOffsetX = (trackWidthDp * defaultProgress) - 1.5.dp

                    Box(
                        Modifier
                            .offset(x = defaultOffsetX)
                            .size(3.dp, 14.dp)
                            .background(Color.Red)
                    )
                }
            }
        }
        Box(modifier = Modifier.width(5.dp).height(8.dp).background(Color(0xFFBCBCBC)))
    }
}