package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LsrChecklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    assertEquals("HSE LSR", appName)
  }

  @Test
  fun `verify checklist items are loaded`() {
    val items = LsrChecklist.items
    assertTrue(items.isNotEmpty())
    assertEquals(42, items.size)
  }
}

