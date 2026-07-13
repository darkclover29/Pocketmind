package com.pocketshadow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketshadow.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen with Mesh Gradient Background
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val totalPages = 4
    val isTermsPage = page == totalPages - 1

    // ── Mesh gradient background animation ────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_glow")
    
    val xOffset1 by infiniteTransition.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "x_offset_1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue  = 0.20f,
        targetValue   = 0.80f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "y_offset_1"
    )
    val xOffset2 by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 0.15f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "x_offset_2"
    )
    val yOffset2 by infiniteTransition.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "y_offset_2"
    )

    PocketShadowTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .drawBehind {
                    // Glowing violet and amber meshes floating organically
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ElectricViolet.copy(alpha = 0.15f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * xOffset1, size.height * yOffset1),
                            radius = size.minDimension * 0.75f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF4C1D95).copy(alpha = 0.20f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * xOffset2, size.height * yOffset2),
                            radius = size.minDimension * 0.85f
                        )
                    )
                }
        ) {
            // ── Page content ──────────────────────────────────────────────────
            AnimatedContent(
                targetState   = page,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn(tween(350, easing = EaseOutCubic))).togetherWith(
                            slideOutHorizontally { -it } + fadeOut(tween(250, easing = EaseInCubic))
                        )
                    } else {
                        (slideInHorizontally { -it } + fadeIn(tween(350, easing = EaseOutCubic))).togetherWith(
                            slideOutHorizontally { it } + fadeOut(tween(250, easing = EaseInCubic))
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isTermsPage) 0.dp else 146.dp),
                label    = "onboarding_page"
            ) { currentPage ->
                when (currentPage) {
                    0    -> WelcomePage()
                    1    -> FeaturesPage()
                    2    -> ReadyOnDevicePage()
                    3    -> TermsPage(onComplete = onComplete)
                    else -> WelcomePage()
                }
            }

            // ── Bottom nav (hidden on Terms page — TermsPage owns its bottom) ─
            AnimatedVisibility(
                visible  = !isTermsPage,
                enter    = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
                exit     = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF000000).copy(alpha = 0.8f),
                                    Color(0xFF000000)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 40.dp),
                    verticalArrangement     = Arrangement.spacedBy(24.dp),
                    horizontalAlignment     = Alignment.CenterHorizontally
                ) {
                    // Page indicators
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(totalPages) { i ->
                            val isActive = i == page
                            val width by animateDpAsState(
                                targetValue  = if (isActive) 28.dp else 8.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label        = "indicator_$i"
                            )
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) ElectricViolet
                                        else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }

                    // Continue button
                    Button(
                        onClick   = { page++ },
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape     = RoundedCornerShape(20.dp),
                        colors    = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor   = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            "Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 1 — Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue  = 0.96f,
        targetValue   = 1.04f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "logo_scale"
    )
    val glowAlpha by pulse.animateFloat(
        initialValue  = 0.15f,
        targetValue   = 0.35f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "glow_alpha"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Clean logo mark without background halo
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .padding(bottom = 44.dp)
                .scale(scale)
        ) {
            PocketShadowLogoMark(size = 110f)
        }

        Text(
            "PocketShadow",
            style     = MaterialTheme.typography.displayMedium.copy(
                fontWeight    = FontWeight.ExtraBold,
                color         = Color.White,
                letterSpacing = (-1.5).sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Your private, on-device AI assistant",
            style     = MaterialTheme.typography.titleMedium.copy(
                color         = ElectricViolet,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // Grid of floating feature pills
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                FeaturePill(Icons.Rounded.WifiOff,  "No Internet")
                FeaturePill(Icons.Rounded.Cloud,    "No Cloud")
            }
            FeaturePill(Icons.Rounded.Person,   "No Account Required")
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String) {
    Surface(
        shape  = RoundedCornerShape(50),
        color  = Color(0x0EFFFFFF),
        border = BorderStroke(
            0.5.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.15f),
                    ElectricViolet.copy(alpha = 0.25f)
                )
            )
        )
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 2 — Features (Staggered Animation List)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeaturesPage() {
    var visibleCards by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        visibleCards = 1
        kotlinx.coroutines.delay(150)
        visibleCards = 2
        kotlinx.coroutines.delay(150)
        visibleCards = 3
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Built for privacy",
            style     = MaterialTheme.typography.headlineLarge.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color         = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Everything runs locally on your phone, always.",
            style     = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(44.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = visibleCards >= 1,
                enter   = slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(400))
            ) {
                FeatureCard(
                    icon        = Icons.Rounded.Lock,
                    title       = "Fully on-device",
                    description = "AI inference runs locally on your phone's CPU/GPU. No request ever leaves your device."
                )
            }
            AnimatedVisibility(
                visible = visibleCards >= 2,
                enter   = slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(400))
            ) {
                FeatureCard(
                    icon        = Icons.Rounded.Storage,
                    title       = "Local history only",
                    description = "Chats are saved in a secure database on your phone. Only you can see them."
                )
            }
            AnimatedVisibility(
                visible = visibleCards >= 3,
                enter   = slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(400))
            ) {
                FeatureCard(
                    icon        = Icons.Rounded.NoAccounts,
                    title       = "Zero account needed",
                    description = "No sign-in, no email, no tracking. Works 100% offline from the first launch."
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 3 — Ready on this device
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyOnDevicePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = ElectricViolet,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Ready on your device",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Your private AI is included with PocketShadow and stays on this phone.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 23.sp
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadinessRow(
                Icons.Rounded.WifiOff,
                "Works offline",
                "PocketShadow does not need a connection to chat."
            )
            ReadinessRow(
                Icons.Rounded.Storage,
                "Built for your phone",
                "The on-device model uses about 3.5 GB of storage."
            )
            ReadinessRow(
                Icons.Rounded.Lock,
                "Private by design",
                "Your prompts and replies stay on this device."
            )
        }
    }
}

