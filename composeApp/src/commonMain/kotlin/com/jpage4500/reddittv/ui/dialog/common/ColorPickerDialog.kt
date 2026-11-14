package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.OutlinedLabel
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import reddittv.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.*

/**
 * color picker dialog with a color wheel, brightness slider, and optional alpha slider
 * @param initialColor initial color value
 * @param allowAlpha show alpha slider if true
 * @param onColorSelected callback with selected color
 * @param onDismiss dismiss callback
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color? = null,
    allowAlpha: Boolean = false,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = initialColor ?: Color.White
    val initialHsv = remember { rgbToHsv(initial) }
    var color by remember { mutableStateOf(initial) }
    var alpha by remember { mutableStateOf(initial.alpha) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(TextFieldValue(colorToHex(initial, allowAlpha))) }

    // use rememberUpdatedState so pointerInput lambda always reads latest values
    val brightnessState by rememberUpdatedState(brightness)
    val alphaState by rememberUpdatedState(alpha)

    BaseDialog(
        title = stringResource(Res.string.pick_a_color),
        okText = stringResource(Res.string.ok),
        cancelText = stringResource(Res.string.cancel),
        horizontalAlignment = Alignment.CenterHorizontally,
        onDismiss = onDismiss,
        onConfirm = { onColorSelected(color) }
    ) {
        // required to avoid cutting off color wheel
        Spacer(Modifier.height(5.dp))

        val wheelSize = 220.dp
        val diameterPx = with(LocalDensity.current) { wheelSize.toPx() }
        val radius = diameterPx / 2

        Row(verticalAlignment = Alignment.CenterVertically) {
            // color wheel with drag handler
            Box(
                modifier = Modifier
                    .heightIn(min = wheelSize)
                    .width(wheelSize)
                    .pointerInput(diameterPx, radius) {
                        awaitPointerEventScope {
                            while (true) {
                                // wait for initial down event
                                val downEvent = awaitPointerEvent()
                                val down = downEvent.changes.firstOrNull { it.pressed }
                                if (down == null) continue
                                var pointer = down
                                while (pointer != null && pointer.pressed) {
                                    val x = pointer.position.x - radius
                                    val y = pointer.position.y - radius
                                    val r = sqrt(x * x + y * y)
                                    if (r <= radius) {
                                        val hue = ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
                                        val sat = (r / radius).coerceIn(0f, 1f)
                                        val newColor = Color.hsv(hue, sat, brightnessState)
                                        color = if (allowAlpha) newColor.copy(alpha = alphaState) else newColor.copy(alpha = 1f)
                                        hexInput = TextFieldValue(colorToHex(color, allowAlpha))
                                    }
                                    // wait for next move or up event
                                    val moveEvent = awaitPointerEvent()
                                    pointer = moveEvent.changes.firstOrNull { it.pressed }
                                }
                            }
                        }
                    }
            ) {
                ColorWheel(
                    selectedColor = color,
                    onColorChange = { picked ->
                        val hsv = rgbToHsv(picked)
                        val adjusted = Color.hsv(hsv[0], hsv[1], brightness)
                        color = if (allowAlpha) adjusted.copy(alpha = alpha) else adjusted.copy(alpha = 1f)
                        hexInput = TextFieldValue(colorToHex(color, allowAlpha))
                    },
                    size = wheelSize
                )
            }

            Spacer(Modifier.width(8.dp))

            // swatch on top, hex field below - both match column width
            Column(
                modifier = Modifier.width(105.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(color, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newVal ->
                        hexInput = newVal
                        val parsed = hexToColor(newVal.text)
                        if (parsed != null) {
                            color = if (allowAlpha) parsed else parsed.copy(alpha = 1f)
                            alpha = color.alpha
                            brightness = rgbToHsv(color)[2]
                        }
                    },
                    label = { Text(stringResource(Res.string.hex)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // brightness slider below wheel
        Spacer(Modifier.height(4.dp))
        OutlinedSlider(
            label = stringResource(Res.string.brightness),
            value = brightness,
            onValueChange = { newBrightness ->
                brightness = newBrightness
                val hsv = rgbToHsv(color)
                val newColor = Color.hsv(hsv[0], hsv[1], newBrightness)
                color = if (allowAlpha) newColor.copy(alpha = alpha) else newColor.copy(alpha = 1f)
                hexInput = TextFieldValue(colorToHex(color, allowAlpha))
            }
        )

        if (allowAlpha) {
            Spacer(Modifier.height(4.dp))
            OutlinedSlider(
                label = stringResource(Res.string.alpha),
                value = alpha,
                onValueChange = {
                    alpha = it
                    color = color.copy(alpha = it)
                    hexInput = TextFieldValue(colorToHex(color, allowAlpha))
                }
            )
        }
    }
}

private fun colorToHex(color: Color?, withAlpha: Boolean): String {
    if (color == null) return ""
    val a = (color.alpha * 255).roundToInt()
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    fun hex2(v: Int) = v.toString(16).padStart(2, '0').uppercase()
    return if (withAlpha) "#${hex2(a)}${hex2(r)}${hex2(g)}${hex2(b)}" else "#${hex2(r)}${hex2(g)}${hex2(b)}"
}

private fun hexToColor(hex: String): Color? {
    val cleanHex = hex.removePrefix("#")
    return try {
        when (cleanHex.length) {
            6 -> {
                val r = cleanHex.substring(0, 2).toInt(16)
                val g = cleanHex.substring(2, 4).toInt(16)
                val b = cleanHex.substring(4, 6).toInt(16)
                Color(r / 255f, g / 255f, b / 255f, 1f)
            }

            8 -> {
                val a = cleanHex.substring(0, 2).toInt(16)
                val r = cleanHex.substring(2, 4).toInt(16)
                val g = cleanHex.substring(4, 6).toInt(16)
                val b = cleanHex.substring(6, 8).toInt(16)
                Color(r / 255f, g / 255f, b / 255f, a / 255f)
            }

            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ColorWheel(
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    size: Dp
) {
    val diameterPx = with(LocalDensity.current) { size.toPx() }
    val radius = diameterPx / 2
    // ensure latest lambda is always used inside the long-lived pointerInput coroutine
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x - radius
                    val y = offset.y - radius
                    val r = sqrt(x * x + y * y)
                    if (r <= radius) {
                        val hue = ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
                        val sat = (r / radius).coerceIn(0f, 1f)
                        val color = Color.hsv(hue, sat, 1f)
                        currentOnColorChange(color)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until 360 step 2) {
                for (j in 0..100 step 4) {
                    val angle = i * (PI / 180f)
                    val sat = j / 100f
                    val r = sat * radius
                    val x = cos(angle) * r + radius
                    val y = sin(angle) * r + radius
                    drawCircle(
                        color = Color.hsv(i.toFloat(), sat, 1f),
                        radius = 7f,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
            // draw selector at hue/sat position
            val hsv = rgbToHsv(selectedColor)
            val selAngle = hsv[0] * (PI / 180f)
            val selSat = hsv[1]
            val selR = selSat * radius
            val selX = cos(selAngle) * selR + radius
            val selY = sin(selAngle) * selR + radius
            drawCircle(
                color = Color.White,
                radius = 14f,
                center = Offset(selX.toFloat(), selY.toFloat())
            )
            drawCircle(
                color = selectedColor,
                radius = 10f,
                center = Offset(selX.toFloat(), selY.toFloat())
            )
        }
    }
}

private fun rgbToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var h = 0f
    val s: Float
    val v = max
    s = if (max == 0f) 0f else delta / max
    if (delta == 0f) {
        h = 0f
    } else if (max == r) {
        h = 60f * (((g - b) / delta) % 6)
    } else if (max == g) {
        h = 60f * (((b - r) / delta) + 2)
    } else if (max == b) {
        h = 60f * (((r - g) / delta) + 4)
    }
    if (h < 0f) h += 360f
    return floatArrayOf(h, s, v)
}

@Composable
private fun OutlinedSlider(
    label: String,
    primaryColor: Color = Color.Black,
    innerCircleColor: Color = Color.White,
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit
) {
    OutlinedLabel(label = label) {
        CustomSlider(
            startValue = 0,
            endValue = 100,
            steps = 99,
            value = (value * 100f).roundToInt(),
            primaryColor = primaryColor,
            innerCircleColor = innerCircleColor,
            onValueChange = { onValueChange(it / 100f) }
        )
    }
}
