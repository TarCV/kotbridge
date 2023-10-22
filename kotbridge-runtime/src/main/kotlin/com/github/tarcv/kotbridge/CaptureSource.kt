package com.github.tarcv.kotbridge

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class CaptureSource(
    val location: String = "",
    val text: String = "",
    val resourcePath: String = ""
)
