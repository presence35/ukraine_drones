package ua.ukrainedrones

import org.osmdroid.util.GeoPoint

data class ThreatEvaluationResult(
    val threatsInner: List<Threat> = emptyList(),
    val threatsOuter: List<Threat> = emptyList(),
    val zoneThreats: Map<String, ThreatZone> = emptyMap(),
    val mapThreats: List<Threat> = emptyList(),
    val threatScores: List<Double> = emptyList(),
    val activeZone: ThreatZone? = null
)

object ThreatEvaluator {

    fun evaluate(
        neptun: NeptunState,
        params: ZoneParams,
        focusLocation: LatLng?,
        mapEnabledTypes: Set<ThreatType> = ThreatTypeCatalog.INFO.keys,
        alertEnabledTypes: Set<ThreatType> = mapEnabledTypes,
        now: Long = System.currentTimeMillis()
    ): ThreatEvaluationResult {
        val inInner = mutableListOf<Threat>()
        val inOuter = mutableListOf<Threat>()
        val zoneThreatsMap = LinkedHashMap<String, ThreatZone>()
        val mapThreats = mutableListOf<Threat>()
        val threatScores = mutableListOf<Double>()

        for (t in neptun.threats.values) {
            if (t.status == "resolved" || t.areaOnly || t.isGhost(now)) continue
            if (t.type !in mapEnabledTypes) continue
            val stale = t.isStale(now)
            if (!stale) {
                ThreatSpeedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            }
            val predicted = ThreatSpeedTracker.estimate(t.id, t)
                ?.let { predictPosition(t, it, now) } ?: GeoPoint(t.lat, t.lon)
            mapThreats.add(t)
            if (stale || focusLocation == null) continue
            if (t.advisory || t.type !in alertEnabledTypes) continue

            val tierLat = if (t.type in FastThreatTypes) predicted.latitude else t.lat
            val tierLon = if (t.type in FastThreatTypes) predicted.longitude else t.lon
            val distKm = distanceMeters(
                focusLocation.lat, focusLocation.lon, tierLat, tierLon
            ) / 1000.0
            val speedKmh = ThreatSpeedTracker.estimate(t.id, t)?.times(3.6)
            val tier = zoneTier(t, distKm, speedKmh, params)
            if (tier != null) {
                val eta = etaMinutes(distKm, speedKmh)
                val (redVal, yellowVal) =
                    if (t.type in FastThreatTypes) params.fastRedMin to params.fastYellowMin
                    else params.slowRedKm to params.slowYellowKm
                threatScores.add(
                    ThreatLevelModel.scoreOf(t, distKm, eta, redVal, yellowVal, now)
                )
                zoneThreatsMap[t.id] = tier
                when (tier) {
                    ThreatZone.INNER -> inInner.add(t)
                    ThreatZone.OUTER -> inOuter.add(t)
                }
            }
        }

        val activeZone = when {
            inInner.isNotEmpty() -> ThreatZone.INNER
            inOuter.isNotEmpty() -> ThreatZone.OUTER
            else -> null
        }

        return ThreatEvaluationResult(
            threatsInner = inInner,
            threatsOuter = inOuter,
            zoneThreats = zoneThreatsMap,
            mapThreats = mapThreats,
            threatScores = threatScores,
            activeZone = activeZone
        )
    }

    fun zoneThreats(
        st: NeptunState,
        params: ZoneParams,
        focus: LatLng?,
        enabled: Set<ThreatType>,
        now: Long
    ): Map<String, ThreatZone> {
        if (focus == null) return emptyMap()
        val map = LinkedHashMap<String, ThreatZone>()
        for (t in st.threats.values) {
            if (t.status == "resolved" || t.isStale(now) || t.areaOnly) continue
            if (t.type !in enabled) continue
            if (t.advisory) continue
            ThreatSpeedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val estimate = ThreatSpeedTracker.estimate(t.id, t)
            val p = estimate?.let { predictPosition(t, it, now) }
            val lat = if (t.type in FastThreatTypes) (p?.latitude ?: t.lat) else t.lat
            val lon = if (t.type in FastThreatTypes) (p?.longitude ?: t.lon) else t.lon
            val distKm = distanceMeters(focus.lat, focus.lon, lat, lon) / 1000.0
            val speedKmh = estimate?.times(3.6)
            val zone = zoneTier(t, distKm, speedKmh, params) ?: continue
            map[t.id] = zone
        }
        return map
    }

