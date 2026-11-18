package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiUsageTracker
import com.example.network.GeminiService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiUsageTrackerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    GeminiUsageTracker.init(context)
    GeminiUsageTracker.resetUsageStats()
  }

  @Test
  fun testTokenEstimationAccuracy() {
    assertEquals(0, GeminiUsageTracker.estimateTokens(0))
    assertEquals(1, GeminiUsageTracker.estimateTokens(2))
    assertEquals(25, GeminiUsageTracker.estimateTokens(100))
    assertEquals(250, GeminiUsageTracker.estimateTokens(1000))
  }

  @Test
  fun testRecordCallUpdatesSummary() {
    val initialRequests = GeminiUsageTracker.usageSummary.value.totalRequests

    GeminiUsageTracker.recordCall(
      feature = "AI Assistant",
      model = "gemini-flash-latest",
      promptLength = 400,
      responseLength = 800,
      latencyMs = 350L,
      isSuccess = true
    )

    val updated = GeminiUsageTracker.usageSummary.value
    assertEquals(initialRequests + 1, updated.totalRequests)
    assertEquals(1, updated.successCount)
    assertEquals(0, updated.failureCount)
    assertTrue(updated.totalPromptTokens >= 100)
    assertTrue(updated.totalResponseTokens >= 200)
    assertTrue(updated.estimatedCreditsConsumed >= 0)
    assertEquals(350L, updated.averageLatencyMs)
    assertTrue((updated.featureBreakdown["AI Assistant"] ?: 0) >= 300)
  }

  @Test
  fun testMultipleFeatureBreakdown() {
    GeminiUsageTracker.recordCall("Originality Check", "gemini-flash-latest", 1000, 500, 600L, true)
    GeminiUsageTracker.recordCall("Writing Tools", "gemini-flash-latest", 200, 200, 300L, true)
    GeminiUsageTracker.recordCall("Originality Check", "gemini-flash-latest", 800, 400, 500L, true)

    val summary = GeminiUsageTracker.usageSummary.value
    assertEquals(3, summary.totalRequests)
    assertTrue((summary.featureBreakdown["Originality Check"] ?: 0) >= 600)
    assertTrue((summary.featureBreakdown["Writing Tools"] ?: 0) >= 100)
  }

  @Test
  fun testFailureRecording() {
    GeminiUsageTracker.recordCall("AI Assistant", "gemini-flash-latest", 100, 0, 150L, false, "Network timeout")

    val summary = GeminiUsageTracker.usageSummary.value
    assertEquals(1, summary.totalRequests)
    assertEquals(0, summary.successCount)
    assertEquals(1, summary.failureCount)
  }

  @Test
  fun testGeminiServiceApiKeyIsConfiguredOutOfBox() {
    // Verifies that Genzii has active Gemini API connectivity configured out of the box
    assertTrue(GeminiService.isApiKeyConfigured())
    val activeKey = GeminiService.getActiveApiKey()
    assertTrue(activeKey.isNotBlank())
    assertTrue(activeKey.length > 10)
  }

  @Test
  fun testResetStatistics() {
    GeminiUsageTracker.recordCall("Originality Check", "gemini-flash-latest", 500, 500, 200L, true)
    assertTrue(GeminiUsageTracker.usageSummary.value.totalRequests > 0)

    GeminiUsageTracker.resetUsageStats()
    assertEquals(0, GeminiUsageTracker.usageSummary.value.totalRequests)
    assertEquals(0, GeminiUsageTracker.usageSummary.value.totalPromptTokens)
    assertEquals(0, GeminiUsageTracker.usageSummary.value.totalResponseTokens)
    assertEquals(0, GeminiUsageTracker.usageSummary.value.estimatedCreditsConsumed)
  }
}
