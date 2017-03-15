# keplin

A mix of Kotlin repls (Spark, Flink, Livy, Toree, Zeppelin, ...all coming soon)

### Gradle /Maven

Using `Kotlin 1.1.x`, add this repo:

```
http://dl.bintray.com/jaysonminard/kohesive
```

|artifact|GAV|
|---|---|
|`keplin-core`|`uy.kohesive.keplin:keplin-core:1.0.0-BETA-03`|
|`keplin-maven-resolver`|`uy.kohesive.keplin:keplin-maven-resolver:1.0.0-BETA-03`|
|`keplin-jsr223-kotlin-engine`|`uy.kohesive.keplin:keplin-jsr223-kotlin-engine:1.0.0-BETA-03`|

# Current Modules:

### keplin-core

* a simple Repl class [SimplifiedRepl](./core/src/main/kotlin/uy/kohesive/keplin/kotlin/script/SimplifiedRepl.kt)
* a file based JAR resolver that can be used with `@file:DependsOnJar(fileInDirRepo|fullyQualifiedFile)` and `@file:DirRepository(fullQyalifiedPath)` annotations in the script to load JAR files
* a script definition that can automatically imply imports `KotlinScriptDefinitionEx`

See [unit tests](./core/src/test/kotlin/uy/kohesive/keplin/kotlin/script)

### keplin-maven-resolver

* a maven based resolver (`maven-resolver` dependency) used with `@file:MavenRepository(mavenRepoUrl)` and `@file:DependsOnMaven(mavenGAV)` annotations in the script to load Maven dependencies

See [unit tests](./maven-resolver/src/test/kotlin/uy/kohesive/keplin/kotlin/script/resolver/maven)

### keplin-jsr223-kotlin-engine

* `keplin-kotlin-repl-compilable` JSR223 engine also implementing Compilable and Invocable interfaces
* `keplin-kotlin-repl-eval-only` JSR223 engine that is atomic eval only (compile+eval) and also Invocable interface

See [unit tests](./jsr223-engine/src/test/kotlin/uy/kohesive/keplin/kotlin/script/jsr223)

### Kotlin-Jupyter 

Keplin is used in the related [Kotlin-Jupyter](https://github.com/ligee/kotlin-jupyter) project to provide a basic REPL for Jupyter notebooks or console.