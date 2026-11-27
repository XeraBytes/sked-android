package com.example.lms

import android.content.Context
import com.example.data.model.ClassBlock
import com.example.ui.theme.CourseColorSwatches
import com.example.util.GeminiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class EguroSyncResult(
    val classes: List<ClassBlock>,
    val sourcePageUrl: String,
    val usedGeminiFallback: Boolean = false,
    val checkedPages: List<String> = emptyList()
)

/**
 * Native HTTP client for CCC e-GURO (https://lms.ccc.edu.ph).
 * Authenticates via standard OkHttp CookieJar + CSRF form token without rendering a WebView.
 * Never auto-retries failed credentials to avoid triggering the 3-attempt account lock.
 */
object EguroClient {

    const val BASE_URL = "https://lms.ccc.edu.ph"
    const val LOGIN_PAGE_URL = "$BASE_URL/index.php"
    const val LOGIN_POST_URL = "$BASE_URL/app/login.php?formSubmitted=true"

    private val inMemoryCookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val existing = inMemoryCookieStore.getOrPut(host) { mutableListOf() }
            synchronized(existing) {
                for (c in cookies) {
                    existing.removeAll { it.name == c.name && it.path == c.path }
                    if (c.expiresAt >= System.currentTimeMillis()) {
                        existing.add(c)
                    }
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val existing = inMemoryCookieStore[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(existing) {
                existing.removeAll { it.expiresAt < now }
                return existing.toList()
            }
        }
    }

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun clearSessionCookies() {
        inMemoryCookieStore.clear()
    }

    internal fun extractCsrfToken(html: String): String? {
        val patterns = listOf(
            Regex("""name=["']token_login_form["'][^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""value=["']([^"']+)["'][^>]*name=["']token_login_form["']""", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(html)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    internal fun looksLikeLoginPage(html: String, finalUrl: String): Boolean {
        val lowerUrl = finalUrl.lowercase(Locale.US)
        val hasLoginForm = html.contains("id=\"login_form\"", ignoreCase = true) ||
            html.contains("name='login_form'", ignoreCase = true) ||
            html.contains("name=\"token_login_form\"", ignoreCase = true)
        return hasLoginForm || lowerUrl.endsWith("/index.php") || lowerUrl == "$BASE_URL/"
    }

    internal fun extractLoginErrorMessage(html: String): String? {
        val alertDivMatch = Regex(
            """<div[^>]*class=["'][^"']*alert-danger[^"']*["'][^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        ).find(html)
        if (alertDivMatch != null) {
            val alertText = stripHtmlTags(alertDivMatch.groupValues[1])
                .replace("×", "")
                .replace("&times;", "")
                .trim()
            if (alertText.isNotBlank()) return alertText
        }

        val clean = stripHtmlTags(html)
        val alertMatch = Regex(
            """(?:Invalid|Incorrect|Locked|suspended|blocked|too many attempts)[^.\n]{0,140}""",
            RegexOption.IGNORE_CASE
        ).find(clean)
        return alertMatch?.value?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Discovers "My Class", schedule, timetable, calendar, and AJAX JSON endpoints
     * using both URL strings and anchor text (e.g. `<a href="...">My Class</a>`).
     */
    internal fun discoverScheduleCandidateUrls(html: String, baseUrl: String): List<String> {
        val base = baseUrl.toHttpUrlOrNull() ?: return emptyList()
        val prioritized = LinkedHashSet<String>()
        val secondary = LinkedHashSet<String>()

        val highKeywords = listOf("my class", "my classes", "class schedule", "timetable", "schedule", "calendar")
        val lowKeywords = listOf("class", "subject", "course", "enroll", "load", "cor", "student")

        fun classifyAndAdd(rawUrl: String, labelText: String = "") {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("javascript:", ignoreCase = true)) return
            val lowerUrl = trimmed.lowercase(Locale.US)
            val lowerLabel = labelText.lowercase(Locale.US)
            if (lowerUrl.contains("logout") || lowerUrl.contains("signout") ||
                lowerUrl.endsWith(".css") || lowerUrl.endsWith(".js") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".ico")
            ) return

            val resolved = base.resolve(trimmed)?.toString() ?: return
            if (!resolved.startsWith(BASE_URL, ignoreCase = true) && base.host != resolved.toHttpUrlOrNull()?.host) return

            val combined = "$lowerUrl $lowerLabel"
            if (highKeywords.any { combined.contains(it) }) {
                prioritized.add(resolved)
            } else if (lowKeywords.any { combined.contains(it) }) {
                secondary.add(resolved)
            }
        }

        // 1. Anchor tags (inspects BOTH href and visible label like "My Class")
        val anchorRegex = Regex("""<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        for (m in anchorRegex.findAll(html)) {
            classifyAndAdd(m.groupValues[1], stripHtmlTags(m.groupValues[2]))
        }

        // 2. Attributes & JS AJAX/Tabulator/FullCalendar endpoints
        val attrUrlRegex = Regex(
            """(?:href|data-url|data-href|ajaxURL|events|url)\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        for (m in attrUrlRegex.findAll(html)) {
            classifyAndAdd(m.groupValues[1])
        }

        // 3. JS fetch(...) / $.get(...) / $.post(...) / window.location / onclick navigation
        val jsCallRegex = Regex(
            """(?:fetch|\$\.get|\$\.getJSON|open|loadPage|location\.href\s*=)\s*\(?\s*["']([^"']+\.php[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        for (m in jsCallRegex.findAll(html)) {
            classifyAndAdd(m.groupValues[1])
        }

        return (prioritized + secondary).toList()
    }

    suspend fun loginAndFetchSchedule(
        context: Context,
        username: String,
        password: String,
        customSchedulePath: String = "",
        httpClient: OkHttpClient = defaultClient,
        geminiFallback: suspend (Context, GeminiHelper.ParseInput) -> List<ClassBlock> = GeminiHelper::parseScheduleWithAI
    ): EguroSyncResult = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        require(cleanUser.isNotBlank()) { "Enter your CCC e-GURO username." }
        require(password.isNotEmpty()) { "Enter your CCC e-GURO password." }

        clearSessionCookies()

        // 1. Fetch public login page to obtain session cookie + CSRF token
        val loginPageReq = Request.Builder()
            .url(LOGIN_PAGE_URL)
            .header("User-Agent", "Sked-Android/1.1")
            .get()
            .build()

        val loginPageHtml = httpClient.newCall(loginPageReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Could not reach CCC e-GURO (HTTP ${resp.code}).")
            }
            resp.body?.string().orEmpty()
        }

        val csrfToken = extractCsrfToken(loginPageHtml)
            ?: throw IOException("Could not find the e-GURO login security token. The portal format may have changed.")

        val agentsJson = JSONObject().apply {
            put("device", "Android")
            put("version", "14")
            put("layout", "mobile")
            put("os", "Android")
            put("description", "Sked App")
        }.toString()

        // 2. Submit credentials once (never retry failed auth so we don't trigger 3-attempt lockout)
        val formBody = FormBody.Builder()
            .add("username", cleanUser)
            .add("password", password)
            .add("token_login_form", csrfToken)
            .add("submit", "login")
            .add("agents", agentsJson)
            .build()

        val loginPostReq = Request.Builder()
            .url(LOGIN_POST_URL)
            .header("User-Agent", "Sked-Android/1.1")
            .header("Referer", LOGIN_PAGE_URL)
            .post(formBody)
            .build()

        val (postAuthHtml, postAuthUrl) = httpClient.newCall(loginPostReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("e-GURO sign-in request failed (HTTP ${resp.code}).")
            }
            resp.body?.string().orEmpty() to resp.request.url.toString()
        }

        if (looksLikeLoginPage(postAuthHtml, postAuthUrl)) {
            val portalMsg = extractLoginErrorMessage(postAuthHtml)
            throw IllegalStateException(
                portalMsg ?: "Sign-in was rejected by e-GURO. Verify your username and password (accounts lock after 3 failed attempts)."
            )
        }

        // 3. Breadth-first discovery across custom path, "My Class" / schedule links, and sub-endpoints
        val queue = ArrayDeque<String>()
        val queuedSet = LinkedHashSet<String>()
        fun enqueue(url: String) {
            if (url.isNotBlank() && queuedSet.size < 14 && queuedSet.add(url)) {
                queue.addLast(url)
            }
        }

        val cleanCustom = customSchedulePath.trim()
        if (cleanCustom.isNotBlank()) {
            val resolved = if (cleanCustom.startsWith("http", ignoreCase = true)) {
                cleanCustom
            } else {
                "$BASE_URL/${cleanCustom.trimStart('/')}"
            }
            enqueue(resolved)
        }
        discoverScheduleCandidateUrls(postAuthHtml, postAuthUrl).forEach { enqueue(it) }
        enqueue(postAuthUrl)

        val checkedPages = mutableListOf<String>()
        val fetchedPayloads = mutableListOf<Pair<String, String>>()
        var untimedFallbackCandidate: Pair<String, List<ClassBlock>>? = null

        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            val pageBody = if (url == postAuthUrl) {
                postAuthHtml
            } else {
                runCatching {
                    httpClient.newCall(
                        Request.Builder()
                            .url(url)
                            .header("User-Agent", "Sked-Android/1.1")
                            .header("Referer", postAuthUrl)
                            .get()
                            .build()
                    ).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                    }
                }.getOrDefault("")
            }
            if (pageBody.isBlank() || looksLikeLoginPage(pageBody, url)) continue

            checkedPages.add(url)
            fetchedPayloads.add(url to pageBody)

            // Discover nested AJAX/JSON or subpage endpoints from "My Class"
            if (url != postAuthUrl) {
                discoverScheduleCandidateUrls(pageBody, url).forEach { enqueue(it) }
            }

            val directTimed = parseScheduleFromPayload(pageBody)
            if (directTimed.isNotEmpty()) {
                return@withContext EguroSyncResult(
                    classes = directTimed,
                    sourcePageUrl = url,
                    usedGeminiFallback = false,
                    checkedPages = checkedPages
                )
            }

            if (untimedFallbackCandidate == null) {
                val untimed = parseUntimedCourseCards(pageBody)
                if (untimed.isNotEmpty()) {
                    untimedFallbackCandidate = url to untimed
                }
            }
        }

        // 4. Optional Gemini fallback on sanitized text of the best candidate page (e.g. "My Class")
        for ((url, body) in fetchedPayloads) {
            val text = stripHtmlTags(body)
            if (text.length < 60) continue
            val aiParsed = runCatching {
                geminiFallback(context, GeminiHelper.ParseInput.Text(text.take(12_000)))
            }.getOrNull().orEmpty()
            if (aiParsed.isNotEmpty()) {
                return@withContext EguroSyncResult(
                    classes = aiParsed,
                    sourcePageUrl = url,
                    usedGeminiFallback = true,
                    checkedPages = checkedPages
                )
            }
        }

        // 5. If "My Class" lists enrolled courses without times/hours, import them into Pool (dayOfWeek = 0)
        if (untimedFallbackCandidate != null) {
            return@withContext EguroSyncResult(
                classes = untimedFallbackCandidate.second,
                sourcePageUrl = untimedFallbackCandidate.first,
                usedGeminiFallback = false,
                checkedPages = checkedPages
            )
        }

        EguroSyncResult(
            classes = emptyList(),
            sourcePageUrl = checkedPages.firstOrNull() ?: postAuthUrl,
            usedGeminiFallback = false,
            checkedPages = checkedPages
        )
    }

    /**
     * Parses timed schedule entries from FullCalendar/Tabulator JSON arrays, HTML tables, or HTML cards.
     */
    internal fun parseScheduleFromPayload(payload: String): List<ClassBlock> {
        val trimmed = payload.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonBlocks = parseJsonEvents(trimmed)
            if (jsonBlocks.isNotEmpty()) return jsonBlocks
        }

        // Extract inline JS JSON arrays if embedded inside an HTML page
        val jsonArrayRegex = Regex("""\[\s*\{[\s\S]{10,16000}?\}\s*\]""")
        for (match in jsonArrayRegex.findAll(payload)) {
            val embedded = parseJsonEvents(match.value)
            if (embedded.isNotEmpty()) return embedded
        }

        val tableBlocks = parseHtmlScheduleTables(payload)
        if (tableBlocks.isNotEmpty()) return tableBlocks

        return parseHtmlTimedCourseCards(payload)
    }

    internal fun parseJsonEvents(json: String): List<ClassBlock> {
        val array = runCatching {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("data")
                    ?: obj.optJSONArray("events")
                    ?: obj.optJSONArray("schedule")
                    ?: obj.optJSONArray("rows")
                    ?: obj.optJSONArray("classes")
                    ?: return emptyList()
            }
        }.getOrNull() ?: return emptyList()

        val result = mutableListOf<ClassBlock>()
        val colorByCourse = mutableMapOf<String, String>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = listOf("title", "subject", "course", "course_name", "subject_name", "class_name", "name")
                .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotEmpty() } }
                ?: continue

            val room = listOf("room", "location", "venue")
                .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotEmpty() } }
                .orEmpty()
            val prof = listOf("instructor", "professor", "faculty", "teacher")
                .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotEmpty() } }
                .orEmpty()

            val startStr = obj.optString("start", "").trim()
            val endStr = obj.optString("end", "").trim()
            val startTimeStr = obj.optString("startTime", "").trim()
            val endTimeStr = obj.optString("endTime", "").trim()
            val scheduleStr = listOf("schedule", "time", "sched")
                .firstNotNullOfOrNull { k -> obj.optString(k, "").trim().takeIf { it.isNotEmpty() } }
                .orEmpty()

            val days = parseDaysList(obj)
            val parsedRange = parseTimeRangeMinutes(
                when {
                    startTimeStr.isNotBlank() && endTimeStr.isNotBlank() -> "$startTimeStr - $endTimeStr"
                    scheduleStr.isNotBlank() -> scheduleStr
                    else -> extractTimeFromIso(startStr, endStr)
                }
            )

            if (days.isNotEmpty() && parsedRange != null) {
                val (startMinTotal, duration) = parsedRange
                val color = colorByCourse.getOrPut(name.lowercase(Locale.US)) {
                    CourseColorSwatches[colorByCourse.size % CourseColorSwatches.size]
                }
                for (d in days) {
                    result.add(
                        ClassBlock(
                            name = name,
                            professor = prof,
                            room = room,
                            dayOfWeek = d,
                            startHour = startMinTotal / 60,
                            startMinute = startMinTotal % 60,
                            durationMinutes = duration.coerceIn(15, 480),
                            colorHex = color
                        )
                    )
                }
            }
        }
        return deduplicateBlocks(result)
    }

    internal fun parseHtmlScheduleTables(html: String): List<ClassBlock> {
        val result = mutableListOf<ClassBlock>()
        val colorByCourse = mutableMapOf<String, String>()

        val rowRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val cellRegex = Regex("""<t[dh][^>]*>([\s\S]*?)</t[dh]>""", RegexOption.IGNORE_CASE)

        for (rowMatch in rowRegex.findAll(html)) {
            val cells = cellRegex.findAll(rowMatch.groupValues[1])
                .map { stripHtmlTags(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
            if (cells.size < 2) continue

            val joined = cells.joinToString(" | ")
            val timeRange = parseTimeRangeMinutes(joined) ?: continue
            val days = parseDaysFromText(joined)
            if (days.isEmpty()) continue

            val nameCell = cells.firstOrNull { cell ->
                parseTimeRangeMinutes(cell) == null &&
                    parseDaysFromText(cell).isEmpty() &&
                    cell.length >= 3 &&
                    !cell.equals("subject", ignoreCase = true) &&
                    !cell.equals("course", ignoreCase = true)
            } ?: continue

            val roomCell = extractRoomFromSegments(cells.filter { it != nameCell })
            val profCell = extractProfessorFromSegments(cells.filter { it != nameCell && it != roomCell })

            val (startTotal, duration) = timeRange
            val color = colorByCourse.getOrPut(nameCell.lowercase(Locale.US)) {
                CourseColorSwatches[colorByCourse.size % CourseColorSwatches.size]
            }
            for (d in days) {
                result.add(
                    ClassBlock(
                        name = nameCell,
                        professor = profCell,
                        room = roomCell,
                        dayOfWeek = d,
                        startHour = startTotal / 60,
                        startMinute = startTotal % 60,
                        durationMinutes = duration.coerceIn(15, 480),
                        colorHex = color
                    )
                )
            }
        }
        return deduplicateBlocks(result)
    }

    /**
     * Parses Bootstrap .card / list-group course items that include schedule day + time text.
     */
    internal fun parseHtmlTimedCourseCards(html: String): List<ClassBlock> {
        val result = mutableListOf<ClassBlock>()
        val colorByCourse = mutableMapOf<String, String>()

        val cardRegex = Regex(
            """<div[^>]*class=["'][^"']*\b(?:card-body|list-group-item|class-card|course-card)\b[^"']*["'][^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
        for (cardMatch in cardRegex.findAll(html)) {
            val cardHtml = cardMatch.groupValues[1]
            val plainLines = cardHtml
                .replace(Regex("""</(?:h[1-6]|p|div|li|span|small|strong|b|td)>|<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .let { stripHtmlTagsPreservingNewlines(it) }
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (plainLines.size < 2) continue
            val joined = plainLines.joinToString(" | ")
            val timeRange = parseTimeRangeMinutes(joined) ?: continue
            val days = parseDaysFromText(joined)
            if (days.isEmpty()) continue

            val title = plainLines.firstOrNull { line ->
                parseTimeRangeMinutes(line) == null &&
                    parseDaysFromText(line).isEmpty() &&
                    line.length >= 3
            } ?: continue

            val room = extractRoomFromSegments(plainLines.filter { it != title })
            val prof = extractProfessorFromSegments(plainLines.filter { it != title && it != room })
            val (startTotal, duration) = timeRange
            val color = colorByCourse.getOrPut(title.lowercase(Locale.US)) {
                CourseColorSwatches[colorByCourse.size % CourseColorSwatches.size]
            }
            for (d in days) {
                result.add(
                    ClassBlock(
                        name = title,
                        professor = prof,
                        room = room,
                        dayOfWeek = d,
                        startHour = startTotal / 60,
                        startMinute = startTotal % 60,
                        durationMinutes = duration.coerceIn(15, 480),
                        colorHex = color
                    )
                )
            }
        }
        return deduplicateBlocks(result)
    }

    /**
     * Fallback for LMS "My Class" pages that display enrolled courses (cards/headings)
     * without schedule hours, importing them into the Unscheduled Pool (dayOfWeek = 0).
     */
    internal fun parseUntimedCourseCards(html: String): List<ClassBlock> {
        val result = mutableListOf<ClassBlock>()
        val colorByCourse = mutableMapOf<String, String>()

        val cardRegex = Regex(
            """<div[^>]*class=["'][^"']*\b(?:card-body|list-group-item|class-card|course-card)\b[^"']*["'][^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
        val courseCodeRegex = Regex("""\b[A-Z]{2,5}\s*-?\s*\d{2,4}[A-Z]?\b""")

        for (cardMatch in cardRegex.findAll(html)) {
            val cardHtml = cardMatch.groupValues[1]
            val plainLines = cardHtml
                .replace(Regex("""</(?:h[1-6]|p|div|li|span|small|strong|b)>|<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .let { stripHtmlTagsPreservingNewlines(it) }
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (plainLines.isEmpty()) continue
            val title = plainLines.firstOrNull { line ->
                line.length in 4..90 &&
                    (courseCodeRegex.containsMatchIn(line) ||
                        cardHtml.contains("<h", ignoreCase = true) ||
                        cardHtml.contains("card-title", ignoreCase = true)) &&
                    !line.equals("My Class", ignoreCase = true) &&
                    !line.equals("SIGN-IN", ignoreCase = true)
            } ?: continue

            val prof = extractProfessorFromSegments(plainLines.filter { it != title })
            val color = colorByCourse.getOrPut(title.lowercase(Locale.US)) {
                CourseColorSwatches[colorByCourse.size % CourseColorSwatches.size]
            }
            result.add(
                ClassBlock(
                    name = title,
                    professor = prof,
                    room = "",
                    dayOfWeek = 0,
                    startHour = 9,
                    startMinute = 0,
                    durationMinutes = 60,
                    colorHex = color
                )
            )
        }
        return deduplicateBlocks(result)
    }

    private fun extractRoomFromSegments(segments: List<String>): String =
        segments.firstOrNull { seg ->
            parseTimeRangeMinutes(seg) == null &&
                (seg.contains("room", ignoreCase = true) ||
                    seg.contains("lab", ignoreCase = true) ||
                    seg.contains("hall", ignoreCase = true) ||
                    seg.contains("bldg", ignoreCase = true) ||
                    seg.matches(Regex("""[A-Za-z]{1,6}[- ]?\d{2,4}[A-Za-z]?""")))
        }.orEmpty()

    private fun extractProfessorFromSegments(segments: List<String>): String =
        segments.firstOrNull { seg ->
            parseTimeRangeMinutes(seg) == null &&
                parseDaysFromText(seg).isEmpty() &&
                (seg.contains("prof", ignoreCase = true) ||
                    seg.contains("dr.", ignoreCase = true) ||
                    seg.contains("engr", ignoreCase = true) ||
                    seg.contains("inst.", ignoreCase = true) ||
                    seg.contains(","))
        }.orEmpty()

    private fun parseDaysList(obj: JSONObject): List<Int> {
        val arr = obj.optJSONArray("daysOfWeek")
        if (arr != null && arr.length() > 0) {
            return (0 until arr.length()).mapNotNull { idx ->
                when (val raw = arr.optInt(idx, -1)) {
                    0 -> 7
                    in 1..6 -> raw
                    7 -> 7
                    else -> null
                }
            }.distinct()
        }
        val dayField = listOf("dayOfWeek", "day", "days", "schedule", "time", "sched")
            .firstNotNullOfOrNull { k -> obj.optString(k, "").takeIf { it.isNotBlank() } }
            .orEmpty()
        val parsedFromText = parseDaysFromText(dayField)
        if (parsedFromText.isNotEmpty()) return parsedFromText

        val numericDay = obj.optInt("dayOfWeek", -1)
        return if (numericDay in 1..7) listOf(numericDay) else emptyList()
    }

    internal fun parseDaysFromText(text: String): List<Int> {
        val days = linkedSetOf<Int>()
        val tokens = listOf(
            "Monday" to 1, "Mon" to 1,
            "Tuesday" to 2, "Tue" to 2,
            "Wednesday" to 3, "Wed" to 3,
            "Thursday" to 4, "Thu" to 4,
            "Friday" to 5, "Fri" to 5,
            "Saturday" to 6, "Sat" to 6,
            "Sunday" to 7, "Sun" to 7
        )
        for ((name, idx) in tokens) {
            if (Regex("""\b$name\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                days.add(idx)
            }
        }
        if (days.isNotEmpty()) return days.toList()

        // Compact & slash-separated codes such as MWF, TTH, M/W, T/TH, MWFS, S
        val compactRegex = Regex("""\b(?:TH|[MTWFS])(?:/?(?:TH|[MTWFS]))*\b""", RegexOption.IGNORE_CASE)
        for (match in compactRegex.findAll(text)) {
            val code = match.value.replace("/", "").uppercase(Locale.US)
            if (code == "T" && text.contains("TBA", ignoreCase = true)) continue
            var i = 0
            while (i < code.length) {
                if (i + 1 < code.length && code.substring(i, i + 2) == "TH") {
                    days.add(4)
                    i += 2
                } else {
                    when (code[i]) {
                        'M' -> days.add(1)
                        'T' -> days.add(2)
                        'W' -> days.add(3)
                        'F' -> days.add(5)
                        'S' -> days.add(6)
                    }
                    i += 1
                }
            }
            if (days.isNotEmpty()) return days.toList()
        }
        return emptyList()
    }

    internal fun parseTimeRangeMinutes(text: String): Pair<Int, Int>? {
        val rangeRegex = Regex(
            """(\d{1,2}):(\d{2})\s*(AM|PM)?\s*[-–to]+\s*(\d{1,2}):(\d{2})\s*(AM|PM)?""",
            RegexOption.IGNORE_CASE
        )
        val m = rangeRegex.find(text) ?: return null
        val sh = m.groupValues[1].toIntOrNull() ?: return null
        val sm = m.groupValues[2].toIntOrNull() ?: return null
        val sAmPm = m.groupValues[3].uppercase(Locale.US)
        val eh = m.groupValues[4].toIntOrNull() ?: return null
        val em = m.groupValues[5].toIntOrNull() ?: return null
        val eAmPm = m.groupValues[6].uppercase(Locale.US)

        val effectiveStartAmPm = sAmPm.ifBlank {
            if (eAmPm == "PM" && sh in 7..11) "AM" else eAmPm
        }
        val start24 = toMinutes24(sh, sm, effectiveStartAmPm) ?: return null
        val end24 = toMinutes24(eh, em, eAmPm) ?: return null
        val duration = end24 - start24
        if (duration <= 0 || duration > 480) return null
        return start24 to duration
    }

    private fun extractTimeFromIso(start: String, end: String): String {
        val sTime = Regex("""T(\d{2}:\d{2})""").find(start)?.groupValues?.getOrNull(1) ?: return ""
        val eTime = Regex("""T(\d{2}:\d{2})""").find(end)?.groupValues?.getOrNull(1) ?: return ""
        return "$sTime - $eTime"
    }

    private fun toMinutes24(hour: Int, minute: Int, amPm: String): Int? {
        if (minute !in 0..59) return null
        val h24 = when (amPm) {
            "AM" -> if (hour == 12) 0 else if (hour in 1..11) hour else return null
            "PM" -> if (hour == 12) 12 else if (hour in 1..11) hour + 12 else return null
            else -> if (hour in 0..23) hour else return null
        }
        return h24 * 60 + minute
    }

    private fun stripHtmlTags(html: String): String =
        html.replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun stripHtmlTagsPreservingNewlines(html: String): String =
        html.replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("""[ \t\r]+"""), " ")
            .replace(Regex(""" *\n *"""), "\n")
            .trim()

    private fun deduplicateBlocks(blocks: List<ClassBlock>): List<ClassBlock> {
        val seen = HashSet<String>()
        return blocks.filter { b ->
            val key = "${b.name.lowercase(Locale.US)}|${b.dayOfWeek}|${b.startHour}|${b.startMinute}"
            seen.add(key)
        }
    }
}
