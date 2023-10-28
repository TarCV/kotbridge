package com.github.tarcv.kotbridge.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.sourceElement
import org.jetbrains.kotlin.backend.jvm.ir.getValueArgument
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
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
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isNullableType
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
    private val capturableFqn: String = "com.github.tarcv.kotbridge.Capturable",
    private val captureSourceFqn: String = "com.github.tarcv.kotbridge.CaptureSource",
    private val converterInfoFqn: String = "com.github.tarcv.kotbridge.Converters"
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

    private data class ExtractToFileInfo(
        val extensionFqns: List<String>,
        val converterInfo: RequestedConverters,
        val parameterTypes: List<String>,
    )

    private fun extractSource(
        fileText: String,
        start: Int,
        end: Int,
        writeToFile: ExtractToFileInfo? = null // null when should not write to file 
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

        val resourcePath: String

        if (writeToFile != null) {
            // TODO: Discover all required 'import'-s
            val text = fileText.substring(start, end).trimIndent().trim()
            val resourceName = generateHash(text)
            val resourceSuffix = ".js" // TODO: Expose as a parameter
            val location = "${path.fileName}:${entry.getLineNumber(trimmedStart)}"
            resourcePath = "$resourceName$resourceSuffix"

            writeSource(
                "$resourceName.kt",
                text,
                writeToFile.parameterTypes,
                writeToFile.converterInfo,
                writeToFile.extensionFqns,
                location
            )
        } else {
            resourcePath = ""
        }

        return Source(
            text = fileText.substring(start, end).trimIndent().trim(),
            location = "$path\n${encodeOffset(trimmedStart)}\n${encodeOffset(trimmedEnd)}",
            resourcePath = resourcePath
        )
    }

    private fun writeSource(
        inputFileName: String,
        text: String,
        parameterTypes: List<String>,
        converterInfo: RequestedConverters,
        extensions: List<String>,
        location: String
    ) {
        fun syntheticArgName(index: Int) = "__arg${index + 1}"

        val safeLocation = location
            .replace('*', '_')
            .replace('/', '_')
        val imports = extensions.joinToString(System.lineSeparator()) {
            "import $it"
        }

        // TODO: Check these are valid function FQNs
        val (fromConverter, toConverter) = converterInfo

        val returnType = parameterTypes.lastOrNull() ?: "Unit"
        val args = parameterTypes.dropLast(1)
        val signatureArgs = args.mapIndexed { index, type -> "${syntheticArgName(index)}: $type" }
        val unnamedSignatureArgsString = args.joinToString(", ")
        val signatureArgsString = signatureArgs.joinToString(", ")
        val callerArgsString = (signatureArgs + "block: ($unnamedSignatureArgsString)->$returnType").joinToString(", ")
        val callerBlockArgs = args.indices.joinToString(transform = ::syntheticArgName)
        val invokeArgs = args.indices.joinToString { "$toConverter(${syntheticArgName(it)})" }

        // TODO: Move the preamble & footer to plugin parameters
        // Don't use 'main' global fun, because that fun is autorun in generated JS:
        val fragment = """
                |$imports
                |inline fun __caller($callerArgsString): $returnType = block($callerBlockArgs)
                |/* $safeLocation */ @JsExport fun entrypoint($signatureArgsString) = $fromConverter(__caller($invokeArgs) {
                |$text
                |})
                """.trimMargin()
        val fragmentFile = extractedTargetDir
            .createDirectories()
            .resolve(inputFileName)
        if (!fragmentFile.exists() || fragmentFile.readText() != fragment) {
            fragmentFile.writeText(fragment)
        }
    }

    private fun generateHash(text: String): String {
        return MessageDigest.getInstance("sha256")
            .digest(text.encodeToByteArray())
            .let { BigInteger(it).toString(32).replace('-', 'x') }
    }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    private fun transformSam(expression: IrTypeOperatorCall, owner: IrClass): IrExpression {
        val symbol = currentScope!!.scope.scopeOwnerSymbol
        val converterInfo = getRequestedConverters(owner)
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

            val allTypes = expression.typeOperand.toKotlinType()
                .arguments
                .map {
                    val type = it.type.getKotlinTypeFqName(true)
                    val nullabilityMarker = if (it.type.isNullableType()) {
                        "?"
                    } else {
                        ""
                    }
                    "$type$nullabilityMarker"
                }

            val source = extractSource(fileText, startOffset, endOffset,
                ExtractToFileInfo(extensions, converterInfo, allTypes))

            addSourceCall.putTypeArgument(0, expression.type)

            /* super call here rather than directly using expression is required to support nesting. otherwise we don't transform the subtree */
            addSourceCall.putValueArgument(0, super.visitTypeOperator(expression))
            addSourceCall.putValueArgument(1, irString(source.location))
            addSourceCall.putValueArgument(2, irString(source.text))
            addSourceCall.putValueArgument(3, irString(source.resourcePath))

            addSourceCall
        }
    }

    private fun getRequestedConverters(owner: IrClass): RequestedConverters {
        // TODO: verify annotations override each other with the right precedence
        // when extending/implementing annotated classes/interfaces
        return (listOf(owner.annotations) + owner.superTypes.map { it.annotations })
            .foldRight(RequestedConverters("", "")) { it, acc ->
                val annotation = it
                    .firstOrNull {
                        it.symbol.owner.constructedClass.kotlinFqName.asString() == converterInfoFqn
                    }
                    ?: return@foldRight acc
                RequestedConverters(
                    annotation.getValueArgument(Name.identifier("fromJsConverter"))
                        ?.asAnnotationArgumentValueAsString(converterInfoFqn)
                        ?: acc.fromJs,
                    annotation.getValueArgument(Name.identifier("toJsConverter"))
                        ?.asAnnotationArgumentValueAsString(converterInfoFqn)
                        ?: acc.toJs
                )
            }
    }

    private fun IrExpression.asAnnotationArgumentValueAsString(annotationClass: String): String? {
        require(this is org.jetbrains.kotlin.ir.expressions.IrConst<*>) {
            "Kotbridge only supports compile-time constants as arguments for $annotationClass"
        }
        return this.value?.toString()
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
                            return transformSam(expression, owner)
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
                declaration.endOffset
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

data class RequestedConverters(val fromJs: String, val toJs: String)
