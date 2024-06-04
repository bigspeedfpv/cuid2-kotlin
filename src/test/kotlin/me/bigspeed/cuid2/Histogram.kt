package me.bigspeed.cuid2

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import java.math.BigInteger
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Histogram {
    private val idsToTest = 100000

    private fun idToBigint(id: String): BigInteger {
        return id.toCharArray().fold(BigInteger.valueOf(0)) { acc, c ->
            return@fold acc * BigInteger.valueOf(36) + BigInteger(c.toString(), 36)
        }
    }

    /**
     * Taken from reference implementation: https://github.com/paralleldrive/cuid2/blob/main/src/histogram.js
     */
    @Test
    fun generateHistogram() {
        println("Generating $idsToTest CUIDs.")

        val gen = CuidGenerator()
        val idPool = mutableSetOf<String>()

        for (i in 0 until idsToTest) {
            idPool.add(gen.generate())
            if (i % (idsToTest / 100) == 0) println("${floor(i.toDouble() / idsToTest * 100)}%")
            if (idPool.size < i + 1) {
                println("Collision detected at $i, stopping!")
                break
            }
        }

        assertEquals(idsToTest, idPool.size, "There should be no collisions with $idsToTest ids")

        val idsAsBigInteger = idPool.map { idToBigint(it) }

        val buckets = Array(20) { 0 }
        var counter = 1.toBigInteger()

        val bucketSize = 36.toBigInteger().pow(24).divide(20.toBigInteger())

        idsAsBigInteger.forEach {
            if (counter.mod(bucketSize) == 0.toBigInteger()) println(it)

            val bucket = it.divide(bucketSize).toInt()
            if (counter.mod(bucketSize) == 0.toBigInteger()) println(bucket)

            buckets[bucket] += 1
            counter++
        }

        // check character freq
        run {
            val tolerance = 0.1
            val idLength = 24
            val totalLetters = idLength * idsToTest
            val base = 36
            val expectedBinSize = ceil(totalLetters.toDouble() / base)
            val minBinSize = round(expectedBinSize * (1 - tolerance))
            val maxBinSize = round(expectedBinSize * (1 + tolerance))

            val charFrequencies = hashMapOf<Char, Int>()
            idPool.forEach {
                it.forEach { char ->
                    charFrequencies[char] = (charFrequencies[char] ?: 0) + 1
                }
            }

            println("Testing frequencies:")
            println("Expected bin size: $expectedBinSize min: $minBinSize max: $maxBinSize")
            println("Frequencies: $charFrequencies")

            val sortedFreqs = charFrequencies.toSortedMap()

            val charFreqHistogram = dataFrameOf(
                "character" to sortedFreqs.keys.toList(),
                "occurrences" to sortedFreqs.values.toList()
            )

            charFreqHistogram.plot {
                bars {
                    x("character")
                    y("occurrences")
                }

                layout.title = "Occurrences of characters in $idsToTest generated CUIDs"
            }.save("characters.png")

            assertEquals(36, charFrequencies.keys.size, "Should represent all characters 0-9a-z")

            assertTrue("All character counts should be within tolerance") {
                charFrequencies.all { it.value > minBinSize && it.value < maxBinSize }
            }
        }
    }
}