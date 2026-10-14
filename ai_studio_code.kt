// ============================================================================
// 1. AndroidManifest.xml (/app/src/main/AndroidManifest.xml)
// ============================================================================
/*
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApplication">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyApplication">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name="com.example.data.BatterySaverService"
            android:exported="false" />
    </application>

</manifest>
*/

// ============================================================================
// 2. MainActivity.kt (/app/src/main/java/com/example/MainActivity.kt)
// ============================================================================
package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AodLiveScreen
import com.example.ui.screens.AodStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AodViewModel
import com.example.ui.viewmodel.AppScreen

class MainActivity : ComponentActivity() {
  private val viewModel: AodViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // 1. Keep screen on while AOD is active
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // 2. Lock screen presentation (Show when locked & turn screen on)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }

    // 3. Start Battery Saver background monitor service
    com.example.data.BatterySaverService.startService(this)

    setContent {
      MyApplicationTheme(darkTheme = true) {
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
        val config by viewModel.config.collectAsStateWithLifecycle()
        val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
        val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
        val batteryInfo by viewModel.batteryInfo.collectAsStateWithLifecycle()
        val burnInTrigger by viewModel.burnInShiftTrigger.collectAsStateWithLifecycle()
        val isAlertActive by viewModel.isAlertActive.collectAsStateWithLifecycle()
        val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()

        // Handle immersive fullscreen & brightness changes
        LaunchedEffect(currentScreen, config.screenBrightness) {
          val insetsController = WindowCompat.getInsetsController(window, window.decorView)
          insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

          val layoutParams = window.attributes

          if (currentScreen == AppScreen.LIVE_AOD) {
            // Hide status and navigation bars for full-bleed AMOLED
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            layoutParams.screenBrightness = config.screenBrightness.coerceIn(0.02f, 1.0f)
          } else {
            // Restore normal system bars in Studio mode
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
          }
          window.attributes = layoutParams
        }

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.Black
        ) {
          Crossfade(
            targetState = currentScreen,
            animationSpec = tween(durationMillis = 350),
            label = "ScreenTransition"
          ) { screen ->
            when (screen) {
              AppScreen.LIVE_AOD -> {
                AodLiveScreen(
                  time = currentTime,
                  date = currentDate,
                  batteryInfo = batteryInfo,
                  config = config,
                  burnInTrigger = burnInTrigger,
                  isAlertActive = isAlertActive,
                  onExit = { viewModel.navigateToStudio() }
                )
              }
              AppScreen.STUDIO -> {
                AodStudioScreen(
                  time = currentTime,
                  date = currentDate,
                  batteryInfo = batteryInfo,
                  config = config,
                  customPresets = customPresets,
                  isAlertActive = isAlertActive,
                  onUpdateConfig = { reducer -> viewModel.updateConfig(reducer) },
                  onApplyPreset = { preset -> viewModel.applyPreset(preset) },
                  onSaveCustomPreset = { name -> viewModel.saveCurrentAsCustomPreset(name) },
                  onTriggerBurnInTest = { viewModel.triggerBurnInShiftTest() },
                  onTriggerAlertSimulation = { viewModel.triggerAlertSimulation() },
                  onReorderWidget = { id, up -> viewModel.reorderWidget(id, up) },
                  onLaunchLiveAod = { viewModel.navigateToLiveAod() }
                )
              }
            }
          }
        }
      }
    }
  }
}

// ============================================================================
// 3. AodConfig.kt (/app/src/main/java/com/example/data/AodConfig.kt)
// ============================================================================
package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

enum class ClockFaceType(val title: String, val subtitle: String) {
  MINIMAL_DIGITAL("Minimal Digital", "Clean typography with seconds & 12/24h"),
  RETRO_FLIP("Retro Flip Clock", "Split-flap mechanical desk clock"),
  ANALOG_DIAL("Analog Dial", "Canvas-rendered dial with ticks & hands"),
  NOTHING_DOT_MATRIX("Nothing Dot-Matrix", "Raw techy dot-matrix aesthetics"),
  CYBERPUNK_NEON("Cyberpunk Neon", "Glowing outlined neon digits"),
  WORD_CLOCK("Word Clock", "Readable spelt out conversational text"),
  DUAL_TIME_ZONE("World Clock", "Home time + secondary world city")
}