    fun buildOfficialReason(
        st: NeptunState,
        token: String?,
        lang: AppLanguage,
        focus: LatLng?,
        params: ZoneParams,
        now: Long,
        enabled: Set<ThreatType>,
        regionFallback: String
    ): Pair<String?, String?> {
        var best: Threat? = null
        var bestScore = -1.0
        for (t in st.threats.values) {
            if (t.status != "active" || t.advisory || t.areaOnly || t.type !in enabled ||
                isExpired(t, now) || !inFocusOblast(t, token)
            ) continue
            ThreatSpeedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val predicted = ThreatSpeedTracker.estimate(t.id, t)?.let { predictPosition(t, it, now) }
            val lat = predicted?.latitude ?: t.lat
            val lon = predicted?.longitude ?: t.lon
            val distKm = if (focus != null) distanceMeters(focus.lat, focus.lon, lat, lon) / 1000.0 else null
            val speed = ThreatSpeedTracker.estimate(t.id, t)
            val eta = if (speed != null && speed > 0.0 && distKm != null) distKm / (speed * 3.6) * 60.0 else null
            val score = if (distKm != null) {
                val (redVal, yellowVal) =
                    if (t.type in FastThreatTypes) params.fastRedMin to params.fastYellowMin
                    else params.slowRedKm to params.slowYellowKm
                ThreatLevelModel.scoreOf(t, distKm, eta, redVal, yellowVal, now)
            } else 0.0
            if (score > bestScore) {
                bestScore = score
                best = t
            }
        }
        return if (best != null) {
            val reason = translateCourseAssessment(best.explanationShort, lang) ?: threatBody(best, lang)
            reason to best.id
        } else {
            String.format(Strings.get(lang).notifReasonFormat, regionFallback) to null
        }
    }

    fun inFocusOblast(t: Threat, token: String?): Boolean {
        if (token == null) return false
        return inOblast(t.region, t.district, t.locality, token)
    }

    /** True when any of the threat's place fields falls in the oblast whose stem is [token]. */
    fun inOblast(region: String?, district: String?, locality: String?, token: String?): Boolean {
        if (token == null) return false
        return (region != null && inOblastText(region, token)) ||
            (district != null && inOblastText(district, token)) ||
            (locality != null && inOblastText(locality, token))
    }

    private fun inOblastText(text: String, token: String): Boolean =
        text.startsWith(token, ignoreCase = true) || Cities.cityOblast[text] == token

    fun threatBody(t: Threat, lang: AppLanguage): String {
        val info = ThreatTypeCatalog.INFO.getValue(t.type)
        val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
        val where = t.locality ?: t.district ?: t.region
        val whereText = if (where == null) null else if (lang == AppLanguage.UA) where
        else Cities.byUa[where]?.nameEn ?: Transliteration.transliterate(where)
        return if (whereText != null) "$label — $whereText" else label
    }

    fun computeProximity(
        t: Threat?,
        focusLocation: LatLng?,
        params: ZoneParams,
        now: Long
    ): ThreatProximity? {
        if (t == null || t.areaOnly) return null
        ThreatSpeedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
        val speedPair = ThreatSpeedTracker.estimateWithSource(t.id, t)
        val speed = speedPair?.first
        val predicted = speed?.let { predictPosition(t, it, now) }
            ?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(t.lat, t.lon)
        val distUser = focusLocation?.let {
            distanceMeters(it.lat, it.lon, predicted.lat, predicted.lon) / 1000.0
        }
        val etaUser = if (distUser != null && speed != null && speed > 0.0) {
            distUser / (speed * 3.6) * 60.0
        } else null
        return ThreatProximity(
            predicted = predicted,
            distToUserKm = distUser,
            etaToUserMin = etaUser,
            params = params,
            speedSource = speedPair?.second ?: SpeedSource.TYPICAL,
            speedKmh = speed?.times(3.6)
        )
    }
}
