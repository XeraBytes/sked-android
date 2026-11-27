package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import com.example.data.model.ClassBlock
import com.example.data.prefs.AppPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiHelper {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @JsonClass(generateAdapter = true)
    data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
        val systemInstruction: Content? = null
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        val parts: List<Part>
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        val mimeType: String,
        val data: String
    )

    @JsonClass(generateAdapter = true)
    data class GenerationConfig(
        val responseMimeType: String? = null,
        val temperature: Float? = null
    )

    @JsonClass(generateAdapter = true)
    data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    @JsonClass(generateAdapter = true)
    data class Candidate(
        val content: Content?
    )

    @JsonClass(generateAdapter = true)
    data class ExtractedClass(
        val name: String,
        val professor: String?,
        val room: String?,
        val dayOfWeek: Int?, // 1=Mon, ..., 7=Sun, 0=unscheduled
        val startHour: Int?,
        val startMinute: Int?,
        val durationMinutes: Int?,
        val colorHex: String?
    )

    sealed class ParseInput {
        data class Image(val bitmap: Bitmap) : ParseInput()
        data class Text(val content: String) : ParseInput()
        data class Error(val message: String) : ParseInput()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val candidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-4-hydrogen",
        "gemini-3.1-flash-lite"
    )

    private const val SYSTEM_PROMPT = """
You extract structured weekly class schedules from timetable images, PDFs, or syllabus text.

Rules:
1. Extract subject/course name, instructor/professor (empty string if omitted), and classroom/hall (empty string if omitted).
2. dayOfWeek: 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday, 7 = Sunday, or 0 if unscheduled.
3. startHour: 0..23 (24-hour clock). startMinute: 0..59.
4. durationMinutes: integer minutes (default 60 if duration is unknown).
5. colorHex: distinct editorial hex colors from '#2E5A4F', '#B85D3B', '#38598B', '#9A6B2F', '#744253', '#4A6B5D', '#5D5179', '#2F6F7E', '#8E443D'. Use the same color for repeated meetings of the same course.
6. Output ONLY a raw JSON array of objects, with no markdown code fences or prose.

Example:
[
  {
    "name": "Linear Algebra",
    "professor": "Prof. R. Mendoza",
    "room": "Room 302",
    "dayOfWeek": 1,
    "startHour": 10,
    "startMinute": 30,
    "durationMinutes": 90,
    "colorHex": "#2E5A4F"
  }
]
"""

    private fun Bitmap.toBase64Jpeg(): String {
        val maxDim = 1280
        var scaled: Bitmap = this
        var needsRecycle = false
        if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val (newW, newH) = if (ratio > 1f) {
                maxDim to (maxDim / ratio).toInt().coerceAtLeast(1)
            } else {
                (maxDim * ratio).toInt().coerceAtLeast(1) to maxDim
            }
            scaled = Bitmap.createScaledBitmap(this, newW, newH, true)
            needsRecycle = (scaled !== this)
        }
        return try {
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (needsRecycle) {
                scaled.recycle()
            }
        }
    }

    private fun renderPdfToBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val width = (page.width * 2).coerceAtMost(2048)
                    val height = (page.height * 2).coerceAtMost(2800)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun processUriToInput(context: Context, uri: Uri): ParseInput = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()

        if (mimeType.startsWith("image/") ||
            path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            path.endsWith(".png") || path.endsWith(".webp")
        ) {
            return@withContext try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) ParseInput.Image(bitmap)
                else ParseInput.Error("Could not decode the selected image.")
            } catch (e: Exception) {
                ParseInput.Error("Error reading image: ${e.localizedMessage ?: "unknown error"}")
            }
        }

        if (mimeType == "application/pdf" || path.endsWith(".pdf")) {
            val bitmap = renderPdfToBitmap(context, uri)
            return@withContext if (bitmap != null) ParseInput.Image(bitmap)
            else ParseInput.Error("Could not render page 1 of the PDF.")
        }

        return@withContext try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (content.isNotBlank()) ParseInput.Text(content)
            else ParseInput.Error("The selected file is empty or unreadable.")
        } catch (e: Exception) {
            ParseInput.Error("Error reading file: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    private class TransientApiException(message: String) : Exception(message)

    suspend fun parseScheduleWithAI(context: Context, input: ParseInput): List<ClassBlock> =
        withContext(Dispatchers.IO) {
            val apiKey = AppPreferences.getEffectiveGeminiApiKey(context)
            if (apiKey.isBlank()) {
                throw IllegalStateException(
                    "No Gemini API key is configured. Add your API key in Settings -> Updates & Keys, or build with GEMINI_API_KEY in .env."
                )
            }

            val contentParts = mutableListOf<Part>()
            when (input) {
                is ParseInput.Text -> {
                    contentParts.add(Part(text = "Extract the weekly class schedule from this text:\n\n${input.content}"))
                }
                is ParseInput.Image -> {
                    try {
                        val base64Data = input.bitmap.toBase64Jpeg()
                        contentParts.add(Part(text = "Analyze this timetable screenshot or PDF page and extract all classes."))
                        contentParts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data)))
                    } finally {
                        if (!input.bitmap.isRecycled) {
                            input.bitmap.recycle()
                        }
                    }
                }
                is ParseInput.Error -> {
                    throw IllegalArgumentException(input.message)
                }
            }

            val requestPayload = GeminiRequest(
                contents = listOf(Content(parts = contentParts)),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1f
                ),
                systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT)))
            )

            val jsonBody = moshi.adapter(GeminiRequest::class.java).toJson(requestPayload)
            var lastError: Exception? = null

            for ((attempt, modelName) in candidateModels.withIndex()) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val apiMessage = extractApiError(bodyString) ?: "${response.code} ${response.message}"
                            if (response.code == 429 || response.code == 404 || response.code >= 500) {
                                throw TransientApiException("Gemini ($modelName): $apiMessage")
                            }
                            throw IllegalStateException("Gemini API error ($modelName): $apiMessage")
                        }

                        if (bodyString.isBlank()) {
                            throw IllegalStateException("Gemini returned an empty response.")
                        }

                        val parsed = moshi.adapter(GeminiResponse::class.java).fromJson(bodyString)
                        val text = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: throw IllegalStateException("Gemini did not return schedule output.")

                        val cleanJson = cleanJsonResponse(text)
                        val type = Types.newParameterizedType(List::class.java, ExtractedClass::class.java)
                        val extracted = moshi.adapter<List<ExtractedClass>>(type).fromJson(cleanJson).orEmpty()

                        return@withContext extracted.map { ext ->
                            ClassBlock(
                                name = ext.name.trim().ifBlank { "Untitled Course" },
                                professor = ext.professor?.trim().orEmpty(),
                                room = ext.room?.trim().orEmpty(),
                                dayOfWeek = (ext.dayOfWeek ?: 0).coerceIn(0, 7),
                                startHour = (ext.startHour ?: 9).coerceIn(0, 23),
                                startMinute = (ext.startMinute ?: 0).coerceIn(0, 59),
                                durationMinutes = (ext.durationMinutes ?: 60).coerceIn(15, 480),
                                colorHex = ScheduleLayoutHelper.normalizeColorHex(ext.colorHex)
                            )
                        }
                    }
                } catch (e: TransientApiException) {
                    lastError = e
                    if (attempt < candidateModels.lastIndex) {
                        delay((attempt + 1) * 1200L)
                    }
                } catch (e: IOException) {
                    lastError = IOException(
                        "Network error contacting Gemini API: ${e.localizedMessage ?: "check internet connection"}"
                    )
                    if (attempt < candidateModels.lastIndex) {
                        delay((attempt + 1) * 1200L)
                    }
                }
            }

            throw lastError ?: IllegalStateException("Schedule extraction failed after exhausting candidate models.")
        }

    private fun extractApiError(rawBody: String): String? = try {
        if (rawBody.isBlank()) null
        else JSONObject(rawBody).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    internal fun cleanJsonResponse(rawResponse: String): String {
        var clean = rawResponse.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.substringAfter("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.substringBeforeLast("```")
        }
        return clean.trim()
    }
}
