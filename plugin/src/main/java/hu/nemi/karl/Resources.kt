package hu.nemi.karl

import com.squareup.kotlinpoet.*
import java.util.*

private val ANDROID_CONTEXT = ClassName.bestGuess("android.content.Context")
private val RESOURCES = ClassName.bestGuess("Resources")
private val CACHE = ParameterizedTypeName.get(WeakHashMap::class.asClassName(), ANDROID_CONTEXT, RESOURCES)

class Symbol(str: String) {
    private val parts = str.split(' ')
    val type by lazy { parts[1] }
    val name by lazy { parts[2] }
    val id by lazy { Integer.decode(parts[3]) }
}

data class Resource(val type: String, val resourceType: TypeName, val name: String, val initializer: CodeBlock.Builder.(id: String) -> Unit) {
    val className = ClassName.bestGuess(name)

    class TypeBuilder(val resource: Resource) {
        private val typeSpec = TypeSpec.classBuilder(resource.name)
                .addProperty(PropertySpec.builder("context", ANDROID_CONTEXT)
                        .initializer("context")
                        .build())
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("context", ANDROID_CONTEXT)
                        .build())

        fun add(name: String) = apply {
            val id = "R.${resource.type}.$name"

            typeSpec.addProperty(PropertySpec.builder(name, resource.resourceType)
                    .delegate(CodeBlock.builder()
                            .add("lazy { ").apply { resource.initializer(this, id) }.add(" }")
                            .build())
                    .build())
        }

        fun build() = typeSpec.build()
    }
}

class Resources {
    private val resources = mutableMapOf<String, Resource>()
    private val receivers = mutableMapOf<String, String>()

    fun resource(name: String, resourceType: ClassName, initializer: CodeBlock.Builder.(id: String) -> Unit) = apply {
        resources.put(name, Resource(name, resourceType, "${name.capitalize()}s", initializer))
    }

    fun receiver(type: String, context: String) = apply {
        receivers.put(type, context)
    }

    fun fileBuilder(packageName: String, fileName: String) = KotlinFileBuilder(this, packageName, fileName)

    class KotlinFileBuilder(val resources: Resources, packageName: String, fileName: String) {
        private val containers = resources.resources.map { it.key to Resource.TypeBuilder(it.value) }.toMap()

        private val kotlinFile = KotlinFile.builder(packageName, fileName).apply {
            // add Resources container
            with(TypeSpec.classBuilder("Resources")) {
                resources.resources.values.forEach {
                    val property = "${it.type}s"
                    addProperty(PropertySpec.builder(property, it.className)
                            .initializer("%T(context)", it.className)
                            .build())
                }
                primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("context", ANDROID_CONTEXT)
                        .build())
            }.build().run { addType(this) }

            // add ResourcesTls
            TypeSpec.objectBuilder("resourcesTls")
                    .addModifiers(KModifier.PRIVATE)
                    .superclass(ParameterizedTypeName.get(ThreadLocal::class.asClassName(), CACHE))
                    .addFun(FunSpec.builder("initialValue")
                            .addModifiers(KModifier.OVERRIDE)
                            .returns(CACHE)
                            .addCode("%[return %T()\n", CACHE)
                            .addCode("%]")
                            .build())
                    .addFun(FunSpec.builder("getResources")
                            .addParameter("context", ANDROID_CONTEXT)
                            .returns(RESOURCES)
                            .addCode(CodeBlock.builder()
                                    .add("%[return with(get()) {\n")
                                    .add("%>get(context) ?: %T(context).also {\n", RESOURCES)
                                    .add("%>put(context, it)\n")
                                    .add("%<}\n")
                                    .add("%<}\n")
                                    .add("%]")
                                    .build())
                            .build())
                    .build().run { addType(this) }

            // add extension properties to receivers
            resources.receivers.forEach { receiver, context ->
                resources.resources.values.forEach { resource ->
                    val property = "${resource.type}s"
                    addProperty(PropertySpec.builder("$receiver.${property}", resource.className)
                            .getter(FunSpec.getterBuilder()
                                    .addCode(CodeBlock.builder()
                                            .add("%[return resourcesTls.getResources($context).${property}\n")
                                            .add("%]")
                                            .build())
                                    .build())
                            .build())
                }
            }
        }

        fun addSymbol(symbol: Symbol) {
            containers.get(symbol.type)?.add(symbol.name)
        }

        fun build(): KotlinFile {
            containers.values.forEach { kotlinFile.addType(it.build()) }
            return kotlinFile.build()

        }
    }
}