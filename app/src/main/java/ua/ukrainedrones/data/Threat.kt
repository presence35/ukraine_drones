package ua.ukrainedrones

import org.json.JSONObject
import java.time.Instant

/** A single past coordinate fix of a threat's trail, with an optional timestamp. */
data class TrailPoint(
    val lat: Double,
    val lon: Double,
    val tMillis: Long?
)

enum class ThreatType(val apiKey: String) {
    SHAHED("shahed"),          // БпЛА — ударні (Shahed-type)
    FPV_LOITERING("fpv"),      // БпЛА — FPV / баражувальні (Lancet, Molniya)
    CRUISE_MISSILE("cruise"),  // Крилаті ракети
    BALLISTIC("ballistic"),    // Балістика
    KAB("kab"),                 // Керовані авіабомби
    AVIATION("aviation"),      // МіГ-31К
    RECON("recon"),            // Розвідка
    UNKNOWN("unknown");        // Невідомі

    companion object {
        fun fromApi(key: String?): ThreatType {
            if (key == null) return UNKNOWN
            return values().firstOrNull { it.apiKey == key } ?: run {
                // best-effort mapping from other possible API strings
                when (key.lowercase()) {
                    "uav", "drone" -> SHAHED
                    "lancet", "molniya", "loitering" -> FPV_LOITERING
                    "missile", "cruise_missile" -> CRUISE_MISSILE
                    "mig31", "mig31k", "kinzhal" -> AVIATION
                    else -> UNKNOWN
                }
            }
        }
    }
}

/** Static display metadata for each threat type — icon glyph, UA/EN label, UA/EN legend description. */
data class ThreatTypeInfo(
    val labelUa: String,
    val labelEn: String,
    val descriptionUa: String,
    val descriptionEn: String,
    val detailsUa: String,
    val detailsEn: String,
    val jokeUa: String = "",
    val jokeEn: String = ""
)

