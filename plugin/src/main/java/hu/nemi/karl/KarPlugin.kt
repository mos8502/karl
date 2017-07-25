package hu.nemi.karl

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class KarPlugin : Plugin<Project> {
    override fun apply(project : Project) = with(project) {
        plugins.all {
            when(it) {
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

                    val symbols = processResources.textSymbolOutputFile
                    val extensionPackage = processResources.packageForR
                    val extensionsPath = File(processResources.sourceOutputDir, "${extensionPackage.replace('.', File.separatorChar)}${File.separatorChar}ResourcesExt.kt")
                    generateResourcesExtension(packageName = extensionPackage,
                            path = extensionsPath,
                            symbolsFile = symbols)

                }
            }
        }
    }
}
