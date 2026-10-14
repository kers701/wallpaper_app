package com.kers.killove.jhsy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/**
 * 3×3 图案锁。松手后回调 [onComplete]；点数不足 4 时不回调成功逻辑由调用方判断。
 */
@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF64B5F6),
    dotColor: Color = Color(0xFF90CAF9),
    onComplete: (List<Int>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var centers by remember { mutableStateOf(List(9) { Offset.Zero }) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selected.clear()
                        finger = offset
                        hitCell(offset, centers)?.let { if (it !in selected) selected.add(it) }
                    },
                    onDrag = { change, _ ->
                        val pos = change.position
                        finger = pos
                        hitCell(pos, centers)?.let { if (it !in selected) selected.add(it) }
                    },
                    onDragEnd = {
                        finger = null
                        if (selected.isNotEmpty()) {
                            onComplete(selected.toList())
                        }
                    },
                    onDragCancel = {
                        finger = null
                        selected.clear()
                    }
                )
            }
    ) {
        val pad = size.minDimension * 0.12f
        val step = (size.minDimension - pad * 2) / 2f
        val pts = (0 until 9).map { i ->
            val row = i / 3
            val col = i % 3
            Offset(pad + col * step, pad + row * step)
        }
        centers = pts
        val r = size.minDimension * 0.045f

        // lines
        if (selected.size >= 2) {
            for (i in 0 until selected.lastIndex) {
                drawLine(
                    color = lineColor,
                    start = pts[selected[i]],
                    end = pts[selected[i + 1]],
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }
        if (selected.isNotEmpty() && finger != null) {
            drawLine(
                color = lineColor.copy(alpha = 0.7f),
                start = pts[selected.last()],
                end = finger!!,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        pts.forEachIndexed { i, c ->
            val active = i in selected
            drawCircle(
                color = if (active) lineColor else dotColor.copy(alpha = 0.55f),
                radius = if (active) r * 1.15f else r,
                center = c
            )
            drawCircle(
                color = if (active) lineColor else dotColor,
                radius = r * 0.35f,
                center = c
            )
            if (active) {
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = r * 1.8f,
                    center = c,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun hitCell(pos: Offset, centers: List<Offset>): Int? {
    if (centers.size < 9) return null
    val threshold = centers.let {
        if (it.size < 2) 80f
        else hypot(it[1].x - it[0].x, it[1].y - it[0].y) * 0.35f
    }
    var best = -1
    var bestD = Float.MAX_VALUE
    centers.forEachIndexed { i, c ->
        val d = hypot(pos.x - c.x, pos.y - c.y)
        if (d < threshold && d < bestD) {
            bestD = d
            best = i
        }
    }
    return best.takeIf { it >= 0 }
}
