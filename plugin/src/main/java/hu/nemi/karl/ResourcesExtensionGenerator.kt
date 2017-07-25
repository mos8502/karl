package hu.nemi.karl

import com.squareup.kotlinpoet.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*

private object Names {
    val resourcesTls = "resourcesTls"
}

private object Types {
    val colors = ClassName.bestGuess("Colors")
    val drawables = ClassName.bestGuess("Drawables")
    val strings = ClassName.bestGuess("Strings")
    val dimens = ClassName.bestGuess("Dimens")
    val ints = ClassName.bestGuess("Ints")
    val resources = ClassName.bestGuess("Resources")
    val context = ClassName.bestGuess("android.content.Context")

    val contextCompat = ClassName.bestGuess("android.support.v4.content.ContextCompat")
    val drawable = ClassName.bestGuess("android.graphics.drawable.Drawable")
    val cache = ParameterizedTypeName.get(WeakHashMap::class.asClassName(), context, resources)
}

private val Resources = TypeSpec.classBuilder("Resources")
        .addModifiers(KModifier.PRIVATE, KModifier.DATA)
        .addProperty(PropertySpec.builder("colors", Types.colors)
                .initializer("colors")
                .build())
        .addProperty(PropertySpec.builder("drawables", Types.drawables)
                .initializer("drawables")
                .build())
        .addProperty(PropertySpec.builder("strings", Types.strings)
                .initializer("strings")
                .build())
        .addProperty(PropertySpec.builder("dimens", Types.dimens)
                .initializer("dimens")
                .build())
        .addProperty(PropertySpec.builder("ints", Types.ints)
                .initializer("ints")
                .build())
        .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("colors", Types.colors)
                .addParameter("drawables", Types.drawables)
                .addParameter("strings", Types.strings)
                .addParameter("dimens", Types.dimens)
                .addParameter("ints", Types.ints)
                .build())
        .build()

private val ResourcesTls = TypeSpec.objectBuilder(Names.resourcesTls)
        .addModifiers(KModifier.PRIVATE)
        .superclass(ParameterizedTypeName.get(ThreadLocal::class.asClassName(), Types.cache))
        .addFun(FunSpec.builder("initialValue")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Types.cache)
                .addCode("%[return %T()\n", Types.cache)
                .addCode("%]")
                .build())
        .addFun(FunSpec.builder("getResources")
                .addParameter("context", Types.context)
                .returns(Types.resources)
                .addCode(CodeBlock.builder()
                        .add("%[return with(get()) {\n")
                        .add("%>get(context) ?: %T(%T(context), %T(context), %T(context), %T(context), %T(context)).also {\n",
                                Types.resources, Types.colors, Types.drawables, Types.strings, Types.dimens, Types.ints)
                        .add("%>put(context, it)\n")
                        .add("%<}\n")
                        .add("%<}\n")
                        .add("%]")
                        .build())
                .build())
        .build()

private val noOpAdd: (Symbol) -> Unit = { }

private class Symbol(str: String) {
    private val parts = str.split(' ')

    val type by lazy { parts[1] }
    val name by lazy { parts[2] }
    val id by lazy { Integer.decode(parts[3]) }
}

private sealed class ResourceTypeBuilder(name: String, val type: TypeName, val getter: CodeBlock.Builder.(id: String) -> Unit) {
    private val typeBuilder = TypeSpec.classBuilder(name)
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("context", Types.context)
                    .build())
            .addProperty(PropertySpec.builder("context", Types.context)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("context")
                    .build())

    fun add(symbol: Symbol) = apply {
        typeBuilder.addProperty(PropertySpec.builder(symbol.name, type)
                .delegate(CodeBlock.builder()
                        .add("lazy { ").apply { getter("R.${symbol.type}.${symbol.name}") }.add(" }")
                        .build())
                .build())
    }

    fun build() = typeBuilder.build()

    class ColorsBuilder : ResourceTypeBuilder("Colors", Int::class.asTypeName(), {
        add("%T.getColor(context, $it)", Types.contextCompat)
    })

    class DrawablesBuilder : ResourceTypeBuilder("Drawables", Types.drawable, {
        add("%T.getDrawable(context, $it)", Types.contextCompat)
    })

    class StringsBuilder : ResourceTypeBuilder("Strings", String::class.asTypeName(), {
        add("context.getString($it)")
    })

    class DimensBuilder : ResourceTypeBuilder("Dimens", Float::class.asTypeName(), {
        add("context.resources.getDimension($it)")
    })

    class IntsBuilder : ResourceTypeBuilder("Ints", Int::class.asTypeName(), {
        add("context.resources.getInteger($it)")
    })
}

