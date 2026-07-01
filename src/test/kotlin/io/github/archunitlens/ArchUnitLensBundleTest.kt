package io.github.archunitlens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.ResourceBundle

class ArchUnitLensBundleTest {
    @Test
    fun resolvesEnglishAndKoreanUserFacingMessages() {
        val noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT)
        val english = ResourceBundle.getBundle("messages.ArchUnitLensBundle", Locale.ENGLISH, noFallback)
        val korean = ResourceBundle.getBundle("messages.ArchUnitLensBundle", Locale.KOREAN, noFallback)

        assertEquals("Go to ArchUnit rule", english.getString("quickfix.goto.family"))
        assertEquals("ArchUnit rule로 이동", korean.getString("quickfix.goto.family"))
        assertEquals("Refresh", english.getString("toolwindow.refresh"))
        assertEquals("새로고침", korean.getString("toolwindow.refresh"))
        assertEquals("ArchUnit Lens", english.getString("settings.display.name"))
        assertEquals("ArchUnit Lens", korean.getString("settings.display.name"))
        assertEquals("Open source", english.getString("toolwindow.openSource"))
        assertEquals("소스 열기", korean.getString("toolwindow.openSource"))
    }
}
