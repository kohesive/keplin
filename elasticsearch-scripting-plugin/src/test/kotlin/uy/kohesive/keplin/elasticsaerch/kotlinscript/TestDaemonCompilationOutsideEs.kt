package uy.kohesive.keplin.elasticsaerch.kotlinscript

import org.junit.Test

class TestDaemonCompilationOutsideEs {
    @Test
    fun testProcRun() {
        val proc = ProcessBuilder("/Library/Java/JavaVirtualMachines/jdk1.8.0_112.jdk/Contents/Home/jre/bin/java", "-version").start()
        Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", "/Library/Java/JavaVirtualMachines/jdk1.8.0_112.jdk/Contents/Home/jre/bin/java"))
        println("hello")
    }

    @Test
    fun testCompileAScript() {
        /*
        val uniqueSessionId = UUID.randomUUID().toString()

        val tempDir = createTempDir("keplin-es-kotlinscript", uniqueSessionId)
        val clientAliveFile: File = File.createTempFile("keplin-es-kotlinscript", uniqueSessionId)
        val compilerMessages: MessageCollector = CapturingMessageCollector()

        val kotlinInstallDirName = "/Users/jminard/Downloads/kotlinc-2"
        val kotlinInstallDir = File(kotlinInstallDirName).absoluteFile

        // TODO: improve this handling, test this all during plugin loading
        val kotlinCompilerJar = File(kotlinInstallDir, "lib/kotlin-compiler.jar").takeIf { it.exists() } ?: throw IllegalStateException("Invalid/missing Kotlin compiler JAR: ${kotlinInstallDir}/lib/kotlin-compiler.jar")

        val compilerClasspath = listOf(kotlinCompilerJar) + ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class)

        val compilerService by lazy {
            val compilerId = CompilerId.makeCompilerId(compilerClasspath)
            val daemonOptions = DaemonOptions(runFilesPath = File(tempDir, "daemonRunPath").absolutePath,
                    verbose = true,
                    reportPerf = true)
            val daemonJVMOptions = org.jetbrains.kotlin.daemon.common.DaemonJVMOptions()
            val daemonReportMessages = arrayListOf<DaemonReportMessage>()

            KotlinCompilerClient.connectToCompileService(compilerId, clientAliveFile, daemonJVMOptions, daemonOptions,
                    DaemonReportingTargets(messages = daemonReportMessages, messageCollector = compilerMessages), true)
                    ?: throw IllegalStateException("Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") { "${it.category.name} ${it.message}" })
        }

        val scriptId = 1
        val scriptSource = """
                    docInt("number", 1) * parmInt("multiplier", 1) + _score
                """
        val compilerOutCapture = CapturingMessageCollector()
        val compilerOutputs = arrayListOf<File>()
        try {
            // val sessionId: Int = leaseSession(compiler, compilerOutCapture, compilerOutputs)
            val compilerArgs = arrayOf<String>()
            val compiler = KotlinRemoteReplCompilerClient(compilerService, clientAliveFile, CompileService.TargetPlatform.JVM, compilerArgs,
                    compilerMessages, ClassPathUtils.findClassJars(EsKotlinScriptTemplate::class),
                    EsKotlinScriptTemplate::class.java.canonicalName)
            try {
                val codeLine = ReplCodeLine(scriptId, 0, scriptSource)
                try {
                    val replState = compiler.createState()
                    val replResult = compiler.compile(replState, codeLine)
                    // val replState = compiler.replCreateState(sessionId).get().getId()
                    // val daemonResult = compiler.replCompile(sessionId, replState, codeLine)
                    // if (!daemonResult.isGood) {
                    //     throw ScriptException("Unknown daemon compiling failure", null, emptyList<String>(), scriptSource, LANGUAGE_NAME)
                    // }
                    // val replResult = daemonResult.get()
                    val compiledCode = when (replResult) {
                        is ReplCompileResult.Error -> throw toScriptException(replResult.message, scriptSource, replResult.location)
                        is ReplCompileResult.Incomplete -> throw toScriptException("Incomplete code", scriptSource, CompilerMessageLocation.NO_LOCATION)
                        is ReplCompileResult.CompiledClasses -> replResult
                    }

                    val classesAsBytes = compiledCode.classes.map {
                        KotlinScriptEngineService.NamedClassBytes(it.path.removeSuffix(".class").replace('/', '.'), it.bytes)
                    }

                    val classLoader = ScriptClassLoader(Thread.currentThread().contextClassLoader).apply {
                        classesAsBytes.forEach {
                            addClass(it.className, it.bytes)
                        }
                    }

                    val goodClassNames = (classesAsBytes.map { it.className } + compiledCode.mainClassName).toSet()
                    val scriptClass = classLoader.loadClass(compiledCode.mainClassName)
                    val scriptConstructor = scriptClass.constructors.first()
                    val resultField = scriptClass.getDeclaredField(KotlinScriptEngineService.SCRIPT_RESULT_FIELD_NAME).apply { isAccessible = true }

                } catch (ex: Exception) {
                    throw ScriptException(ex.message ?: "unknown error", ex, emptyList<String>(), scriptSource, KotlinScriptEngineService.LANGUAGE_NAME)
                }
            } finally {
                try {
                    compiler.dispose()
                    //compiler.releaseReplSession(sessionId)
                } catch (ex: RemoteException) {
                    // assuming that communication failed and daemon most likely is already down
                }
            }
        } catch (ex: Exception) {
            if (ex is ScriptException) throw ex
            else throw ScriptException(ex.message ?: "unknown error", ex, emptyList(), scriptSource, KotlinScriptEngineService.LANGUAGE_NAME)
        }
        */
    }
}