enum class EdgeBorderStyle(val title: String) {
  SOLID_NEON("Solid Neon"),
  PULSATING_GLOW("Pulsating Glow"),
  ROTATING_LASERS("Rotating Lasers"),
  GRADIENT_STREAM("Gradient Stream")
}

enum class EdgeColorMode(val title: String) {
  SINGLE_COLOR("Single Color"),
  DUOTONE_PULSE("Duotone"),
  RAINBOW_CYCLE("Rainbow RGB")
}

enum class EdgeTriggerMode(val title: String) {
  ALWAYS_ON("Always On"),
  NOTIFICATION_PULSE("Pulse on Alert")
}

enum class BatteryIndicatorStyle(val title: String) {
  CIRCULAR_RING("Circular Ring"),
  HORIZONTAL_BAR("Gauge Bar"),
  PERCENTAGE_TEXT("Text Only")
}

enum class DateFormatOption(val title: String, val pattern: String) {
  SHORT_DAY_DATE("Tue, 13 Oct", "EEE, d MMM"),
  NUMERIC_DATE("13/10/2026", "dd/MM/yyyy"),
  FULL_DATE("October 13, 2026", "MMMM d, yyyy"),
  ISO_DATE("2026-10-13", "yyyy-MM-dd")
}

enum class FontFamilyOption(val title: String, val fontFamily: FontFamily) {
  DEFAULT("System Default", FontFamily.Default),
  MONOSPACE("Monospace", FontFamily.Monospace),
  SERIF("Serif", FontFamily.Serif),
  CURSIVE("Cursive", FontFamily.Cursive),
  SANS_SERIF("Modern Sans", FontFamily.SansSerif)
}

enum class FontWeightOption(val title: String, val weight: FontWeight) {
  THIN("Thin", FontWeight.Thin),
  LIGHT("Light", FontWeight.Light),
  NORMAL("Normal", FontWeight.Normal),
  MEDIUM("Medium", FontWeight.Medium),
  BOLD("Bold", FontWeight.Bold),
  EXTRA_BOLD("Extra Bold", FontWeight.ExtraBold)
}

data class WorldCity(val name: String, val timeZoneId: String, val label: String)

val AVAILABLE_WORLD_CITIES = listOf(
  WorldCity("Tokyo", "Asia/Tokyo", "TYO"),
  WorldCity("London", "Europe/London", "LON"),
  WorldCity("New York", "America/New_York", "NYC"),
  WorldCity("Paris", "Europe/Paris", "PAR"),
  WorldCity("Sydney", "Australia/Sydney", "SYD"),
  WorldCity("Dubai", "Asia/Dubai", "DXB"),
  WorldCity("San Francisco", "America/Los_Angeles", "SFO")
)

data class AodConfig(
  val clockFace: ClockFaceType = ClockFaceType.CYBERPUNK_NEON,
  val is24HourFormat: Boolean = false,
  val showSeconds: Boolean = true,
  val analogUseRomanNumerals: Boolean = false,
  val selectedWorldCityIndex: Int = 0,

  val edgeEnabled: Boolean = true,
  val edgeBorderStyle: EdgeBorderStyle = EdgeBorderStyle.GRADIENT_STREAM,
  val edgeColorMode: EdgeColorMode = EdgeColorMode.DUOTONE_PULSE,
  val edgeTriggerMode: EdgeTriggerMode = EdgeTriggerMode.ALWAYS_ON,
  val edgeAnimationDurationMs: Int = 1800,
  val edgeThicknessDp: Float = 4.5f,
  val edgePrimaryColor: Long = 0xFF00F0FF,
  val edgeSecondaryColor: Long = 0xFFFF007F,

  val fontFamilyOption: FontFamilyOption = FontFamilyOption.MONOSPACE,
  val fontWeightOption: FontWeightOption = FontWeightOption.BOLD,
  val fontScaleFactor: Float = 1.0f,
  val primaryClockColor: Long = 0xFF00F0FF,
  val secondaryClockColor: Long = 0xFFFF007F,
  val useNumeralGradient: Boolean = true,
  val screenBrightness: Float = 0.65f,

  val batteryWidgetEnabled: Boolean = true,
  val batteryStyle: BatteryIndicatorStyle = BatteryIndicatorStyle.CIRCULAR_RING,

  val dateWidgetEnabled: Boolean = true,
  val dateFormatOption: DateFormatOption = DateFormatOption.SHORT_DAY_DATE,
  val calendarEventText: String = "Design Review @ 2:00 PM",
  val showCalendarEvent: Boolean = true,

  val memoWidgetEnabled: Boolean = true,
  val memoText: String = "NEVER SETTLE • AOD ON",
  val memoColor: Long = 0xFF88A0C0,

  val widgetOrder: List<String> = listOf("date", "battery", "memo"),

  val antiBurnInEnabled: Boolean = true,
  val powerSaveMode: Boolean = true,
  val batterySaverThreshold: Int = 40,
  val lowPowerShiftIntervalSeconds: Int = 180,
  val lowPowerEdgeDimFactor: Float = 0.35f,
  val simulateBatterySaver: Boolean = false
)

