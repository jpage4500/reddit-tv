package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.theme.Colors
import kotlinx.coroutines.launch

/**
 * the indication a TV uses in place of the ripple, whose focus state is a ~10% tint no one can see
 * from the couch: a light brand-blue fill on whatever the remote is on.
 *
 * provided as LocalIndication by App on a TV, so every clickable that doesn't pick its own
 * indication gets it. Material components (Button, IconButton…) always use their ripple.
 */
object FocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}

private class FocusIndicationNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var isFocused = false
    private var isPressed = false

    override fun onAttach() {
        coroutineScope.launch {
            // counts rather than flags: a press can start before the last one's release lands
            var focusCount = 0
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressCount--
                }
                val focused = focusCount > 0
                val pressed = pressCount > 0
                if (focused != isFocused || pressed != isPressed) {
                    isFocused = focused
                    isPressed = pressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (isFocused || isPressed) drawRect(Colors.FocusHighlight)
    }
}

/**
 * a rounded outline while the clickable after it in the chain has focus, for a row or button whose
 * [FocusIndication] fill alone is easy to miss. goes ahead of the clickable: the outline is drawn
 * over its fill, and the fill is clipped to the same rounded shape. give what's inside padding of
 * its own, so the outline doesn't touch it
 */
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    onFocusChanged { isFocused = it.isFocused }
        .border(2.dp, if (isFocused) Colors.FocusOutline else Color.Transparent, shape)
        .clip(shape)
}

/**
 * a ring while this (or the button inside it) has keyboard focus — for the phone/desktop layouts,
 * where the ripple's focus tint is all a Tab or arrow key would otherwise show. touch never
 * focuses a button, so a phone only sees it with a keyboard. goes ahead of the clickable
 */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    onFocusChanged { isFocused = it.hasFocus }
        .border(2.dp, if (isFocused) Colors.FocusRing else Color.Transparent, shape)
}

/**
 * [focusRing] for a Material button (Button, TextButton…): passed as the button's own `border`, so
 * it follows the button's shape. a [focusRing] on the modifier goes around the 48dp touch target
 * instead, taller than the 40dp pill. give the button the same [interactionSource]
 */
@Composable
fun focusRingBorder(interactionSource: InteractionSource): BorderStroke? {
    val isFocused by interactionSource.collectIsFocusedAsState()
    return if (isFocused) BorderStroke(2.dp, Colors.FocusRing) else null
}
