package com.example.nearme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nearme.data.local.CachedPlace
import com.example.nearme.ui.StationsViewModel
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val vm: StationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { PlacesScreen(vm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(vm: StationsViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val category by vm.currentCategory.collectAsStateWithLifecycle()
    val fuelGrade by vm.currentFuelGrade.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.any { it }
        if (hasPermission) vm.refresh()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            vm.refresh()
        }
    }

    var reportFor by remember { mutableStateOf<CachedPlace?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val isGas = category == "GAS"

    Scaffold(
        floatingActionButton = {
            if (hasPermission) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add place") }
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("NearMe") },
                actions = {
                    // Run/Pause toggle for periodic auto-refresh.
                    TextButton(onClick = { if (hasPermission) vm.toggleRunning() }) {
                        Icon(
                            if (running) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = if (running) "Pause" else "Run"
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (running) "Pause" else "Run")
                    }
                    IconButton(onClick = { if (hasPermission) vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            CategorySelector(category) { vm.setCategory(it) }

            // Current device location, shown for debugging the GPS/emulator fix.
            Text(
                text = location?.let { (lat, lon) ->
                    "Lat %.5f, Lon %.5f".format(lat, lon)
                } ?: "Location: —",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Fuel grade only applies to gas.
            if (isGas) {
                FuelGradeSelector(fuelGrade) { vm.setFuelGrade(it) }
            }

            // Pull-to-refresh wraps the results area: dragging down triggers a
            // refresh, and the indicator reflects ANY refresh (pull, the toolbar
            // button, or the periodic auto-refresh).
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { if (hasPermission) vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (places.isEmpty()) {
                    // Make the empty/first-load state scrollable so the pull
                    // gesture works even with no items \u2014 empty is exactly when
                    // you want to pull to refresh.
                    Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            refreshing -> CircularProgressIndicator()
                            else -> Text(
                                if (!hasPermission)
                                    "Location permission is needed to find nearby places."
                                else error
                                    ?: "No ${category.lowercase()} places cached yet. Pull down to refresh.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        error?.let {
                            Text(
                                "Showing cached data \u2014 $it",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(places, key = { it.stationId }) { p ->
                                PlaceCard(p, isGas = isGas, onReport = { reportFor = p })
                            }
                        }
                    }
                }
            }
        }
    }

    reportFor?.let { place ->
        ReportPriceDialog(
            place = place,
            onDismiss = { reportFor = null },
            onSubmit = { price ->
                vm.reportPrice(place.stationId, price)
                reportFor = null
            }
        )
    }

    if (showAdd) {
        AddPlaceDialog(
            category = category,
            location = location,
            onDismiss = { showAdd = false },
            onSubmit = { name, brand, address, rating, hours ->
                vm.addPlace(name, brand, address, rating, hours)
                showAdd = false
            }
        )
    }
}

@Composable
fun CategorySelector(selected: String, onSelect: (String) -> Unit) {
    val labels = mapOf(
        "GAS" to "Gas", "COFFEE" to "Coffee", "RESTAURANT" to "Restaurants",
        "HOTEL" to "Hotels", "MECHANIC" to "Mechanics", "HOSPITAL" to "Hospitals"
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StationsViewModel.CATEGORIES.forEach { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                label = { Text(labels[cat] ?: cat) }
            )
        }
    }
}

@Composable
fun FuelGradeSelector(selected: String, onSelect: (String) -> Unit) {
    val grades = listOf("REGULAR", "MIDGRADE", "PREMIUM", "DIESEL")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grades.forEach { g ->
            FilterChip(
                selected = g == selected,
                onClick = { onSelect(g) },
                label = { Text(g.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
fun PlaceCard(p: CachedPlace, isGas: Boolean, onReport: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name ?: p.brand ?: "Place", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatDistance(p.distanceMeters), style = MaterialTheme.typography.bodySmall)
                    // Rating shown for any category when present.
                    if (p.rating != null) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("%.1f".format(p.rating), style = MaterialTheme.typography.bodySmall)
                    }
                }
                p.openingHours?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                // Tappable address: opens the spot in Google Maps (uses the exact
                // coordinates, so it works even when the address text is fuzzy).
                p.address?.let { addr ->
                    Text(
                        addr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            val uri = Uri.parse(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                        "${p.latitude},${p.longitude}"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    )
                }
            }
            // Price column ONLY for gas.
            if (isGas) {
                val hasPrice = p.price != null
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (hasPrice) "$${"%.2f".format(p.price)}" else "\u2014",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        // Dim seeded estimates so real community reports stand out.
                        color = if (hasPrice && !p.crowdsourced)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasPrice) {
                        if (p.crowdsourced) {
                            Text(
                                "reported ${priceAge(p.priceReportedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                "est. average",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onReport) { Text("Report") }
                }
            }
        }
    }
}

@Composable
fun ReportPriceDialog(place: CachedPlace, onDismiss: () -> Unit, onSubmit: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report price") },
        text = {
            Column {
                Text(place.name ?: "Station")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Price per gallon") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toDoubleOrNull()?.let(onSubmit) },
                enabled = text.toDoubleOrNull() != null
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPlaceDialog(
    category: String,
    location: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, brand: String?, address: String?, rating: Double?, hours: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    val isGas = category == "GAS"

    // Rating, when entered, must parse to 0..5.
    val ratingValid = rating.isBlank() || rating.toDoubleOrNull()?.let { it in 0.0..5.0 } == true
    val canSubmit = name.isNotBlank() && location != null && ratingValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${category.lowercase()} place") },
        text = {
            Column {
                Text(
                    location?.let { (lat, lon) -> "At your location: %.5f, %.5f".format(lat, lon) }
                        ?: "Waiting for location…",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name *") }, singleLine = true
                )
                OutlinedTextField(
                    value = brand, onValueChange = { brand = it },
                    label = { Text("Brand (optional)") }, singleLine = true
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Address (optional)") }, singleLine = true
                )
                // Rating + hours only matter for non-gas categories.
                if (!isGas) {
                    OutlinedTextField(
                        value = rating, onValueChange = { rating = it },
                        label = { Text("Rating 0–5 (optional)") },
                        isError = !ratingValid,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it },
                        label = { Text("Opening hours (optional)") }, singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        name,
                        brand.ifBlank { null },
                        address.ifBlank { null },
                        if (isGas) null else rating.toDoubleOrNull(),
                        if (isGas) null else hours.ifBlank { null }
                    )
                },
                enabled = canSubmit
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDistance(meters: Double): String {
    val miles = meters / 1609.34
    return if (miles < 0.1) "${meters.roundToInt()} m" else "${"%.1f".format(miles)} mi"
}

private fun priceAge(reportedAt: String?): String {
    if (reportedAt == null) return "no recent price"
    return try {
        val mins = Duration.between(Instant.parse(reportedAt), Instant.now()).toMinutes()
        when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    } catch (e: Exception) {
        ""
    }
}
