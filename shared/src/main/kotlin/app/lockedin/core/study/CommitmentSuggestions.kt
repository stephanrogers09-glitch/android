package app.lockedin.core.study

/**
 * Short commitment phrases shown in the grey area on the focus screen. They rotate, and tapping one
 * fills the commitment. Written to be specific and a little vivid, not generic motivation.
 */
object CommitmentSuggestions {
    val all: List<String> = listOf(
        "Read one page without checking the phone.",
        "Answer the hardest question first.",
        "Finish the thought before opening another tab.",
        "Make the next paragraph worth keeping.",
        "Stay with the part that feels difficult.",
        "Write it in your own words.",
        "One problem, fully solved.",
        "Earn the next break.",
        "Slow is still forward.",
        "Do the reading, then the rest.",
        "Turn the notes into questions.",
        "The phone can wait for this.",
    )

    /** The phrase for a rotation step. Steps wrap around, so any number gives a valid phrase. */
    fun at(step: Int): String = all[Math.floorMod(step, all.size)]
}
