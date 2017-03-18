# keplin

A mix of libraries around secure Kotlin scripting and REPLs (_eventually for projects like Spark, Flink, Livy, Toree, Zeppelin, ..._)

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
|`kotlin-script`|coming soon|
|`elasticsearch-kotlin-plugin`|coming soon https://vimeo.com/apatrida/es-lang-kotlin |
|`keplin-chilambda`|coming soon|
|`keplin-quarantine`|coming soon|
|`keplin-quarantine-policy-painless`|coming soon|
|`keplin-quarantine-policy-painless-kotlin`|coming soon|

TODO:  

* finish breaking out the new "coming soon" modules
* security reviews on `keplin-quarantine`
* document the modules
* elasticsearch script client, containing Kotlin extensions to the Java client to easily add Lambda-scripts anywhere scripting would be used
* add `keplin-quarantine` support to Kotlin and Keplin JSR223 engines
* add maven / file resolver quarantine support for whitelisting GAV, and local file patterns

# Current Modules:

### keplin-core

* a simple Repl class [SimplifiedRepl](./core/src/main/kotlin/uy/kohesive/keplin/kotlin/script/SimplifiedRepl.kt)
* a file based JAR resolver that can be used with `@file:DependsOnJar(fileInDirRepo|fullyQualifiedFile)` and `@file:DirRepository(fullQyalifiedPath)` annotations in the script to load JAR files
* a script definition that can automatically imply imports `KotlinScriptDefinitionEx`

See [unit tests](./core/src/test/kotlin/uy/kohesive/keplin/kotlin/script)

### kotlin-script

* a set of Kotlin REPLs that can apply `keplin-quarantine` whitelist to any scripts compiled/executed before class loading or execution
* helpers for script compile / execute that can also apply `keplin-quarantine` whitelist before class loading or execution

### Elasticsearch-Kotlin-Plugin

Kotlin as a secure scripting language for Elasticsearch.  Using the same whitelist security model as Painless scripting.  Combined with allowing you to use client native Lambda and Functions as binary scripts from within your client code, sent to the cluster for execution with the same protection and security of scripts.  Coming soon:  https://vimeo.com/apatrida/es-lang-kotlin

### keplin-quarantine

Verification of JVM classes against whitelist of allowed access to Classes (to the granular method/property level)

* a class validator that uses byte code inspection to determine if the class accesses anything outside of a whitelist
* a serialization/deserialization for classes that limits which classes can be serialized/deserialized in the tree against a whitelist

Subprojects:

* `keplin-quarantine-policy-painless` a set of generated `keplin-quarantine` policy rules from [Painless Scripting](https://www.elastic.co/guide/en/elasticsearch/reference/master/modules-scripting-painless.html) whitelists
* `keplin-quarantine-policy-painless-kotlin` a set of `keplin-quarantine` policy rules to extend the Painless set for Kotlin stdlib, and safe lambda serialization

### keplin-chilambda

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
