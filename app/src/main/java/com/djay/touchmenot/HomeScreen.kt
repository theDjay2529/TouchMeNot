package com.djay.touchmenot

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(ctx: Context) {
    val prefs = PrefsBridge.prefs(ctx)

    var blockPowerControls by remember { mutableStateOf(prefs.getBoolean("tmn_block_power_controls", true)) }
    var blockInternet by remember { mutableStateOf(prefs.getBoolean("tmn_block_internet", true)) }
    var blockAirplane by remember { mutableStateOf(prefs.getBoolean("tmn_block_airplane", true)) }
    var blockBluetooth by remember { mutableStateOf(prefs.getBoolean("tmn_block_bluetooth", true)) }
    var blockHotspot by remember { mutableStateOf(prefs.getBoolean("tmn_block_hotspot", true)) }
    var blockDnd by remember { mutableStateOf(prefs.getBoolean("tmn_block_dnd", true)) }
    var blockVolumeRingerMode by remember { mutableStateOf(prefs.getBoolean("tmn_block_volume_ringer_mode", true)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E1F),
                        Color(0xFF1A1330),
                        Color(0xFF0D0D22)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 5.dp)
        ) {
            HeroSection()
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                InfoTile(
                    title = "LSPosed Permissions",
                    infoText = "\nEnable the following permissions in LSposed Manager and restart your phone.",
                    bulletPoints = listOf(
                        "System Framework",
                        "System UI"
                    ),
                    note = "In case you are not able to find System UI in Lsposed: \nOpen Lsposed > Click on our Module > Top-right 3 dots > Hide > UNCHECK System apps"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                SectionHeader(title = "Power Menu", icon = Icons.Filled.PowerSettingsNew)
                Spacer(modifier = Modifier.height(16.dp))
                
                PowerMenuTile(
                    isEnabled = blockPowerControls,
                    onToggle = { enabled ->
                        blockPowerControls = enabled
                        PrefsBridge.save(ctx, "tmn_block_power_controls", enabled)
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))

                SectionHeader(title = "Quick Settings Panel", icon = Icons.Filled.Settings)
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QSTile(
                        title = "Airplane Mode",
                        icon = Icons.Filled.AirplanemodeActive,
                        isEnabled = blockAirplane,
                        description = "Disables Airplane Mode tile when the device is locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockAirplane = enabled
                            PrefsBridge.save(ctx, "tmn_block_airplane", enabled)
                        }
                    )

                    QSTile(
                        title = "Internet Tile",
                        icon = Icons.Filled.SignalCellularAlt,
                        isEnabled = blockInternet,
                        description = "Disables Wi‑Fi & Internet tile when the device is locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockInternet = enabled
                            PrefsBridge.save(ctx, "tmn_block_internet", enabled)
                        }
                    )

                    QSTile(
                        title = "Bluetooth Toggle",
                        icon = Icons.Filled.Bluetooth,
                        isEnabled = blockBluetooth,
                        description = "Disables Bluetooth tile when the device is locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockBluetooth = enabled
                            PrefsBridge.save(ctx, "tmn_block_bluetooth", enabled)
                        }
                    )

                    QSTile(
                        title = "Hotspot Toggle",
                        icon = Icons.Filled.WifiTethering,
                        isEnabled = blockHotspot,
                        description = "Disables Hotspot tile when the device is locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockHotspot = enabled
                            PrefsBridge.save(ctx, "tmn_block_hotspot", enabled)
                        }
                    )

                    QSTile(
                        title = "Do Not Disturb",
                        icon = Icons.Filled.DoNotDisturb,
                        isEnabled = blockDnd,
                        description = "Disables DND tile when the device is locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockDnd = enabled
                            PrefsBridge.save(ctx, "tmn_block_dnd", enabled)
                        }
                    )

                    QSTile(
                        title = "Volume Ringer Mode",
                        icon = Icons.Filled.VolumeUp,
                        isEnabled = blockVolumeRingerMode,
                        description = "Blocks ringer mode changes from volume dialog when locked",
                        modifier = Modifier.fillMaxWidth(),
                        onToggle = { enabled ->
                            blockVolumeRingerMode = enabled
                            PrefsBridge.save(ctx, "tmn_block_volume_ringer_mode", enabled)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Version and Update Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Version (non-clickable)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Current Version: V1.1",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Check for Updates (clickable)
                    var isPressed by remember { mutableStateOf(false) }
                    val haptic = LocalHapticFeedback.current
                    val context = LocalContext.current
                    
                    LaunchedEffect(isPressed) {
                        if (isPressed) {
                            delay(600)
                            isPressed = false
                        }
                    }
                    
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isPressed) MaterialTheme.colorScheme.primary else Color(0xFF1A1A2E),
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "updateBgColor"
                    )
                    
                    val textColor by animateColorAsState(
                        targetValue = if (isPressed) Color(0xFF0D0D0D) else Color.White.copy(alpha = 0.7f),
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "updateTextColor"
                    )
                    
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPressed = true
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theDjay2529/TouchMeNot"))
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Check for Updates",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HeroSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TouchMeNot",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "LOCKSCREEN PROTECTOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Clickable credit text with mixed font weights
            val context = LocalContext.current
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/theDjay2529"))
                    context.startActivity(intent)
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "built by - ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Text(
                    text = "Djay",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PowerMenuTile(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val borderWidth by animateDpAsState(
        targetValue = if (isEnabled) 1.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "borderWidth"
    )
    val elevation by animateDpAsState(
        targetValue = if (isEnabled) 8.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "elevation"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF1A1A2E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF2A2A3E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "borderColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(16.dp), spotColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clip(RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isEnabled)
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (isEnabled) Color(0xFF0D0D0D) else Color.White.copy(alpha = 0.4f)
                )
                Column {
                    Text(
                        text = "Block Power Menu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) Color(0xFF0D0D0D) else Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Prevent power menu access on lockscreen",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isEnabled) Color(0xFF0D0D0D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoTile(title: String, infoText: String, bulletPoints: List<String>, note: String? = null) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isExpanded = !isExpanded
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and dropdown icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .scale(1.2f)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Content with smooth collapse animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = infoText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    bulletPoints.forEach { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = point,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                    
                    // Display note if provided
                    note?.let { noteText ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val annotatedNote = buildAnnotatedString {
                            val parts = noteText.split("System UI")
                            append(parts[0])
                            if (parts.size > 1) {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("System UI")
                                }
                                append(parts[1])
                            }
                        }
                        Text(
                            text = annotatedNote,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.5f),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusTile(title: String, isOk: Boolean, description: String) {
    val borderWidth by animateDpAsState(
        targetValue = if (isOk) 1.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "borderWidth"
    )
    val elevation by animateDpAsState(
        targetValue = if (isOk) 8.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "elevation"
    )
    val tileHeight by animateDpAsState(
        targetValue = if (isOk) 100.dp else 160.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileHeight"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isOk) MaterialTheme.colorScheme.primary else Color(0xFF1A1A2E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(tileHeight)
            .shadow(elevation, RoundedCornerShape(16.dp), spotColor = if (isOk) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOk) Color(0xFF0D0D0D) else Color.White,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isOk) Color(0xFF0D0D0D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Start,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = if (isOk) Color(0xFF6BCB6E).copy(alpha = 0.9f) else Color(0xFFEF5350).copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun QSTile(title: String, icon: ImageVector, isEnabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String = "") {
    val haptic = LocalHapticFeedback.current
    val borderWidth by animateDpAsState(
        targetValue = if (isEnabled) 1.5.dp else 3.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "borderWidth"
    )
    val elevation by animateDpAsState(
        targetValue = if (isEnabled) 6.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "elevation"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF1A1A2E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF2A2A3E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "borderColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = modifier
            .height(100.dp)
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(16.dp), spotColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clip(RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isEnabled)
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(26.dp),
                    tint = if (isEnabled) Color(0xFF0D0D0D) else Color.White.copy(alpha = 0.5f)
                )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) Color(0xFF0D0D0D) else Color.White,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isEnabled) Color(0xFF0D0D0D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Start,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
