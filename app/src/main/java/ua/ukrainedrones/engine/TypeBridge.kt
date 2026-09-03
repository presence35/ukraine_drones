package ua.ukrainedrones.engine

import ua.ukrainedrones.ThreatType
import ua.ukrainedrones.ThreatTypeCatalog
import ua.ukrainedrones.ThreatTypeInfo

fun String.toThreatType(): ThreatType = ThreatType.fromApi(this)

fun threatTypeInfoByString(type: String): ThreatTypeInfo? =
    ThreatTypeCatalog.INFO[type.toThreatType()]
