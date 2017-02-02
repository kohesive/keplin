# keplin

A mix of Kotlin repls (Spark, Flink, Livy, Toree, Zeppelin, ...)

# Modules core, maven-resolver and JSR223

This provides the following:

### keplin-core

* a simple Repl class [SimplifiedRepl](./tree/master/core/src/main/kotlin/uy/kohesive/keplin/kotlin/script/SimplifiedRepl.kt)
* a file based JAR resolver that can be used with `@file:DependsOnJar(fileInDirRepo|fullyQualifiedFile)` and `@file:DirRepository(fullQyalifiedPath)` annotations in the script to load JAR files
* a script definition that can automatically imply imports `KotlinScriptDefinitionEx`

See [unit tests](./tree/master/core/src/test/kotlin/uy/kohesive/keplin/kotlin/script)

### keplin-maven-resolver

* a maven based resolver (`maven-resolver` dependency) used with `@file:MavenRepository(mavenRepoUrl)` and `@file:DependsOnMaven(mavenGAV)` annotations in the script to load Maven dependencies

See [unit tests](./tree/master/maven-resolver/src/test/kotlin/uy/kohesive/keplin/kotlin/script/resolver/maven)

### keplin-jsr223-kotlin-engine

* `kotin-repl-compilable` JSR223 engine also implementing Compilable and Invocable interfaces
* `kotin-repl-eval-only` JSR223 engine that is atomic eval only (compile+eval) and also Invocable interface

See [unit tests](./tree/master/jsr223-engine/src/test/kotlin/uy/kohesive/keplin/kotlin/script/jsr223)