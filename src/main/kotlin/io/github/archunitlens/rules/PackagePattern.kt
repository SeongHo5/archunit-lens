package io.github.archunitlens.rules

/**
 * Segment-aware matcher for ArchUnit-style package patterns such as `..domain..`.
 */
object PackagePattern {
    fun matches(pattern: String, target: String): Boolean {
        val patternSegments = pattern.split("..", ".")
            .filter { it.isNotBlank() }
        if (patternSegments.isEmpty()) return false

        val targetSegments = target.split('.')
            .filter { it.isNotBlank() }
        if (targetSegments.isEmpty()) return false

        return when {
            pattern.startsWith("..") && pattern.endsWith("..") ->
                targetSegments.containsSequence(patternSegments)

            pattern.startsWith("..") ->
                targetSegments.endsWithSequence(patternSegments)

            pattern.endsWith("..") ->
                targetSegments.startsWithSequence(patternSegments)

            else ->
                targetSegments == patternSegments
        }
    }

    private fun List<String>.containsSequence(sequence: List<String>): Boolean {
        if (sequence.size > size) return false
        return windowed(sequence.size).any { it == sequence }
    }

    private fun List<String>.startsWithSequence(sequence: List<String>) = size >= sequence.size && subList(0, sequence.size) == sequence

    private fun List<String>.endsWithSequence(sequence: List<String>) = size >= sequence.size && subList(size - sequence.size, size) == sequence
}
