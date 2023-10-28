@file:Suppress(
    "PackageDirectoryMismatch",
    "UnusedReceiverParameter",
    "UNUSED_PARAMETER"
)
package org.w3c.dom

import com.github.tarcv.kotbridge.KtJsNoImport

// Stubs of kt/js only APIs

@KtJsNoImport
fun Document.querySelectorAll(selector: String): ItemArrayLike<Node> {
    TODO()
}

fun <T> ItemArrayLike<T>.asList(): List<T> = TODO()

interface ItemArrayLike<out T>