fun AodConfig.isBatterySaverActive(batteryInfo: BatteryInfo): Boolean {
  if (!powerSaveMode) return false
  if (simulateBatterySaver) return true
  return !batteryInfo.isCharging && batteryInfo.level < batterySaverThreshold
}

data class ThemePreset(
  val id: String,
  val name: String,
  val description: String,
  val previewPrimaryColor: Long,
  val previewSecondaryColor: Long,
  val config: AodConfig
)

// ============================================================================
// 4. EdgeLightingBorder.kt (/app/src/main/java/com/example/ui/components/EdgeLightingBorder.kt)
// ============================================================================
package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.data.EdgeBorderStyle
import com.example.data.EdgeColorMode
import com.example.data.EdgeTriggerMode

@Composable
fun EdgeLightingBorder(
  enabled: Boolean,
  borderStyle: EdgeBorderStyle,
  colorMode: EdgeColorMode,
  triggerMode: EdgeTriggerMode,
  durationMs: Int,
  thicknessDp: Float,
  primaryColor: Color,
  secondaryColor: Color,
  dimMultiplier: Float = 1.0f,
  isAlertActive: Boolean = false,
  cornerRadiusDp: Float = 32f,
  modifier: Modifier = Modifier
) {
  if (!enabled) return
  if (triggerMode == EdgeTriggerMode.NOTIFICATION_PULSE && !isAlertActive) return

  val infiniteTransition = rememberInfiniteTransition(label = "EdgeLighting")
  val safeDuration = durationMs.coerceIn(400, 5000)

  val rotationProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = safeDuration, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "EdgeRotation"
  )

  val pulseProgress by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = safeDuration / 2, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "EdgePulse"
  )

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .graphicsLayer { alpha = dimMultiplier.coerceIn(0.05f, 1.0f) }
  ) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return@Canvas

    val strokeWidthPx = thicknessDp.dp.toPx()
    val halfStroke = strokeWidthPx / 2f
    val cornerRadiusPx = cornerRadiusDp.dp.toPx()

    val activeColors = when (colorMode) {
      EdgeColorMode.SINGLE_COLOR -> listOf(primaryColor, primaryColor)
      EdgeColorMode.DUOTONE_PULSE -> listOf(primaryColor, secondaryColor, primaryColor)
      EdgeColorMode.RAINBOW_CYCLE -> listOf(
        Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
        Color(0xFF00FF00), Color(0xFF00F0FF), Color(0xFF0000FF),
        Color(0xFF8B00FF), Color(0xFFFF0000)
      )
    }

    when (borderStyle) {
      EdgeBorderStyle.SOLID_NEON -> {
        val baseColor = if (colorMode == EdgeColorMode.DUOTONE_PULSE) {
          Color(
            red = primaryColor.red * (1f - pulseProgress) + secondaryColor.red * pulseProgress,
            green = primaryColor.green * (1f - pulseProgress) + secondaryColor.green * pulseProgress,
            blue = primaryColor.blue * (1f - pulseProgress) + secondaryColor.blue * pulseProgress,
            alpha = 1f
          )
        } else if (colorMode == EdgeColorMode.RAINBOW_CYCLE) {
          val hue = (rotationProgress * 360f) % 360f
          Color.hsv(hue, 0.95f, 1f)
        } else {
          primaryColor
        }

        drawRoundRect(
          color = baseColor.copy(alpha = 0.35f),
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(width - strokeWidthPx, height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx * 1.8f)
        )

        drawRoundRect(
          color = baseColor,
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(width - strokeWidthPx, height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx)
        )
      }

      EdgeBorderStyle.PULSATING_GLOW -> {
        val alphaMultiplier = pulseProgress
        val glowColor = if (colorMode == EdgeColorMode.RAINBOW_CYCLE) {
          val hue = (rotationProgress * 360f) % 360f
          Color.hsv(hue, 1f, 1f)
        } else if (colorMode == EdgeColorMode.DUOTONE_PULSE) {
          Color(
            red = primaryColor.red * (1f - pulseProgress) + secondaryColor.red * pulseProgress,
            green = primaryColor.green * (1f - pulseProgress) + secondaryColor.green * pulseProgress,
            blue = primaryColor.blue * (1f - pulseProgress) + secondaryColor.blue * pulseProgress,
            alpha = 1f
          )
        } else {
          primaryColor
        }

        drawRoundRect(
          color = glowColor.copy(alpha = 0.28f * alphaMultiplier),
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(width - strokeWidthPx, height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx * 2.2f)
        )
        drawRoundRect(
          color = glowColor.copy(alpha = 0.95f * alphaMultiplier),
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(width - strokeWidthPx, height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx)
        )
      }

      EdgeBorderStyle.GRADIENT_STREAM -> {
        val center = Offset(width / 2f, height / 2f)
        val shiftedColors = if (colorMode == EdgeColorMode.RAINBOW_CYCLE) {
          activeColors
        } else if (colorMode == EdgeColorMode.DUOTONE_PULSE) {
          listOf(primaryColor, secondaryColor, primaryColor.copy(alpha = 0.2f), secondaryColor, primaryColor)
        } else {
          listOf(primaryColor, primaryColor.copy(alpha = 0.15f), primaryColor, primaryColor.copy(alpha = 0.15f), primaryColor)
        }

        val sweepBrush = Brush.sweepGradient(colors = shiftedColors, center = center)

        drawRoundRect(
          brush = sweepBrush,
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(width - strokeWidthPx, height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx),
          alpha = 0.9f
        )
      }

      EdgeBorderStyle.ROTATING_LASERS -> {
        val roundRect = RoundRect(
          left = halfStroke, top = halfStroke,
          right = width - halfStroke, bottom = height - halfStroke,
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )
        val fullPath = Path().apply { addRoundRect(roundRect) }
        val pathMeasure = PathMeasure().apply { setPath(fullPath, false) }
        val totalLength = pathMeasure.length

        if (totalLength > 0f) {
          val beamLength = totalLength * 0.22f
          for (i in 0..1) {
            val startRatio = (rotationProgress + (i * 0.5f)) % 1f
            val startDist = startRatio * totalLength
            val endDist = startDist + beamLength

            val laserPath = Path()
            if (endDist <= totalLength) {
              pathMeasure.getSegment(startDist, endDist, laserPath, true)
            } else {
              pathMeasure.getSegment(startDist, totalLength, laserPath, true)
              pathMeasure.getSegment(0f, endDist - totalLength, laserPath, true)
            }

            val laserColor = if (colorMode == EdgeColorMode.RAINBOW_CYCLE) {
              val hue = ((rotationProgress + (i * 0.5f)) * 360f) % 360f
              Color.hsv(hue, 1f, 1f)
            } else if (i == 0) primaryColor else secondaryColor

            drawPath(
              path = laserPath,
              color = laserColor,
              style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
          }
        }
      }
    }
  }
}

