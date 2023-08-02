package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowMetrics
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Stack
import java.util.concurrent.locks.Lock


// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    var currPageIndex = 0

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage
    lateinit var pageNum: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<LockableScrollView>(R.id.pdfLayout)
        layout.isEnabled = true

        var scroll = false

        pageImage = PDFimage(this)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        var prev = findViewById<Button>(R.id.button1)
        var next = findViewById<Button>(R.id.button2)
        pageNum = findViewById<TextView>(R.id.pageNumber)
        var title = findViewById<TextView>(R.id.pdfTitle)
        title.text = FILENAME

        prev.setOnClickListener {
            println("PREVV")
            showPage(currPageIndex - 1)
        }

        next.setOnClickListener {
            showPage(currPageIndex + 1)
        }

        var pen = findViewById<ToggleButton>(R.id.draw)
        var highlight = findViewById<ToggleButton>(R.id.highlight)
        var erase = findViewById<ToggleButton>(R.id.erase)

        pen.setOnClickListener {
            highlight.isChecked = false
            erase.isChecked = false
            pageImage.tool = 0
            layout.setScrollingEnabled(false)
        }

        highlight.setOnClickListener {
            pen.isChecked = false
            erase.isChecked = false
            pageImage.tool = 1
            layout.setScrollingEnabled(false)
        }

        erase.setOnClickListener {
            highlight.isChecked = false
            pen.isChecked = false
            pageImage.tool = 2
            layout.setScrollingEnabled(false)
        }

        var scrollBttn = findViewById<ToggleButton>(R.id.scroll)
        scrollBttn.setOnClickListener{
            scroll = !scroll
            layout.setScrollingEnabled(scroll)
        }



        var undo = findViewById<Button>(R.id.undo)
        var redo = findViewById<Button>(R.id.redo)

        undo.setOnClickListener {
            pageImage.undoCmmd()
        }

        redo.setOnClickListener {
            pageImage.redoCmmd()
        }

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            showPage(0)
            //closeRenderer()
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        var layout = findViewById<LockableScrollView>(R.id.pdfLayout)
        Log.d(LOGNAME, "Configuration changed")
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(LOGNAME, "Changed to landscape")

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(LOGNAME, "Changed to portrait")

        }
    }


    override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index || index < 0) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)
        currPageIndex = index
        pageNum.text = "Page ${currPageIndex + 1}/${pdfRenderer.pageCount}"
        pageImage.currPage = index
        if (pageImage.paths[index] == null) {
            pageImage.paths[index] = mutableListOf<Pair<Int, Path?>>()
        }
        if (pageImage.undo[index] == null) {
            pageImage.undo[index] = Stack<List<Pair<Pair<Int, Path?>,Int>>>()
        }
        if (pageImage.redo[index] == null) {
            pageImage.redo[index] = Stack<List<Pair<Pair<Int, Path?>,Int>>>()
        }

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }
}