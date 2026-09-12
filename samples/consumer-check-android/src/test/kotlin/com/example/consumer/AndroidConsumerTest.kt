package com.example.consumer

import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.kmatch.defaultProcess
import io.github.likhithsj.kmatch.extractOne
import kotlin.test.Test
import kotlin.test.assertEquals

/** Golden-value assertions from inside an Android (AGP) module. */
class AndroidConsumerTest {

    @Test
    fun ratioIsBitExact() {
        assertEquals(96.55172413793103, Fuzz.ratio("this is a test", "this is a test!"))
        assertEquals(100.0, Fuzz.tokenSortRatio("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear"))
    }

    @Test
    fun menuSearchShape() {
        val menu = listOf("Caffe Latte", "Flat White", "Cold Brew", "Iced Matcha Latte")
        val best = extractOne("latte", menu, processor = ::defaultProcess)!!
        assertEquals("Caffe Latte", best.choice)
    }
}