// ============================================================================
// 5. BurnInShiftContainer.kt (/app/src/main/java/com/example/ui/components/BurnInShiftContainer.kt)
// ============================================================================
package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun BurnInShiftContainer(
  enabled: Boolean,
  shiftTrigger: Int = 0,
  intervalSeconds: Int = 60,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  var targetOffsetX by remember { mutableFloatStateOf(0f) }
  var targetOffsetY by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(enabled, shiftTrigger, intervalSeconds) {
    if (!enabled) {
      targetOffsetX = 0f
      targetOffsetY = 0f
      return@LaunchedEffect
    }

    if (shiftTrigger > 0) {
      targetOffsetX = Random.nextDouble(-15.0, 15.0).toFloat()
      targetOffsetY = Random.nextDouble(-15.0, 15.0).toFloat()
    }

    val safeDelayMs = (intervalSeconds.coerceAtLeast(10) * 1000L)
    while (true) {
      delay(safeDelayMs)
      targetOffsetX = Random.nextDouble(-15.0, 15.0).toFloat()
      targetOffsetY = Random.nextDouble(-15.0, 15.0).toFloat()
    }
  }

  val animatedOffsetX by animateDpAsState(
    targetValue = targetOffsetX.dp,
    animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
    label = "BurnInShiftX"
  )

  val animatedOffsetY by animateDpAsState(
    targetValue = targetOffsetY.dp,
    animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
    label = "BurnInShiftY"
  )

  Box(modifier = modifier.offset(x = animatedOffsetX, y = animatedOffsetY)) {
    content()
  }
}

