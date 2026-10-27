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

    // "Allah" as it appears joined to other words: allah, llah, alla, 'Allah.
    private const val ALLAH = "'?a?l+a+h?"

    private const val MUBARAK = "mu?ba+ra?k"

    private fun phrase(pattern: String) = Regex("""\b(?:$pattern)\b""", RegexOption.IGNORE_CASE)

    private val fixes: List<Pair<Regex, String>> = listOf(
        // Greetings
        phrase("(?:a?s+$S)?sa+la+m+$S(?:[uoe]+$S)?a?l[ae][iy]?[ck]u+m+[-'’ ,]*(?:wa|va|w)${S}ra[hk]?ma?t[uo]?l+a+h?[iy]?[-'’ ,]*(?:wa|va|w)${S}bara?kat[uo]h?")
                to "Assalamu alaikum wa rahmatullahi wa barakatuh",
        phrase("(?:wa?$S)?a?l[ae][iy]?[ck]u+m+$S(?:a?s+$S)?sa+la+m+")
                to "Walaikum assalam",
        phrase("(?:a?s+$S)?sa+la+m+$S(?:[uoe]+$S)?a?l[ae][iy]?[ck]u+m+")
                to "Assalamu alaikum",
        phrase("a+l+a+h?${S}ha+fi[zs]") to "Allah Hafiz",
        phrase("k?hu+da${S}ha+fi[zs]") to "Khuda Hafiz",

        // Longer phrases
        phrase("la${S}ila+ha?${S}il+a+$S$ALLAH") to "La ilaha illallah",
        phrase("in+a${S}lil+a+hi?[-'’ ,]*(?:wa$S)?in+a${S}ila[iy]?hi?${S}ra+ji['’]?[ou]+n")
                to "Inna lillahi wa inna ilayhi raji'un",
        phrase("sal+al+a+hu?${S}a?l[ae][iy]?hi?$S(?:wa)?${S}sal+a+m")
                to "Sallallahu alaihi wasallam",

        // Everyday phrases
        phrase("(?:al$S)?ha[mn]+${S}d[uo]?${S}l+[iy]?${S}l*a+h?") to "Alhamdulillah",
        phrase("in${S}sha+h?$S$ALLAH") to "InshaAllah",
        phrase("ma+${S}sha+h?$S$ALLAH") to "MashaAllah",
        phrase("su+b${S}ha+n+a?$S$ALLAH") to "SubhanAllah",
        phrase("bis+${S}mi+l+a+h?") to "Bismillah",
        phrase("ja+z+a+k+$S$ALLAH[-'’ ,]+k?ha[iy]?r(?:an)?") to "JazakAllah Khair",
        phrase("ja+z+a+k+$S$ALLAH") to "JazakAllah",
        phrase("a+l+a+h?${S}u?${S}a?kb[ae]r") to "Allahu Akbar",
        phrase("a?st[ae]g?h?f[iu]?r+${S}u?l+a+h?") to "Astaghfirullah",
        phrase("bara?k${S}a?l+a+hu?${S}f[ie]+k") to "Barakallahu feek",
        phrase("bara?k${S}a?l+a+hu?") to "Barakallah",

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