object ThreatTypeCatalog {
    val INFO: Map<ThreatType, ThreatTypeInfo> = mapOf(
        ThreatType.SHAHED to ThreatTypeInfo(
            labelUa = "БпЛА",
            labelEn = "UAV",
            descriptionUa = "Ударні безпілотники, зокрема «Шахеди».",
            descriptionEn = "Strike drones, including \"Shahed\"-type.",
            detailsUa = "Shahed-136 (Герань-2) — дрон-камікадзе. Великі хвилі, найчастіше вночі, низько (50–200 м) на ~180 км/год, БЧ ~40 кг, дальність до ~1000 км, години барражування. Через малу швидкість зазвичай є 10–30 хв. Звук нагадує мопед/газонокосарку. Сприймай будь-яку БпЛА-тривогу серйозно — дрон може відокремитися від хвилі будь-де.",
            detailsEn = "Shahed-136 / Geran-2 is a one-way attack drone. Large waves, often at night, flying low (50–200 m) at ~180 km/h, ~40 kg warhead, range up to ~1,000 km, hours of loiter. Because it's slow, you usually get 10–30 min of warning. It sounds like a moped/lawnmower. Treat any UAV alert as real — one can drop out of the wave at any point."
        ),
        ThreatType.FPV_LOITERING to ThreatTypeInfo(
            labelUa = "FPV-дрон",
            labelEn = "FPV drone",
            descriptionUa = "FPV та баражувальні боєприпаси (Ланцет, Молнія) — короткої дальності, біля лінії фронту.",
            descriptionEn = "FPV and loitering munitions (Lancet, Molniya) — short range, near the front line.",
            detailsUa = "Баражувальні боєприпаси малої дальності (Ланцет-3, Молнія) та FPV-камікадзе з БЧ 1–5 кг, ~100–120 км/год, дальність 10–40 км — кілька хвилин польоту. Загроза для техніки, артилерії, піхоти на передовій. Далеко від фронту це зазвичай не пряма загроза цивільним — індикатор активності, а не дальній удар.",
            detailsEn = "Short-range loitering munitions (Lancet-3, Molniya) and FPV kamikazes with a 1–5 kg warhead, ~100–120 km/h, 10–40 km range — minutes of flight. They threaten vehicles, artillery, troops at the front. Far behind the front line this is usually not a direct danger to civilians — treat it as a frontline indicator, not a long-range strike."
        ),
        ThreatType.CRUISE_MISSILE to ThreatTypeInfo(
            labelUa = "Крилата ракета",
            labelEn = "Cruise missile",
            descriptionUa = "Крилаті ракети повітряного, морського та наземного базування.",
            descriptionEn = "Air-, sea-, and ground-launched cruise missiles.",
            detailsUa = "Калібр, Х-101/555, Іскандер-К — основа дальніх ударів. Летять низько (часто <100 м), огинаючи рельєф, ~850 км/год. Дальність 1000–2500+ км, БЧ 400–500 кг. Час польоту 30–90 хв — зазвичай є справжнє попередження. У залпі бувають порожні імітатори. Координати можуть бути приблизними — дій за офіційною сиреною.",
            detailsEn = "Kalibr, Kh-101/555, Iskander-K — the backbone of long-range strikes. They fly low (often <100 m), terrain-hugging to hide from radar, at ~850 km/h. Range 1,000–2,500+ km, warhead 400–500 kg. Flight time 30–90 min, so real warning is usual. Salvoes can include empty decoys. Positions can be approximate — always follow the official siren."
        ),
        ThreatType.BALLISTIC to ThreatTypeInfo(
            labelUa = "Балістика",
            labelEn = "Ballistic",
            descriptionUa = "Балістичні ракети з коротким часом підльоту — найвищий пріоритет.",
            descriptionEn = "Ballistic missiles with short flight time — highest priority.",
            detailsUa = "Іскандер-М, KN-23, Кинджал — 3–8 Махів. Від пуску до прильоту 2–6 хв — часу лишити будівлю немає, лише укритися. Важка БЧ; перехоплення на кінцевій ділянці вкрай складне. Показана координата — екстраполяція від пуску, може бути неточною. Почув балістичну тривогу — негайно в укриття, не чекай підтвердження.",
            detailsEn = "Iskander-M, KN-23, air-launched Kinzhal reach 3–8 Mach. Launch-to-impact: 2–6 min — time only to shelter, not to leave a building. Heavy warheads; terminal interception very hard. The shown position is usually extrapolated from launch/splash and can be far off. On a ballistic alert, take cover immediately — don't wait to confirm."
        ),
        ThreatType.KAB to ThreatTypeInfo(
            labelUa = "КАБ",
            labelEn = "Guided bomb",
            descriptionUa = "Керовані авіабомби, що застосовуються поблизу лінії фронту.",
            descriptionEn = "Guided aerial bombs, used near the front line.",
            detailsUa = "Керовані плануючі бомби (ФАБ-250…1500 + УМПК) скидають за десятки км і планують на 40–70 км, здебільшого по фронту. БЧ — сотні кг, тому влучання руйнівні. ~900 км/год, але в глибокий тил не дістають. Тривога КАБ важлива для прифронтових/прикордонних регіонів.",
            detailsEn = "Guided glide bombs (FAB-250…1500 + UMPK) are dropped from dozens of km away and glide 40–70 km, mostly at the frontline. Warhead hundreds of kg — that's why impacts are so destructive. ~900 km/h, but they can't reach deep rear areas. KAB alerts matter mainly for border/frontline regions."
        ),
        ThreatType.AVIATION to ThreatTypeInfo(
            labelUa = "МіГ-31К",
            labelEn = "MiG-31K",
            descriptionUa = "Зліт носіїв «Кинджал» — загроза для всієї території країни.",
            descriptionEn = "Takeoff of \"Kinzhal\" carrier aircraft — threat to the entire country.",
            detailsUa = "МіГ-31К/І несе гіперзвуковий Кинджал, тож тривога про зліт — на всю країну: запуск можливий майже з будь-якої точки, до будь-якого міста долітає за хвилини. Літак летить високо й швидко; небезпека — ракета після пуску. Стався до зльоту МіГ-31К серйозно навіть без пуску — це надійний ранній сигнал.",
            detailsEn = "MiG-31K/І launches the Kinzhal hypersonic missile, so its takeoff alert covers the whole country — it can be fired from almost anywhere and reach any city in minutes. The plane flies high and fast; the danger is the missile after launch. Treat a MiG-31K takeoff alert seriously even before any launch — it's a reliable early signal."
        ),
        ThreatType.RECON to ThreatTypeInfo(
            labelUa = "Розвідка",
            labelEn = "Reconnaissance",
            descriptionUa = "Розвідувальна активність, що передує ударам.",
            descriptionEn = "Reconnaissance activity that precedes strikes.",
            detailsUa = "Малі спостережні дрони (Орлан-10/30, ZALA, Supercam): 100–1500 м, 90–150 км/год, кілька годин у повітрі. БЧ не несуть — розвідка та коригування артилерії. Їхня поява часто передує ударам — це попередження. Для цивільних у глибокому тилу: ознака активності, а не пряма загроза.",
            detailsEn = "Small observation drones (Orlan-10/30, ZALA, Supercam): 100–1,500 m, 90–150 km/h, hours aloft. No warhead — they spot and correct artillery. Their presence often precedes strikes, so a recon alert is a heads-up. For civilians deep behind the front: a warning sign, not a direct danger."
        ),
        ThreatType.UNKNOWN to ThreatTypeInfo(
            labelUa = "Невідомий",
            labelEn = "Unknown",
            descriptionUa = "Сигнали, тип яких ще уточнюється джерелами.",
            descriptionEn = "Signals whose type is still being confirmed by sources.",
            detailsUa = "Джерела бачать об'єкт, але тип ще не підтверджено — це може бути БпЛА, ракета чи імітатор. Показані швидкість і дальність — орієнтовні. Не вважай, що «це просто так»: стався до сигналу як до реального, поки він не розв'язався, і керуйся офіційними сигналами.",
            detailsEn = "Sources see an object but haven't confirmed the type — it could be a UAV, missile or decoy. Speed/range shown are guesses. Don't assume \"probably nothing\": treat it as a real alert until it resolves, and stay with official signals.",
            jokeUa = "Об'єкт Шредінгера: і дрон, і ракета — поки хтось не скаже інакше.",
            jokeEn = "Schrödinger's object: both a drone and a missile until someone says otherwise."
        )
    )
}

