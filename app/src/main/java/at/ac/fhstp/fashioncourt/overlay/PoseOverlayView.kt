package at.ac.fhstp.fashioncourt.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null

    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    // indices use MediaPipe's landmark indices
    private val bodyConnections = listOf(
        11 to 12,          // shoulders
        11 to 13, 13 to 15, // left arm
        12 to 14, 14 to 16, // right arm
        11 to 23, 12 to 24, // torso sides
        23 to 24,          // hips
        23 to 25, 25 to 27, // left leg
        24 to 26, 26 to 28  // right leg
    )

    fun updateResults(result: PoseLandmarkerResult) {
        results = result
        invalidate()   // trigger redraw
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val result = results ?: return
        if (result.landmarks().isEmpty()) return

        val landmarks = result.landmarks()[0]

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // draw bones
        for ((startIdx, endIdx) in bodyConnections) {
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                val start = landmarks[startIdx]
                val end = landmarks[endIdx]

                val startX = start.x() * viewWidth
                val startY = start.y() * viewHeight
                val endX = end.x() * viewWidth
                val endY = end.y() * viewHeight

                canvas.drawLine(startX, startY, endX, endY, linePaint)
            }
        }

        // draw joints
        for (lm in landmarks) {
            val cx = lm.x() * viewWidth
            val cy = lm.y() * viewHeight
            canvas.drawCircle(cx, cy, 12f, pointPaint)
        }
    }
}
