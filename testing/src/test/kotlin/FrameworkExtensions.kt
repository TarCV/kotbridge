import com.microsoft.playwright.ElementHandle
import com.github.tarcv.kotbridge.KtJsNoImport
import org.openqa.selenium.WebElement
import org.w3c.dom.Node

@KtJsNoImport
fun WebElement.asNode(): Node = throw NotImplementedError()

@KtJsNoImport
fun ElementHandle.asNode(): Node = throw NotImplementedError()