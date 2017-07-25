package hu.nemi.karl

import com.squareup.kotlinpoet.*
import java.util.*

private val ANDROID_CONTEXT = ClassName.bestGuess("android.content.Context")
private val RESOURCES = ClassName.bestGuess("Resources")
private val CACHE = ParameterizedTypeName.get(WeakHashMap::class.asClassName(), ANDROID_CONTEXT, RESOURCES)

private class Symbol(str: String) {
    private val parts = str.split(' ')
    val type by lazy { parts[1] }
    val name by lazy { parts[2] }
    val id by lazy { Integer.decode(parts[3]) }
}

private data class Receiver(val receiverClass: String, val contextAccessor: String)

private data class ResourceType(val type: String, val resourceType: TypeName, val name: String, val initializer: String, val initializerArgs : Array<out Any> = emptyArray<Any>()) {
    val className = ClassName.bestGuess(name)

    private val typeSpecBuilder = TypeSpec.classBuilder(name)
            .addProperty(PropertySpec.builder("context", ANDROID_CONTEXT)
                    .initializer("context")
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("context", ANDROID_CONTEXT)
                    .build())

    fun toTypeSpec(propertyNames: Iterable<String>) = with(typeSpecBuilder) {
        val properties = propertyNames.map { propertyName ->
            PropertySpec.builder(propertyName, resourceType)
                    .delegate(CodeBlock.builder()
                            .add("lazy { $initializer }", *initializerArgs, "R.$type.$propertyName")
                            .build())
                    .build()
        }
        typeSpecBuilder.addProperties(properties).build()
    }
}

class Resources {
    private val symbols = mutableSetOf<Symbol>()
    private val receivers = mutableSetOf<Receiver>()
    private val resourceTypes = mutableSetOf<ResourceType>()

    fun resource(name: String, resourceType: ClassName, initializer: String, vararg args : Any) = apply {
        resourceTypes.add(ResourceType(name, resourceType, "${name.capitalize()}s", initializer, args))
    }

    fun receiver(type: String, context: String) = apply {
        receivers.add(Receiver(type, context))
    }

    fun symbol(symbol: String) = apply {
        symbols.add(Symbol(symbol))
    }

    fun toKotlinFile(packageName: String, fileName: String): KotlinFile {
        val kotlinFile = KotlinFile.builder(packageName, fileName)

        with(TypeSpec.classBuilder("Resources")) {
            resourceTypes.forEach { resourceType ->
                val property = "${resourceType.type}s"
                addProperty(PropertySpec.builder(property, resourceType.className)
                        .initializer("%T(context)", resourceType.className)
                        .build())

                kotlinFile.addType(resourceType.toTypeSpec(symbols.filter { it.type == resourceType.type }.map(Symbol::name)))

                receivers.forEach { (receiverClass, contextAccessor) ->

                    kotlinFile.addProperty(PropertySpec.builder("$receiverClass.$property", resourceType.className)
                            .getter(FunSpec.getterBuilder()
                                    .addCode(CodeBlock.builder()
                                            .add("%[return resourcesTls.getResources($contextAccessor).$property\n")
                                            .add("%]")
                                            .build())
                                    .build())
                            .build())
                }
            }

            primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("context", ANDROID_CONTEXT)
                    .build())

        }.build().run { kotlinFile.addType(this) }

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
                .build().run { kotlinFile.addType(this) }

        return kotlinFile.build()
    }
}
