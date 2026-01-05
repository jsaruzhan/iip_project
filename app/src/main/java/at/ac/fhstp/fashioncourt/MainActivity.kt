package at.ac.fhstp.fashioncourt

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import at.ac.fhstp.fashioncourt.camera.CameraManager
import at.ac.fhstp.fashioncourt.detection.PoseDetectorHelper
import at.ac.fhstp.fashioncourt.overlay.CalibrationOverlayView
import at.ac.fhstp.fashioncourt.overlay.ClothingOverlayView
import at.ac.fhstp.fashioncourt.overlay.PoseOverlayView
import at.ac.fhstp.fashioncourt.ui.theme.FashionCourtTheme
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : ComponentActivity() {

    private var cameraManager: CameraManager? = null
    private var poseDetectorHelper: PoseDetectorHelper? = null
    private var poseOverlayView: PoseOverlayView? = null
    private var calibrationOverlayView: CalibrationOverlayView? = null
    private var clothingOverlayView: ClothingOverlayView? = null

    private var topLabelView: TextView? = null
    private var bottomLabelView: TextView? = null
    private var shoesLabelView: TextView? = null
    private var switchLabelView: TextView? = null

    private val topsList = mutableListOf<Bitmap>()
    private val bottomsList = mutableListOf<Bitmap>()
    private val shoesList = mutableListOf<Bitmap>()
    private var currentTopIndex = 0
    private var currentBottomIndex = 0
    private var currentShoesIndex = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraAndDetection()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadAllClothing()

        setContent {
            FashionCourtTheme {
                ARTryOnScreen(
                    onViewsCreated = { previewView, poseOverlay, calibrationOverlay, clothingOverlay,
                                       switchButton, prevTopBtn, nextTopBtn, prevBottomBtn, nextBottomBtn,
                                       prevShoesBtn, nextShoesBtn, topLabel, bottomLabel, shoesLabel, switchLabel ->

                        poseOverlayView = poseOverlay
                        calibrationOverlayView = calibrationOverlay
                        clothingOverlayView = clothingOverlay

                        topLabelView = topLabel
                        bottomLabelView = bottomLabel
                        shoesLabelView = shoesLabel
                        switchLabelView = switchLabel

                        fun updateLabels() {
                            topLabelView?.text =
                                if (topsList.isNotEmpty()) "Shirt ${currentTopIndex + 1}/${topsList.size}" else "Shirt"
                            bottomLabelView?.text =
                                if (bottomsList.isNotEmpty()) "Pants ${currentBottomIndex + 1}/${bottomsList.size}" else "Pants"
                            shoesLabelView?.text =
                                if (shoesList.isNotEmpty()) "Shoes ${currentShoesIndex + 1}/${shoesList.size}" else "Shoes"
                            switchLabelView?.text = "Switch"
                        }

                        calibrationOverlay.onCalibrationComplete = {
                            runOnUiThread {
                                calibrationOverlayView?.visibility = android.view.View.GONE
                                clothingOverlayView?.visibility = android.view.View.VISIBLE

                                if (topsList.isNotEmpty()) {
                                    clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                                }
                                if (bottomsList.isNotEmpty()) {
                                    clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                                }
                                if (shoesList.isNotEmpty()) {
                                    clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                                }

                                updateLabels()

                                prevTopBtn.visibility = android.view.View.VISIBLE
                                nextTopBtn.visibility = android.view.View.VISIBLE
                                prevBottomBtn.visibility = android.view.View.VISIBLE
                                nextBottomBtn.visibility = android.view.View.VISIBLE
                                prevShoesBtn.visibility = android.view.View.VISIBLE
                                nextShoesBtn.visibility = android.view.View.VISIBLE

                                topLabel.visibility = android.view.View.VISIBLE
                                bottomLabel.visibility = android.view.View.VISIBLE
                                shoesLabel.visibility = android.view.View.VISIBLE
                                switchLabel.visibility = android.view.View.VISIBLE
                            }
                        }

                        switchButton.setOnClickListener {
                            cameraManager?.switchCamera()
                        }

                        prevTopBtn.setOnClickListener {
                            if (topsList.isNotEmpty()) {
                                currentTopIndex = if (currentTopIndex > 0) currentTopIndex - 1 else topsList.size - 1
                                clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                                updateLabels()
                            }
                        }
                        nextTopBtn.setOnClickListener {
                            if (topsList.isNotEmpty()) {
                                currentTopIndex = (currentTopIndex + 1) % topsList.size
                                clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                                updateLabels()
                            }
                        }

                        prevBottomBtn.setOnClickListener {
                            if (bottomsList.isNotEmpty()) {
                                currentBottomIndex = if (currentBottomIndex > 0) currentBottomIndex - 1 else bottomsList.size - 1
                                clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                                updateLabels()
                            }
                        }
                        nextBottomBtn.setOnClickListener {
                            if (bottomsList.isNotEmpty()) {
                                currentBottomIndex = (currentBottomIndex + 1) % bottomsList.size
                                clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                                updateLabels()
                            }
                        }

                        prevShoesBtn.setOnClickListener {
                            if (shoesList.isNotEmpty()) {
                                currentShoesIndex = if (currentShoesIndex > 0) currentShoesIndex - 1 else shoesList.size - 1
                                clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                                updateLabels()
                            }
                        }
                        nextShoesBtn.setOnClickListener {
                            if (shoesList.isNotEmpty()) {
                                currentShoesIndex = (currentShoesIndex + 1) % shoesList.size
                                clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                                updateLabels()
                            }
                        }

                        setupCamera(previewView)
                        checkCameraPermission()
                    }
                )
            }
        }
    }

    private fun loadAllClothing() {
        for (i in 1..5) {
            loadBitmapFromAssets("clothes/Tops/top$i.png")?.let { topsList.add(it) }
        }

        for (i in 1..4) {
            loadBitmapFromAssets("clothes/Bottom/bottom$i.png")?.let { bottomsList.add(it) }
        }

        for (i in 1..4) {
            loadBitmapFromAssets("clothes/Shoes/shoes$i.png")?.let { shoesList.add(it) }
        }

        Log.d("MainActivity", "Loaded ${topsList.size} tops, ${bottomsList.size} bottoms, ${shoesList.size} shoes")
    }

    private fun setupCamera(previewView: PreviewView) {
        cameraManager = CameraManager(
            context = this,
            previewView = previewView,
            onFrameAnalyzed = { bitmap, frameTime ->
                poseDetectorHelper?.detectAsync(bitmap, frameTime)
            }
        )

        poseDetectorHelper = PoseDetectorHelper(
            context = this,
            onResults = { result, _, _, _ ->
                runOnUiThread {
                    poseOverlayView?.updateResults(result)
                    calibrationOverlayView?.updateResults(result)
                    clothingOverlayView?.updateResults(result)
                }
            },
            onError = { error ->
                Log.e("MainActivity", "Pose detection error: $error")
            }
        )
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraAndDetection()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraAndDetection() {
        cameraManager?.startCamera(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
        poseDetectorHelper?.close()
    }

    private fun loadBitmapFromAssets(path: String): Bitmap? {
        return try {
            assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load bitmap: $path", e)
            null
        }
    }
}

@Composable
fun ARTryOnScreen(
    onViewsCreated: (PreviewView, PoseOverlayView, CalibrationOverlayView, ClothingOverlayView,
                     ImageButton, ImageButton, ImageButton, ImageButton, ImageButton,
                     ImageButton, ImageButton, TextView, TextView, TextView, TextView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val previewView = PreviewView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val poseOverlay = PoseOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    visibility = android.view.View.GONE
                }

                val calibrationOverlay = CalibrationOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val clothingOverlay = ClothingOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    visibility = android.view.View.GONE
                }

                val switchButton = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 80, 40, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_menu_camera)
                    setPadding(20, 20, 20, 20)
                }

                val switchLabel = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 210, 40, 0)
                    }
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#80000000"))
                    setPadding(18, 10, 18, 10)
                    text = "Switch"
                    visibility = android.view.View.GONE
                }

                val prevTopBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 500, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextTopBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 500, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                val topLabel = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 420, 0, 0)
                    }
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#80000000"))
                    setPadding(22, 12, 22, 12)
                    text = "Shirt"
                    visibility = android.view.View.GONE
                }

                val prevBottomBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 1000, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80E1BEE7"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextBottomBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 1000, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80E1BEE7"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                val bottomLabel = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 920, 0, 0)
                    }
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#80000000"))
                    setPadding(22, 12, 22, 12)
                    text = "Pants"
                    visibility = android.view.View.GONE
                }


                val prevShoesBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 1600, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80B3E5FC"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextShoesBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 1600, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80B3E5FC"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                val shoesLabel = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 1520, 0, 0)
                    }
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#80000000"))
                    setPadding(22, 12, 22, 12)
                    text = "Shoes"
                    visibility = android.view.View.GONE
                }

                addView(previewView)
                addView(poseOverlay)
                addView(clothingOverlay)
                addView(calibrationOverlay)

                addView(switchButton)
                addView(switchLabel)

                addView(prevTopBtn)
                addView(nextTopBtn)
                addView(topLabel)

                addView(prevBottomBtn)
                addView(nextBottomBtn)
                addView(bottomLabel)

                addView(prevShoesBtn)
                addView(nextShoesBtn)
                addView(shoesLabel)

                onViewsCreated(
                    previewView, poseOverlay, calibrationOverlay, clothingOverlay,
                    switchButton, prevTopBtn, nextTopBtn, prevBottomBtn, nextBottomBtn,
                    prevShoesBtn, nextShoesBtn, topLabel, bottomLabel, shoesLabel, switchLabel
                )
            }
        }
    )
}
