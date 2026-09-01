package com.example.investa.ui.assets

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

internal abstract class OnSwipeTouchListener(
    context: Context,
    private val consumeTouch: Boolean = true
) : View.OnTouchListener {
    private var swiped = false
    private var downX = 0f
    private var downY = 0f
    private var horizontalGesture = false
    private var flingDirection = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100

        override fun onDown(event: MotionEvent): Boolean {
            swiped = false
            return true
        }

        override fun onFling(
            start: MotionEvent?,
            end: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (start == null) return false
            val distanceX = end.x - start.x
            val distanceY = end.y - start.y
            if (abs(distanceX) > swipeThreshold &&
                abs(distanceX) > abs(distanceY) &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                swiped = true
                flingDirection = if (distanceX > 0) 1 else -1
                return true
            }
            return false
        }
    })

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiped = false
                horizontalGesture = false
                flingDirection = 0
            }

            MotionEvent.ACTION_MOVE -> {
                val distanceX = event.x - downX
                val distanceY = event.y - downY
                if (abs(distanceX) > touchSlop || abs(distanceY) > touchSlop) {
                    horizontalGesture = abs(distanceX) > abs(distanceY)
                    // Keep horizontal gestures on the card so the parent ScrollView
                    // cannot intercept them. Vertical gestures remain scrollable.
                    view.parent?.requestDisallowInterceptTouchEvent(horizontalGesture)
                    if (horizontalGesture) onSwipeProgress(distanceX)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (horizontalGesture || flingDirection != 0) {
                onSwipeRelease(event.x - downX, flingDirection)
            } else if (consumeTouch && !swiped) {
                view.performClick()
            }
        }
        return consumeTouch
    }

    protected open fun onSwipeProgress(distanceX: Float) = Unit

    protected open fun onSwipeRelease(distanceX: Float, flingDirection: Int) {
        when {
            flingDirection > 0 -> onSwipeRight()
            flingDirection < 0 -> onSwipeLeft()
            distanceX > 0 -> onSwipeRight()
            distanceX < 0 -> onSwipeLeft()
        }
    }

    open fun onSwipeRight() = Unit

    open fun onSwipeLeft() = Unit
}
