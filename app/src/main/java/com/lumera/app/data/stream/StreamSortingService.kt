package com.lumera.app.data.stream

import com.lumera.app.data.model.StreamQuality
import com.lumera.app.data.model.stremio.Stream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSortingService @Inject constructor() {

    fun sortAndFilter(
        streams: List<Stream>,
        enabledQualities: Set<StreamQuality>,
        excludePhrases: List<String>,
        addonSortOrders: Map<String, Int>,
        primarySort: String = "quality",
        secondarySort: String = "size"
    ): List<Stream> {
        val lowerPhrases = excludePhrases
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        return streams
            .filter { stream ->
                val info = StreamParser.parse(stream)
                info.quality in enabledQualities
            }
            .filter { stream ->
                if (lowerPhrases.isEmpty()) return@filter true
                val text = StreamParser.combinedText(stream).lowercase()
                lowerPhrases.none { phrase -> text.contains(phrase) }
            }
            .sortedWith(buildComparator(addonSortOrders, primarySort, secondarySort))
    }

    private fun buildComparator(
        addonSortOrders: Map<String, Int>,
        primarySort: String,
        secondarySort: String
    ): Comparator<Stream> {
        // Determine tertiary: whichever of quality/size/seeds isn't primary or secondary
        val allSorts = listOf("quality", "size", "seeds")
        val tertiary = allSorts.firstOrNull { it != primarySort && it != secondarySort } ?: "seeds"

        var comparator = sortComparatorFor(primarySort)
        comparator = comparator.then(sortComparatorFor(secondarySort))
        comparator = comparator.then(sortComparatorFor(tertiary))
        // Final tiebreaker: addon priority
        comparator = comparator.thenBy { addonSortOrders[it.addonTransportUrl] ?: Int.MAX_VALUE }
        return comparator
    }

    private fun sortComparatorFor(sort: String): Comparator<Stream> {
        return when (sort) {
            "quality" -> compareByDescending { StreamParser.parse(it).quality.sortOrder }
            "size" -> compareByDescending { StreamParser.parse(it).sizeBytes ?: 0L }
            "seeds" -> compareByDescending { StreamParser.parse(it).seeds ?: 0 }
            else -> compareByDescending { StreamParser.parse(it).quality.sortOrder }
        }
    }

    companion object {
        fun parseEnabledQualities(qualitiesString: String): Set<StreamQuality> {
            if (qualitiesString.isBlank()) return StreamQuality.entries.toSet()
            return qualitiesString.split(",")
                .mapNotNull { StreamQuality.fromKey(it.trim()) }
                .toSet()
        }

        fun parseExcludePhrases(phrasesString: String): List<String> {
            if (phrasesString.isBlank()) return emptyList()
            return phrasesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
