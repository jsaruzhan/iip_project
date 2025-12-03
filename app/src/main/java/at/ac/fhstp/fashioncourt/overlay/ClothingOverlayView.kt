package at.ac.fhstp.fashioncourt.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.hypot

class ClothingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null
    private var topBitmap: Bitmap? = null
    private var bottomBitmap: Bitmap? = null
    private var shoesBitmap: Bitmap? = null

    // Cached clothing bounds (so we don't recalculate every frame)
    private var topBounds: ClothingBounds? = null
    private var bottomBounds: ClothingBounds? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // MediaPipe landmark indices
    private val LEFT_SHOULDER = 11
    private val RIGHT_SHOULDER = 12
    private val LEFT_HIP = 23
    private val RIGHT_HIP = 24
    private val LEFT_ANKLE = 27
    private val RIGHT_ANKLE = 28

    data class ClothingBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val width: Int,
        val height: Int
    )

    fun updateResults(result: PoseLandmarkerResult) {
        results = result
        invalidate()
    }

    fun setTopBitmap(bitmap: Bitmap?) {
        topBitmap = bitmap
        topBounds = bitmap?.let { findClothingBounds(it) }
        invalidate()
    }

    fun setBottomBitmap(bitmap: Bitmap?) {
        bottomBitmap = bitmap
        bottomBounds = bitmap?.let { findClothingBounds(it) }
        invalidate()
    }

    fun setShoesBitmap(bitmap: Bitmap?) {
        shoesBitmap = bitmap
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    /**
     * Finds the actual bounds of the clothing by detecting non-transparent pixels
     */
    private fun findClothingBounds(bitmap: Bitmap): ClothingBounds {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = 0
        var maxY = 0

        // Sample pixels (checking every 4th pixel for performance)
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha > 50) { // Non-transparent pixel
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Add small margin
        minX = (minX - 4).coerceAtLeast(0)
        minY = (minY - 4).coerceAtLeast(0)
        maxX = (maxX + 4).coerceAtMost(bitmap.width)
        maxY = (maxY + 4).coerceAtMost(bitmap.height)

        return ClothingBounds(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY,
            width = maxX - minX,
            height = maxY - minY
        )
    }

    private fun midpoint(a: PointF, b: PointF) = PointF(
        (a.x + b.x) / 2f,
        (a.y + b.y) / 2f
    )

    private fun dist(a: PointF, b: PointF) =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val result = results ?: return
        if (result.landmarks().isEmpty()) return

        val lm = result.landmarks()[0]
        if (lm.size <= RIGHT_ANKLE) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Get landmarks in screen coordinates
        val leftShoulder = PointF(lm[LEFT_SHOULDER].x() * w, lm[LEFT_SHOULDER].y() * h)
        val rightShoulder = PointF(lm[RIGHT_SHOULDER].x() * w, lm[RIGHT_SHOULDER].y() * h)
        val leftHip = PointF(lm[LEFT_HIP].x() * w, lm[LEFT_HIP].y() * h)
        val rightHip = PointF(lm[RIGHT_HIP].x() * w, lm[RIGHT_HIP].y() * h)
        val leftAnkle = PointF(lm[LEFT_ANKLE].x() * w, lm[LEFT_ANKLE].y() * h)
        val rightAnkle = PointF(lm[RIGHT_ANKLE].x() * w, lm[RIGHT_ANKLE].y() * h)

        // Draw top (shirt)
        topBitmap?.let { bm ->
            topBounds?.let { bounds ->
                drawTop(canvas, bm, bounds, leftShoulder, rightShoulder, leftHip, rightHip)
            }
        }

        // Draw bottom (pants)
        bottomBitmap?.let { bm ->
            bottomBounds?.let { bounds ->
                drawBottom(canvas, bm, bounds, leftHip, rightHip, leftAnkle, rightAnkle)
            }
        }

        // Draw shoes
        shoesBitmap?.let { bm ->
            drawShoe(canvas, bm, leftAnkle, true)
            drawShoe(canvas, bm, rightAnkle, false)
        }
    }

    private fun drawTop(
        canvas: Canvas,
        bm: Bitmap,
        bounds: ClothingBounds,
        leftShoulder: PointF,
        rightShoulder: PointF,
        leftHip: PointF,
        rightHip: PointF
    ) {
        val shoulderMid = midpoint(leftShoulder, rightShoulder)
        val hipMid = midpoint(leftHip, rightHip)

        // Measure body
        val bodyShoulderWidth = dist(leftShoulder, rightShoulder)
        val bodyTorsoHeight = dist(shoulderMid, hipMid)

        // Use actual clothing width (not full image width)
        val clothingWidth = bounds.width.toFloat()
        val clothingHeight = bounds.height.toFloat()

        // Scale to fit body - clothing should be ~1.3x shoulder width for natural look
        val targetWidth = bodyShoulderWidth * 2.5f
        val scale = targetWidth / clothingWidth

        // Calculate scaled dimensions
        val scaledWidth = clothingWidth * scale
        val scaledHeight = clothingHeight * scale

        // Position: align top of clothing with shoulders
        val centerX = shoulderMid.x
        val topY = shoulderMid.y - (scaledHeight * 0.15f) // Slight offset for neckline

        // Source rectangle (the actual clothing part of the image)
        val srcRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

        // Destination rectangle (where to draw on screen)
        val destRect = RectF(
            centerX - scaledWidth / 2f,
            topY,
            centerX + scaledWidth / 2f,
            topY + scaledHeight
        )

        canvas.drawBitmap(bm, srcRect, destRect, bitmapPaint)
    }

    private fun drawBottom(
        canvas: Canvas,
        bm: Bitmap,
        bounds: ClothingBounds,
        leftHip: PointF,
        rightHip: PointF,
        leftAnkle: PointF,
        rightAnkle: PointF
    ) {
        val hipMid = midpoint(leftHip, rightHip)
        val ankleMid = midpoint(leftAnkle, rightAnkle)

        // Measure body
        val bodyHipWidth = dist(leftHip, rightHip)
        val bodyLegLength = dist(hipMid, ankleMid)

        // Use actual clothing dimensions
        val clothingWidth = bounds.width.toFloat()
        val clothingHeight = bounds.height.toFloat()

        // Scale to fit body
        val targetWidth = bodyHipWidth * 3.0f
        val scale = targetWidth / clothingWidth

        // Calculate scaled dimensions
        val scaledWidth = clothingWidth * scale
        val scaledHeight = clothingHeight * scale

        // Position: top of pants at hip line
        val centerX = hipMid.x
        val topY = hipMid.y

        // Source rectangle
        val srcRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)

        // Destination rectangle
        val destRect = RectF(
            centerX - scaledWidth / 2f,
            topY,
            centerX + scaledWidth / 2f,
            topY + scaledHeight
        )

        canvas.drawBitmap(bm, srcRect, destRect, bitmapPaint)
    }

    private fun drawShoe(canvas: Canvas, bm: Bitmap, ankle: PointF, isLeft: Boolean) {
        val scale = 0.4f
        val scaledWidth = bm.width * scale
        val scaledHeight = bm.height * scale

        val left = ankle.x - (scaledWidth / 2f)
        val top = ankle.y

        if (!isLeft) {
            val matrix = Matrix()
            matrix.postScale(-1f, 1f, bm.width / 2f, bm.height / 2f)
            val mirroredBm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)
            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(mirroredBm, null, destRect, bitmapPaint)
        } else {
            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(bm, null, destRect, bitmapPaint)
        }
    }
}