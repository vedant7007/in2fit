package io.github.vedant7007.katori.ml.asr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The platform's ICU against the JVM's: `DevanagariTest` pinned these strings with icu4j 75.1,
 * and the phone runs `android.icu`, which is the same transform at whatever ICU the ROM ships.
 * If a line here differs from the JVM's, the spelling step has to learn the difference, not the
 * demo. Device queue item 6; written 21 Sep with the phone away, UNPROVEN until run.
 */
class DevanagariDeviceTest {

    private val r = IcuRomaniser()

    @Test fun theNineFoodWords() {
        assertEquals("roti", r.romanise("रोटी"))
        assertEquals("dal", r.romanise("दाल"))
        assertEquals("chaval", r.romanise("चावल"))
        assertEquals("dahi", r.romanise("दही"))
        assertEquals("anda", r.romanise("अंडा"))
        assertEquals("idli", r.romanise("इडली"))
        assertEquals("sambar", r.romanise("सांबर"))
        assertEquals("dosa", r.romanise("दोसा"))
        assertEquals("chatni", r.romanise("चटनी"))
    }

    @Test fun theDemoRows() {
        assertEquals("mainne do roti aur thodi dal khai", r.romanise("मैंने दो रोटी और थोड़ी दाल खाई"))
        assertEquals("do roti, ek katori dal, aur do chammach tel", r.romanise("दो रोटी, एक कटोरी दाल, और दो चम्मच तेल"))
        assertEquals("ek plet chaval, dal aur ek katori dahi", r.romanise("एक प्लेट चावल, दाल और एक कटोरी दही"))
        assertEquals("nashte men tin idli aur sambar khaya", r.romanise("नाश्ते में तीन इडली और सांबर खाया"))
        assertEquals("hun", r.romanise("हूँ"))
    }
}
