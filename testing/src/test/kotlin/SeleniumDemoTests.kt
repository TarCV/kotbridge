import io.koalaql.kapshot.Capturable
import io.koalaql.kapshot.Source
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

class SeleniumDemoTests {
    fun interface CapturedBlock1<T, R>: Capturable<CapturedBlock1<T, R>> {
        operator fun invoke(arg: T): R

        override fun withSource(source: Source): CapturedBlock1<T, R> = object : CapturedBlock1<T, R> by this {
            override val source = source
        }
    }

    private val scriptToPinMap = mutableMapOf<String, ScriptKey>()

    fun <T> WebDriver.executeKtJs(vararg args: Any, block: CapturedBlock1<Array<out Any>, T>): T {
        @Suppress("ConstantConditionIf")
        if (false) { // Catch type issues
            block(args)
        }

        val resourcePath = block.source.resourcePath.apply {
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
                        return e["$fragmentName"].entrypoint(arguments)
                    """.trimIndent()

            val newPin = (this as JavascriptExecutor).pin(runnableJs)
            scriptToPinMap[resourcePath] = newPin
            newPin
        }
        val seleniumResult = (this as JavascriptExecutor).executeScript(pin, *args)
        val result = if (seleniumResult is List<*>) {
            // Selenium converts an array to a list automagically. Convert it back to avoid runtime type errors: 
            seleniumResult.toTypedArray()
        } else {
            seleniumResult
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun readColumnTexts(): Unit = ChromeDriver(ChromeOptions().addArguments("--headless=new")).runAndQuit {
        (1..10).map {
            listOf(
                "KtJs comparable" to assertAndMeasure { cells ->
                    executeKtJs(cells) { (e) ->
                        (e as Array<org.w3c.dom.Element>)
                            .map { it.textContent }
                            .toTypedArray()
                    }.toList()
                },

                "KtJs optimal" to assertAndMeasure { _ ->
                    executeKtJs { _ ->
                        kotlinx.browser.document.querySelectorAll(".column-15")
                            .asList()
                            .map { it.textContent }
                            .toTypedArray()
                    }.toList()
                },

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
}