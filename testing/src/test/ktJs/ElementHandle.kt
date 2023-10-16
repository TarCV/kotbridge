package com.microsoft.playwright

import org.w3c.dom.Node

interface ElementHandle {
    fun asNode(): Node
}