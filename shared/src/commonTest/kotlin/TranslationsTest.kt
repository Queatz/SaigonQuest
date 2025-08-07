import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationsTest {

    /**
     * This test file tests the `str` function of the `Translations` object.
     *
     * The `Translations.str` function returns the localized translation of a given key
     * based on the currently set language. If no translation is found for the key, or
     * if the key does not exist, the function returns the key itself.
     */

    @Test
    fun `test str returns translation for existing key in set language`() {
        // Arrange
        Translations.setLanguage("vi")

        // Act
        val result = Translations.str("Close")

        // Assert
        assertEquals("Đóng", result)
    }

    @Test
    fun `test str falls back to default key when language-specific translation is unavailable`() {
        // Arrange
        Translations.setLanguage("vi")

        // Act
        val result = Translations.str("NonExistingKey")

        // Assert
        assertEquals("NonExistingKey", result)
    }

    @Test
    fun `test str returns default key when no language is set`() {
        // Arrange
        Translations.setLanguage(null)

        // Act
        val result = Translations.str("Close")

        // Assert
        assertEquals("Close", result)
    }

    @Test
    fun `test str returns the correct translation for multiple calls with changed language`() {
        // Arrange
        Translations.setLanguage("vi")
        val firstCall = Translations.str("Open")

        Translations.setLanguage(null)
        val secondCall = Translations.str("Open")

        // Act & Assert
        assertEquals("Mở", firstCall)
        assertEquals("Open", secondCall)
    }

    @Test
    fun `test str returns the correct behavior for untranslated keys`() {
        // Arrange
        Translations.setLanguage("vi")

        // Act
        val result = Translations.str("NonTranslatedKey")

        // Assert
        assertEquals("NonTranslatedKey", result)
    }
}
