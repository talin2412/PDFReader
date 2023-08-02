package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import java.util.Stack


@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing path
    var path: Path? = null
    var paths = mutableMapOf<Int, MutableList<Pair<Int, Path?>>>()

    var tool = 0
    var currPage = 0

    // image to display
    var bitmap: Bitmap? = null
    var penPaint = Paint().apply {
        color = Color.BLUE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 10f
    }
    var paint = penPaint
    var highlightPen = Paint().apply {
        color = Color.YELLOW
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 50f
        alpha = 100
    }
    var erasePen = Paint().apply {
        color = Color.TRANSPARENT
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 50f
        alpha = 0
    }

    var undo = mutableMapOf<Int, Stack<List<Pair<Pair<Int, Path?>,Int>>>>()
    var redo = mutableMapOf<Int, Stack<List<Pair<Pair<Int, Path?>,Int>>>>()

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(LOGNAME, "Action down")
                path = Path()
                path!!.moveTo(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d(LOGNAME, "Action move")
                path!!.lineTo(event.x, event.y)
            }

            MotionEvent.ACTION_UP -> {
                Log.d(LOGNAME, "Action up")
                // check if this is a erase path and then remove the paths it intersects
                var newPath = Pair(tool, path)

                if (tool != 2) {
                    paths[currPage]?.add(newPath)
                    undo[currPage]?.push(mutableListOf<Pair<Pair<Int, Path?>,Int>>(Pair(newPath, tool)))
                } else {
                    var erasedPaths = mutableListOf<Pair<Pair<Int, Path?>,Int>>()
                    for (pathVar in paths[currPage]!!.reversed()) {
                        val result = Path()
                        result.op(path!!, pathVar.second!!, Path.Op.INTERSECT)
                        if (!result.isEmpty) {
                            erasedPaths.add(Pair(pathVar, tool))
                            paths[currPage]?.remove(pathVar)
                        }
                    }
                    undo[currPage]?.push(erasedPaths)
                }
            }
        }
        return true
    }

    fun undoCmmd() {
        if (!undo[currPage]?.empty()!!) {
            var cmmdList = undo[currPage]?.pop()
            for (cmmd in cmmdList!!) {
                if (cmmd.second != 2) {
                    paths[currPage]?.remove(cmmd.first)
                } else {
                    paths[currPage]?.add(cmmd.first)
                }
            }
            redo[currPage]?.push(cmmdList)
        }
    }

    fun redoCmmd() {
        if (!redo[currPage]?.isEmpty()!!) {
            var cmmdList = redo[currPage]?.pop()
            for (cmmd in cmmdList!!) {
                if (cmmd.second != 2) {
                    paths[currPage]?.add(cmmd.first)
                } else {
                    paths[currPage]?.remove(cmmd.first)
                }
            }
            undo[currPage]?.push(cmmdList)
        }
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    fun setBrush(paint: Paint) {
        this.paint = paint
    }

    override fun onDraw(canvas: Canvas) {
        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }
        // draw lines over it
        var listTo = paths[currPage]
        for (path in listTo!!) {
            if (path.first == 1) {
                setBrush(highlightPen)
            } else if (path.first == 0) {
                setBrush(penPaint)
            } else {
                setBrush(erasePen)
            }
            path?.let { canvas.drawPath(it.second!!, paint) }
        }
        super.onDraw(canvas)
    }
}