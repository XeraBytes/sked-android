package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import java.io.File
import java.io.FileOutputStream

object ExportHelper {

    private fun getDayName(day: Int): String {
        return when (day) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> ""
        }
    }

    // Helper to determine if a hex color is light or dark
    private fun isColorLight(colorHex: String): Boolean {
        return try {
            val color = Color.parseColor(colorHex)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            luminance > 180
        } catch (e: Exception) {
            false
        }
    }

    fun generateScheduleBitmap(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Bitmap {
        val width = 1920
        val numHours = settings.endHour - settings.startHour

        // Dynamically scale the row height to handle larger schedules gracefully without vertical squishing
        val desiredRowHeight = 110f
        val topPadding = 240f
        val bottomPadding = 60f
        val calculatedHeight = (topPadding + bottomPadding + numHours * desiredRowHeight).toInt()
        val height = maxOf(1350, calculatedHeight) // Ensure at least 1350 for normal proportions

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw solid white background
        canvas.drawColor(Color.WHITE)

        // Paints
        val bgHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E293B") // Slate 800
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textHeaderTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textHeaderSubPaint = Paint().apply {
            color = Color.parseColor("#94A3B8") // Slate 400
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val gridLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0") // Slate 200
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val dayHeaderPaint = Paint().apply {
            color = Color.parseColor("#334155") // Slate 700
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val timeLabelPaint = Paint().apply {
            color = Color.parseColor("#64748B") // Slate 500
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        // 2. Draw Top Banner with subtle shadow/border line
        canvas.drawRect(0f, 0f, width.toFloat(), 160f, bgHeaderPaint)
        canvas.drawText(settings.schoolName.uppercase(), 60f, 75f, textHeaderTitlePaint)
        canvas.drawText(settings.termName, 60f, 120f, textHeaderSubPaint)

        val headerBorderPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Slate 900
            alpha = 35
            strokeWidth = 6f
        }
        canvas.drawLine(0f, 160f, width.toFloat(), 160f, headerBorderPaint)

        // 3. Grid Coordinates setup
        val leftPadding = 160f
        val rightPadding = 40f

        val availableWidth = width - leftPadding - rightPadding
        val numDays = if (settings.showWeekends) 7 else 5
        val colWidth = availableWidth / numDays

        val availableHeight = height - topPadding - bottomPadding
        val rowHeight = availableHeight / numHours

        // Modern visual polish: Draw background card for day headers row
        val dayHeaderBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9") // Slate 100
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(leftPadding, topPadding - 72f, width - rightPadding, topPadding - 12f),
            12f, 12f,
            dayHeaderBgPaint
        )

        // 4. Draw Column Headers (Days)
        for (i in 0 until numDays) {
            val dayName = getDayName(i + 1)
            val x = leftPadding + (i + 0.5f) * colWidth
            canvas.drawText(dayName, x, topPadding - 32f, dayHeaderPaint)

            // Vertical separating lines
            val lineX = leftPadding + i * colWidth
            canvas.drawLine(lineX, topPadding - 72f, lineX, height - bottomPadding, gridLinePaint)
        }
        // Last vertical line
        canvas.drawLine(leftPadding + numDays * colWidth, topPadding - 72f, leftPadding + numDays * colWidth, height - bottomPadding, gridLinePaint)

        // Header bottom separator
        canvas.drawLine(leftPadding, topPadding - 12f, width - rightPadding, topPadding - 12f, gridLinePaint)

        // 5. Draw Row Grid & Time Labels
        for (h in 0..numHours) {
            val currentHour = settings.startHour + h
            val y = topPadding + h * rowHeight

            // Grid Line
            canvas.drawLine(leftPadding, y, width - rightPadding, y, gridLinePaint)

            // Hour Label (format: 08:00 AM / 01:00 PM)
            val amPm = if (currentHour >= 12) "PM" else "AM"
            val displayHour = when {
                currentHour == 0 -> 12
                currentHour > 12 -> currentHour - 12
                else -> currentHour
            }
            val timeText = String.format("%02d:00 %s", displayHour, amPm)
            canvas.drawText(timeText, leftPadding - 20f, y + 8f, timeLabelPaint)
        }

        // 6. Pre-calculate overlapping lanes for classes day-by-day to support side-by-side positioning
        val classLaneInfo = mutableMapOf<Int, Pair<Int, Int>>() // classId -> Pair(laneIndex, totalLanes)
        for (day in 1..numDays) {
            val dayClasses = classes.filter { it.isScheduled && it.dayOfWeek == day }
                .sortedWith(compareBy({ it.startHour * 60 + it.startMinute }, { -it.durationMinutes }))

            val components = mutableListOf<MutableList<ClassBlock>>()
            for (cls in dayClasses) {
                val start = cls.startHour * 60 + cls.startMinute
                val end = start + cls.durationMinutes
                var mergedComponent: MutableList<ClassBlock>? = null
                for (comp in components) {
                    val overlapsAny = comp.any { other ->
                        val oStart = other.startHour * 60 + other.startMinute
                        val oEnd = oStart + other.durationMinutes
                        start < oEnd && oStart < end
                    }
                    if (overlapsAny) {
                        if (mergedComponent == null) {
                            comp.add(cls)
                            mergedComponent = comp
                        } else {
                            mergedComponent.addAll(comp)
                            comp.clear()
                        }
                    }
                }
                if (mergedComponent == null) {
                    components.add(mutableListOf(cls))
                }
                components.removeAll { it.isEmpty() }
            }

            for (comp in components) {
                val sortedComp = comp.sortedWith(compareBy({ it.startHour * 60 + it.startMinute }, { -it.durationMinutes }))
                val lanes = mutableListOf<Int>()
                val compLanes = mutableMapOf<Int, Int>()

                for (cls in sortedComp) {
                    val start = cls.startHour * 60 + cls.startMinute
                    val end = start + cls.durationMinutes
                    var placed = false
                    for (laneIdx in lanes.indices) {
                        if (lanes[laneIdx] <= start) {
                            lanes[laneIdx] = end
                            compLanes[cls.id] = laneIdx
                            placed = true
                            break
                        }
                    }
                    if (!placed) {
                        lanes.add(end)
                        compLanes[cls.id] = lanes.size - 1
                    }
                }

                val totalLanes = lanes.size
                for (cls in sortedComp) {
                    val laneIdx = compLanes[cls.id] ?: 0
                    classLaneInfo[cls.id] = Pair(laneIdx, totalLanes)
                }
            }
        }

        // 7. Draw Class Blocks
        val cardPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#000000")
            alpha = 30 // Subtle border
            isAntiAlias = true
        }

        for (cls in classes) {
            // Only draw if scheduled and fits columns
            if (!cls.isScheduled || cls.dayOfWeek > numDays) continue

            val colIndex = cls.dayOfWeek - 1
            val startFraction = cls.startHour + cls.startMinute / 60f
            val endFraction = startFraction + cls.durationMinutes / 60f

            // Check if within bounds
            if (endFraction < settings.startHour || startFraction > settings.endHour) continue

            // Clamp positions to grid
            val clampedStart = maxOf(startFraction, settings.startHour.toFloat())
            val clampedEnd = minOf(endFraction, settings.endHour.toFloat())

            val topY = topPadding + (clampedStart - settings.startHour) * rowHeight
            val bottomY = topPadding + (clampedEnd - settings.startHour) * rowHeight

            // Retrieve pre-calculated lane details to position class side-by-side
            val laneInfo = classLaneInfo[cls.id] ?: Pair(0, 1)
            val laneIndex = laneInfo.first
            val totalLanes = laneInfo.second

            val subColWidth = (colWidth - 16f) / totalLanes
            val leftX = leftPadding + colIndex * colWidth + 8f + laneIndex * subColWidth
            val rightX = leftX + subColWidth

            // Card Rect
            val rect = RectF(leftX, topY + 4f, rightX, bottomY - 4f)

            // Draw Card Body
            try {
                cardPaint.color = Color.parseColor(cls.colorHex)
            } catch (e: Exception) {
                cardPaint.color = Color.parseColor("#4F46E5") // Indigo fallback
            }
            canvas.drawRoundRect(rect, 16f, 16f, cardPaint)
            canvas.drawRoundRect(rect, 16f, 16f, cardBorderPaint)

            // Determine Text Color (Light or Dark Contrast)
            val darkText = isColorLight(cls.colorHex)
            val textColorCode = if (darkText) "#0F172A" else "#FFFFFF"

            // Adjust text sizes dynamically based on width (total lanes) and card height
            val textPadding = 12f
            val maxTextWidth = (rect.width() - textPadding * 2).toInt()
            val cardHeightVal = rect.height()

            if (maxTextWidth > 50 && cardHeightVal > 36f) {
                canvas.save()
                canvas.translate(rect.left + textPadding, rect.top + textPadding)

                val heightScaleFactor = (cardHeightVal / 100f).coerceIn(0.5f, 1.0f)
                val widthScaleFactor = if (totalLanes > 1) (1.2f / totalLanes).coerceIn(0.6f, 1.0f) else 1.0f
                val scaleFactor = minOf(heightScaleFactor, widthScaleFactor)

                val subjectTextPaint = TextPaint().apply {
                    color = Color.parseColor(textColorCode)
                    textSize = 24f * scaleFactor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val detailsTextPaint = TextPaint().apply {
                    color = Color.parseColor(if (darkText) "#334155" else "#CBD5E1")
                    textSize = 18f * scaleFactor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

                // Layout Subject Name
                val subjectLayout = StaticLayout.Builder.obtain(
                    cls.name, 0, cls.name.length, subjectTextPaint, maxTextWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                subjectLayout.draw(canvas)

                // Layout Professor / Room details below, only if we have sufficient vertical space left
                val detailsText = mutableListOf<String>()
                if (cls.room.isNotBlank()) detailsText.add("Room: ${cls.room}")
                if (cls.professor.isNotBlank()) detailsText.add("Prof: ${cls.professor}")

                if (detailsText.isNotEmpty() && cardHeightVal - textPadding * 2 - subjectLayout.height > 24f * scaleFactor) {
                    val detailStr = detailsText.joinToString("\n")
                    canvas.translate(0f, subjectLayout.height + 4f)

                    val detailsLayout = StaticLayout.Builder.obtain(
                        detailStr, 0, detailStr.length, detailsTextPaint, maxTextWidth
                    ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                    detailsLayout.draw(canvas)
                }

                canvas.restore()
            }
        }

        return bitmap
    }

    // Export and Share schedule as a JPEG Photo
    fun exportAndSharePhoto(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ) {
        try {
            val bitmap = generateScheduleBitmap(context, classes, settings)
            val cachePath = File(context.cacheDir, "schedules")
            cachePath.mkdirs()

            val file = File(cachePath, "school_schedule.jpg")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My School Schedule")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Schedule Photo"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Export and Share schedule as a PDF Document
    fun exportAndSharePdf(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ) {
        try {
            // First, render the schedule to a Bitmap
            val bitmap = generateScheduleBitmap(context, classes, settings)
            val width = bitmap.width
            val height = bitmap.height

            // PDF dimensions dynamically match the high-resolution bitmap
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val pdfCanvas = page.canvas
            val rect = Rect(0, 0, width, height)
            pdfCanvas.drawBitmap(bitmap, null, rect, null)

            pdfDocument.finishPage(page)

            val cachePath = File(context.cacheDir, "schedules")
            cachePath.mkdirs()

            val file = File(cachePath, "school_schedule.pdf")
            val stream = FileOutputStream(file)
            pdfDocument.writeTo(stream)
            stream.flush()
            stream.close()
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "My School Schedule PDF")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Schedule PDF"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Capture and Save schedule directly to public Downloads folder (Offline PDF Downloader)
    fun downloadPdfToDevice(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ) {
        try {
            // Render the grid state to high-resolution Bitmap
            val bitmap = generateScheduleBitmap(context, classes, settings)
            val width = bitmap.width
            val height = bitmap.height

            // Compile pages using PdfDocument API matching the dynamic size
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val pdfCanvas = page.canvas
            val rect = Rect(0, 0, width, height)
            pdfCanvas.drawBitmap(bitmap, null, rect, null)

            pdfDocument.finishPage(page)

            val schoolClean = settings.schoolName.replace("\\s+".toRegex(), "_")
            val fileName = "${schoolClean}_Schedule_${System.currentTimeMillis()}.pdf"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        pdfDocument.writeTo(stream)
                    }
                    Toast.makeText(context, "Successfully downloaded: $fileName in Downloads folder!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to create PDF output.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { stream ->
                    pdfDocument.writeTo(stream)
                }
                Toast.makeText(context, "Downloaded to Downloads folder: ${file.name}", Toast.LENGTH_LONG).show()
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Capture and Save schedule directly to public Downloads folder as high-quality PNG
    fun downloadPngToDevice(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ) {
        try {
            // Render the grid state to high-resolution Bitmap
            val bitmap = generateScheduleBitmap(context, classes, settings)

            val schoolClean = settings.schoolName.replace("\\s+".toRegex(), "_")
            val fileName = "${schoolClean}_Schedule_${System.currentTimeMillis()}.png"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    Toast.makeText(context, "Successfully downloaded PNG: $fileName in Downloads folder!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to create PNG output.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(context, "Downloaded PNG to Downloads folder: ${file.name}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PNG Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
