package de.knuffi.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.knuffi.app.ui.PhotoStore
import de.knuffi.core.GameJson
import de.knuffi.core.GameState
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups as a ZIP file: the game state as JSON plus all photos. Everything stays on the
 * phone: the player picks where to save it, and once a week a copy goes into Downloads/Knuffi.
 */
object Backup {
    const val MIME = "application/zip"
    val OPEN_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

    private const val FORMAT = 1
    private const val INFO = "knuffi-backup.json"
    private const val STATE = "state.json"
    private const val PHOTOS = "photos/"
    private const val FOLDER = "Knuffi"
    private const val AUTO_PREFIX = "Knuffi-Auto-Sicherung-"
    private const val KEEP_AUTO = 3
    private const val MAX_ENTRY = 24L * 1024 * 1024
    private const val MAX_TOTAL = 200L * 1024 * 1024
    const val AUTO_EVERY_MS = 7L * 86_400_000L

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "Knuffi-Sicherung-${stamp.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.zip"

    /** What a backup contains, for the question before restoring. */
    class Contents(val state: GameState, val photos: Map<String, ByteArray>, val createdAt: Long, val version: String) {
        val petName: String get() = state.pet?.name ?: "–"
        val petLevel: Int get() = state.pet?.level ?: 0
        val pets: Int get() = state.allPets.size
    }

    /** Writes the game and all photos as a ZIP into [out]. */
    fun write(context: Context, state: GameState, out: OutputStream, now: Long = System.currentTimeMillis()) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            val info = JSONObject()
                .put("format", FORMAT)
                .put("app", "Knuffi")
                .put("version", de.knuffi.app.BuildConfig.VERSION_NAME)
                .put("createdAt", now)
                .put("pet", state.pet?.name ?: "")
            zip.putNextEntry(ZipEntry(INFO))
            zip.write(info.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(STATE))
            zip.write(GameJson.encode(state).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (f in PhotoStore.dir(context).listFiles().orEmpty()) {
                if (!f.isFile || !safeName(f.name)) continue
                zip.putNextEntry(ZipEntry(PHOTOS + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun safeName(name: String): Boolean =
        name.length in 1..120 && !name.startsWith(".") && name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IllegalArgumentException("Die Sicherung ist zu groß.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Reads a backup. Throws [IllegalArgumentException] with a message for the player if it is not a Knuffi backup. */
    fun read(input: InputStream): Contents {
        var stateJson: String? = null
        var info: JSONObject? = null
        val photos = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (!e.isDirectory && (name == STATE || name == INFO || name.startsWith(PHOTOS))) {
                    val bytes = readLimited(zip, MAX_ENTRY)
                    total += bytes.size
                    if (total > MAX_TOTAL) throw IllegalArgumentException("Die Sicherung ist zu groß.")
                    when {
                        name == STATE -> stateJson = String(bytes, Charsets.UTF_8)
                        name == INFO -> info = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                        else -> {
                            // Only plain file names directly in photos/, never paths (no "../").
                            val file = name.removePrefix(PHOTOS)
                            if (safeName(file)) photos[file] = bytes
                        }
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        val json = stateJson ?: throw IllegalArgumentException("Das ist keine Knuffi-Sicherung.")
        val state = GameJson.decode(json) ?: throw IllegalArgumentException("Die Sicherung ist beschädigt und kann nicht gelesen werden.")
        if (!state.onboarded) throw IllegalArgumentException("In dieser Sicherung ist noch kein Spielstand.")
        return Contents(state, photos, info?.optLong("createdAt") ?: 0L, info?.optString("version") ?: "")
    }

    /** Replaces the game and the photos with the backup. */
    fun restore(context: Context, contents: Contents) {
        val dir = PhotoStore.dir(context)
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        for ((name, bytes) in contents.photos) File(dir, name).writeBytes(bytes)
        GameRepository.restore(contents.state)
    }

    // ------------------------------------------------------------------ automatic backups

    fun autoDue(state: GameState, now: Long): Boolean =
        state.onboarded && state.settings.autoBackup && (now - state.lastAutoBackup >= AUTO_EVERY_MS || now < state.lastAutoBackup)

    /** Where automatic backups go (shown in the settings). */
    val folderText: String
        get() = if (Build.VERSION.SDK_INT >= 29) "Downloads/$FOLDER" else "Android/data/de.knuffi.app/files/Download/$FOLDER"

    /**
     * Saves a backup into Downloads/Knuffi (on Android 8 and 9 into the app's own download folder)
     * and keeps only the newest [KEEP_AUTO] automatic ones. [prefix] names it.
     */
    fun saveToDownloads(context: Context, state: GameState, now: Long = System.currentTimeMillis(), prefix: String = AUTO_PREFIX): Boolean = runCatching {
        val name = prefix + stamp.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())) + ".zip"
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            try {
                resolver.openOutputStream(uri)?.use { write(context, state, it, now) } ?: throw IllegalStateException("no stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            if (prefix == AUTO_PREFIX) pruneMediaStore(context)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, FOLDER).apply { mkdirs() }
            File(dir, name).outputStream().use { write(context, state, it, now) }
            if (prefix == AUTO_PREFIX) {
                dir.listFiles { f -> f.name.startsWith(AUTO_PREFIX) }?.sortedByDescending { it.name }?.drop(KEEP_AUTO)?.forEach { it.delete() }
            }
        }
        true
    }.getOrDefault(false)

    /** Saves the current game before a backup replaces it (in the app's own files if Downloads fails). */
    fun saveBeforeImport(context: Context, state: GameState): Boolean =
        saveToDownloads(context, state, prefix = "Knuffi-vor-dem-Import-") || runCatching {
            File(context.filesDir, "Knuffi-vor-dem-Import.zip").outputStream().use { write(context, state, it) }
            true
        }.getOrDefault(false)

    private fun pruneMediaStore(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val found = mutableListOf<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$AUTO_PREFIX%"),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) found += c.getLong(idCol)
        }
        for (id in found.drop(KEEP_AUTO)) {
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }
}
