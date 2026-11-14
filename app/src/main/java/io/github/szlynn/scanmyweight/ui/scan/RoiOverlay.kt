package io.github.szlynn.scanmyweight.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semi-transparent overlay with a cut-out ROI bounding box.
 *
 * The overlay darkens everything outside the ROI to guide the user to align
 * the scale display within the highlighted rectangle.
 *
 * @param horizontalPaddingFraction Fraction of total width to inset from each side (0..0.5).
 * @param verticalCenterFraction    Vertical centre of the ROI as a fraction of total height (0..1).
 * @param roiHeightFraction         Height of the ROI as a fraction of total height (0..1).
 * @param dimColor                  Color of the dimmed area outside the ROI.
 * @param strokeColor               Color of the ROI border.
 * @param cornerRadius              Corner radius of the ROI rectangle.
 * @param strokeWidth               Width of the ROI border stroke.
 */
@Composable
fun RoiOverlay(
    modifier: Modifier = Modifier,
    horizontalPaddingFraction: Float = 0.06f,
    verticalCenterFraction: Float = 0.42f,
    roiHeightFraction: Float = 0.18f,
    dimColor: Color = Color.Black.copy(alpha = 0.55f),
    strokeColor: Color = Color.White,
    cornerRadius: Dp = 8.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier) {
        val roiLeft = size.width * horizontalPaddingFraction
        val roiRight = size.width * (1f - horizontalPaddingFraction)
        val roiHeight = size.height * roiHeightFraction
        val roiTop = size.height * verticalCenterFraction - roiHeight / 2f
        val roiBottom = roiTop + roiHeight
        val cornerPx = cornerRadius.toPx()

        val roiRect = Rect(roiLeft, roiTop, roiRight, roiBottom)

        val roiPath = Path().apply {
            addRoundRect(RoundRect(roiRect, CornerRadius(cornerPx)))
        }

        // Dim the area outside the ROI.
        clipPath(roiPath, clipOp = ClipOp.Difference) {
            drawRect(color = dimColor)
        }

        // Draw the ROI border.
        drawPath(
            path = roiPath,
            color = strokeColor,
            style = Stroke(width = strokeWidth.toPx()),
        )
    }
}
