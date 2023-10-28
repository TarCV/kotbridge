import com.github.tarcv.kotbridge.Capturable
import com.github.tarcv.kotbridge.Converters
import com.github.tarcv.kotbridge.Source
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.ScriptKey
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.w3c.dom.asList
import org.w3c.dom.querySelectorAll
import java.util.stream.Collectors
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CONVERTER_SUFFIX = "ConverterSe"
class SeleniumDemoTests {
    @Suppress("ConvertToStringTemplate") // this tests constant expressions work as annotation arguments:
    @Converters(toJsConverter = "t" + 0 + "Js" + CONVERTER_SUFFIX, fromJsConverter = "fr${0}mJs${CONVERTER_SUFFIX}")
    fun interface CapturedBlock0<R>: Capturable<CapturedBlock0<R>> {
        operator fun invoke(): R

        override fun withSource(source: Source): CapturedBlock0<R> = object : CapturedBlock0<R> by this {
            override val source = source
        }
    }

    @Suppress("ConvertToStringTemplate") // this tests constant expressions work as annotation arguments:
    @Converters(toJsConverter = "t" + 0 + "Js" + CONVERTER_SUFFIX, fromJsConverter = "fr${0}mJs${CONVERTER_SUFFIX}")
    fun interface CapturedBlock1<T, R>: Capturable<CapturedBlock1<T, R>> {
        operator fun invoke(arg: T): R

        override fun withSource(source: Source): CapturedBlock1<T, R> = object : CapturedBlock1<T, R> by this {
            override val source = source
        }
    }

    private val scriptToPinMap = mutableMapOf<String, ScriptKey>()

    fun <R> WebDriver.executeKtJs(block: CapturedBlock0<R>): R {
        @Suppress("ConstantConditionIf")
        if (false) { // Catch type issues
            block()
        }

        val pin = getPinForBlock(block.source)
        val seleniumResult = (this as JavascriptExecutor).executeScript(pin)
        @Suppress("UNCHECKED_CAST")
        return seleniumResult as R
    }

    fun <T, R> WebDriver.executeKtJs(arg1: T, block: CapturedBlock1<T, R>): R {
        @Suppress("ConstantConditionIf")
        if (false) { // Catch type issues
            block(arg1)
        }

        val pin = getPinForBlock(block.source)
        val seleniumResult = (this as JavascriptExecutor).executeScript(pin, arg1)
        @Suppress("UNCHECKED_CAST")
        return seleniumResult as R
    }

    @Test
    fun readColumnTexts(): Unit = ChromeDriver(ChromeOptions().addArguments("--headless=new")).runAndQuit {
        (1..10).map {
            listOf(
                "KtJs comparable" to assertAndMeasure { cells -> //                     <^-code outside is executed in the JVM world
                    executeKtJs(cells) { e -> //                                       vvv-code inside this block is executed in the JS world
                        e.map { it.asNode().textContent ?: "" }
                    } //                                                               ^^^-code inside this block is executed in the JS world
                }, //                                                                   <V-code outside is executed in the JVM world

                "KtJs optimal" to assertAndMeasure { _ -> //                      <^-code outside is executed in the JVM world
                    executeKtJs { //                                             vvv-code inside this block is executed in the JS world
                        kotlinx.browser.document.querySelectorAll(".column-15")
                            .asList()
                            .map { it.textContent ?: "" }
                    } //                                                         ^^^-code inside this block is executed in the JS world
                }, //                                                             <V-code outside is executed in the JVM world

                "Selenium comparable" to assertAndMeasure { cells ->
                    cells.map { it.getDomProperty("textContent") }
                },

                "Selenium getText" to assertAndMeasure { cells ->
                    cells.map { it.text }
                }
            )
        }
            .flatten()
            .groupBy { it.first }
            .mapValues {
                it.value.stream()
                    .collect(Collectors.summarizingLong { it.second })
            }
            .forEach {
                println("=== ${it.key} ===")
                println(it.value)
            }
    }

    private inline fun ChromeDriver.assertAndMeasure(textGetter: SeleniumDemoTests.(List<WebElement>) -> List<String>): Long {
        val result: List<String>

        get("https://the-internet.herokuapp.com/large")
        val cells = findElements(By.className("column-15"))

        val time = measureTimeMillis {
            result = textGetter(cells)
        }
        assertEquals(
            listOf("15") + (1..50).map { "$it.15" },
            result
        )
        assertTrue(result.all { it.endsWith("15") }, "Unexpected values: $result")
        return time
    }

    private inline fun <T : RemoteWebDriver, R> T.runAndQuit(block: T.() -> R): R = try {
        block()
    } finally {
        quit()
    }

    private fun WebDriver.getPinForBlock(source: Source): ScriptKey {
        val resourcePath = source.resourcePath.apply {
            require(isNotEmpty()) { "Failed to find compiled source for the fragment" }
        }

        this as JavascriptExecutor
        var pin = scriptToPinMap[resourcePath]
        pin = if (pin != null && pin in pinnedScripts) {
            pin
        } else {
            println("NOTE: Script $resourcePath is not pinned yet")
            val moduleJs = this@SeleniumDemoTests::class.java.getResourceAsStream(resourcePath)
                .run {
                    requireNotNull(this) { "Failed to read the compiled fragment $resourcePath" }
                }
                .bufferedReader()
                .use {
                    it.readText()
                }
            val fragmentName = resourcePath.removeSuffix(".js")
            @Language("JavaScript") val runnableJs = """
                            const e = {};
                            (function () { $moduleJs }).call(e);
                            const entryModule = e["$fragmentName"];
                            return entryModule.entrypoint.apply(entryModule, arguments)
                        """.trimIndent()

            val newPin: ScriptKey = (this as JavascriptExecutor).pin(runnableJs)
            scriptToPinMap[resourcePath] = newPin
            newPin
        }
        return pin
    }
}