package io.github.archunitlens

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.ArchUnitLensBundle"

/**
 * Localized messages for user-facing ArchUnit Lens UI, inspections, and quick fixes.
 */
object ArchUnitLensBundle {
    private val instance = DynamicBundle(ArchUnitLensBundle::class.java, BUNDLE)

    @JvmStatic
    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String = instance.getMessage(key, *params)
}
