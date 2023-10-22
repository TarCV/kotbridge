package com.github.tarcv.kotbridge.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.nio.file.Path

class GenerationExtension(
    private val messages: MessageCollector,
    private val projectDir: Path,
    private val extractedTargetDir: Path,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val addSourceToBlock = pluginContext
            .referenceFunctions(CallableId(
                packageName = FqName("com.github.tarcv.kotbridge"),
                callableName = Name.identifier("addSourceToBlock")
            ))
            .first()

        moduleFragment.transform(
            CaptureTransformer(
                pluginContext,
                projectDir,
                extractedTargetDir,
                addSourceToBlock
            ),
            null
        )
    }
}
