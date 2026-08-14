package ua.ukrainedrones

import org.json.JSONObject
import java.time.Instant

/** A single past coordinate fix of a threat's trail, with an optional timestamp. */
data class TrailPoint(
    val lat: Double,
    val lon: Double,
    val tMillis: Long?
)

/**
 * Threat types whose approach timing makes the red/yellow tier distinction pointless —
 * crossing even the yellow boundary is seconds away. With "fast objects alert sooner" on,
 * they fire the urgent siren at any zone entry instead of waiting for the red circle.
 */
val FAST_THREAT_TYPES: Set<ThreatType> = setOf(
    ThreatType.BALLISTIC,
    ThreatType.CRUISE_MISSILE,
    ThreatType.AVIATION,
    ThreatType.KAB
)

enum class ThreatType(val apiKey: String) {
    SHAHED("shahed"),          // БпЛА — ударні (Shahed-type)
    FPV_LOITERING("fpv"),      // БпЛА — FPV / баражувальні (Lancet, Molniya)
    CRUISE_MISSILE("cruise"),  // Крилаті ракети
    BALLISTIC("ballistic"),    // Балістика
    KAB("kab"),                 // Керовані авіабомби
    AVIATION("aviation"),      // Авіація / МіГ-31К
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
            detailsEn = "Kalibr, Kh-101/555, Iskander-K — the backbone of long-range strikes. They fly low (often <100 m), terrain-hugging to hide from radar, at ~850 km/h. Range 1,000–2,500+ km, warhead 400–500 kg. Flight time 30–90 min, so real warning is usual. Salvoes can include empty decoys. Positions can be rough — always follow the official siren."
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
            labelUa = "Авіація / МіГ-31К",
            labelEn = "Aviation / MiG-31K",
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
     * Direction to display, mirroring NEPTUN's `predict().heading`: the velocity bearing while
     * the track is live, otherwise the reported heading, otherwise NEPTUN's deterministic A(id)
     * pseudo-course (what their own map shows when no course is known). 0 = north.
     */
    val courseDeg: Double
        get() = if (flying) bearingDeg!! else heading ?: fallbackCourse(id)
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

private val COURSE_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("^Група БпЛА курсом на (.+)$") to "Group of UAVs heading toward {X}",
    Regex("^Шахеди? курсом на (.+)$") to "Shahed heading toward {X}",
    Regex("^БпЛА курсом на (.+)$") to "UAV heading toward {X}",
    Regex("^БпЛА (?:летить )?(?:зі сторони|з боку) (.+)$") to "UAV from the direction of {X}",
    Regex("^Шахеди? зі сторони (.+)$") to "Shahed from the direction of {X}",
    Regex("^Ракета (?:летить )?(?:у напрямку|на) (.+)$") to "Missile heading toward {X}",
    Regex("^БпЛА над (.+)$") to "UAV over {X}",
    Regex("^БпЛА рухається в напрямку (.+)$") to "UAV moving toward {X}",
    Regex("^Курс на (.+)$") to "Course toward {X}"
)

/**
 * Best-effort EN translation of NEPTUN's course assessment (`explanationShort`), which is
 * always Ukrainian. Only known sentence templates are translated; the place name is looked
 * up in our city dictionary, and anything unrecognised stays as the raw Ukrainian text —
 * a wrong "translation" in a safety app is worse than none.
 */
fun translateCourseAssessment(text: String?, lang: AppLanguage): String? {
    if (text.isNullOrBlank()) return null
    if (lang == AppLanguage.UA) return text
    val t = text.trim()
    for ((pattern, template) in COURSE_PATTERNS) {
        val m = pattern.find(t) ?: continue
        val place = m.groupValues.getOrNull(1)?.trim()?.trimEnd('.', '—', '-') ?: continue
        val en = Cities.uaToEn[place] ?: place
        return template.replace("{X}", en)
    }
    return text
}
