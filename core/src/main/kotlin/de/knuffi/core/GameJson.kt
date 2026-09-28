package de.knuffi.core

import kotlinx.serialization.json.Json

object GameJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState? = try {
        json.decodeFromString(GameState.serializer(), text)
    } catch (e: Exception) {
        null
    }
}
