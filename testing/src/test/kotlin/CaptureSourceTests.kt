import io.koalaql.kapshot.CaptureSource
import io.koalaql.kapshot.sourceOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureSourceTests {
    @CaptureSource
    interface TestClass {
        fun boo() = "boo!"

        @CaptureSource
        class Nested {
            val test get() = "nest"
        }
    }

    @Test
    fun `capture class source`() {
        val source = sourceOf<TestClass>()

        assertEquals(
            """
            interface TestClass {
                fun boo() = "boo!"
        
                @CaptureSource
                class Nested {
                    val test get() = "nest"
                }
            }
            """.trimIndent(),
            source.text
        )

        val location = source.location

        assertEquals("src/test/kotlin/CaptureSourceTests.kt", location.path)

        assertEquals(182, location.from.char)
        assertEquals(7, location.from.line)
        assertEquals(4, location.from.column)

        assertEquals(329, location.to.char)
        assertEquals(14, location.to.line)
        assertEquals(5, location.to.column)

        assertEquals("", source.resourcePath)
    }

    @Test
    fun `capture nested class source`() {
        val source = sourceOf<TestClass.Nested>()
        assertEquals(
            """
            class Nested {
                val test get() = "nest"
            }
            """.trimIndent(),
            source.text
        )
        assertEquals("", source.resourcePath)
    }

    private val capMethodSourceSource = """
    @Test
    fun `capture method source`() {
        class Inner {
            @CaptureSource
            fun five() = 5
        }

        val source1 = sourceOf(::`capture method source`)
        assertEquals(
            capMethodSourceSource,
            source1.text
        )
        assertEquals("", source1.resourcePath)

        val source2 = sourceOf(Inner::five)
        assertEquals(
            "fun five() = 5", source2.text
        )
        assertEquals("", source2.resourcePath)
    }
    """.trimIndent()

    @CaptureSource
    @Test
    fun `capture method source`() {
        class Inner {
            @CaptureSource
            fun five() = 5
        }

        val source1 = sourceOf(::`capture method source`)
        assertEquals(
            capMethodSourceSource,
            source1.text
        )
        assertEquals("", source1.resourcePath)

        val source2 = sourceOf(Inner::five)
        assertEquals(
            "fun five() = 5", source2.text
        )
        assertEquals("", source2.resourcePath)
    }

    @CaptureSource
    interface CapturedInterface {
        fun type() = "interface"
    }

    @CaptureSource
    object CapturedObj {
        val test = "123"
    }

    @CaptureSource
    sealed class CapturedSealed {
        object Left: CapturedSealed()
        object Right: CapturedSealed()

        @CaptureSource
        companion object { }
    }

    @Test
    fun `capture assorted declarations`() {
        val source1 = sourceOf<CapturedInterface>()
        assertEquals(
            """
            interface CapturedInterface {
                fun type() = "interface"
            }
            """.trimIndent(),
            source1.text
        )
        assertEquals("", source1.resourcePath)

        val source2 = sourceOf<CapturedObj>()
        assertEquals(
            """
            object CapturedObj {
                val test = "123"
            }
            """.trimIndent(),
            source2.text
        )
        assertEquals("", source2.resourcePath)

        val source3 = sourceOf<CapturedSealed>()
        assertEquals(
            """
            sealed class CapturedSealed {
                object Left: CapturedSealed()
                object Right: CapturedSealed()

                @CaptureSource
                companion object { }
            }
            """.trimIndent(),
            source3.text
        )
        assertEquals("", source3.resourcePath)

        val source4 = sourceOf<CapturedSealed.Companion>()
        assertEquals(
            """
                companion object { }
            """.trimIndent(),
            source4.text
        )
        assertEquals("", source4.resourcePath)
    }

    @CaptureSource
    @Transient /* test this annotation is present in source */
    private val someVal = 5

    @CaptureSource
    private var someVar: Int get() = 10
        set(value) { }

    @Test
    fun `can capture source of valvars`() {
        val source1 = sourceOf(::someVal)
        assertEquals(
            """
                @Transient /* test this annotation is present in source */
                private val someVal = 5
            """.trimIndent(),
            source1.text
        )
        assertEquals("", source1.resourcePath)

        val source2 = sourceOf(::someVar)
        assertEquals(
            """
                private var someVar: Int get() = 10
                    set(value) { }
            """.trimIndent(),
            source2.text
        )
        assertEquals("", source2.resourcePath)
    }
}