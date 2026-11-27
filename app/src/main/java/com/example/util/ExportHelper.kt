package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ExportHelper {

    private fun isColorLight(colorHex: String): Boolean = try {
        val color = Color.parseColor(colorHex)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        (0.299 * r + 0.587 * g + 0.114 * b) > 180
    } catch (_: Exception) {
        false
    }

    fun generateScheduleBitmap(
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Bitmap {
        val safeStart = settings.startHour.coerceIn(0, 22)
        val safeEnd = settings.endHour.coerceIn(safeStart + 1, 23)
        val numHours = safeEnd - safeStart

        val width = 1920
        val desiredRowHeight = 112f
        val topPadding = 248f
        val bottomPadding = 64f
        val height = maxOf(1360, (topPadding + bottomPadding + numHours * desiredRowHeight).toInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm off-white archival sheet background
        canvas.drawColor(Color.parseColor("#F6F4EF"))

        val bgHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E3A34")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val accentStripePaint = Paint().apply {
            color = Color.parseColor("#B85D3B")
            style = Paint.Style.FILL
        }
        val textHeaderTitlePaint = Paint().apply {
            color = Color.parseColor("#F8F7F4")
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textHeaderSubPaint = Paint().apply {
            color = Color.parseColor("#D8E6E1")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val gridLinePaint = Paint().apply {
            color = Color.parseColor("#DDD7CC")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val dayHeaderPaint = Paint().apply {
            color = Color.parseColor("#1B1B18")
            textSize = 24f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val timeLabelPaint = Paint().apply {
            color = Color.parseColor("#5E5C54")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        // Top editorial masthead
        canvas.drawRect(0f, 0f, width.toFloat(), 158f, bgHeaderPaint)
        canvas.drawRect(0f, 152f, width.toFloat(), 158f, accentStripePaint)
        canvas.drawText(settings.schoolName.ifBlank { "Class Schedule" }.uppercase(), 64f, 72f, textHeaderTitlePaint)
        canvas.drawText(settings.termName.ifBlank { "Weekly Timetable" }, 64f, 118f, textHeaderSubPaint)

        val leftPadding = 160f
        val rightPadding = 48f
        val availableWidth = width - leftPadding - rightPadding
        val numDays = if (settings.showWeekends) 7 else 6
        val colWidth = availableWidth / numDays
        val availableHeight = height - topPadding - bottomPadding
        val rowHeight = availableHeight / numHours

        // Crisp paper grid interior behind schedule columns
        val sheetPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(leftPadding, topPadding - 72f, width - rightPadding, height - bottomPadding),
            10f,
            10f,
            sheetPaint
        )

        val dayHeaderBgPaint = Paint().apply {
            color = Color.parseColor("#EDE9DF")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(leftPadding, topPadding - 72f, width - rightPadding, topPadding - 10f),
            10f,
            10f,
            dayHeaderBgPaint
        )

        for (i in 0 until numDays) {
            val dayName = ScheduleLayoutHelper.dayFullName(i + 1).uppercase()
            val x = leftPadding + (i + 0.5f) * colWidth
            canvas.drawText(dayName, x, topPadding - 32f, dayHeaderPaint)
            val lineX = leftPadding + i * colWidth
            canvas.drawLine(lineX, topPadding - 72f, lineX, height - bottomPadding, gridLinePaint)
        }
        canvas.drawLine(
            leftPadding + numDays * colWidth,
            topPadding - 72f,
            leftPadding + numDays * colWidth,
            height - bottomPadding,
            gridLinePaint
        )
        canvas.drawLine(leftPadding, topPadding - 10f, width - rightPadding, topPadding - 10f, gridLinePaint)

        for (h in 0..numHours) {
            val currentHour = safeStart + h
            val y = topPadding + h * rowHeight
            canvas.drawLine(leftPadding, y, width - rightPadding, y, gridLinePaint)
            val label = ScheduleLayoutHelper.formatHourShort(currentHour)
            canvas.drawText(label, leftPadding - 20f, y + 8f, timeLabelPaint)
        }

        val laneByDay = (1..numDays).associateWith { day ->
            ScheduleLayoutHelper.computeDayLanes(classes.filter { it.isScheduled && it.dayOfWeek == day })
        }

        val cardPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.BLACK
            alpha = 28
            isAntiAlias = true
        }

        for (cls in classes) {
            if (!cls.isScheduled || cls.dayOfWeek !in 1..numDays) continue
            val colIndex = cls.dayOfWeek - 1
            val startFraction = cls.startHour + cls.startMinute / 60f
            val endFraction = startFraction + cls.durationMinutes.coerceAtLeast(15) / 60f
            if (endFraction <= safeStart || startFraction >= safeEnd) continue

            val clampedStart = maxOf(startFraction, safeStart.toFloat())
            val clampedEnd = minOf(endFraction, safeEnd.toFloat())
            val topY = topPadding + (clampedStart - safeStart) * rowHeight
            val bottomY = topPadding + (clampedEnd - safeStart) * rowHeight

            val lane = laneByDay[cls.dayOfWeek]?.get(cls.id) ?: LanePlacement(0, 1)
            val subColWidth = (colWidth - 16f) / lane.totalLanes
            val leftX = leftPadding + colIndex * colWidth + 8f + lane.laneIndex * subColWidth
            val rightX = leftX + subColWidth
            val rect = RectF(leftX, topY + 4f, rightX, bottomY - 4f)

            val safeHex = ScheduleLayoutHelper.normalizeColorHex(cls.colorHex)
            cardPaint.color = Color.parseColor(safeHex)
            canvas.drawRoundRect(rect, 10f, 10f, cardPaint)
            canvas.drawRoundRect(rect, 10f, 10f, cardBorderPaint)

            val darkText = isColorLight(safeHex)
            val textColor = Color.parseColor(if (darkText) "#1B1B18" else "#FFFFFF")
            val subColor = Color.parseColor(if (darkText) "#47453F" else "#EAE6DF")

            val textPadding = 12f
            val maxTextWidth = (rect.width() - textPadding * 2).toInt()
            val cardHeightVal = rect.height()

            if (maxTextWidth > 48 && cardHeightVal > 32f) {
                canvas.save()
                canvas.translate(rect.left + textPadding, rect.top + textPadding)

                val heightScaleFactor = (cardHeightVal / 100f).coerceIn(0.55f, 1.0f)
                val widthScaleFactor = if (lane.totalLanes > 1) (1.2f / lane.totalLanes).coerceIn(0.65f, 1.0f) else 1.0f
                val scaleFactor = minOf(heightScaleFactor, widthScaleFactor)

                val subjectPaint = TextPaint().apply {
                    color = textColor
                    textSize = 23f * scaleFactor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                val detailsPaint = TextPaint().apply {
                    color = subColor
                    textSize = 18f * scaleFactor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

                val subjectLayout = StaticLayout.Builder.obtain(
                    cls.name, 0, cls.name.length, subjectPaint, maxTextWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                subjectLayout.draw(canvas)

                val detailParts = buildList {
                    if (cls.room.isNotBlank()) add(cls.room)
                    if (cls.professor.isNotBlank()) add(cls.professor)
                }
                if (detailParts.isNotEmpty() && cardHeightVal - textPadding * 2 - subjectLayout.height > 22f * scaleFactor) {
                    val detailStr = detailParts.joinToString("\n")
                    canvas.translate(0f, subjectLayout.height + 4f)
                    val detailsLayout = StaticLayout.Builder.obtain(
                        detailStr, 0, detailStr.length, detailsPaint, maxTextWidth
                    ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                    detailsLayout.draw(canvas)
                }
                canvas.restore()
            }
        }
        return bitmap
    }

    suspend fun exportAndSharePhoto(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = generateScheduleBitmap(classes, settings)
            val cachePath = File(context.cacheDir, "schedules").apply { mkdirs() }
            val file = File(cachePath, "school_schedule.jpg")
            try {
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
            } finally {
                bitmap.recycle()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${settings.schoolName} Schedule")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(shareIntent, "Share Schedule Image")
        }
    }

    suspend fun exportAndSharePdf(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = generateScheduleBitmap(classes, settings)
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, null, Rect(0, 0, bitmap.width, bitmap.height), null)
                pdfDocument.finishPage(page)

                val cachePath = File(context.cacheDir, "schedules").apply { mkdirs() }
                val file = File(cachePath, "school_schedule.pdf")
                FileOutputStream(file).use { stream ->
                    pdfDocument.writeTo(stream)
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${settings.schoolName} Schedule PDF")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Intent.createChooser(shareIntent, "Share Schedule PDF")
            } finally {
                pdfDocument.close()
                bitmap.recycle()
            }
        }
    }

    suspend fun downloadPdfToDevice(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = generateScheduleBitmap(classes, settings)
            val pdfDocument = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, null, Rect(0, 0, bitmap.width, bitmap.height), null)
                pdfDocument.finishPage(page)

                val schoolClean = settings.schoolName.trim().ifBlank { "Schedule" }.replace("\\s+".toRegex(), "_")
                val fileName = "${schoolClean}_${System.currentTimeMillis()}.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create PDF file in Downloads")
                    resolver.openOutputStream(uri)?.use { stream ->
                        pdfDocument.writeTo(stream)
                    } ?: error("Could not open Downloads output stream")
                    "Saved $fileName to Downloads"
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "downloads").apply { mkdirs() }
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { stream ->
                        pdfDocument.writeTo(stream)
                    }
                    "Saved ${file.name}"
                }
            } finally {
                pdfDocument.close()
                bitmap.recycle()
            }
        }
    }

    suspend fun downloadPngToDevice(
        context: Context,
        classes: List<ClassBlock>,
        settings: ScheduleSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = generateScheduleBitmap(classes, settings)
            try {
                val schoolClean = settings.schoolName.trim().ifBlank { "Schedule" }.replace("\\s+".toRegex(), "_")
                val fileName = "${schoolClean}_${System.currentTimeMillis()}.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create PNG file in Downloads")
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    } ?: error("Could not open Downloads output stream")
                    "Saved $fileName to Downloads"
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "downloads").apply { mkdirs() }
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    "Saved ${file.name}"
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
