package de.knuffi.core

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------------- music room

enum class Instrument(val title: String, val emoji: String, val keys: Int) {
    PIANO("Klavier", "🎹", 8),
    XYLOPHONE("Xylophon", "🎼", 8),
    DRUMS("Trommeln", "🥁", 4),
}

/** One note of a recorded song: which instrument, which key and when (milliseconds from the start). */
@Serializable
data class SongNote(val instrument: Instrument, val key: Int, val at: Int)

@Serializable
data class Song(val name: String, val notes: List<SongNote>, val createdAt: Long = 0) {
    val lengthMs: Int get() = notes.maxOfOrNull { it.at } ?: 0
}

object Music {
    const val MAX_SONGS = 6
    const val MAX_NOTES = 240
    const val MAX_LENGTH_MS = 60_000
    const val REWARD_COOLDOWN_MS = 10 * 60_000L
    const val MIN_NOTES = 8

    /** Names of the piano and xylophone keys (C major). */
    val NOTE_NAMES = listOf("C", "D", "E", "F", "G", "A", "H", "C")
    val DRUM_NAMES = listOf("Bum", "Tschak", "Tsss", "Bong")
}
