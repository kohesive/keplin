package uy.kohesive.keplin.common

import org.jetbrains.kotlin.script.ScriptTemplateDefinition
import org.jetbrains.kotlin.script.util.FilesAndMavenResolver
import org.jetbrains.kotlin.script.util.LocalFilesResolver

@ScriptTemplateDefinition(resolver = LocalFilesResolver::class, scriptFilePattern = ".*\\.kts")
abstract class StandardArgsScriptTemplateWithLocalResolving(val args: Array<String>)

@ScriptTemplateDefinition(resolver = FilesAndMavenResolver::class, scriptFilePattern = ".*\\.kts")
abstract class StandardArgsScriptTemplateWithMavenResolving(val args: Array<String>)

@ScriptTemplateDefinition(resolver = LocalFilesResolver::class, scriptFilePattern = ".*\\.kts")
abstract class BindingsScriptTemplateWithLocalResolving(val bindings: Map<String, Any?>)

@ScriptTemplateDefinition(resolver = FilesAndMavenResolver::class, scriptFilePattern = ".*\\.kts")
abstract class BindingsScriptTemplateWithMavenResolving(val bindings: Map<String, Any?>)
