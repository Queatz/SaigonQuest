package models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserModelsTest {
    @Test
    fun testIsValidUsername() {
        assertTrue(isValidUsername("user"))
        assertTrue(isValidUsername("user123"))
        assertTrue(isValidUsername("user_name"))
        assertTrue(isValidUsername("USER_NAME"))
        assertTrue(isValidUsername("nguyễn")) // Vietnamese
        assertTrue(isValidUsername("thành_phố")) // Vietnamese with underscore
        assertTrue(isValidUsername("ユーザー")) // Japanese Katakana
        assertTrue(isValidUsername("用户")) // Chinese
        
        assertFalse(isValidUsername(""))
        assertFalse(isValidUsername(" "))
        assertFalse(isValidUsername("user name"))
        assertFalse(isValidUsername("user-name"))
        assertFalse(isValidUsername("user!"))
        assertFalse(isValidUsername("user@"))
        assertFalse(isValidUsername(" user"))
        assertFalse(isValidUsername("user "))
    }
}