enum class Reliability { LOW, MEDIUM, HIGH, UNKNOWN;
    companion object {
        fun fromApi(v: String?): Reliability = when (v?.lowercase()) {
            "high" -> HIGH
            "medium", "mid" -> MEDIUM
            "low" -> LOW
            else -> UNKNOWN
        }
    }
}

data class Threat(
    val id: String,
    val type: ThreatType,
    val title: String,
    val region: String?,
    val district: String?,
    val locality: String?,
    val lat: Double,
    val lon: Double,
    val heading: Double?,
    val bearingDeg: Double?,   // velocity.bearingDeg — authoritative course, per NEPTUN SDK
    val status: String,        // active | stale | resolved
    val advisory: Boolean,
    val areaOnly: Boolean,     // true = no real point, lat/lon is an oblast centroid
    val confirmations: Int,    // sourceCount — how many independent sources confirm it
    val reliability: Reliability,
    val count: Int,            // group size of a raid, 0 = not specified
    val explanationShort: String?,
    val speedKmh: Double?,
    val uncertaintyKm: Double?,
    val positionQuality: String?,  // confirmed | approx
    val confirmedAt: String?,
    val confirmedAtMillis: Long?,
    val updatedAt: String?,
    val updatedAtMillis: Long?,
    val trail: List<TrailPoint> = emptyList()
) {
    /**
     * True when NEPTUN's SDK would dead-reckon this track (`predict()`): it needs a real
     * velocity (bearing + speed), a confirmed fix to anchor on, and an active status.
     */
    val flying: Boolean
        get() = bearingDeg != null && speedKmh != null && confirmedAtMillis != null && status == "active"

    /**
     * Direction to display, mirroring `predictPosition`'s heading resolution (the velocity
     * bearing when present, else the reported heading, else NEPTUN's deterministic A(id)
     * pseudo-course — what their own map shows when no course is known). When no real course
     * is reported but the course message names a destination ("…курсом на Київ"), the icon
     * faces the bearing toward that city instead of a pseudo-random angle. 0 = north. Kept in
     * lockstep with `predictPosition` so a marker that glides along its bearing always faces it.
     */
    val courseDeg: Double
        get() = bearingDeg ?: heading ?: courseFromMessage() ?: fallbackCourse(id)

    /**
     * Best-effort course from the NEPTUN course text when the velocity bearing and heading
     * are both absent: the message names a destination ("…курсом на Київ" / "у напрямку"),
     * resolve it in the city dictionary and aim the icon at its coordinates. Null when the
     * text names no known place or carries no direction — then the A(id) pseudo-course applies.
     */
    private fun courseFromMessage(): Double? {
        val text = explanationShort ?: title.takeIf { it.isNotBlank() } ?: return null
        for (pattern in COURSE_TARGET_PATTERNS) {
            val m = pattern.find(text) ?: continue
            val place = m.groupValues.getOrNull(1)?.trim()?.trimEnd('.', '—', '-') ?: continue
            val city = Cities.byUa[place] ?: continue
            return bearingDegrees(lat, lon, city.lat, city.lon)
        }
        return null
    }

    companion object {
        /** NEPTUN's deterministic pseudo-course when no real course is reported (their SDK `A(id)`). */
        fun fallbackCourse(id: String): Double {
            var t = 0
            for (ch in id) t = (t + ch.code) % 360
            return if (t == 0) 45.0 else t.toDouble()
        }

        fun fromJson(o: JSONObject): Threat? {
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            if (o.optString("id").isBlank()) return null

            fun optNullable(key: String): String? =
                o.optString(key, "").takeIf { it.isNotBlank() }

            val velocity = o.optJSONObject("velocity")
            val speedKmh = velocity?.takeIf { it.has("speedKmh") }?.optDouble("speedKmh", Double.NaN)
                ?.takeIf { !it.isNaN() }
            val bearingDeg = velocity?.takeIf { it.has("bearingDeg") }?.optDouble("bearingDeg", Double.NaN)
                ?.takeIf { !it.isNaN() }
            val uncertainty = o.optDouble("uncertaintyKm", Double.NaN)
                .takeIf { !it.isNaN() }

            val updatedAt = optNullable("updatedAt")
            val updatedAtMillis = runCatching { updatedAt?.let { Instant.parse(it).toEpochMilli() } }
                .getOrNull()
            val confirmedAt = optNullable("confirmedAt")
            val confirmedAtMillis = runCatching { confirmedAt?.let { Instant.parse(it).toEpochMilli() } }
                .getOrNull()

            return Threat(
                id = o.optString("id"),
                type = ThreatType.fromApi(if (o.has("type") && !o.isNull("type")) o.optString("type") else null),
                title = sanitizeCourse(o.optString("title", "")) ?: "",
                region = optNullable("region"),
                district = optNullable("district"),
                locality = optNullable("locality"),
                lat = lat,
                lon = lon,
                heading = if (o.has("heading") && !o.isNull("heading")) o.optDouble("heading") else null,
                bearingDeg = bearingDeg,
                status = o.optString("status", "active"),
                advisory = o.optBoolean("advisory", false),
                areaOnly = o.optBoolean("areaOnly", false),
                confirmations = o.optInt("sourceCount", o.optInt("sources", o.optInt("confirmations", 0))),
                reliability = Reliability.fromApi(
                    optNullable("confidenceLevel") ?: optNullable("reliability")
                ),
                count = o.optInt("count", 0),
                explanationShort = sanitizeCourse(optNullable("explanationShort")),
                speedKmh = speedKmh,
                uncertaintyKm = uncertainty,
                positionQuality = optNullable("positionQuality"),
                confirmedAt = confirmedAt,
                confirmedAtMillis = confirmedAtMillis,
                updatedAt = updatedAt,
                updatedAtMillis = updatedAtMillis,
                trail = parseTrail(o)
            )
        }

        /**
         * NEPTUN sometimes fills `explanationShort` with a bare confirmation count
         * (e.g. "Підтверджень: 3") that duplicates our own confirmations pill. Strip any
         * "підтвердж…" phrase and a leading bare-count ("3 джерелами: …"); drop the field
         * entirely when nothing course-relevant remains.
         */
        private fun sanitizeCourse(text: String?): String? {
            if (text == null) return null
            val cyr = "[А-Яа-яіїєґІЇЄҐ']"
            var t = text.replace(Regex("(?iu)підтвердж$cyr*"), " ")
            t = t.replace(Regex("^[\\s:.,—-]+"), "").trim()
            t = t.replaceFirst(Regex("(?iu)^\\d+\\s*(?:джерел$cyr*|sources?)?[\\s:.,—-]*"), "").trim()
            t = t.replace(Regex("(?iu)[\\s:.,—-]+\\d+(?:\\s*(?:джерел$cyr*|sources?|підтвердж$cyr*))?\\s*$"), "")
            if (t.isEmpty()) return null
            // A bare count like "3" or "3 джерела" carries no course info.
            if (t.matches(Regex("(?iu)^\\d+(?:\\s*(?:джерел$cyr*|sources?))?\\.?$"))) return null
            return t
        }

        /**
         * Best-effort trail parser; the API shape is not documented, so accept either
         * objects ({lat,lon[,t]} / {lon,lat}) or coordinate pairs. Returns empty on malformed input.
         */
        private fun parseTrail(o: JSONObject): List<TrailPoint> {
            val arr = o.optJSONArray("trail") ?: return emptyList()
            val out = ArrayList<TrailPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.isNull("lat") || item.isNull("lon")) continue
                val pLat = item.optDouble("lat", Double.NaN)
                val pLon = item.optDouble("lon", Double.NaN)
                if (pLat.isNaN() || pLon.isNaN()) continue
                val t = if (item.has("t") && !item.isNull("t")) {
                    runCatching { Instant.parse(item.optString("t")).toEpochMilli() }.getOrNull()
                } else null
                out.add(TrailPoint(pLat, pLon, t))
            }
            return out
        }
    }
}

