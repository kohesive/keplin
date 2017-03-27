# keplin

A mix of libraries around secure Kotlin scripting and REPLs (_eventually also for projects like Spark, Flink, Livy, Toree, Zeppelin, ..._)

### Gradle /Maven

Using `Kotlin 1.1.x`, add this repo:

```
http://dl.bintray.com/jaysonminard/kohesive
```

|artifact|GAV|
|---|---|
|`keplin-core`|`uy.kohesive.keplin:keplin-core:1.0.0-BETA-08`|
|`keplin-maven-resolver`|`uy.kohesive.keplin:keplin-maven-resolver:1.0.0-BETA-08`|
|`keplin-jsr223-kotlin-engine`|`uy.kohesive.keplin:keplin-jsr223-kotlin-engine:1.0.0-BETA-08`|
|`kotlin-script`|coming soon|
|`elasticsearch-kotlin-plugin`|coming soon https://vimeo.com/apatrida/es-lang-kotlin |
|`chillambda`|`uy.kohesive.chillambda:chillambda:1.0.0-BETA-08`|
|`cuarentena`|`uy.kohesive.cuarentena:cuarentena:1.0.0-BETA-08`|
|`cuarentena-policy`|`uy.kohesive.cuarentena:cuarentena-policy:1.0.0-BETA-08`|
|`cuarentena-painless-policy`|`uy.kohesive.cuarentena:cuarentena-painless-policy:1.0.0-BETA-08`|

TODO:  

* security reviews for `Cuarentena`
* document the modules
* elasticsearch script client, containing Kotlin extensions to the Java client to easily add Lambda-scripts anywhere scripting would be used
* add `Cuarentena` support to Kotlin and Keplin JSR223 engines
* add maven / file resolver `Cuarentena`-like support for whitelisting GAV, and local file patterns

# Current Modules:

### keplin-core

* a simple Repl class [SimplifiedRepl](./core/src/main/kotlin/uy/kohesive/keplin/kotlin/script/SimplifiedRepl.kt)
* a file based JAR resolver that can be used with `@file:DependsOnJar(fileInDirRepo|fullyQualifiedFile)` and `@file:DirRepository(fullQyalifiedPath)` annotations in the script to load JAR files
* a script definition that can automatically imply imports `KotlinScriptDefinitionEx`

See [unit tests](./core/src/test/kotlin/uy/kohesive/keplin/kotlin/script)

### kotlin-script

_coming soon..._

* a set of Kotlin REPLs that can apply `Cuarentena` whitelist policies to any scripts compiled/executed before class loading or execution
* helpers for script compile / execute that can also apply `Cuarentena` whitelist policies before class loading or execution

### Elasticsearch-Kotlin-Plugin

_coming soon..._

Kotlin as a secure scripting language for Elasticsearch.  

* Using the same whitelist security model as Painless scripting to provide secure native code execution
* Support for Kotlin Script as inline text with full `Cuarentena` protection
* Support for Kotlin Lambdas/Functions to be executed as binary scripts with full `Cuarentena` protection

Coming soon:  https://vimeo.com/apatrida/es-lang-kotlin

### Cuarentena (Quarantine)

Verification of JVM classes against whitelist of allowed access to Classes (to the granular method/property level)

* a class validator that uses byte code inspection to determine if the class accesses anything outside of a whitelist
* a serialization/deserialization for classes that limits which classes can be serialized/deserialized in the tree against a whitelist

Subprojects:

* `cuarentena-policy-painless` a set of generated `Cuarentena` policy rules from [Painless Scripting](https://www.elastic.co/guide/en/elasticsearch/reference/master/modules-scripting-painless.html) whitelists
* `cuarentena-policy-painless-kotlin` a set of `Cuarentena` policy rules to extend the Painless set for Kotlin stdlib, and safe lambda serialization

### Chilambda

Remote Lambda/Function execution in a secure manner.

* whitelisted security around what the closure can capture
* whitelisted security allowing only access to specific Class methods and properties
* a binary packaging format for Lambda/Function class data including the serialized Lambda
* a Base64 packaging format

### keplin-maven-resolver

Allowing Maven artifacts to be specified and used in Kotlin scripts.

* a maven based resolver (`maven-resolver` dependency) used with `@file:MavenRepository(mavenRepoUrl)` and `@file:DependsOnMaven(mavenGAV)` annotations in the script to load Maven dependencies

See [unit tests](./maven-resolver/src/test/kotlin/uy/kohesive/keplin/kotlin/script/resolver/maven)

### keplin-jsr223-kotlin-engine

* `keplin-kotlin-repl-compilable` JSR223 engine also implementing Compilable and Invocable interfaces
* `keplin-kotlin-repl-eval-only` JSR223 engine that is atomic eval only (compile+eval) and also Invocable interface

See [unit tests](./jsr223-engine/src/test/kotlin/uy/kohesive/keplin/kotlin/script/jsr223)

### Kotlin-Jupyter 

Keplin is used in the related [Kotlin-Jupyter](https://github.com/ligee/kotlin-jupyter) project to provide a basic REPL for Jupyter notebooks or console.
