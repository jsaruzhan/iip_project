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

    private var topBounds: ClothingBounds? = null
    private var bottomBounds: ClothingBounds? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    private fun findClothingBounds(bitmap: Bitmap): ClothingBounds {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = 0
        var maxY = 0

        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha > 50) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

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

        val leftShoulder = PointF(lm[LEFT_SHOULDER].x() * w, lm[LEFT_SHOULDER].y() * h)
        val rightShoulder = PointF(lm[RIGHT_SHOULDER].x() * w, lm[RIGHT_SHOULDER].y() * h)
        val leftHip = PointF(lm[LEFT_HIP].x() * w, lm[LEFT_HIP].y() * h)
        val rightHip = PointF(lm[RIGHT_HIP].x() * w, lm[RIGHT_HIP].y() * h)
        val leftAnkle = PointF(lm[LEFT_ANKLE].x() * w, lm[LEFT_ANKLE].y() * h)
        val rightAnkle = PointF(lm[RIGHT_ANKLE].x() * w, lm[RIGHT_ANKLE].y() * h)

        // Draw top
        topBitmap?.let { bm ->
            topBounds?.let { bounds ->
                drawTop(canvas, bm, bounds, leftShoulder, rightShoulder, leftHip, rightHip)
            }
        }

        // Draw bottom
        bottomBitmap?.let { bm ->
            bottomBounds?.let { bounds ->
                drawBottom(canvas, bm, bounds, leftHip, rightHip, leftAnkle, rightAnkle)
            }
        }

        // Draw shoes
        shoesBitmap?.let { bm ->
            drawShoes(canvas, bm, leftAnkle, rightAnkle)
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

        val bodyShoulderWidth = dist(leftShoulder, rightShoulder)

        val clothingWidth = bounds.width.toFloat()
        val clothingHeight = bounds.height.toFloat()

        val targetWidth = bodyShoulderWidth * 2.5f
        val scale = targetWidth / clothingWidth

        val scaledWidth = clothingWidth * scale
        val scaledHeight = clothingHeight * scale

        val centerX = shoulderMid.x
        val topY = shoulderMid.y - (scaledHeight * 0.15f)

        val srcRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
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

        val bodyHipWidth = dist(leftHip, rightHip)
        val bodyLegLength = dist(hipMid, ankleMid)

        val clothingWidth = bounds.width.toFloat()
        val clothingHeight = bounds.height.toFloat()

        val targetWidth = bodyHipWidth * 3.0f
        val scale = targetWidth / clothingWidth

        val scaledWidth = clothingWidth * scale
        val scaledHeight = bodyLegLength * 1.1f  // Go down to ankles

        val centerX = hipMid.x
        val topY = hipMid.y - (scaledHeight * 0.1f)  // Start slightly above hips

        val srcRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val destRect = RectF(
            centerX - scaledWidth / 2f,
            topY,
            centerX + scaledWidth / 2f,
            topY + scaledHeight
        )

        canvas.drawBitmap(bm, srcRect, destRect, bitmapPaint)
    }

    private fun drawShoes(
        canvas: Canvas,
        bm: Bitmap,
        leftAnkle: PointF,
        rightAnkle: PointF
    ) {
        val halfWidth = bm.width / 2

        val ankleDistance = dist(leftAnkle, rightAnkle)
        val shoeScale = ankleDistance / bm.width * 3.0f

        val scaledWidth = halfWidth * shoeScale
        val scaledHeight = bm.height * shoeScale

        // SWAPPED: Right half of image goes to LEFT foot
        val leftSrcRect = Rect(halfWidth, 0, bm.width, bm.height)
        val leftDestRect = RectF(
            leftAnkle.x - scaledWidth / 2f,
            leftAnkle.y - scaledHeight * 0.2f,
            leftAnkle.x + scaledWidth / 2f,
            leftAnkle.y + scaledHeight * 0.8f
        )
        canvas.drawBitmap(bm, leftSrcRect, leftDestRect, bitmapPaint)

        // SWAPPED: Left half of image goes to RIGHT foot
        val rightSrcRect = Rect(0, 0, halfWidth, bm.height)
        val rightDestRect = RectF(
            rightAnkle.x - scaledWidth / 2f,
            rightAnkle.y - scaledHeight * 0.2f,
            rightAnkle.x + scaledWidth / 2f,
            rightAnkle.y + scaledHeight * 0.8f
        )
        canvas.drawBitmap(bm, rightSrcRect, rightDestRect, bitmapPaint)
    }
}