data class OblastAlert(
    val key: String,
    val name: String,
    val oblast: String,
    val since: String?
)

/** True when the official alert belongs to the oblast whose adjectival stem is [token]. */
fun OblastAlert.inOblast(token: String): Boolean =
    oblast.startsWith(token, ignoreCase = true) || name.startsWith(token, ignoreCase = true)

/** Which data source reported the active official alert (used for the notification source tag). */
enum class AlertSource { NEPTUN, BACKUP, BOTH }

/**
 * Merge two oblast-alert lists into one, de-duplicated by whether they refer to the same
 * oblast. Uses the same startsWith/stem-tolerant comparison as [inOblast] rather than exact
 * string equality, since the two sources format oblast names independently (NEPTUN's
 * `oblast` field vs. alerts.com.ua's `name` reused as `oblast`) and are not guaranteed to
 * match verbatim. NEPTUN entries win on tie; the backup only adds oblasts NEPTUN didn't
 * already list.
 */
fun mergeAlerts(primary: List<OblastAlert>, backup: List<OblastAlert>): List<OblastAlert> {
    val out = ArrayList<OblastAlert>(primary.size + backup.size)
    out.addAll(primary)
    for (b in backup) {
        val alreadyCovered = primary.any { p ->
            p.oblast.startsWith(b.oblast, ignoreCase = true) ||
                b.oblast.startsWith(p.oblast, ignoreCase = true) ||
                p.name.startsWith(b.name, ignoreCase = true) ||
                b.name.startsWith(p.name, ignoreCase = true)
        }
        if (!alreadyCovered) out.add(b)
    }
    return out
}

