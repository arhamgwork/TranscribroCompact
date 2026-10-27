/*
 * Transcribro Compact - a personal modification of Transcribro.
 * Original work: Transcribro, Copyright (c) 2024-2025 soupslurpr, ISC License (see LICENSE.txt).
 */

package dev.soupslurpr.transcribro.recognitionservice.whisper

/**
 * Fixes how common Islamic greetings and phrases are written after speech recognition.
 * Whisper sometimes splits or misspells them (for example "in sha Allah" or "al hamdu lillah"),
 * so each rule below matches the usual variations and replaces them with one spelling.
 *
 * To add a phrase: add a line `phrase("pattern") to "Spelling"`. Longer phrases go first.
 */
object PhraseFixes {
    // Optional space, hyphen or apostrophe between parts of a phrase.
    private const val S = "[-'’ ]?"

    // Optional "wa" ("and") between parts of a phrase, with any spacing or commas.
    private const val WA = "[-'’ ,]*(?:wa|va|w|and)?[-'’ ]?"

    // "Allah" as it appears joined to other words: allah, llah, alla, 'Allah.
    private const val ALLAH = "'?a?l+a+h?"

    // "alaikum" / "alaykum" / "aleikum"
    private const val ALAIKUM = "a?l[ae][iy]?[ck]u+m+"

    // "salam" / "salaam" / "as-salam" / "assalamu"
    private val SALAM = "(?:a?s+$S)?sa+la+m+"

    // "rahmatullahi" / "rahmatullah" / "ramatullah"
    private val RAHMATULLAH = "ra[hk]?ma?t[uo]?${S}l+a+h?[iy]?"

    private const val MUBARAK = "mu?ba+ra?k"

    private fun phrase(pattern: String) = Regex("""\b(?:$pattern)\b""", RegexOption.IGNORE_CASE)

    private val fixes: List<Pair<Regex, String>> = listOf(
        // Greetings
        phrase("$SALAM$S(?:[uoe]+$S)?$ALAIKUM$WA$RAHMATULLAH${WA}bara?[ck]a+t[uo]+h?u?")
                to "Assalamualaikum wa rahmatullahi wa barakatuh",
        phrase("$SALAM$S(?:[uoe]+$S)?$ALAIKUM$WA$RAHMATULLAH")
                to "Assalamualaikum wa rahmatullahi",
        phrase("(?:wa?$S)?$ALAIKUM$S$SALAM")
                to "Walaikumassalam",
        phrase("$SALAM$S(?:[uoe]+$S)?$ALAIKUM")
                to "Assalamualaikum",
        phrase("a+l+a+h?${S}ha+fi[zs]") to "Allah Hafiz",
        phrase("k?hu+da${S}ha+fi[zs]") to "Khuda Hafiz",

        // Longer phrases
        phrase("la+${S}il+a+ha+${S}il+a+$S$ALLAH") to "La ilaha illallah",
        phrase("in+a+${S}l?il+a+h?i?$WA(?:in+a+$S)?il[ae][iy]?hi?${S}ra+ji['’]?[ou]+n")
                to "Inna lillahi wa inna ilayhi raji'un",
        phrase("s[ae]l+[ae]?l+a+hu?${S}a?l[ae][iy]?hi?$WA${S}s[ae]l+a+m")
                to "Sallallahu alaihi wasallam",

        // Everyday phrases
        phrase("(?:al$S)?ha[mn]+${S}d[uo]?${S}l+[iy]?${S}l*a+h?") to "Alhamdulillah",
        phrase("in${S}sha+h?$S$ALLAH") to "InshaAllah",
        phrase("ma+${S}sha+h?$S$ALLAH") to "MashaAllah",
        phrase("su+b${S}ha+n+a?$S$ALLAH") to "SubhanAllah",
        phrase("bis+${S}mi+l+a+h?") to "Bismillah",
        phrase("j[ae]+z+a+k+[ae]?$S$ALLAH(?:h?u)?[-'’ ,]+k?h[ae][iy]?r(?:an|un)?") to "JazakAllah Khair",
        phrase("j[ae]+z+a+k+[ae]?$S$ALLAH(?:h?u)?") to "JazakAllah",
        phrase("a+l+a+h?$S(?:h?[uo]+|who)?${S}a?kh?b[ae]r") to "Allahu Akbar",
        phrase("a?st[ae]g?h?f[iu]?r+${S}u?l+a+h?") to "Astaghfirullah",
        phrase("bara?[ck]+a?$S$ALLAH(?:h?u)?${S}f[ie]+(?:k|kum)") to "Barakallahu feek",
        phrase("bara?[ck]+a?$S$ALLAH(?:h?u)?(?!${S}f[ie]+(?:k|kum)\\b)") to "Barakallah",
        phrase("a+me+n|a+mine") to "Ameen",

        // Mubarak wishes
        phrase("e+i?d$S$MUBARAK") to "Eid Mubarak",
        phrase("ramadh?a+n$S$MUBARAK") to "Ramadan Mubarak",
        phrase("ramza+n$S$MUBARAK") to "Ramzan Mubarak",
        phrase("ju+m+u?a+h?$S$MUBARAK") to "Jumma Mubarak",
    )

    fun apply(text: String): String {
        var result = text
        for ((regex, replacement) in fixes) {
            result = regex.replace(result, Regex.escapeReplacement(replacement))
        }
        return result
    }
}