// ============================================================================
// 6. AodLiveScreen.kt (/app/src/main/java/com/example/ui/screens/AodLiveScreen.kt)
// ============================================================================
package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AodConfig
import com.example.data.BatteryInfo
import com.example.data.isBatterySaverActive
import com.example.ui.components.AodDisplayContent
import com.example.ui.components.BurnInShiftContainer
import com.example.ui.components.EdgeLightingBorder
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun AodLiveScreen(
  time: LocalTime,
  date: LocalDate,
  batteryInfo: BatteryInfo,
  config: AodConfig,
  burnInTrigger: Int,
  isAlertActive: Boolean,
  onExit: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler { onExit() }

  var showWakeHint by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    delay(4000L)
    showWakeHint = false
  }

  val isBatterySaverActive = config.isBatterySaverActive(batteryInfo)
  val activeShiftInterval = if (isBatterySaverActive) config.lowPowerShiftIntervalSeconds else 60
  val activeEdgeDim = if (isBatterySaverActive) config.lowPowerEdgeDimFactor else 1.0f

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("aod_live_screen")
      .pointerInput(Unit) {
        detectTapGestures(
          onDoubleTap = { onExit() },
          onTap = { showWakeHint = true }
        )
      }
  ) {
    EdgeLightingBorder(
      enabled = config.edgeEnabled,
      borderStyle = config.edgeBorderStyle,
      colorMode = config.edgeColorMode,
      triggerMode = config.edgeTriggerMode,
      durationMs = config.edgeAnimationDurationMs,
      thicknessDp = config.edgeThicknessDp,
      primaryColor = Color(config.edgePrimaryColor),
      secondaryColor = Color(config.edgeSecondaryColor),
      dimMultiplier = activeEdgeDim,
      isAlertActive = isAlertActive,
      cornerRadiusDp = 28f
    )

    BurnInShiftContainer(
      enabled = config.antiBurnInEnabled,
      shiftTrigger = burnInTrigger,
      intervalSeconds = activeShiftInterval,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        AodDisplayContent(
          time = time,
          date = date,
          batteryInfo = batteryInfo,
          config = config
        )
      }
    }

    val dimAlpha = ((1.0f - config.screenBrightness) * 0.75f).coerceIn(0f, 0.75f)
    if (dimAlpha > 0.05f) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = dimAlpha))
      )
    }

    if (isBatterySaverActive) {
      Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 28.dp)
          .background(Color(0xFF1C1800), RoundedCornerShape(12.dp))
          .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          text = "⚡ BATTERY SAVER ACTIVE (<${config.batterySaverThreshold}%): 3M SHIFT • DIM GLOW",
          color = Color(0xFFFFB300),
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp
        )
      }
    }

    AnimatedVisibility(
      visible = showWakeHint,
      enter = fadeIn(tween(400)),
      exit = fadeOut(tween(800)),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 36.dp)
    ) {
      Text(
        text = "Double-tap anywhere to wake",
        color = Color(0xFF6B7280).copy(alpha = 0.65f),
        fontSize = 11.sp,
        letterSpacing = 1.sp
      )
    }
  }
}