private val COURSE_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("^(?:Група|Рій) БпЛА курсом на (.+)$", RegexOption.IGNORE_CASE) to "Group of UAVs heading toward {X}",
    Regex("^Шахеди? курсом на (.+)$", RegexOption.IGNORE_CASE) to "Shahed heading toward {X}",
    Regex("^БпЛА курсом на (.+)$", RegexOption.IGNORE_CASE) to "UAV heading toward {X}",
    Regex("^БпЛА (?:летить |рухається )?(?:зі сторони|з боку|з напрямку) (.+)$", RegexOption.IGNORE_CASE) to "UAV from the direction of {X}",
    Regex("^Шахеди? (?:зі сторони|з боку|з напрямку) (.+)$", RegexOption.IGNORE_CASE) to "Shahed from the direction of {X}",
    Regex("^(?:Ракета|Крилата ракета) (?:летить |рухається )?(?:у напрямку|в напрямку|на) (.+)$", RegexOption.IGNORE_CASE) to "Missile heading toward {X}",
    Regex("^Швидкісна ціль (?:у напрямку|в напрямку|на|курсом на) (.+)$", RegexOption.IGNORE_CASE) to "High-speed target heading toward {X}",
    Regex("^КАБи? (?:у напрямку|в напрямку|на|курсом на) (.+)$", RegexOption.IGNORE_CASE) to "Guided bomb heading toward {X}",
    Regex("^(?:БпЛА|Шахед|Шахеди|Група БпЛА|Рій БпЛА) (?:баражує|барражує|баражують|баражуют|барражують|барражуют|патрулює|патрулюють) над (.+)$", RegexOption.IGNORE_CASE) to "UAV patrolling over {X}",
    Regex("^(?:БпЛА|Шахед|Шахеди|Група БпЛА|Рій БпЛА) (?:баражує|барражує|баражують|баражуют|барражують|барражуют|патрулює|патрулюють) (?:в районі|у районі) (.+)$", RegexOption.IGNORE_CASE) to "UAV patrolling in the area of {X}",
    Regex("^(?:БпЛА|Шахед|Шахеди|Група БпЛА|Рій БпЛА) (?:баражує|барражує|баражують|баражуют|барражують|барражуют|патрулює|патрулюють) (.+)$", RegexOption.IGNORE_CASE) to "UAV patrolling {X}",
    Regex("^(?:БпЛА|Шахед|Шахеди) (?:маневрує|маневрують|маневруют|кружляє|кружляють) (?:в районі|у районі) (.+)$", RegexOption.IGNORE_CASE) to "UAV maneuvering in the area of {X}",
    Regex("^(?:БпЛА|Шахед|Шахеди) (?:маневрує|маневрують|маневруют|кружляє|кружляють) над (.+)$", RegexOption.IGNORE_CASE) to "UAV maneuvering over {X}",
    Regex("^БпЛА над (.+)$", RegexOption.IGNORE_CASE) to "UAV over {X}",
    Regex("^БпЛА (?:рухається|прямує) (?:в напрямку|у напрямку|в бік|у бік) (.+)$", RegexOption.IGNORE_CASE) to "UAV moving toward {X}",
    Regex("^Курс на (.+)$", RegexOption.IGNORE_CASE) to "Course toward {X}"
)

