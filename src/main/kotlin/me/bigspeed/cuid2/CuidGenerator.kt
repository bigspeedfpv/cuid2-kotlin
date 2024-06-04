package me.bigspeed.cuid2

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.abs

class CuidGenerator(private val length: Int = 24) {
    private val fingerprint: String
    private var counter: Int = 0

    init {
        if (length < 2 || length > 32) throw IllegalArgumentException("CUID length must be between 2 and 32 inclusive")

        val fingerprintData = arrayOf(
            abs(nextIntValue()),
            abs(nextIntValue()),
            ProcessHandle.current().pid(),
            @Suppress("DEPRECATION") Thread.currentThread().id
        )

        fingerprint = hash(fingerprintData.joinToString("")).substring(0, 32)
    }

    private fun createEntropy(entropyLength: Int): String {
        return buildString(entropyLength) {
            while (length < entropyLength) {
                append(abs(nextIntValue()).toString(36))
            }
        }
    }

    private fun nextCount(): Int {
        if (counter >= Int.MAX_VALUE) counter = abs(nextIntValue())
        return counter++
    }

    private fun hash(input: String): String {
        val messageDigest = MessageDigest.getInstance("SHA3-512")
        val hashBytes = messageDigest.digest(input.toByteArray())
        // passing 1 as the first argument forces a positive bigint
        return BigInteger(1, hashBytes).toString(36).substring(1)
    }

    /**
     * Generates a CUID.
     * Salt from JS reference implementation: https://github.com/paralleldrive/cuid2/blob/53e246b0919c8123e492e6b6bbab41fe66f4b462/src/index.js#L83
     * @return a string in CUID format
     */
    fun generate(): String {
        val time = System.currentTimeMillis().toString(36)
        val currentCount = nextCount().toString(36)
        // salt is the same length as the hash to ideally be unique across the whole hash
        val salt = createEntropy(length)

        val hashInput = "$time$salt$currentCount$fingerprint"
        val hashed = hash(hashInput)

        return hashed.substring(0, length)
    }

    companion object {
        private val rng = SecureRandom()

        private const val RANDOM_BUFFER_SIZE = 4096
        private val randomBuffer = ByteArray(RANDOM_BUFFER_SIZE)

        private var randomBufferIndex = RANDOM_BUFFER_SIZE

        /**
         * Generates a random int value from an array of random bytes.
         * Taken from Java implementation:
         * https://github.com/thibaultmeyer/cuid-java/blob/c55aa797b13348a01ad1a059fe88a5af1c613e8f/src/main/java/io/github/thibaultmeyer/cuid/CUID.java#L315
         */
        @Synchronized
        private fun nextIntValue(): Int {
            if (randomBufferIndex == RANDOM_BUFFER_SIZE) {
                // SecureRandom can be blocking (depending on host) so generating a huge buffer can be better for perfs
                // (this would obviously block still, but much less often - 1 in 1024 calls rather than every time)
                rng.nextBytes(randomBuffer)
                randomBufferIndex = 0
            }

            return (randomBuffer[randomBufferIndex++].toInt() shl 24 //
                    or (randomBuffer[randomBufferIndex++].toInt() and 0xFF) shl 16 //
                    or (randomBuffer[randomBufferIndex++].toInt() and 0xFF) shl 8 //
                    or (randomBuffer[randomBufferIndex++].toInt() and 0xFF))
        }

        fun isValid(input: String): Boolean {
            return (input.length in 2..32 && Regex("^[0-9a-z]+$").matches(input))
        }
    }
}