private fun KotlinFile.Builder.addResourceProperty(receiver: String, context: String, propertyName: String, propertyType: ClassName) = apply {
    addProperty(PropertySpec.builder("$receiver.$propertyName", propertyType)
            .getter(FunSpec.getterBuilder()
                    .addCode(CodeBlock.builder()
                            .add("%[return ${Names.resourcesTls}.getResources($context).${propertyName}\n")
                            .add("%]")
                            .build())
                    .build())
            .build())
}

internal fun generateResourcesExtension(packageName: String, path: File, symbolsFile: File) {
    val colors = ResourceTypeBuilder.ColorsBuilder()
    val drawables = ResourceTypeBuilder.DrawablesBuilder()
    val strings = ResourceTypeBuilder.StringsBuilder()
    val dimens = ResourceTypeBuilder.DimensBuilder()
    val ints = ResourceTypeBuilder.IntsBuilder()

    BufferedReader(FileReader(symbolsFile)).use { symbols ->
        symbols.forEachLine { line ->
            val symbol = Symbol(line)
            when (symbol.type) {
                "color" -> colors::add
                "drawable" -> drawables::add
                "string" -> strings::add
                "dimen" -> dimens::add
                "integer" -> ints::add
                else -> noOpAdd
            }(symbol)
        }
    }

    val file = KotlinFile.builder(packageName, path.name)
            .addType(colors.build())
            .addType(drawables.build())
            .addType(strings.build())
            .addType(dimens.build())
            .addType(ints.build())
            .addType(Resources)
            .addType(ResourcesTls)

            .addResourceProperty("Context", "this", "colors", Types.colors)
            .addResourceProperty("android.view.View", "context", "colors", Types.colors)
            .addResourceProperty("android.support.v4.app.Fragment", "context", "colors", Types.colors)
            .addResourceProperty("android.app.Fragment", "context", "colors", Types.colors)

            .addResourceProperty("Context", "this", "drawables", Types.drawables)
            .addResourceProperty("android.view.View", "context", "drawables", Types.drawables)
            .addResourceProperty("android.support.v4.app.Fragment", "context", "drawables", Types.drawables)
            .addResourceProperty("android.app.Fragment", "context", "drawables", Types.drawables)

            .addResourceProperty("Context", "this", "strings", Types.strings)
            .addResourceProperty("android.view.View", "context", "strings", Types.strings)
            .addResourceProperty("android.support.v4.app.Fragment", "context", "strings", Types.strings)
            .addResourceProperty("android.app.Fragment", "context", "strings", Types.strings)

            .addResourceProperty("Context", "this", "dimens", Types.dimens)
            .addResourceProperty("android.view.View", "context", "dimens", Types.dimens)
            .addResourceProperty("android.support.v4.app.Fragment", "context", "dimens", Types.dimens)
            .addResourceProperty("android.app.Fragment", "context", "dimens", Types.dimens)

            .addResourceProperty("Context", "this", "ints", Types.ints)
            .addResourceProperty("android.view.View", "context", "ints", Types.ints)
            .addResourceProperty("android.support.v4.app.Fragment", "context", "ints", Types.ints)
            .addResourceProperty("android.app.Fragment", "context", "ints", Types.ints)
            .build()

    FileWriter(path).use {
        file.writeTo(it)
    }
}
