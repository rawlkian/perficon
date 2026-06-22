package com.kian.perficon.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.max

class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress = percentage).asComposePath()
        val bounds = path.getBounds()

        matrix.reset()
        val scale = max(size.width / bounds.width, size.height / bounds.height)
        matrix.scale(scale, scale)
        matrix.translate(-bounds.left * scale, -bounds.top * scale)

        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@Composable
fun MorphingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shapeA = remember {
        RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(0.2f)
        )
    }
    val shapeB = remember {
        RoundedPolygon(
            numVertices = 8,
            rounding = CornerRounding(0.5f)
        )
    }
    val morph = remember { Morph(shapeA, shapeB) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "morphProgress"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(MorphPolygonShape(morph, progress))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
