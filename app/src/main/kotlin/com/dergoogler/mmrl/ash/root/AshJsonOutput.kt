package com.dergoogler.mmrl.ash.root

/** Extracts the last complete top-level JSON object emitted at the beginning of a line. */
internal object AshJsonOutput {
    fun extractObject(raw: String): String? {
        var objectStart = -1
        var depth = 0
        var inString = false
        var escaped = false
        var lineOnlyWhitespace = true
        var lastObject: String? = null

        raw.forEachIndexed { index, character ->
            if (objectStart < 0) {
                if (character == '{' && lineOnlyWhitespace) {
                    objectStart = index
                    depth = 1
                    inString = false
                    escaped = false
                    lineOnlyWhitespace = false
                } else if (character == '\n') {
                    lineOnlyWhitespace = true
                } else if (!character.isWhitespace()) {
                    lineOnlyWhitespace = false
                }
                return@forEachIndexed
            }

            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                return@forEachIndexed
            }

            when (character) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        lastObject = raw.substring(objectStart, index + 1)
                        objectStart = -1
                        lineOnlyWhitespace = false
                    }
                }
            }
        }

        return lastObject
    }
}
