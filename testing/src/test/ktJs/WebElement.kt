package org.openqa.selenium

import org.w3c.dom.Node

interface WebElement {
    fun asNode(): Node
}