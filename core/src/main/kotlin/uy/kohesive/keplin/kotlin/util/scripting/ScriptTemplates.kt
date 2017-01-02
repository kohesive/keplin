package uy.kohesive.keplin.kotlin.util.scripting

abstract class ScriptTemplateWithDefaultConstructor()
abstract class ScriptTemplateWithArgs(val args: Array<String>)
abstract class ScriptTemplateWithBindings(val bindings: Map<String, Any?>)