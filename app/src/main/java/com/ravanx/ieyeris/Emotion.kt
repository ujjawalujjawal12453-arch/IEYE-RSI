package com.ravanx.ieyeris

/** Lightweight offline emotion cue layer for more natural replies. */
object Emotion {
    enum class Mood { CALM, HAPPY, SAD, ANGRY, URGENT, UNKNOWN }
    fun detect(text: String): Mood {
        val t = text.lowercase()
        return when {
            listOf("emergency", "urgent", "jaldi", "help", "bachao").any(t::contains) -> Mood.URGENT
            listOf("gussa", "angry", "pagal", "bakwas").any(t::contains) -> Mood.ANGRY
            listOf("dukhi", "udaas", "sad", "rona", "ro raha").any(t::contains) -> Mood.SAD
            listOf("khush", "great", "thanks", "shukriya", "mast").any(t::contains) -> Mood.HAPPY
            else -> Mood.UNKNOWN
        }
    }
    fun prefix(text: String): String = when (detect(text)) {
        Mood.URGENT -> "Main samajh raha hoon ki ye urgent hai. "
        Mood.ANGRY -> "Aap gusse me lag rahe hain; aaram se solve karte hain. "
        Mood.SAD -> "Mujhe lag raha hai aap pareshaan hain. Main sun raha hoon. "
        Mood.HAPPY -> "Ye sunkar achha laga. "
        else -> ""
    }
}