@Composable
private fun ReadinessRow(icon: ImageVector, title: String, detail: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x0CFFFFFF),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                Text(detail, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp))
            }
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Surface(
        shape  = RoundedCornerShape(24.dp),
        color  = Color(0x0CFFFFFF),
        border = BorderStroke(
            0.5.dp,
            Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        )
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Neon accent left border line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ElectricViolet,
                                ElectricViolet.copy(alpha = 0.3f)
                            )
                        )
                    )
            )

            Row(
                modifier              = Modifier
                    .padding(vertical = 20.dp)
                    .padding(end = 20.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ElectricViolet.copy(alpha = 0.10f))
                        .border(
                            1.dp,
                            ElectricViolet.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = ElectricViolet, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 3 — Terms & Privacy
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TermsPage(onComplete: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }

    // Pulse Rocket when accepted
    val infiniteTransition = rememberInfiniteTransition(label = "rocket_pulse")
    val rocketScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "rocket_scale"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))

        Text(
            "Almost there!",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color         = Color.White
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Quick read before you start",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        )

        Spacer(Modifier.height(24.dp))

        // ── Scrollable legal card ─────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            color    = Color(0x08FFFFFF),
            border   = BorderStroke(
                0.5.dp,
                Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                LegalSection(heading = "📋 Terms of Service", body = TERMS_OF_SERVICE)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                LegalSection(heading = "🔒 Privacy Policy", body = PRIVACY_POLICY)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Glowing Accept Card ───────────────────────────────────────────────
        Surface(
            shape    = RoundedCornerShape(18.dp),
            color    = if (accepted) ElectricViolet.copy(alpha = 0.08f)
                       else Color(0x06FFFFFF),
            border   = BorderStroke(
                width = 1.dp,
                color = if (accepted) ElectricViolet
                        else Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { accepted = !accepted }
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = accepted,
                    onCheckedChange = { accepted = it },
                    colors          = CheckboxDefaults.colors(
                        checkedColor   = ElectricViolet,
                        uncheckedColor = Color.White.copy(alpha = 0.3f),
                        checkmarkColor = Color.Black
                    )
                )
                Text(
                    "I have read and agree to the Terms of Service and Privacy Policy",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color      = if (accepted) Color.White
                                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 20.sp,
                        fontWeight = if (accepted) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Get Started button ────────────────────────────────────────────────
        Button(
            onClick   = onComplete,
            enabled   = accepted,
            modifier  = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape     = RoundedCornerShape(20.dp),
            colors    = ButtonDefaults.buttonColors(
                containerColor         = ElectricViolet,
                contentColor           = Color.Black,
                disabledContainerColor = Color(0x08FFFFFF),
                disabledContentColor   = Color.White.copy(alpha = 0.2f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(
                Icons.Rounded.RocketLaunch, 
                null, 
                modifier = Modifier
                    .size(20.dp)
                    .scale(if (accepted) rocketScale else 1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // Page indicators
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false, false, true).forEach { isActive ->
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isActive) 28.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) ElectricViolet
                            else Color.White.copy(alpha = 0.2f)
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LegalSection(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall.copy(
                color      = ElectricViolet,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 22.sp,
                fontSize   = 13.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legal text — friendly, plain English
// ─────────────────────────────────────────────────────────────────────────────

private val TERMS_OF_SERVICE = """
Last updated: June 2026

We've written this in plain English. Here's what you need to know.

▸ What PocketShadow does
PocketShadow is an AI assistant that runs entirely on your device. Every response is generated locally — nothing you type is sent to any server, ever.

▸ AI can be wrong
The AI makes mistakes. It can misremember facts, give outdated information, or simply be incorrect. Please don't rely on PocketShadow for:
  • Medical or health decisions
  • Legal or financial advice
  • Anything safety-critical

Always double-check important information with a qualified professional or trusted source.

▸ How to use the app
Use PocketShadow only for lawful purposes. Don't use it to generate content that is harmful, abusive, or illegal.

▸ Age requirement
You must be at least 13 years old to use PocketShadow.

▸ Intellectual property
The app and its original content belong to the developer. The Gemma AI model is subject to Google's Gemma Terms of Use.

▸ No warranty
The app is provided "as is." We do our best to keep things running smoothly, but we can't guarantee it's always error-free.

▸ Changes to these terms
We may update these Terms occasionally. Continued use of the app after changes means you accept the updated Terms.

▸ Questions?
Reach out via the Play Store listing page.
""".trimIndent()

private val PRIVACY_POLICY = """
Last updated: June 2026

Short version: we collect absolutely nothing.

▸ Zero data collection
PocketShadow does not collect, store remotely, or transmit any personal information. No servers are involved. No databases outside your phone.

▸ What lives on your phone
The only data we store is local to your device:
  • Your chat history (only if you enable it in Settings)
  • Your app preferences

This data never leaves your device and is never accessible to us or anyone else.

▸ No trackers, no ads, no analytics
We have none of the following:
  • Analytics SDKs (no Firebase, no Mixpanel, nothing)
  • Advertising SDKs
  • Crash reporting tools that send data off-device
  • Any third-party service that can see your data

▸ How the AI works
Your messages are processed by a Gemma language model running directly on your phone's processor. Think of it like a calculator — everything is computed locally and nothing is sent anywhere.

▸ Permissions we use
The only permission PocketShadow requests is VIBRATE, for optional haptic feedback when you send a message. That's it.

▸ Delete your data anytime
You're always in control:
  • Delete individual chats inside the app
  • Clear all app data via Android Settings → Apps → PocketShadow → Clear Data

▸ Children
We don't collect data from anyone — including children under 13.

▸ Changes to this policy
If we make significant changes, we'll note them in the Play Store release notes.

▸ Questions?
Reach out via the Play Store listing page.
""".trimIndent()
