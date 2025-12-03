package at.ac.fhstp.fashioncourt.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.hypot

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null

    // Clothing bitmap
    private var clothingBitmap: Bitmap? = null

    // MediaPipe landmark indices
    private val LEFT_SHOULDER = 11
    private val RIGHT_SHOULDER = 12
    private val LEFT_HIP = 23
    private val RIGHT_HIP = 24

    // Helper paint just in case
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun updateResults(result: PoseLandmarkerResult) {
        results = result
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    /** Public method for MainActivity/Compose to set chosen clothing */
    fun setClothingBitmap(bitmap: Bitmap?) {
        clothingBitmap = bitmap
        invalidate()
    }

    // Helper functions
    private fun midpoint(a: PointF, b: PointF) = PointF(
        (a.x + b.x) / 2f,
        (a.y + b.y) / 2f
    )

    private fun dist(a: PointF, b: PointF) =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bm = clothingBitmap ?: return
        val result = results ?: return
        if (result.landmarks().isEmpty()) return

        val lm = result.landmarks()[0]

        // Safety check
        if (lm.size <= RIGHT_HIP) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Convert from normalized coordinates → screen coordinates
        val leftShoulder = PointF(lm[LEFT_SHOULDER].x() * w, lm[LEFT_SHOULDER].y() * h)
        val rightShoulder = PointF(lm[RIGHT_SHOULDER].x() * w, lm[RIGHT_SHOULDER].y() * h)
        val leftHip = PointF(lm[LEFT_HIP].x() * w, lm[LEFT_HIP].y() * h)
        val rightHip = PointF(lm[RIGHT_HIP].x() * w, lm[RIGHT_HIP].y() * h)

        // Compute position and transforms
        val shoulderMid = midpoint(leftShoulder, rightShoulder)
        val hipMid = midpoint(leftHip, rightHip)

        val detectedShoulderWidth = dist(leftShoulder, rightShoulder)

        // This is a tunable parameter depending on your PNG design
        val referenceShoulderWidthPx = 250f

        val scale = detectedShoulderWidth / referenceShoulderWidthPx

        val angle = Math.toDegrees(
            atan2(
                (rightShoulder.y - leftShoulder.y).toDouble(),
                (rightShoulder.x - leftShoulder.x).toDouble()
            )
        ).toFloat()

        // Vertical placement between shoulders & hips
        val centerX = (shoulderMid.x + hipMid.x) / 2f
        val centerY = (shoulderMid.y * 0.35f) + (hipMid.y * 0.65f)

        val matrix = Matrix()

        // Move image pivot to its center
        matrix.postTranslate(-bm.width / 2f, -bm.height / 2f)

        // Scale to body
        matrix.postScale(scale, scale)

        // Rotate to body angle
        matrix.postRotate(angle)

        // Move to detected body center
        matrix.postTranslate(centerX, centerY)

        canvas.drawBitmap(bm, matrix, bitmapPaint)
    }
}
