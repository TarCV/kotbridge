import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.github.tarcv.kotbridge.Capturable
import com.github.tarcv.kotbridge.Converters
import com.github.tarcv.kotbridge.Source
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.w3c.dom.asList
import org.w3c.dom.querySelectorAll
import java.util.stream.Collectors
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CONVERTER_SUFFIX = "ConverterPlay"
class PlaywrightDemoTests {
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
    fun interface CapturedBlock1<T, R> : Capturable<CapturedBlock1<T, R>> {
        operator fun invoke(arg: T): R

        override fun withSource(source: Source): CapturedBlock1<T, R> = object : CapturedBlock1<T, R> by this {
            override val source = source
        }
    }

    fun <R> Page.evaluateKtJs(block: CapturedBlock0<R>): R {
        @Suppress("ConstantConditionIf")
        if (false) { // Catch type issues
            block()
        }

        val runnableJs = prepareRunnableJs(block.source)
        val seleniumResult = evaluate(runnableJs)
        @Suppress("UNCHECKED_CAST")
        return seleniumResult as R
    }

    fun <T, R> Page.evaluateKtJs(arg: T, block: CapturedBlock1<T, R>): R {
        @Suppress("ConstantConditionIf")
        if (false) { // Catch type issues
            block(arg)
        }

        val runnableJs = prepareRunnableJs(block.source)
        val seleniumResult = evaluate(runnableJs, arg)
        @Suppress("UNCHECKED_CAST")
        return seleniumResult as R
    }

    fun <R> Page.evalKtJsOnSelectorAll(selector: String, block: CapturedBlock1<List<org.w3c.dom.Element>, R>): R {
        val runnableJs = prepareRunnableJs(block.source)
        val seleniumResult = evalOnSelectorAll(selector, runnableJs)
        @Suppress("UNCHECKED_CAST")
        return seleniumResult as R
    }

    @Test
    fun readColumnTexts(): Unit = Playwright.create().use { p ->
        p.chromium().launch(LaunchOptions().setChannel("chrome")).use { browser ->
            (1..10).map {
                listOf(
                    "KtJs map(textContent)" to browser.assertAndMeasure {page, _, cells ->
                        page.evaluateKtJs(cells) { e ->
                            e.map { it.asNode().textContent ?: "" }
                        }
                    },

                    "KtJs evalOnSelectorAll" to browser.assertAndMeasure { page, _, _ ->
                        page.evalKtJsOnSelectorAll(".column-15") { e ->
                            e.map { it.textContent ?: "" }
                        }
                    },

                    "KtJs querySelectorAll" to browser.assertAndMeasure { page, _, _ ->
                        page.evaluateKtJs {
                            kotlinx.browser.document.querySelectorAll(".column-15")
                                .asList()
                                .map { it.textContent ?: "" }
                        }
                    },

                    "Playwright map(textContent)" to browser.assertAndMeasure { _, _, cells ->
                        cells.map { it.textContent() }
                    },

                    "Playwright locator allTextContents" to browser.assertAndMeasure { _, locator, _ ->
                        locator.allTextContents()
                    },
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
    }

    private inline fun Browser.assertAndMeasure(textGetter: PlaywrightDemoTests.(Page, Locator, List<ElementHandle>) -> List<String>): Long {
        val result: List<String>

        val page = newPage()
        page.navigate("https://the-internet.herokuapp.com/large")
        val locator = page.locator("css=.column-15")
        val cells = locator.elementHandles()

        val time = measureTimeMillis {
            result = textGetter(page, locator, cells)
        }
        assertEquals(
            listOf("15") + (1..50).map { "$it.15" },
            result
        )
        assertTrue(result.all { it.endsWith("15") }, "Unexpected values: $result")
        return time
    }

    private fun prepareRunnableJs(source: Source): String {
        val resourcePath = source.resourcePath.apply {
            require(isNotEmpty()) { "Failed to find compiled source for the fragment" }
        }

        val moduleJs = this@PlaywrightDemoTests::class.java.getResourceAsStream(resourcePath)
            .run {
                requireNotNull(this) { "Failed to read the compiled fragment $resourcePath" }
            }
            .bufferedReader()
            .use {
                it.readText()
            }
        val fragmentName = resourcePath.removeSuffix(".js")
        @Language("JavaScript") val runnableJs = """
                            arguments => {
                                const e = {};
                                (function () { $moduleJs }).call(e);
                                return e["$fragmentName"].entrypoint(arguments);
                            }
                            """.trimIndent()
        return runnableJs
    }
}