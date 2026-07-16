package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.ClassBlock
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are an expert school schedule and timetable parser. Your task is to analyze the provided schedule (either an image/screenshot or plain text/syllabus) and extract a structured list of class blocks.

IMPORTANT instructions for parsing:
1. Identify each class/subject name, professor, and classroom/room.
2. Determine the day of the week (1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday, 7 = Sunday).
3. Determine the starting hour (0 to 23) and starting minute (0 to 59).
4. Estimate the duration of the class in minutes (e.g., 60, 90, 120 minutes). If duration is not clear, default to 60.
5. Choose a distinctive hex color code ('colorHex') to represent the class visually (e.g., modern Material Design color hexes like '#4F46E5', '#EF4444', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4').

You MUST output ONLY a valid JSON array of objects representing the class blocks. Do NOT include markdown code fences (like ```json) or any explanation. Output raw, clean JSON text.

Output format:
[
  {
    "name": "Introduction to Computer Science",
    "professor": "Dr. Alan Turing",
    "room": "Room 401",
    "dayOfWeek": 1,
    "startHour": 10,
    "startMinute": 30,
    "durationMinutes": 90,
    "colorHex": "#4F46E5"
  }
]
"""

    /**
     * Converts a Bitmap to a base64 encoded string.
     * Downsizes the bitmap if it exceeds reasonable dimensions to prevent huge payload sizes,
     * which are a frequent cause of API gateway 503 and network timeout errors.
     */
    private fun Bitmap.toBase64(): String {
        val maxDim = 1024
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val (newWidth, newHeight) = if (ratio > 1f) {
                Pair(maxDim, (maxDim / ratio).toInt())
            } else {
                Pair((maxDim * ratio).toInt(), maxDim)
            }
            try {
                Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
            } catch (e: Exception) {
                this
            }
        } else {
            this
        }
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Render the first page of a PDF file into a high quality bitmap.
     */
    private fun renderPdfToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                if (pdfRenderer.pageCount > 0) {
                    val page = pdfRenderer.openPage(0)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pdfRenderer.close()
                    parcelFileDescriptor.close()
                    bitmap
                } else {
                    pdfRenderer.close()
                    parcelFileDescriptor.close()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Processes a Uri to produce either an Image bitmap or Text content.
     */
    fun processUriToInput(context: Context, uri: Uri): ParseInput {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val path = uri.path?.lowercase() ?: ""
        
        // Check if image
        if (mimeType.startsWith("image/") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")) {
            return try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    ParseInput.Image(bitmap)
                } else {
                    ParseInput.Error("Failed to decode image bitmap.")
                }
            } catch (e: Exception) {
                ParseInput.Error("Error reading image: ${e.localizedMessage}")
            }
        }
        
        // Check if PDF
        if (mimeType == "application/pdf" || path.endsWith(".pdf")) {
            val bitmap = renderPdfToBitmap(context, uri)
            return if (bitmap != null) {
                ParseInput.Image(bitmap)
            } else {
                ParseInput.Error("Failed to render PDF to bitmap.")
            }
        }
        
        // Default: Try reading as a plain text file
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use { it.readText() }
            if (!content.isNullOrBlank()) {
                ParseInput.Text(content)
            } else {
                ParseInput.Error("File is empty or could not be read as text.")
            }
        } catch (e: Exception) {
            ParseInput.Error("Error reading file content: ${e.localizedMessage}")
        }
    }

    private class TransientApiException(message: String, val code: Int) : Exception(message)

    /**
     * Analyzes the given input using Gemini API and returns the parsed list of ClassBlocks.
     * Retries automatically on transient API failures (like 503 or 429) using exponential backoff.
     */
    suspend fun parseScheduleWithAI(input: ParseInput): List<ClassBlock> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API key is not configured. Please add your key to the Secrets panel in AI Studio.")
        }

        // Build the request contents based on whether the input is text or image
        val contentParts = mutableListOf<Part>()
        when (input) {
            is ParseInput.Text -> {
                contentParts.add(Part(text = "Please organize this syllabus text into schedule blocks:\n\n${input.content}"))
            }
            is ParseInput.Image -> {
                val base64Data = input.bitmap.toBase64()
                contentParts.add(Part(text = "Analyze this timetable screenshot or PDF page and extract the classes."))
                contentParts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data)))
            }
            is ParseInput.Error -> {
                throw Exception(input.message)
            }
        }

        val request = GeminiRequest(
            contents = listOf(Content(parts = contentParts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            ),
            systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT)))
        )

        val jsonRequest = moshi.adapter(GeminiRequest::class.java).toJson(request)

        var maxRetries = 3
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            val modelName = when (attempt) {
                0 -> "gemini-3.5-flash"
                1 -> "gemini-3.1-flash-lite-preview"
                else -> "gemini-3.5-flash"
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val okHttpRequest = Request.Builder()
                .url(url)
                .post(jsonRequest.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(okHttpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val msg = response.message
                        if (code == 503 || code == 429 || code >= 500) {
                            throw TransientApiException("Gemini API is temporarily busy (Error $code: $msg on model $modelName). Retrying...", code)
                        } else {
                            throw Exception("Gemini API error: $code $msg (model: $modelName)")
                        }
                    }
                    val bodyString = response.body?.string() ?: throw Exception("Empty response from Gemini API.")
                    
                    val geminiResponse = moshi.adapter(GeminiResponse::class.java).fromJson(bodyString)
                    val responseText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw Exception("No response parsed from Gemini candidate.")

                    // Parse the list of ExtractedClasses from the JSON text
                    val cleanJson = cleanJsonResponse(responseText)
                    val type = Types.newParameterizedType(List::class.java, ExtractedClass::class.java)
                    val listAdapter = moshi.adapter<List<ExtractedClass>>(type)
                    val extractedList = listAdapter.fromJson(cleanJson) ?: emptyList()

                    // Map to ClassBlock data models
                    return@withContext extractedList.map { ext ->
                        ClassBlock(
                            name = ext.name.ifBlank { "Untitled Class" },
                            professor = ext.professor ?: "",
                            room = ext.room ?: "",
                            dayOfWeek = (ext.dayOfWeek ?: 0).coerceIn(0, 7),
                            startHour = (ext.startHour ?: 9).coerceIn(0, 23),
                            startMinute = (ext.startMinute ?: 0).coerceIn(0, 59),
                            durationMinutes = (ext.durationMinutes ?: 60).coerceAtLeast(10),
                            colorHex = ext.colorHex?.ifBlank { "#4F46E5" } ?: "#4F46E5"
                        )
                    }
                }
            } catch (e: TransientApiException) {
                lastException = e
                attempt++
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(attempt * 2000L) // Backoff: 2s, 4s delay
                }
            } catch (e: java.io.IOException) {
                lastException = Exception("Network connectivity issue. Please check your internet. Details: ${e.localizedMessage}")
                attempt++
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(attempt * 2000L)
                }
            } catch (e: Exception) {
                throw e // Propagate parsing errors or other permanent exceptions immediately
            }
        }
        throw lastException ?: Exception("Analysis failed after maximum retry attempts.")
    }

    /**
     * Helper to clean up any markdown response formatting.
     */
    private fun cleanJsonResponse(rawResponse: String): String {
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
