package com.github.tarcv.kotbridge

interface Capturable<T : Capturable<T>> {
    val source: Source get() = error("there is no source code for this block")

    fun withSource(source: Source): T
}

@Target(AnnotationTarget.CLASS)
annotation class Converters(
    /**
     *  Fully qualified name of a converter function called on JS side to convert fragment arguments.
     *  Empty when there is no converter.
     **/
    val toJsConverter: String = "",

    /**
     *  Fully qualified name of a converter function called on JS side to convert fragment return values.
     *  Empty when there is no converter.
     **/
    val fromJsConverter: String = "",
)