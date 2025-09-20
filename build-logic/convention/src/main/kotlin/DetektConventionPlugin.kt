import com.geeksville.mesh.buildlogic.configureDetekt
import com.geeksville.mesh.buildlogic.configureKotlinJvm
import com.geeksville.mesh.buildlogic.libs
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureKotlinJvm()
            apply(plugin = libs.findPlugin("detekt").get().get().pluginId)
            val extension = extensions.getByType<DetektExtension>()
            configureDetekt(extension)
        }
    }
}
