package hu.nemi.karl

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

private val CONTEXT_COMPAT = ClassName.bestGuess("android.support.v4.content.ContextCompat")
private val DRAWABLE = ClassName.bestGuess("android.graphics.drawable.Drawable")

class KarPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        plugins.all {
            when (it) {
                is LibraryPlugin -> applyPlugin(extensions.getByType(LibraryExtension::class.java).libraryVariants)
                is AppPlugin -> applyPlugin(extensions.getByType(AppExtension::class.java).applicationVariants)
            }
        }
    }

    private fun applyPlugin(variants: DomainObjectSet<out BaseVariant>) {
        variants.all { variant ->
            variant.outputs.forEach { output ->
                val processResources = output.processResources
                processResources.doLast {

                    val extensionPackage = processResources.packageForR
                    val extensionsPath = File(processResources.sourceOutputDir, "${extensionPackage.replace('.', File.separatorChar)}${File.separatorChar}ResourcesExt.kt")

                    val kotlinFile = with(Resources()) {
                        resource("color", Int::class.asTypeName(), "%T.getColor(context, %L)", CONTEXT_COMPAT)

                        resource("drawable", DRAWABLE, "%T.getDrawable(context, %L)", CONTEXT_COMPAT)

                        resource("string", String::class.asTypeName(), "context.getString(%L)")

                        resource("dimen", Float::class.asTypeName(), "context.resources.getDimension(%L)")

                        resource("int", Int::class.asTypeName(), "context.resources.getInteger(%L)")

                        receiver("android.content.Context", "this")

                        receiver("android.view.View", "context")

                        receiver("android.support.v4.app.Fragment", "context")

                        receiver("android.app.Fragment", "context")

                        apply {
                            BufferedReader(FileReader(processResources.textSymbolOutputFile)).use {
                                it.forEachLine { symbol(it) }
                            }
                        }
                    }.toKotlinFile(extensionPackage, "ResourcesExt.kt")

                    FileWriter(extensionsPath).use {
                        kotlinFile.writeTo(it)
                    }
                }
            }
        }
    }
}
