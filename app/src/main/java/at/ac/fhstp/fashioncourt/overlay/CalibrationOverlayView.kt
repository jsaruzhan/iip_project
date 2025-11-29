package at.ac.fhstp.fashioncourt.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class CalibrationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null

    private var shouldersDetected = false
    private var torsoDetected = false
    private var legsDetected = false
    private var feetDetected = false

    var onCalibrationComplete: (() -> Unit)? = null

    private val bracketPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val checkPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Green
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val pinkPaint = Paint().apply {
        color = Color.parseColor("#F8BBD9")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#880E4F")
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint().apply {
        color = Color.parseColor("#F48FB1")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    // Landmark indices
    private val LEFT_SHOULDER = 11
    private val RIGHT_SHOULDER = 12
    private val LEFT_HIP = 23
    private val RIGHT_HIP = 24
    private val LEFT_KNEE = 25
    private val RIGHT_KNEE = 26
    private val LEFT_ANKLE = 27
    private val RIGHT_ANKLE = 28

    fun updateResults(result: PoseLandmarkerResult) {
        this.results = result
        checkZones()
        invalidate()

        if (shouldersDetected && torsoDetected && legsDetected && feetDetected) {
            onCalibrationComplete?.invoke()
        }
    }

    private fun checkZones() {
        val result = results ?: return
        if (result.landmarks().isEmpty()) {
            shouldersDetected = false
            torsoDetected = false
            legsDetected = false
            feetDetected = false
            return
        }

        val landmarks = result.landmarks()[0]

        // Check if landmarks are within expected zones (normalized 0-1)
        if (landmarks.size > RIGHT_ANKLE) {
            val leftShoulder = landmarks[LEFT_SHOULDER]
            val rightShoulder = landmarks[RIGHT_SHOULDER]
            val leftHip = landmarks[LEFT_HIP]
            val rightHip = landmarks[RIGHT_HIP]
            val leftKnee = landmarks[LEFT_KNEE]
            val rightKnee = landmarks[RIGHT_KNEE]
            val leftAnkle = landmarks[LEFT_ANKLE]
            val rightAnkle = landmarks[RIGHT_ANKLE]

            // Shoulders in upper zone (y between 0.15 and 0.35)
            shouldersDetected = leftShoulder.y() in 0.1f..0.4f &&
                    rightShoulder.y() in 0.1f..0.4f

            // Torso in middle zone (hips y between 0.35 and 0.55)
            torsoDetected = leftHip.y() in 0.3f..0.6f &&
                    rightHip.y() in 0.3f..0.6f

            // Legs in lower-middle zone (knees y between 0.55 and 0.75)
            legsDetected = leftKnee.y() in 0.5f..0.8f &&
                    rightKnee.y() in 0.5f..0.8f

            // Feet in bottom zone (ankles y between 0.75 and 0.95)
            feetDetected = leftAnkle.y() in 0.7f..1.0f &&
                    rightAnkle.y() in 0.7f..1.0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val bracketSize = 60f
        val margin = w * 0.15f

        // Draw shoulder zone brackets (top)
        drawCornerBrackets(canvas, margin, h * 0.18f, w - margin, h * 0.38f, bracketSize)
        if (shouldersDetected) {
            drawCheckmark(canvas, w * 0.5f, h * 0.28f)
        }

        // Draw torso zone brackets (upper middle)
        drawCornerBrackets(canvas, margin, h * 0.38f, w - margin, h * 0.55f, bracketSize)
        if (torsoDetected) {
            drawCheckmark(canvas, w * 0.5f, h * 0.46f)
        }

        // Draw legs zone brackets (lower middle)
        drawCornerBrackets(canvas, margin, h * 0.55f, w - margin, h * 0.75f, bracketSize)
        if (legsDetected) {
            drawCheckmark(canvas, w * 0.5f, h * 0.65f)
        }

        // Draw feet zone brackets (bottom)
        drawCornerBrackets(canvas, margin, h * 0.75f, w - margin, h * 0.92f, bracketSize)
        if (feetDetected) {
            drawCheckmark(canvas, w * 0.5f, h * 0.83f)
        }

        // Draw message box
        if (!shouldersDetected) {
            drawMessageBox(canvas, "Place your shoulders in the frame", w * 0.5f, h * 0.32f)
        } else if (!torsoDetected) {
            drawMessageBox(canvas, "Align your torso", w * 0.5f, h * 0.50f)
        } else if (!legsDetected) {
            drawMessageBox(canvas, "Show your legs", w * 0.5f, h * 0.68f)
        } else if (!feetDetected) {
            drawMessageBox(canvas, "Include your feet", w * 0.5f, h * 0.86f)
        }

        // Draw navigation arrows
        drawArrow(canvas, 40f, h * 0.5f, true)  // Left arrow
        drawArrow(canvas, w - 40f, h * 0.5f, false) // Right arrow
        drawArrow(canvas, 40f, h * 0.8f, true)
        drawArrow(canvas, w - 40f, h * 0.8f, false)
    }

    private fun drawCornerBrackets(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, size: Float) {
        // Top-left corner
        canvas.drawLine(left, top + size, left, top, bracketPaint)
        canvas.drawLine(left, top, left + size, top, bracketPaint)

        // Top-right corner
        canvas.drawLine(right - size, top, right, top, bracketPaint)
        canvas.drawLine(right, top, right, top + size, bracketPaint)

        // Bottom-left corner
        canvas.drawLine(left, bottom - size, left, bottom, bracketPaint)
        canvas.drawLine(left, bottom, left + size, bottom, bracketPaint)

        // Bottom-right corner
        canvas.drawLine(right - size, bottom, right, bottom, bracketPaint)
        canvas.drawLine(right, bottom, right, bottom - size, bracketPaint)
    }

    private fun drawCheckmark(canvas: Canvas, cx: Float, cy: Float) {
        val path = Path()
        path.moveTo(cx - 30f, cy)
        path.lineTo(cx - 5f, cy + 25f)
        path.lineTo(cx + 35f, cy - 25f)
        canvas.drawPath(path, checkPaint)
    }

    private fun drawMessageBox(canvas: Canvas, message: String, cx: Float, cy: Float) {
        val textWidth = textPaint.measureText(message)
        val padding = 30f
        val rect = RectF(cx - textWidth/2 - padding, cy - 35f, cx + textWidth/2 + padding, cy + 35f)
        canvas.drawRoundRect(rect, 25f, 25f, pinkPaint)
        canvas.drawText(message, cx, cy + 12f, textPaint)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, isLeft: Boolean) {
        val size = 25f
        val path = Path()
        if (isLeft) {
            path.moveTo(cx + size, cy - size)
            path.lineTo(cx, cy)
            path.lineTo(cx + size, cy + size)
        } else {
            path.moveTo(cx - size, cy - size)
            path.lineTo(cx, cy)
            path.lineTo(cx - size, cy + size)
        }
        canvas.drawPath(path, arrowPaint)
    }

    fun clear() {
        results = null
        shouldersDetected = false
        torsoDetected = false
        legsDetected = false
        feetDetected = false
        invalidate()
    }
}