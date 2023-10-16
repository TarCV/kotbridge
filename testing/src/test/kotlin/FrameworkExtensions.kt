import com.microsoft.playwright.ElementHandle
import io.koalaql.kapshot.KtJsNoImport
import org.openqa.selenium.WebElement
import org.w3c.dom.Node

@KtJsNoImport
fun WebElement.asNode(): Node = throw NotImplementedError()

@KtJsNoImport
fun ElementHandle.asNode(): Node = throw NotImplementedError()