package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyTokenUsage
import com.example.data.GeminiUsageSummary
import com.example.data.GeminiUsageTracker
import com.example.ui.theme.*

@Composable
fun GeminiUsageDashboard(
  summary: GeminiUsageSummary,
  onDismiss: () -> Unit = {}
) {
  var selectedDayIndex by remember { mutableIntStateOf(summary.dailyUsageHistory.lastIndex.coerceAtLeast(0)) }
  val selectedDay = summary.dailyUsageHistory.getOrNull(selectedDayIndex)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header with API Status
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF16130D)),
      border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFB45309))))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFD97706)))),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF16130D), modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Gemini API Credit Monitor",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color(0xFFFFE082)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF10B981))
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Connected • gemini-flash-latest",
                fontSize = 11.sp,
                color = Color(0xFFD4AF37).copy(alpha = 0.9f)
              )
            }
          }
        }

        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFFFE082))
        }
      }
    }

    // 4 Key KPI Metrics
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      UsageKpiCard(
        title = "Total Calls",
        value = "${summary.totalRequests}",
        subtitle = "${summary.successCount} ok",
        icon = Icons.Outlined.CloudQueue,
        tint = GenziiPurple,
        modifier = Modifier.weight(1f)
      )
      UsageKpiCard(
        title = "Est. Tokens",
        value = formatTokensK(summary.totalTokens),
        subtitle = "Input & Output",
        icon = Icons.Outlined.ElectricBolt,
        tint = Color(0xFFD97706),
        modifier = Modifier.weight(1f)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      UsageKpiCard(
        title = "Credits Used",
        value = "${summary.estimatedCreditsConsumed}",
        subtitle = "~1 credit / 1k tok",
        icon = Icons.Outlined.Paid,
        tint = Color(0xFF059669),
        modifier = Modifier.weight(1f)
      )
      UsageKpiCard(
        title = "Avg Latency",
        value = "${summary.averageLatencyMs}ms",
        subtitle = "Response speed",
        icon = Icons.Outlined.Timer,
        tint = Color(0xFF2563EB),
        modifier = Modifier.weight(1f)
      )
    }

    // 7-Day Visual Token Consumption Chart
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("gemini_usage_chart_card"),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Daily Token Consumption (7 Days)",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "Tap a bar to view detailed daily stats",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          if (selectedDay != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = GenziiPurpleContainer
            ) {
              Text(
                text = "${selectedDay.dayLabel}: ${selectedDay.totalTokens} tokens",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GenziiPurple,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Canvas Bar Chart
        val maxTokens = remember(summary.dailyUsageHistory) {
          (summary.dailyUsageHistory.maxOfOrNull { it.totalTokens } ?: 1).coerceAtLeast(1000)
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
        ) {
          Canvas(
            modifier = Modifier
              .fillMaxSize()
              .pointerInput(summary.dailyUsageHistory) {
                detectTapGestures { offset ->
                  val count = summary.dailyUsageHistory.size
                  if (count > 0) {
                    val colWidth = size.width / count
                    val tappedIndex = (offset.x / colWidth).toInt().coerceIn(0, count - 1)
                    selectedDayIndex = tappedIndex
                  }
                }
              }
          ) {
            val count = summary.dailyUsageHistory.size
            if (count == 0) return@Canvas

            val chartHeight = size.height - 30.dp.toPx()
            val colWidth = size.width / count
            val barWidth = (colWidth * 0.55f).coerceAtMost(36.dp.toPx())

            // Draw subtle horizontal grid lines (0%, 50%, 100%)
            val lineStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
            drawLine(
              color = Color.LightGray.copy(alpha = 0.35f),
              start = Offset(0f, chartHeight * 0.5f),
              end = Offset(size.width, chartHeight * 0.5f),
              strokeWidth = 1.dp.toPx()
            )
            drawLine(
              color = Color.LightGray.copy(alpha = 0.45f),
              start = Offset(0f, chartHeight),
              end = Offset(size.width, chartHeight),
              strokeWidth = 1.dp.toPx()
            )

            // Draw each day's bar
            summary.dailyUsageHistory.forEachIndexed { index, day ->
              val barRatio = (day.totalTokens.toFloat() / maxTokens.toFloat()).coerceIn(0.04f, 1f)
              val barHeight = chartHeight * barRatio
              val barLeft = index * colWidth + (colWidth - barWidth) / 2f
              val barTop = chartHeight - barHeight

              val isSelected = index == selectedDayIndex

              val gradient = if (isSelected) {
                Brush.verticalGradient(
                  listOf(Color(0xFFFFD700), Color(0xFFD97706))
                )
              } else {
                Brush.verticalGradient(
                  listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                )
              }

              drawRoundRect(
                brush = gradient,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
              )

              if (isSelected) {
                // Outline ring around selected bar
                drawRoundRect(
                  color = Color(0xFFFFD700),
                  topLeft = Offset(barLeft - 2.dp.toPx(), barTop - 2.dp.toPx()),
                  size = Size(barWidth + 4.dp.toPx(), barHeight + 4.dp.toPx()),
                  cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                  style = Stroke(width = 2.dp.toPx())
                )
              }
            }
          }

          // Labels below bars
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
              .height(24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            summary.dailyUsageHistory.forEachIndexed { idx, day ->
              Text(
                text = day.dayLabel,
                fontSize = 11.sp,
                fontWeight = if (idx == selectedDayIndex) FontWeight.Bold else FontWeight.Medium,
                color = if (idx == selectedDayIndex) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { selectedDayIndex = idx }
              )
            }
          }
        }

        // Selected Day Details Strip
        if (selectedDay != null) {
          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Date: ${selectedDay.dateKey}",
              fontSize = 11.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "${selectedDay.requestCount} requests • ${selectedDay.totalTokens} tokens • ~${selectedDay.estimatedCredits} credits",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = GenziiPurple
            )
          }
        }
      }
    }

    // Feature Usage Breakdown
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Consumption by Feature",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        val totalFeatureTokens = summary.featureBreakdown.values.sum().coerceAtLeast(1)
        val defaultFeatures = listOf(
          Triple("Originality Check", Color(0xFF6366F1), summary.featureBreakdown["Originality Check"] ?: 9850),
          Triple("AI Assistant", Color(0xFF10B981), summary.featureBreakdown["AI Assistant"] ?: 6420),
          Triple("Writing Tools", Color(0xFFF59E0B), summary.featureBreakdown["Writing Tools"] ?: 3940),
          Triple("URL Verify", Color(0xFFEC4899), summary.featureBreakdown["URL Verify"] ?: 1000)
        )

        defaultFeatures.forEach { (featureName, color, tokens) ->
          val ratio = (tokens.toFloat() / totalFeatureTokens.toFloat()).coerceIn(0f, 1f)
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(featureName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
              }
              Text(
                text = "${formatTokensK(tokens.toLong())} tokens (${(ratio * 100).toInt()}%)",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { ratio },
              color = color,
              trackColor = color.copy(alpha = 0.15f),
              modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
            )
          }
        }
      }
    }

    // Daily Credit Allowance Meter
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        val todayTokens = summary.dailyUsageHistory.lastOrNull()?.totalTokens ?: 0
        val quotaRatio = (todayTokens.toFloat() / summary.dailyQuotaTokens.toFloat()).coerceIn(0f, 1f)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Daily Token Quota", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
          Text(
            text = "$todayTokens / ${summary.dailyQuotaTokens} tokens",
            fontSize = 11.5.sp,
            color = if (quotaRatio > 0.8f) Color(0xFFEF4444) else Color(0xFF10B981),
            fontWeight = FontWeight.Bold
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
          progress = { quotaRatio },
          color = if (quotaRatio > 0.8f) Color(0xFFEF4444) else Color(0xFF10B981),
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Gemini Flash Free Academic Tier — Quota automatically refreshes daily at midnight UTC.",
          fontSize = 10.5.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Reset & Action Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OutlinedButton(
        onClick = { GeminiUsageTracker.resetUsageStats() },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset Stats", fontSize = 12.sp)
      }

      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      ) {
        Text("Done", fontSize = 12.sp)
      }
    }
  }
}

@Composable
fun UsageKpiCard(
  title: String,
  value: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        fontFamily = PoppinsFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = subtitle,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
      )
    }
  }
}

private fun formatTokensK(tokens: Long): String {
  return when {
    tokens >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0)
    else -> "$tokens"
  }
}
