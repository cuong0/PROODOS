package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPasswordValidation() {
    val invalidShort = com.example.ui.AccountAuthManager.validatePassword("Pass1")
    assertFalse(invalidShort.hasMinLength)
    assertFalse(invalidShort.isValid)

    val invalidNoUpper = com.example.ui.AccountAuthManager.validatePassword("password123")
    assertFalse(invalidNoUpper.hasUppercase)
    assertFalse(invalidNoUpper.isValid)

    val invalidNoDigit = com.example.ui.AccountAuthManager.validatePassword("PasswordABC")
    assertFalse(invalidNoDigit.hasDigit)
    assertFalse(invalidNoDigit.isValid)

    val valid = com.example.ui.AccountAuthManager.validatePassword("Password123")
    assertTrue(valid.hasMinLength)
    assertTrue(valid.hasUppercase)
    assertTrue(valid.hasDigit)
    assertTrue(valid.isValid)
  }

  @Test
  fun testPasswordHashingConsistency() {
    val salt = "testSalt123"
    val hash1 = com.example.ui.AccountAuthManager.hashPassword("Password123", salt)
    val hash2 = com.example.ui.AccountAuthManager.hashPassword("Password123", salt)
    val hashDifferent = com.example.ui.AccountAuthManager.hashPassword("WrongPassword1", salt)

    assertEquals(hash1, hash2)
    assertNotEquals(hash1, hashDifferent)
  }
}