/** The subset of [COURSE_PATTERNS] that names a *destination* ("heading toward X") — used to
 *  aim the marker icon at that place when the stream reports no velocity bearing or heading.
 *  "From the direction of" / "over" are excluded: they don't face the threat toward a target. */
private val COURSE_TARGET_PATTERNS: List<Regex> = listOf(
    Regex("^(?:Група|Рій) БпЛА курсом на (.+)$", RegexOption.IGNORE_CASE),
    Regex("^Шахеди? курсом на (.+)$", RegexOption.IGNORE_CASE),
    Regex("^БпЛА курсом на (.+)$", RegexOption.IGNORE_CASE),
    Regex("^(?:Ракета|Крилата ракета) (?:летить |рухається )?(?:у напрямку|в напрямку|на) (.+)$", RegexOption.IGNORE_CASE),
    Regex("^Швидкісна ціль (?:у напрямку|в напрямку|на|курсом на) (.+)$", RegexOption.IGNORE_CASE),
    Regex("^КАБи? (?:у напрямку|в напрямку|на|курсом на) (.+)$", RegexOption.IGNORE_CASE),
    Regex("^БпЛА (?:рухається|прямує) (?:в напрямку|у напрямку|в бік|у бік) (.+)$", RegexOption.IGNORE_CASE),
    Regex("^Курс на (.+)$", RegexOption.IGNORE_CASE)
)

/**
 * Best-effort EN rendering of NEPTUN's course assessment (`explanationShort`), which is
 * always Ukrainian. Only known sentence templates are translated; the place name is looked
 * up in our city dictionary and otherwise transliterated — a proper noun is never semantically
 * translated, a wrong "translation" in a safety app is worse than none. Common (non-place)
 * words, however, come from [COMMON_WORDS] so a phrase like "над морем" reads "over the sea"
 * instead of the useless "over morem". Unrecognised sentence forms get their hard-coded
 * vocabulary swapped to EN and the remainder transliterated.
 */
