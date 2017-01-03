package org.jetbrains.kotlin.script.util

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import kotlin.reflect.KClass

fun makeTestConfiguration(scriptDefinition: KotlinScriptDefinition, extraClasses: List<KClass<out Any>>): CompilerConfiguration {
    return CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script-util-test")

        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        contextClasspath(PathUtil.KOTLIN_JAVA_RUNTIME_JAR, Thread.currentThread().contextClassLoader)?.let {
            //  addJvmClasspathRoots(it)
        }

        (listOf(DependsOn::class, scriptDefinition.template, Pair::class, JvmName::class) + extraClasses).forEach {
            PathUtil.getResourcePathForClass(it.java).let {
                if (it.exists()) {
                    addJvmClasspathRoot(it)
                } else {
                    // attempt to workaround some maven quirks
                    manifestClassPath(Thread.currentThread().contextClassLoader)?.let {
                        val files = it.filter { it.name.startsWith("kotlin-") }
                        addJvmClasspathRoots(files)
                    }
                }
            }
        }
        put(JVMConfigurationKeys.INCLUDE_RUNTIME, true)
    }
}