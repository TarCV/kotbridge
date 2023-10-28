package com.github.tarcv.kotbridge

class Source(
    val location: SourceLocation,
    val text: String,
    val resourcePath: String
) {
    override fun toString(): String = text
}