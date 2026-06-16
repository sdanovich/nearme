package com.example.nearme.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nearme.NearMeApp
import com.example.nearme.data.local.CachedPlace
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Backs the UI from the Room cache, which is the single source of truth for the
 * screen. The list is a Flow from Room, so it paints instantly on open (even
 * offline) and updates the moment a refresh writes fresh data.
 *
 * The user picks ONE category at a time (GAS, COFFEE, ...). Price is only
 * meaningful for GAS; other categories show rating/distance/hours.
 *
 * Run/Pause: when "running", a background loop refreshes every REFRESH_INTERVAL.
 * Pausing stops the loop. A manual refresh is always available regardless.
 *
 * refresh() reads from the backend (Postgres) and writes through to Room. It
 * never writes to Postgres. The one path that does is reportPrice() (gas only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StationsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L
        private const val LOCATION_TIMEOUT_MS = 4_000L
        val CATEGORIES = listOf("GAS", "COFFEE", "RESTAURANT", "HOTEL", "MECHANIC", "HOSPITAL")
    }

    private val repo = (app as NearMeApp).repository
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    private val category = MutableStateFlow("GAS")
    val currentCategory: StateFlow<String> = category

    /** Fuel grade, only used when category == GAS. */
    private val fuelGrade = MutableStateFlow("REGULAR")
    val currentFuelGrade: StateFlow<String> = fuelGrade

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Last location read from the device, shown on screen for debugging. */
    private val _location = MutableStateFlow<Pair<Double, Double>?>(null)
    val location: StateFlow<Pair<Double, Double>?> = _location

    private var loopJob: Job? = null

    /** Live list from Room for the selected category — the UI observes this. */
    val places: StateFlow<List<CachedPlace>> =
        category.flatMapLatest { c -> repo.observePlaces(c) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategory(c: String) {
        if (c == category.value) return
        category.value = c
        refresh()
    }

    fun setFuelGrade(grade: String) {
        fuelGrade.value = grade
        if (category.value == "GAS") refresh()
    }

    /** Toggle the periodic auto-refresh loop. */
    fun toggleRunning() {
        if (_running.value) pause() else run()
    }

    private fun run() {
        if (_running.value) return
        _running.value = true
        loopJob = viewModelScope.launch {
            while (isActive && _running.value) {
                refreshOnce()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun pause() {
        _running.value = false
        loopJob?.cancel()
        loopJob = null
    }

    /** Manual one-off refresh (always available, independent of run/pause). */
    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        _refreshing.value = true
        _error.value = null
        try {
            val loc = currentLocation()
            _location.value = loc
            if (loc == null) {
                _error.value = "Couldn't get location."
            } else {
                repo.refresh(loc.first, loc.second, category.value, fuelGrade.value)
            }
        } catch (e: Exception) {
            // On failure the UI still shows the last cached list from Room.
            _error.value = e.message ?: "Refresh failed"
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Create a place of the currently-selected category at the device's current
     * location, then refresh so it appears in the list. rating/openingHours are
     * only meaningful for non-gas categories.
     */
    fun addPlace(name: String, brand: String?, address: String?, rating: Double?, openingHours: String?) {
        viewModelScope.launch {
            try {
                val loc = currentLocation()
                if (loc == null) {
                    _error.value = "Couldn't get location."
                    return@launch
                }
                repo.createPlace(
                    name = name.trim(),
                    brand = brand?.trim()?.ifBlank { null },
                    address = address?.trim()?.ifBlank { null },
                    lat = loc.first, lon = loc.second,
                    category = category.value,
                    rating = rating,
                    openingHours = openingHours?.trim()?.ifBlank { null }
                )
                refreshOnce()
            } catch (e: Exception) {
                _error.value = "Couldn't add place: ${e.message}"
            }
        }
    }

    /** Gas-only: submit a crowdsourced price for a station. */
    fun reportPrice(stationId: Long, price: Double) {
        viewModelScope.launch {
            try {
                repo.reportPrice(stationId, fuelGrade.value, price)
                refreshOnce()
            } catch (e: Exception) {
                _error.value = "Couldn't submit price: ${e.message}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Pair<Double, Double>? {
        // Primary: request a fresh fix. Bounded by a timeout because on a broken
        // emulator GPS HAL the high-accuracy request can hang indefinitely.
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            awaitLocation { fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null) }
        }?.let { return it }
        // Secondary: the last known fix, if any.
        return awaitLocation { fused.lastLocation }
    }

    private suspend fun awaitLocation(request: () -> Task<Location>): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            request()
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) loc.latitude to loc.longitude else null)
                }
                .addOnFailureListener { cont.resume(null) }
        }
}
