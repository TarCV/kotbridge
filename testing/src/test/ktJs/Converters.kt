import com.microsoft.playwright.ElementHandle
import org.openqa.selenium.WebElement
import org.w3c.dom.Node

value class JsWebElement(private val node: Node): WebElement {
    override fun asNode(): Node = node
}

value class JsElementHandle(private val node: Node): ElementHandle {
    override fun asNode(): Node = node
}

inline fun <reified T> t0JsConverterSe(list: List<T>): List<T> = t0JsConverter<T>(list)
inline fun <reified T> t0JsConverterPlay(list: List<T>): List<T> = t0JsConverter<T>(list)
inline fun <reified T> t0JsConverter(list: List<T>): List<T> {
    // list is actually an array because of Selenium/Playwright conversions
    var actualList = (list).unsafeCast<Array<T>>().toList()
    if (T::class == WebElement::class) {
        actualList = actualList.map { JsWebElement(it as Node) as T }
    } else if (T::class == ElementHandle::class) {
        actualList = actualList.map { JsElementHandle(it as Node) as T }
    }
    return actualList
}

fun t0JsConverterSe(element: WebElement): WebElement {
    // element is actually a node because of Selenium conversions
    return JsWebElement((element).unsafeCast<Node>())
}

fun t0JsConverterPlay(element: ElementHandle): ElementHandle {
    // element is actually a node because of Playwright conversions
    return JsElementHandle((element).unsafeCast<Node>())
}

fun <T> t0JsConverterSe(other: Any): T = t0JsConverter(other)
fun <T> t0JsConverterPlay(other: Any): T = t0JsConverter(other)
fun <T> t0JsConverter(other: Any): T = other as T

inline fun <reified T> fr0mJsConverterSe(list: List<T>): List<T> = fr0mJsConverter<T>(list)
inline fun <reified T> fr0mJsConverterPlay(list: List<T>): List<T> = fr0mJsConverter<T>(list)
inline fun <reified T> fr0mJsConverter(list: List<T>): List<T> {
    // Selenium and Playwright can only create a List on JVM side when JS side returns an Array
    var jsList = list.toList()
    if (T::class == WebElement::class
        || T::class == JsWebElement::class) {
        jsList = jsList.map { fr0mJsConverter((it).unsafeCast<WebElement>()) as T }
    } else if (T::class == ElementHandle::class
        || T::class == JsElementHandle::class) {
        jsList = jsList.map { fr0mJsConverter((it).unsafeCast<ElementHandle>()) as T }
    }
    return (jsList.toTypedArray().unsafeCast<List<T>>())
}

fun fr0mJsConverterSe(element: WebElement): WebElement {
    // Selenium can only create a WebElement on JVM side when JS side returns a Node
    return (element.asNode().unsafeCast<WebElement>())
}

fun fr0mJsConverterPlay(element: ElementHandle): ElementHandle {
    return (element.asNode().unsafeCast<ElementHandle>())
}

fun <T> fr0mJsConverterSe(other: Any): T = fr0mJsConverter<T>(other)
fun <T> fr0mJsConverterPlay(other: Any): T = fr0mJsConverter<T>(other)
fun <T> fr0mJsConverter(other: Any): T = other as T