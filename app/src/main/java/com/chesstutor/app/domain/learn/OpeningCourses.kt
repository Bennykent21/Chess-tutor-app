package com.chesstutor.app.domain.learn

/**
 * Links an opening family from the player's games ("Sicilian Defense", "Queen's Gambit
 * Declined") to the Learn course that teaches it ("Sicilian Defence", "Queen's Gambit"), so
 * Account > Stats can open the course from an opening row (D44).
 */
object OpeningCourses {

    /** The course for [family], or null when no course teaches it. */
    fun courseFor(family: String, courses: List<Course>): Course? {
        val key = normalise(family)
        return courses
            .filter { it.category == LessonCategory.OPENINGS }
            .map { it to normalise(it.title) }
            .filter { (_, title) -> key == title || key.startsWith("$title ") }
            // "Queen's Gambit Declined" prefers a course of that name over "Queen's Gambit".
            .maxByOrNull { (_, title) -> title.length }
            ?.first
    }

    /** Lower case, British spelling and plain apostrophes, so the database and course names meet. */
    internal fun normalise(name: String): String =
        name.lowercase()
            .replace('’', '\'')
            .replace("defense", "defence")
            .trim()
}
