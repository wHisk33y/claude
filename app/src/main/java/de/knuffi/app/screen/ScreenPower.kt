package de.knuffi.app.screen

/** The battery setting for the pet on the home screen (live wallpaper and floating pet). */
object ScreenPower {
    val titles = listOf("🌊 Flüssig", "⚖️ Ausgewogen", "🔋 Akku sparen")

    fun describe(level: Int): String = when (level) {
        0 -> "Ganz flüssige Bewegungen. Braucht am meisten Akku."
        2 -> "Ruhigere Bewegungen, braucht deutlich weniger Akku."
        else -> "Flüssig genug und sparsam. Empfohlen."
    }

    fun wallpaperFrameMs(level: Int): Long = when (level) {
        0 -> 33L
        2 -> 100L
        else -> 50L
    }

    fun overlayFrameNs(level: Int): Long = when (level) {
        0 -> 0L
        2 -> 66_000_000L
        else -> 33_000_000L
    }

    fun pet3dNs(level: Int): Long = when (level) {
        0 -> 30_000_000L
        2 -> 95_000_000L
        else -> 48_000_000L
    }
}
