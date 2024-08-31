package ru.somarov.gateway.infrastructure.logging

import com.fasterxml.jackson.core.JsonStreamContext
import net.logstash.logback.mask.ValueMasker
import java.util.regex.Pattern

class Masker : ValueMasker {
    private val fields = listOf(
        "password"
    )

    private val pattern = "(: \"|=|\":|: |:)[^,& :}\n]{2,}"
    private var maskPatterns: List<Pair<Pattern, String>>

    init {
        val patterns = fields.map { Pair(Pattern.compile("$it$pattern"), "$it=") }.toMutableList()
        patterns.addAll(
            listOf(
                Pair(
                    Pattern.compile("Contact[(]typeRef=ref.ContactType.Mobile, code=[(0-9)], value=[^,& :}\n]*"),
                    "Contact(typeRef=ref.ContactType.Mobile, code=*, value="
                ),
                Pair(Pattern.compile("Income[(]amount=[^& :)]*"), "Income(amount="),
            )
        )
        maskPatterns = patterns
    }

    override fun mask(jsonStreamContext: JsonStreamContext, o: Any): Any {
        if (o is CharSequence) {
            return maskMessage(o as String)
        }
        return o
    }

    private fun maskMessage(message: String): String {
        val maskedMessage = StringBuilder(message)

        for ((pattern, prefix) in maskPatterns) {
            val matcher = pattern.matcher(maskedMessage)

            while (matcher.find()) {
                val match = matcher.group()
                val maskedValue = mask(match, prefix)
                maskedMessage.replace(matcher.start(), matcher.end(), maskedValue)
            }
        }

        return maskedMessage.toString()
    }

    private fun mask(value: String, prefix: String): String {
        var maskedValue = prefix
        val originalValue = value.substring(
            if (prefix.lastIndex + 1 < value.length) prefix.lastIndex + 1 else prefix.lastIndex,
            value.length
        )
        if (originalValue.length > ORIGINAL_VALUE_THRESHOLD) {
            maskedValue += originalValue.substring(ORIGINAL_VALUE_UNMASKED_START, ORIGINAL_VALUE_UNMASKED_END)
        }
        repeat(value.length - maskedValue.length) { maskedValue += "*" }
        return maskedValue
    }

    companion object {
        const val ORIGINAL_VALUE_THRESHOLD = 5

        const val ORIGINAL_VALUE_UNMASKED_START = 0
        const val ORIGINAL_VALUE_UNMASKED_END = 2
    }
}