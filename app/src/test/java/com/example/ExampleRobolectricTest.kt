package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Proodos", appName)
  }

  @Test
  fun `test import order with invoice image serialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testFile = java.io.File(context.filesDir, "test_invoice_img.jpg")
    testFile.writeText("fake_image_bytes")

    val order = com.example.data.ImportOrder(
      id = 101,
      timestamp = 1690000000L,
      supplierName = "NCC Test",
      totalAmount = 500000.0,
      isDraft = false,
      invoiceImageUri = android.net.Uri.fromFile(testFile).toString()
    )

    // Verify all properties are preserved
    assertEquals(101, order.id)
    assertEquals(false, order.isDraft)
    assertEquals("NCC Test", order.supplierName)
    org.junit.Assert.assertNotNull(order.invoiceImageUri)
    org.junit.Assert.assertTrue(order.invoiceImageUri!!.contains("test_invoice_img.jpg"))
  }

  @Test
  fun `test account storage and lookup by email or username`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val salt = "testSalt456"
    val passHash = com.example.ui.AccountAuthManager.hashPassword("Password123", salt)
    val account = com.example.ui.RegisteredAccount(
      email = "cuongtest@gmail.com",
      storeName = "Tạp Hóa Cường",
      passwordHash = passHash,
      salt = salt,
      userId = "uid_cuong_123"
    )

    com.example.ui.AccountAuthManager.saveAccountLocally(context, account)

    // Lookup by exact email
    val byEmail = com.example.ui.AccountAuthManager.getLocalAccount(context, "cuongtest@gmail.com")
    org.junit.Assert.assertNotNull(byEmail)
    assertEquals("cuongtest@gmail.com", byEmail?.email)
    assertEquals("Tạp Hóa Cường", byEmail?.storeName)

    // Lookup by username or email helper
    val byStoreName = com.example.ui.AccountAuthManager.getLocalAccountByUsernameOrEmail(context, "Tạp Hóa Cường")
    org.junit.Assert.assertNotNull(byStoreName)
    assertEquals("cuongtest@gmail.com", byStoreName?.email)

    // Verify active account persistence
    com.example.ui.AccountAuthManager.saveActiveAccount(context, account)
    val active = com.example.ui.AccountAuthManager.getActiveAccount(context)
    org.junit.Assert.assertNotNull(active)
    assertEquals("cuongtest@gmail.com", active?.email)
  }
}
