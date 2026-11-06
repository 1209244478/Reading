package com.wrz.reading.ui.random.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 随机数生成逻辑的回归测试：覆盖范围取最值、禁止重复、范围耗尽三种原有行为。
 */
class RandomNumberGeneratorTest {

    @Test
    fun generatedNumberStaysWithinRange() {
        val random = Random(42)
        repeat(500) {
            val number = RandomNumberGenerator.generate(
                random = random,
                start = 5,
                end = 10,
                existing = emptyList(),
                allowDuplicates = true
            )
            assertTrue("number=$number", number in 5..10)
        }
    }

    @Test
    fun reversedRangeUsesMinAndMax() {
        val random = Random(7)
        repeat(200) {
            val number = RandomNumberGenerator.generate(
                random = random,
                start = 10,
                end = 5,
                existing = emptyList(),
                allowDuplicates = true
            )
            assertTrue("number=$number", number in 5..10)
        }
    }

    @Test
    fun exhaustedWhenAllNumbersUsed() {
        val number = RandomNumberGenerator.generate(
            random = Random(1),
            start = 1,
            end = 3,
            existing = listOf("1", "2", "3"),
            allowDuplicates = false
        )
        assertEquals(RandomNumberGenerator.EXHAUSTED, number)
    }

    @Test
    fun noDuplicatesWhenDisallowed() {
        val random = Random(99)
        val existing = mutableListOf<String>()
        repeat(3) {
            val number = RandomNumberGenerator.generate(
                random = random,
                start = 1,
                end = 100,
                existing = existing,
                allowDuplicates = false
            )
            assertTrue("number=$number", number in 1..100)
            assertTrue("duplicated number=$number", existing.add(number.toString()))
        }
    }

    @Test
    fun duplicatesAllowedCanRepeatSameNumber() {
        val random = Random(3)
        repeat(10) {
            val number = RandomNumberGenerator.generate(
                random = random,
                start = 1,
                end = 1,
                existing = emptyList(),
                allowDuplicates = true
            )
            assertEquals(1, number)
        }
    }
}
