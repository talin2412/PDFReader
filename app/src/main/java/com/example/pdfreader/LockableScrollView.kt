package com.example.pdfreader

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ScrollView

class LockableScrollView : ScrollView {
    // true if we can scroll (not locked)
    // false if we cannot scroll (locked)
    var isScrollable = false
        private set

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {        // TODO Auto-generated constructor stub
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?) : super(context) {}

    fun setScrollingEnabled(enabled: Boolean) {
        isScrollable = enabled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // if we can scroll pass the event to the superclass
                if (isScrollable) super.onTouchEvent(ev) else isScrollable
                // only continue to handle the touch event if scrolling enabled
                // mScrollable is always false at this point
            }

            else -> super.onTouchEvent(ev)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Don't do anything with intercepted touch events if
        // we are not scrollable
        return if (!isScrollable) false else super.onInterceptTouchEvent(ev)
    }
}