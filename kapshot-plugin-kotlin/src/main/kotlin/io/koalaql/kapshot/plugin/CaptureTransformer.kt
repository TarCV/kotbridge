package io.koalaql.kapshot.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.sourceElement
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import java.io.File
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val PREPROCESSED_TEXT_NEWLINE = "\n"
class CaptureTransformer(
    private val context: IrPluginContext,
    private val projectDir: Path,
    private val extractedTargetDir: Path,
    private val addSourceToBlock: IrSimpleFunctionSymbol,
    private val capturableFqn: String = "io.koalaql.kapshot.Capturable",
    private val captureSourceFqn: String = "io.koalaql.kapshot.CaptureSource",
): IrElementTransformerVoidWithContext() {

    private fun currentFileText(): String {
        /* https://youtrack.jetbrains.com/issue/KT-41888 */
        return File(currentFile.path).readText().replace("\r\n", PREPROCESSED_TEXT_NEWLINE)
    }

    private class Source(
        val text: String,
        val location: String,
        val resourcePath: String
    )

    private fun extractSource(
        fileText: String,
        start: Int,
        end: Int,
        extensions: List<String> = emptyList(),
        writeToFile: Boolean = false
    ): Source {
        val entry = currentFile.fileEntry
        val path = projectDir.relativize(Path(currentFile.path))

        /* trim offsets so source location info matches .trimIndent().trim(). TODO roll trimIndent().trim() ourselves */
        var trimmedStart = start
        var trimmedEnd = end

        while (trimmedEnd > trimmedStart && fileText[trimmedEnd - 1].isWhitespace()) trimmedEnd--
        while (trimmedStart < trimmedEnd && fileText[trimmedStart].isWhitespace()) trimmedStart++

        fun encodeOffset(offset: Int): String =
            "$offset,${entry.getLineNumber(offset)},${entry.getColumnNumber(offset)}"

        // TODO: Discover all required 'import'-s
        val text = fileText.substring(start, end).trimIndent().trim()
        val resourceName = generateHash(text)
        val resourceSuffix = ".js" // TODO: Expose as a parameter
        val location = "${path.fileName}:${entry.getLineNumber(trimmedStart)}"

        if (writeToFile) {
            writeSource(
                resourceName,
                ".kt",
                text,
                extensions,
                location
            )
        }

        return Source(
            text = fileText.substring(start, end).trimIndent().trim(),
            location = "$path\n${encodeOffset(trimmedStart)}\n${encodeOffset(trimmedEnd)}",
            resourcePath = if (!writeToFile) {
                ""
            } else {
                "$resourceName$resourceSuffix"
            }
        )
    }

    private fun writeSource(inputFileName: String, inputFileSuffix: String, text: String, extensions: List<String>, location: String) {
        val safeLocation = location
            .replace('*', '_')
            .replace('/', '_')
        val imports = extensions.joinToString(System.lineSeparator()) {
            "import $it"
        }

        // TODO: Move the preamble & footer to plugin parameters
        // Don't use 'main' global fun, because that fun is autorun in generated JS:
        val fragment = """
                |$imports
                |/* $safeLocation */ @JsExport fun entrypoint(arguments: Array<Any>) = arguments.let {
                |$text
                |}
                """.trimMargin()
        val fragmentFile = extractedTargetDir
            .createDirectories()
            .resolve("$inputFileName$inputFileSuffix")
        if (!fragmentFile.exists() || fragmentFile.readText() != fragment) {
            fragmentFile.writeText(fragment)
        }
    }

    private fun generateHash(text: String): String {
        return MessageDigest.getInstance("sha256")
            .digest(text.encodeToByteArray())
            .let { BigInteger(it).toString(32).replace('-', 'x') }
    }

    private fun transformSam(expression: IrTypeOperatorCall): IrExpression {
        val symbol = currentScope!!.scope.scopeOwnerSymbol

        val sourceElement = expression.sourceElement() ?: return super.visitTypeOperator(expression)
        val extensions = Collections.synchronizedList(mutableListOf<String>())
        expression.acceptVoid(object : IrElementVisitorVoid {
            override fun visitCall(expression: IrCall) {
                val expressionOwner = expression.symbol.owner
                if (expressionOwner.extensionReceiverParameter != null) {
                    if (expressionOwner.annotations.none { it.symbol.owner.constructedClass.name.asString() == "KtJsNoImport" }) {
                        extensions.add(expressionOwner.kotlinFqName.asString())
                    }
                }

                super.visitCall(expression)
            }

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }
        })
        return with(DeclarationIrBuilder(context, symbol, expression.startOffset, expression.endOffset)) {
            val addSourceCall = this.irCall(
                addSourceToBlock,
                expression.typeOperand
            )

            val fileText = currentFileText()

            var startOffset = sourceElement.startOffset
            var endOffset = sourceElement.endOffset

            while (endOffset > startOffset && fileText[endOffset - 1] != '}') endOffset--
            while (startOffset < endOffset && fileText[startOffset] != '{') startOffset++

            if (endOffset > startOffset + 1) {
                /* assume {...} and trim */
                endOffset--
                startOffset++
            }

            val source = extractSource(fileText, startOffset, endOffset, extensions, writeToFile = true)

            addSourceCall.putTypeArgument(0, expression.type)

            /* super call here rather than directly using expression is required to support nesting. otherwise we don't transform the subtree */
            addSourceCall.putValueArgument(0, super.visitTypeOperator(expression))
            addSourceCall.putValueArgument(1, irString(source.location))
            addSourceCall.putValueArgument(2, irString(source.text))
            addSourceCall.putValueArgument(3, irString(source.resourcePath))

            addSourceCall
        }
    }

    private fun typeIsFqn(type: IrType, fqn: String): Boolean {
        if (type !is IrSimpleType) return false

        return when (val owner = type.classifier.owner) {
            is IrClass -> owner.kotlinFqName.asString() == fqn
            else -> false
        }
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
        if (expression.operator == IrTypeOperator.SAM_CONVERSION) {
            when (val type = expression.type) {
                is IrSimpleType -> {
                    when (val owner = type.classifier.owner) {
                        is IrClass -> if (owner.superTypes.any { typeIsFqn(it, capturableFqn) }) {
                            return transformSam(expression)
                        }
                    }
                }
            }
        }

        return super.visitTypeOperator(expression)
    }

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        val captureSource = declaration.annotations.singleOrNull {
            typeIsFqn(it.type, captureSourceFqn)
        }

        if (captureSource != null) {
            val source = extractSource(
                currentFileText(),
                /* we start from end of captureSource rather than declaration.startOffset to exclude the capture annotation */
                captureSource.endOffset,
                declaration.endOffset,
                writeToFile = false
            )

            captureSource.putValueArgument(0,
                IrConstImpl.string(
                    captureSource.startOffset,
                    captureSource.endOffset,
                    context.irBuiltIns.stringType,
                    source.location
                )
            )

            captureSource.putValueArgument(1,
                IrConstImpl.string(
                    captureSource.startOffset,
                    captureSource.endOffset,
                    context.irBuiltIns.stringType,
                    source.text
                )
            )

            captureSource.putValueArgument(2,
                IrConstImpl.string(
                    captureSource.startOffset,
                    captureSource.endOffset,
                    context.irBuiltIns.stringType,
                    ""
                )
            )
        }

        return super.visitDeclaration(declaration)
    }
}