fun translateCourseAssessment(text: String?, lang: AppLanguage): String? {
    if (text.isNullOrBlank()) return null
    if (lang == AppLanguage.UA) return text
    val t = text.trim()
    for ((pattern, template) in COURSE_PATTERNS) {
        val m = pattern.find(t) ?: continue
        val place = m.groupValues.getOrNull(1)?.trim()?.trimEnd('.', '—', '-') ?: continue
        val en = Cities.uaToEn[place]
            ?: COMMON_WORDS[place.lowercase()]
            ?: Transliteration.transliterate(place)
        return template.replace("{X}", en)
    }
    return courseFallback(text)
}

/** Military vocabulary hard-coded for the EN fallback; longest phrases first so "на" never
 *  swallows "у напрямку". Applied as whole words only (Unicode word boundaries). */
private val COURSE_GLOSSARY: List<Pair<String, String>> = listOf(
    "постійно змінює курс" to "constantly changing course",
    "локаційно втрачено" to "location lost",
    "локаційно втрачені" to "location lost",
    "припинив існування" to "ceased to exist",
    "припинили існування" to "ceased to exist",
    "рухається в напрямку" to "moving toward",
    "рухаються в напрямку" to "moving toward",
    "набирає висоту" to "climbing",
    "знижує висоту" to "descending",
    "знижується" to "descending",
    "змінює курс" to "changing course",
    "змінюють курс" to "changing course",
    "зміна курсу" to "course change",
    "змінив курс" to "changed course",
    "змінили курс" to "changed course",
    "швидкісна ціль" to "high-speed target",
    "крилата ракета" to "cruise missile",
    "балістична ракета" to "ballistic missile",
    "керована авіабомба" to "guided bomb",
    "курсом на" to "heading toward",
    "у напрямку" to "toward",
    "в напрямку" to "toward",
    "зі сторони" to "from the direction of",
    "з напрямку" to "from the direction of",
    "з боку" to "from the side of",
    "в районі" to "in the area of",
    "у районі" to "in the area of",
    "в межах" to "within",
    "у межах" to "within",
    "на межі" to "on the border of",
    "на стику" to "at the junction of",
    "по курсу" to "on course",
    "в бік" to "toward",
    "у бік" to "toward",
    "на низькій висоті" to "at low altitude",
    "на наднизькій висоті" to "at ultra-low altitude",
    "робота ппо" to "air defense active",
    "працює ппо" to "air defense active",
    "БпЛА" to "UAV",
    "шахед" to "Shahed",
    "шахеди" to "Shaheds",
    "КАБ" to "guided bomb",
    "КАБи" to "guided bombs",
    "ракета" to "missile",
    "ракети" to "missiles",
    "група" to "group",
    "групи" to "groups",
    "курс" to "course",
    "летить" to "flying",
    "летять" to "flying",
    "летят" to "flying",
    "рухається" to "moving",
    "рухаються" to "moving",
    "рухаются" to "moving",
    "рухают" to "moving",
    "прямує" to "heading",
    "прямують" to "heading",
    "прямуют" to "heading",
    "перетинає" to "crossing",
    "перетинають" to "crossing",
    "перетинают" to "crossing",
    "маневрує" to "maneuvering",
    "маневрують" to "maneuvering",
    "маневруют" to "maneuvering",
    "кружляє" to "circling",
    "кружляють" to "circling",
    "кружляют" to "circling",
    "заходить" to "entering",
    "заходять" to "entering",
    "заходят" to "entering",
    "повертає" to "turning",
    "повертають" to "turning",
    "повертают" to "turning",
    "патрулює" to "patrolling",
    "патрулюють" to "patrolling",
    "патрулюют" to "patrolling",
    "баражує" to "patrolling",
    "барражує" to "patrolling",
    "баражують" to "patrolling",
    "баражуют" to "patrolling",
    "барражують" to "patrolling",
    "барражуют" to "patrolling",
    "баражування" to "patrolling",
    "барражування" to "patrolling",
    "над" to "over",
    "біля" to "near",
    "транзитом" to "in transit",
    "відбій" to "all clear",
    "тривога" to "alert",
    "розвідник" to "recon drone",
    "розвідники" to "recon drones",
    "ціль" to "target",
    "цілі" to "targets",
    "пуск" to "launch",
    "пуски" to "launches"
)

