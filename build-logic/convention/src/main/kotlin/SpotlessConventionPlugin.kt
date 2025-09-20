import com.diffplug.gradle.spotless.SpotlessExtension
import com.geeksville.mesh.buildlogic.configureKotlinJvm
import com.geeksville.mesh.buildlogic.configureSpotless
import com.geeksville.mesh.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureKotlinJvm()
            apply(plugin = libs.findPlugin("spotless").get().get().pluginId)
            val extension = extensions.getByType<SpotlessExtension>()
            configureSpotless(extension)
        }
    }
}
