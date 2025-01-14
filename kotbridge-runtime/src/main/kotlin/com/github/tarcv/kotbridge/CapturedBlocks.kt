package com.github.tarcv.kotbridge

fun parseLocation(location: String): SourceLocation {
    fun parseOffset(offset: String): SourceOffset = offset
        .splitToSequence(",")
        .take(3)
        .map { it.toInt() }
        .toList()
        .let {
            SourceOffset(
                char = it[0],
                line = it[1],
                column = it[2]
            )
        }

    return location
        .splitToSequence("\n")
        .take(3)
        .toList()
        .let {
            SourceLocation(
                path = it[0],
                from = parseOffset(it[1]),
                to = parseOffset(it[2])
            )
        }
}

fun <T : Capturable<T>> addSourceToBlock(
    block: Capturable<T>,
    location: String,
    source: String,
    resourcePath: String
): T {
    return block.withSource(
        Source(parseLocation(location), source, resourcePath)
    )
}