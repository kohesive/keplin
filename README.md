[![Kotlin](https://img.shields.io/badge/kotlin-1.2.21-blue.svg)](http://kotlinlang.org)  [![Maven Central](https://img.shields.io/maven-central/v/uy.kohesive.keplin/keplin-core.svg)](https://mvnrepository.com/artifact/uy.kohesive.keplin) [![CircleCI branch](https://img.shields.io/circleci/project/kohesive/keplin/master.svg)](https://circleci.com/gh/kohesive/keplin/tree/master) [![Issues](https://img.shields.io/github/issues/kohesive/keplin.svg)](https://github.com/kohesive/keplin/issues?q=is%3Aopen) [![DUB](https://img.shields.io/dub/l/vibe-d.svg)](https://github.com/kohesive/keplin/blob/master/LICENSE) [![Kotlin Slack](https://img.shields.io/badge/chat-kotlin%20slack%20%23kohesive-orange.svg)](http://kotlinslackin.herokuapp.com)

# keplin

Extended Kotlin Scripting

### Gradle /Maven

With `Kotlin 1.2.+` in your classpath, add:

|artifact|GAV|
|---|---|
|`keplin-core`|`uy.kohesive.keplin:keplin-core`|
|`keplin-maven-resolver`|`uy.kohesive.keplin:keplin-maven-resolver`|
|`keplin-jsr223-kotlin-engine`|`uy.kohesive.keplin:keplin-jsr223-kotlin-engine`|

TODO:  

* document the modules
* add maven / file resolver Cuarentena-like support for whitelisting GAV, and local file patterns
* add Cuarentena support for securing each script engine.

# Current Modules:

### keplin-core

* a simple Repl class [SimplifiedRepl](./keplin-core/src/main/kotlin/uy/kohesive/keplin/kotlin/script/SimplifiedRepl.kt)
* a file based JAR resolver that can be used with `@file:DependsOnJar(fileInDirRepo|fullyQualifiedFile)` and `@file:DirRepository(fullQualifiedPath)` annotations in the script to load JAR files
* a script definition that can automatically imply imports `KotlinScriptDefinitionEx`

See [unit tests](./keplin-core/src/test/kotlin/uy/kohesive/keplin/kotlin/script)

### keplin-maven-resolver

Allowing Maven artifacts to be specified and used in Kotlin scripts.

* a maven based resolver (`maven-resolver` dependency) used with `@file:MavenRepository(mavenRepoUrl)` and `@file:DependsOnMaven(mavenGAV)` annotations in the script to load Maven dependencies

See [unit tests](./keplin-maven-resolver/src/test/kotlin/uy/kohesive/keplin/kotlin/script/resolver/maven)

### keplin-jsr223-kotlin-engine

* `keplin-kotlin-repl-compilable` JSR223 engine also implementing Compilable and Invocable interfaces
* `keplin-kotlin-repl-eval-only` JSR223 engine that is atomic eval only (compile+eval) and also Invocable interface

See [unit tests](./keplin-jsr223-kotlin-engine/src/test/kotlin/uy/kohesive/keplin/kotlin/script/jsr223)

### Kotlin-Jupyter 

Keplin is used in the related [Kotlin-Jupyter](https://github.com/ligee/kotlin-jupyter) project to provide a basic REPL for Jupyter notebooks or console.
