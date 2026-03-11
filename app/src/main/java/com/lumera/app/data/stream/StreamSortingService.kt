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
        sortBy: String = "quality",
        maxSizeGb: Int = 0,
        excludedFormats: Set<String> = emptySet()
    ): List<Stream> {
        val lowerPhrases = excludePhrases
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val maxSizeBytes = if (maxSizeGb > 0) maxSizeGb.toLong() * 1_073_741_824L else Long.MAX_VALUE

        return streams
            .map { stream -> stream to StreamParser.parse(stream) }
            .filter { (_, info) -> info.quality in enabledQualities }
            .filter { (stream, _) ->
                if (lowerPhrases.isEmpty()) return@filter true
                val text = StreamParser.combinedText(stream).lowercase()
                lowerPhrases.none { phrase -> text.contains(phrase) }
            }
            .filter { (_, info) ->
                if (maxSizeGb <= 0) return@filter true
                val sizeBytes = info.sizeBytes ?: return@filter true
                sizeBytes <= maxSizeBytes
            }
            .filter { (_, info) ->
                if (excludedFormats.isEmpty()) return@filter true
                excludedFormats.intersect(info.formats).isEmpty()
            }
            .map { (stream, _) -> stream }
            .sortedWith(buildComparator(addonSortOrders, sortBy))
    }

    private fun buildComparator(
        addonSortOrders: Map<String, Int>,
        sortBy: String
    ): Comparator<Stream> {
        // Sort chain: chosen primary → other → seeds → addon priority
        var comparator = when (sortBy) {
            "size" -> sortComparatorFor("size").then(sortComparatorFor("quality"))
            else -> sortComparatorFor("quality").then(sortComparatorFor("size"))
        }
        comparator = comparator.then(sortComparatorFor("seeds"))
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

        fun parseExcludedFormats(formatsString: String): Set<String> {
            if (formatsString.isBlank()) return emptySet()
            return formatsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }
}
