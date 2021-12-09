package app.grapheneos.camera.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix

import android.view.ScaleGestureDetector

import android.view.MotionEvent

import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener

import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.InAppGallery
import kotlin.math.abs

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    lateinit var mMatrix : Matrix

    private var mode = NONE

    private var last = PointF()
    private var start = PointF()
    private var minScale = 1f
    private var maxScale = 3f
    private var m: FloatArray = FloatArray(9)
    private var viewWidth = 0
    private var viewHeight = 0
    private var saveScale = 1f
    private var origWidth = 0f
    private var origHeight = 0f
    private var oldMeasuredWidth = 0
    private var oldMeasuredHeight = 0
    private var mScaleDetector: ScaleGestureDetector? = null

    lateinit var gActivity : InAppGallery

    init {
        sharedConstructing()
    }

    private val currentInstance : ZoomableImageView
        get() {
            return gActivity.gallerySlider.getChildAt(0)
                .findViewById(R.id.slide_preview)
        }

    fun setGalleryActivity(gActivity: InAppGallery) {
        this.gActivity = gActivity
    }

    private fun sharedConstructing() {
        super.setClickable(true)
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        mMatrix = Matrix()
        imageMatrix = mMatrix
        scaleType = ScaleType.MATRIX

        setOnTouchListener { _, event ->

            currentInstance.mScaleDetector!!.onTouchEvent(event)
            val curr = PointF(event.x, event.y)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentInstance.last.set(curr)
                    currentInstance.start.set(currentInstance.last)
                    currentInstance.mode = DRAG
                }
                MotionEvent.ACTION_MOVE -> if (currentInstance.mode == DRAG) {
                    val deltaX = curr.x - currentInstance.last.x
                    val deltaY = curr.y - currentInstance.last.y

                    val fixTransX = currentInstance.getFixDragTrans(
                        deltaX, currentInstance.viewWidth.toFloat(),
                        currentInstance.origWidth
                                * currentInstance.saveScale
                    )

                    val fixTransY = currentInstance.getFixDragTrans(
                        deltaY, currentInstance.viewHeight.toFloat(),
                        currentInstance.origHeight * currentInstance.saveScale
                    )

                    currentInstance.mMatrix.postTranslate(fixTransX, fixTransY)
                    currentInstance.fixTrans()
                    currentInstance.last[curr.x] = curr.y
                }
                MotionEvent.ACTION_UP -> {
                    currentInstance.mode = NONE
                    val xDiff = abs(curr.x - currentInstance.start.x).toInt()
                    val yDiff = abs(curr.y - currentInstance.start.y).toInt()
                    if (xDiff < CLICK && yDiff < CLICK) performClick()
                }
                MotionEvent.ACTION_POINTER_UP -> currentInstance.mode = NONE
            }
            currentInstance.imageMatrix = currentInstance.mMatrix
            currentInstance.invalidate()
            true
        }
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {

            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }

            if (origWidth * saveScale <= viewWidth
                || origHeight * saveScale <= viewHeight
            ) mMatrix.postScale(
                mScaleFactor, mScaleFactor, viewWidth / 2f,
                viewHeight / 2f
            ) else mMatrix.postScale(
                mScaleFactor, mScaleFactor,
                detector.focusX, detector.focusY
            )

            fixTrans()

            if (saveScale == 1f) {
                moveOutOfZoomMode()
            } else {
                moveIntoZoomMode()
            }

            return true
        }
    }

    private var isInZoomMode = false

    fun moveIntoZoomMode() {

        if (isInZoomMode) return

        isInZoomMode = true

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            ContextCompat.getColor(gActivity, R.color.system_neutral1_900),
            Color.BLACK
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            gActivity.rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()

        gActivity.supportActionBar?.hide()

        gActivity.gallerySlider.isUserInputEnabled = false
    }

    fun moveOutOfZoomMode() {

        if (!isInZoomMode) return

        isInZoomMode = false

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.BLACK,
            ContextCompat.getColor(gActivity, R.color.system_neutral1_900),
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            gActivity.rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()

        gActivity.supportActionBar?.show()

        gActivity.rootView
        gActivity.gallerySlider.isUserInputEnabled = true
    }

    fun fixTrans() {

        mMatrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(
            transY, viewHeight.toFloat(), origHeight
                    * saveScale
        )
        if (fixTransX != 0f || fixTransY != 0f) mMatrix.postTranslate(fixTransX, fixTransY)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {

        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return -trans + minTrans
        return if (trans > maxTrans) -trans + maxTrans else 0f
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        return if (contentSize <= viewSize) {
            0f
        } else delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Rescales image on rotation
        if (oldMeasuredHeight == viewWidth &&
            oldMeasuredHeight == viewHeight || viewWidth == 0 ||
            viewHeight == 0) return

        oldMeasuredHeight = viewHeight
        oldMeasuredWidth = viewWidth

        if (saveScale == 1f) {
            // Fit to screen.
            val scale: Float
            if (drawable?.intrinsicWidth ?: 0 == 0 || drawable?.intrinsicHeight ?: 0 == 0) return
            val bmWidth = drawable.intrinsicWidth
            val bmHeight = drawable.intrinsicHeight
            Log.d("bmSize", "bmWidth: $bmWidth bmHeight : $bmHeight")
            val scaleX = viewWidth.toFloat() / bmWidth.toFloat()
            val scaleY = viewHeight.toFloat() / bmHeight.toFloat()
            scale = scaleX.coerceAtMost(scaleY)
            mMatrix.setScale(scale, scale)

            // Center the image
            var redundantYSpace = viewHeight.toFloat() - scale * bmHeight.toFloat()
            var redundantXSpace = viewWidth.toFloat() - scale * bmWidth.toFloat()
            redundantYSpace /= 2f
            redundantXSpace /= 2f
            mMatrix.postTranslate(redundantXSpace, redundantYSpace)
            origWidth = viewWidth - 2 * redundantXSpace
            origHeight = viewHeight - 2 * redundantYSpace
            imageMatrix = mMatrix
        }
        fixTrans()
    }

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
        const val CLICK = 3
    }
}