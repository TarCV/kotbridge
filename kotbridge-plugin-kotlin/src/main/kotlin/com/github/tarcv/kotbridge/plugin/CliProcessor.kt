package com.github.tarcv.kotbridge.plugin

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CommandLineProcessor::class)
@OptIn(ExperimentalCompilerApi::class)
class CliProcessor: CommandLineProcessor {
    override val pluginId: String = "com.github.tarcv.kotbridge-plugin"

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = "projectDir",
            valueDescription = "path",
            description = "root project path",
            required = false,
        ),
        CliOption(
            optionName = "extractedDir",
            valueDescription = "path",
            description = "path to extract kt/js fragments to",
            required = true,
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        return when (option.optionName) {
            "projectDir" -> configuration.put(PROJECT_DIR_KEY, value)
            "extractedDir" -> configuration.put(EXTRACTED_DIR_KEY, value)
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}