/**
 * Common (non-place) Ukrainian words that carry real meaning for an EN reader and are
 * translated, not transliterated — "морем" → "morem" would be pointless. Looked up
 * whole-phrase first in the course-template slot; applied word-by-word in the fallback.
 * Lowercase keys; proper nouns must NOT be added here (they live in [Cities]).
 */
private val COMMON_WORDS: Map<String, String> = mapOf(
    "чорне море" to "Black Sea",
    "чорного моря" to "Black Sea",
    "чорним морем" to "Black Sea",
    "азовське море" to "Sea of Azov",
    "азовського моря" to "Sea of Azov",
    "азовським морем" to "Sea of Azov",
    "прибережна зона" to "the coastal zone",
    "прибережній зоні" to "the coastal zone",
    "повітряний простір" to "airspace",
    "повітряному просторі" to "airspace",
    "населений пункт" to "a populated area",
    "населеного пункту" to "a populated area",
    "населених пунктів" to "populated areas",
    "акваторія" to "the water area",
    "акваторії" to "the water area",
    "акваторією" to "the water area",
    "морем" to "the sea",
    "моря" to "the sea",
    "море" to "the sea",
    "берег" to "the coast",
    "берега" to "the coast",
    "берегом" to "the coast",
    "узбережжя" to "the coast",
    "узбережжі" to "the coast",
    "кордон" to "the border",
    "кордону" to "the border",
    "кордоном" to "the border",
    "межа" to "border",
    "межі" to "border",
    "межею" to "border",
    "простір" to "airspace",
    "русло" to "riverbed",
    "руслом" to "riverbed",
    "водосховище" to "reservoir",
    "водосховища" to "reservoir",
    "водосховищем" to "reservoir",
    "лиман" to "estuary",
    "лиману" to "estuary",
    "лиманом" to "estuary",
    "затока" to "gulf",
    "затоки" to "gulf",
    "затокою" to "gulf",
    "острів" to "island",
    "острова" to "island",
    "село" to "a village",
    "села" to "a village",
    "селом" to "a village",
    "місто" to "a city",
    "міста" to "a city",
    "містом" to "a city",
    "передмістя" to "suburbs",
    "район" to "district",
    "району" to "district",
    "районом" to "district",
    "область" to "oblast",
    "області" to "oblast",
    "областю" to "oblast",
    "рій" to "swarm",
    "барражує" to "patrolling",
    "баражує" to "patrolling",
    "баражують" to "patrolling",
    "баражуют" to "patrolling",
    "барражують" to "patrolling",
    "барражуют" to "patrolling",
    "атакує" to "attacks",
    "атакують" to "attacking",
    "атакуют" to "attacking",
    "поблизу" to "near",
    "поруч" to "near",
    "вздовж" to "along",
    "північ" to "north",
    "півдня" to "south",
    "південь" to "south",
    "захід" to "west",
    "заходу" to "west",
    "схід" to "east",
    "сходу" to "east",
    "північніше" to "north of",
    "південніше" to "south of",
    "західніше" to "west of",
    "східніше" to "east of"
)

private fun courseFallback(raw: String): String {
    var out = raw
    val dictionary = (COURSE_GLOSSARY + COMMON_WORDS.entries.map { it.key to it.value })
        .distinctBy { it.first.lowercase() }
        .sortedByDescending { it.first.length }

    for ((ua, en) in dictionary) {
        val replacement = { match: MatchResult ->
            val w = match.value
            when {
                w.all { it.isUpperCase() } -> en.uppercase()
                w[0].isUpperCase() -> en.replaceFirstChar { it.uppercase() }
                else -> en
            }
        }
        out = out.replace(
            Regex("(?iu)(?<![\\p{L}])" + Regex.escape(ua) + "(?![\\p{L}])"),
            replacement
        )
    }
    return Transliteration.transliterate(out)
}
