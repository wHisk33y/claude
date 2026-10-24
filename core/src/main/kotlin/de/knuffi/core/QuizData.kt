package de.knuffi.core

/** All quiz questions by topic (the texts are in QuizData1 to QuizData3). */
internal object QuizData {
    val raw: List<Pair<QuizCategory, String>> = listOf(
        QuizCategory.TIERE to QUIZ_TIERE,
        QuizCategory.NATUR to QUIZ_NATUR,
        QuizCategory.ERDE to QUIZ_ERDE,
        QuizCategory.WELTRAUM to QUIZ_WELTRAUM,
        QuizCategory.KOERPER to QUIZ_KOERPER,
        QuizCategory.GESCHICHTE to QUIZ_GESCHICHTE,
        QuizCategory.TECHNIK to QUIZ_TECHNIK,
        QuizCategory.WISSENSCHAFT to QUIZ_WISSENSCHAFT,
        QuizCategory.SPORT to QUIZ_SPORT,
        QuizCategory.MUSIK to QUIZ_MUSIK,
        QuizCategory.SPRACHE to QUIZ_SPRACHE,
        QuizCategory.FILM to QUIZ_FILM,
        QuizCategory.ESSEN to QUIZ_ESSEN,
        QuizCategory.DEUTSCHLAND to QUIZ_DEUTSCHLAND,
        QuizCategory.ZAHLEN to QUIZ_ZAHLEN,
    )
}
