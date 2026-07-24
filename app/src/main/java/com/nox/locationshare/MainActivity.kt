package com.nox.locationshare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.nox.locationshare.ui.theme.NoxLocationShareTheme
import com.nox.locationshare.ui.theme.SuccessGreen

class MainActivity : ComponentActivity() {

    private lateinit var fusedClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            NoxLocationShareTheme {
                LocationShareScreen(
                    hasPermission = { hasLocationPermission() },
                    onRequestPermission = { onGranted -> requestPermission(onGranted) },
                    onFetchLocation = { onResult -> fetchLocation(onResult) },
                    onShare = { text -> shareToApps(text) }
                )
            }
        }
    }

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        pendingPermissionCallback?.invoke(granted)
        pendingPermissionCallback = null
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(onGranted: (Boolean) -> Unit) {
        pendingPermissionCallback = onGranted
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun fetchLocation(onResult: (Double?, Double?) -> Unit) {
        if (!hasLocationPermission()) {
            onResult(null, null)
            return
        }
        try {
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onResult(location.latitude, location.longitude)
                    } else {
                        // fallback به آخرین لوکیشن شناخته‌شده
                        fusedClient.lastLocation.addOnSuccessListener { last ->
                            if (last != null) onResult(last.latitude, last.longitude)
                            else onResult(null, null)
                        }.addOnFailureListener { onResult(null, null) }
                    }
                }
                .addOnFailureListener { onResult(null, null) }
        } catch (e: SecurityException) {
            onResult(null, null)
        }
    }

    private fun shareToApps(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "ارسال موقعیت با..."))
    }
}

@Composable
fun LocationShareScreen(
    hasPermission: () -> Boolean,
    onRequestPermission: ((Boolean) -> Unit) -> Unit,
    onFetchLocation: ((Double?, Double?) -> Unit) -> Unit,
    onShare: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("برای ارسال موقعیت خود، دکمه زیر را بزنید") }
    var isError by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isLoading) 0.94f else 1f,
        animationSpec = tween(200),
        label = "buttonScale"
    )

    fun startFlow() {
        isError = false
        isSuccess = false
        if (!hasPermission()) {
            onRequestPermission { granted ->
                if (granted) {
                    isLoading = true
                    statusText = "در حال دریافت موقعیت مکانی..."
                    onFetchLocation { lat, lon ->
                        isLoading = false
                        if (lat != null && lon != null) {
                            isSuccess = true
                            statusText = "موقعیت پیدا شد! انتخاب کنید کجا ارسال شود"
                            val mapsLink = "https://maps.google.com/?q=$lat,$lon"
                            onShare("📍 موقعیت من:\n$mapsLink")
                        } else {
                            isError = true
                            statusText = "دریافت موقعیت ناموفق بود. GPS را روشن کنید و دوباره امتحان کنید"
                        }
                    }
                } else {
                    isError = true
                    statusText = "برای ارسال موقعیت، اجازه دسترسی به مکان لازم است"
                }
            }
        } else {
            isLoading = true
            statusText = "در حال دریافت موقعیت مکانی..."
            onFetchLocation { lat, lon ->
                isLoading = false
                if (lat != null && lon != null) {
                    isSuccess = true
                    statusText = "موقعیت پیدا شد! انتخاب کنید کجا ارسال شود"
                    val mapsLink = "https://maps.google.com/?q=$lat,$lon"
                    onShare("📍 موقعیت من:\n$mapsLink")
                } else {
                    isError = true
                    statusText = "دریافت موقعیت ناموفق بود. GPS را روشن کنید و دوباره امتحان کنید"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF160A38),
                        Color(0xFF2A0E61),
                        Color(0xFF4C1D95)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (!isLoading) pulseScale else 1f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEC4899).copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "ارسال موقعیت من",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = { startFlow() },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEC4899),
                    disabledContainerColor = Color(0xFFEC4899).copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .scale(buttonScale)
                    .height(58.dp)
                    .fillMaxWidth(0.75f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ارسال موقعیت من",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            AnimatedVisibility(visible = isError) {
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF87171))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("لطفاً دسترسی مکان و GPS را بررسی کنید", color = Color(0xFFF87171), fontSize = 13.sp)
                }
            }

            AnimatedVisibility(visible = isSuccess) {
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("با موفقیت آماده ارسال شد", color = SuccessGreen, fontSize = 13.sp)
                }
            }
        }
    }
}
