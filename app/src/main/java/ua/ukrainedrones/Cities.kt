package ua.ukrainedrones

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/** A place name drawn on the map. `major` cities always show; `minor` only at higher zoom. */
data class City(
    val nameUa: String,
    val lat: Double,
    val lon: Double,
    val major: Boolean,
    val nameEn: String = Transliteration.transliterate(nameUa)
)

/**
 * Curated city list (no hoods/villages) so users get a sense of distance/scale while zooming.
 * Cities are grouped by oblast; each region declares its alert-match stem once. Names are drawn
 * in the current UI language, replacing the (Cyrillic, non-English) labels baked into the
 * basemap tiles. EN names derive from the app's own КМУ №55 transliteration. Minor cities are
 * map-context only — attribution/banner always resolve to majors.
 */
object Cities {

    /** One oblast region: [stem] is matched against the alert oblast name; [cities] its places. */
    private data class Region(val stem: String, val cities: List<City>)

    private val REGIONS: List<Region> = listOf(
        Region("Київськ", listOf(
            City("Київ", 50.4501, 30.5234, true),
            City("Біла Церква", 49.7954, 30.1167, false),
            City("Бровари", 50.5184, 30.7908, false),
            City("Бориспіль", 50.3506, 30.9553, false),
            City("Ірпінь", 50.5217, 30.2506, false),
            City("Фастів", 50.0767, 29.9184, false),
            City("Васильків", 50.1836, 30.3213, false),
            City("Обухів", 50.1300, 30.6180, false),
            City("Вишгород", 50.5826, 30.4883, false),
            City("Буча", 50.5434, 30.2207, false),
            City("Переяслав", 50.0650, 31.4500, false),
            City("Яготин", 50.2550, 31.7820, false),
            City("Миронівка", 49.6600, 30.9840, false),
            City("Сквира", 49.7300, 29.6700, false),
            City("Тетіїв", 49.3730, 29.6940, false)
        )),
        Region("Одеськ", listOf(
            City("Одеса", 46.4825, 30.7233, true),
            City("Чорноморськ", 46.3036, 30.6566, false),
            City("Южне", 46.6226, 31.1014, false),
            City("Білгород-Дністровський", 46.1947, 30.3484, false),
            City("Ізмаїл", 45.3503, 28.8348, false),
            City("Рені", 45.4571, 28.2867, false),
            City("Кілія", 45.4552, 29.2637, false),
            City("Овідіополь", 46.2450, 30.4413, false),
            City("Біляївка", 46.4829, 30.1985, false),
            City("Березівка", 47.2044, 30.9093, false),
            City("Роздільна", 46.8493, 30.0833, false),
            City("Арциз", 45.9919, 29.4183, false),
            City("Татарбунари", 45.8527, 29.6144, false),
            City("Подільськ", 47.7433, 29.5350, false),
            City("Балта", 47.9364, 29.6226, false),
            City("Болград", 45.6810, 28.6130, false),
            City("Ананьїв", 47.7220, 29.9580, false),
            City("Кодима", 48.1020, 29.1240, false),
            City("Саврань", 48.1320, 30.0820, false),
            City("Любашівка", 47.8370, 30.2630, false)
        )),
        Region("Львівськ", listOf(
            City("Львів", 49.8397, 24.0297, true),
            City("Дрогобич", 49.3500, 23.5050, false),
            City("Стрий", 49.2620, 23.8500, false),
            City("Самбір", 49.5180, 23.1970, false),
            City("Червоноград", 50.3850, 24.2270, false),
            City("Борислав", 49.2860, 23.4250, false),
            City("Трускавець", 49.2760, 23.5060, false),
            City("Броди", 50.0790, 25.1530, false),
            City("Золочів", 49.8060, 24.9000, false),
            City("Сокаль", 50.4860, 24.2790, false),
            City("Яворів", 49.9380, 23.3840, false),
            City("Мостиська", 49.7940, 23.1500, false),
            City("Жовква", 50.0580, 23.9700, false),
            City("Пустомити", 49.7130, 23.9050, false)
        )),
        Region("Дніпропетровськ", listOf(
            City("Дніпро", 48.4647, 35.0462, true),
            City("Кривий Ріг", 47.9105, 33.3918, true),
            City("Кам'янське", 48.5147, 34.6102, false),
            City("Нікополь", 47.5720, 34.3580, false),
            City("Павлоград", 48.5170, 35.8730, false),
            City("Новомосковськ", 48.6330, 35.2200, false),
            City("Марганець", 47.6430, 34.6150, false),
            City("Жовті Води", 48.3480, 33.5000, false),
            City("Покров", 47.6670, 34.0910, false),
            City("П'ятихатки", 48.4130, 33.7080, false),
            City("Апостолове", 47.6600, 33.7160, false),
            City("Верхньодніпровськ", 48.6550, 34.3350, false),
            City("Синельникове", 48.3180, 35.5190, false),
            City("Першотравенськ", 48.3460, 36.3990, false),
            City("Тернівка", 48.5230, 36.0820, false)
        )),
        Region("Харківськ", listOf(
            City("Харків", 49.9935, 36.2304, true),
            City("Чугуїв", 49.8370, 36.9390, false),
            City("Лозова", 48.8890, 36.3900, false),
            City("Ізюм", 49.2090, 37.2520, false),
            City("Куп'янськ", 49.7100, 37.6210, false),
            City("Первомайський", 49.3860, 36.2140, false),
            City("Балаклія", 49.4620, 36.8590, false),
            City("Богодухів", 50.1640, 35.5270, false),
            City("Вовчанськ", 50.2900, 36.9400, false),
            City("Зміїв", 49.6950, 36.3610, false),
            City("Дергачі", 50.1150, 36.1180, false),
            City("Мерефа", 49.8180, 36.0500, false),
            City("Красноград", 49.3810, 35.4420, false),
            City("Люботин", 49.9470, 35.9290, false)
        )),
        Region("Запорізьк", listOf(
            City("Запоріжжя", 47.8388, 35.1396, true),
            City("Мелітополь", 46.8380, 35.3600, false),
            City("Бердянськ", 46.7540, 36.7890, false),
            City("Енергодар", 47.4980, 34.6560, false),
            City("Токмак", 47.2550, 35.7130, false),
            City("Пологи", 47.4820, 36.2540, false),
            City("Василівка", 47.4420, 35.2750, false),
            City("Приморськ", 46.7340, 36.3550, false),
            City("Оріхів", 47.5670, 35.7840, false),
            City("Гуляйполе", 47.6640, 36.2580, false),
            City("Вільнянськ", 47.9440, 35.4350, false),
            City("Кам'янка-Дніпровська", 47.4850, 34.4140, false),
            City("Михайлівка", 47.2700, 35.2210, false),
            City("Розівка", 47.3780, 37.0670, false)
        )),
        Region("Вінницьк", listOf(
            City("Вінниця", 49.2331, 28.4682, true),
            City("Жмеринка", 49.0370, 28.1130, false),
            City("Могилів-Подільський", 48.4470, 27.7980, false),
            City("Хмільник", 49.5540, 27.9580, false),
            City("Козятин", 49.7180, 28.8400, false),
            City("Гайсин", 48.8120, 29.3930, false),
            City("Бар", 49.0780, 27.6820, false),
            City("Шаргород", 48.7540, 28.0780, false),
            City("Липовець", 49.2170, 29.0560, false),
            City("Іллінці", 49.1030, 29.2080, false),
            City("Немирів", 48.9680, 28.8440, false),
            City("Калинівка", 49.4470, 28.5260, false),
            City("Тульчин", 48.6740, 28.8500, false),
            City("Ладижин", 48.6850, 29.2400, false)
        )),
        Region("Миколаївськ", listOf(
            City("Миколаїв", 46.9750, 31.9946, true),
            City("Вознесенськ", 47.5653, 31.3311, false),
            City("Первомайськ", 48.0446, 30.8506, false),
            City("Южноукраїнськ", 47.8150, 31.1780, false),
            City("Очаків", 46.6160, 31.5400, false),
            City("Новий Буг", 47.6940, 32.5160, false),
            City("Баштанка", 47.4040, 32.4400, false),
            City("Снігурівка", 47.0710, 32.7960, false),
            City("Ольшанське", 47.1990, 31.7970, false),
            City("Веселинове", 47.3570, 31.2350, false),
            City("Казанка", 47.8360, 32.8230, false)
        )),
        Region("Херсонськ", listOf(
            City("Херсон", 46.6354, 32.6169, true),
            City("Нова Каховка", 46.7560, 33.3850, false),
            City("Каховка", 46.7980, 33.4760, false),
            City("Генічеськ", 46.1750, 34.8000, false),
            City("Скадовськ", 46.1180, 32.9110, false),
            City("Олешки", 46.6290, 32.7230, false),
            City("Гола Пристань", 46.5250, 32.5260, false),
            City("Берислав", 46.8400, 33.4270, false),
            City("Таврійськ", 46.7500, 33.4380, false),
            City("Чаплинка", 46.3450, 33.5320, false),
            City("Велика Лепетиха", 47.1740, 33.9370, false),
            City("Нижні Сірогози", 47.0250, 34.3790, false)
        )),
        Region("Кіровоградськ", listOf(
            City("Кропивницький", 48.5079, 32.2603, true),
            City("Олександрія", 48.6690, 33.1150, false),
            City("Світловодськ", 49.0490, 33.2510, false),
            City("Знам'янка", 48.7140, 32.6740, false),
            City("Долинська", 48.1130, 32.7500, false),
            City("Новоукраїнка", 48.3150, 31.5250, false),
            City("Бобринець", 48.0590, 32.1580, false),
            City("Гайворон", 48.3390, 29.8480, false),
            City("Новомиргород", 48.7810, 31.6430, false),
            City("Помічна", 48.2410, 31.4070, false),
            City("Мала Виска", 48.6460, 31.6340, false),
            City("Устинівка", 48.1540, 32.5350, false)
        )),
        Region("Полтавськ", listOf(
            City("Полтава", 49.5883, 34.5514, true),
            City("Кременчук", 49.0680, 33.4230, false),
            City("Горішні Плавні", 49.0110, 33.6500, false),
            City("Лубни", 50.0190, 32.9970, false),
            City("Миргород", 49.9650, 33.6110, false),
            City("Гадяч", 50.3650, 33.9910, false),
            City("Пирятин", 50.2420, 32.5110, false),
            City("Карлівка", 49.4570, 35.1300, false),
            City("Хорол", 49.7830, 33.2780, false),
            City("Зіньків", 50.2090, 34.3590, false),
            City("Лохвиця", 50.3590, 33.2650, false),
            City("Решетилівка", 49.5590, 34.0740, false),
            City("Кобеляки", 49.1410, 34.2050, false),
            City("Чутове", 49.7170, 35.1610, false)
        )),
        Region("Черкаськ", listOf(
            City("Черкаси", 49.4444, 32.0598, true),
            City("Умань", 48.7484, 30.2211, true),
            City("Сміла", 49.2170, 31.8710, false),
            City("Золотоноша", 49.6680, 32.0400, false),
            City("Канів", 49.7510, 31.4700, false),
            City("Корсунь-Шевченківський", 49.4170, 31.2600, false),
            City("Звенигородка", 49.0780, 30.9690, false),
            City("Шпола", 49.0060, 31.3910, false),
            City("Ватутіне", 49.0090, 31.0720, false),
            City("Тальне", 48.8860, 30.6930, false),
            City("Христинівка", 48.8200, 29.9670, false),
            City("Монастирище", 48.9940, 29.7950, false),
            City("Чигирин", 49.0780, 32.6610, false),
            City("Жашків", 49.2470, 30.1110, false),
            City("Городище", 49.2880, 31.4510, false)
        )),
        Region("Хмельницьк", listOf(
            City("Хмельницький", 49.4220, 26.9871, true),
            City("Кам'янець-Подільський", 48.6840, 26.5910, false),
            City("Шепетівка", 50.1850, 27.0640, false),
            City("Нетішин", 50.3400, 26.6430, false),
            City("Славута", 50.3020, 26.8650, false),
            City("Старокостянтинів", 49.7570, 27.2210, false),
            City("Городок", 49.1640, 26.5730, false),
            City("Волочиськ", 49.5400, 26.1860, false),
            City("Деражня", 49.2680, 27.4330, false),
            City("Дунаївці", 48.8880, 26.8550, false),
            City("Полонне", 50.1200, 27.5070, false),
            City("Красилів", 49.6520, 26.9720, false),
            City("Ізяслав", 50.1180, 26.8200, false),
            City("Чемерівці", 49.0030, 26.3500, false)
        )),
        Region("Житомирськ", listOf(
            City("Житомир", 50.2546, 28.6587, true),
            City("Бердичів", 49.8930, 28.6020, false),
            City("Коростень", 50.9520, 28.6370, false),
            City("Новоград-Волинський", 50.5950, 27.6170, false),
            City("Малин", 50.7720, 29.2700, false),
            City("Овруч", 51.3260, 28.8100, false),
            City("Коростишів", 50.3180, 29.0590, false),
            City("Радомишль", 50.4970, 29.2220, false),
            City("Брусилів", 50.2840, 29.5260, false),
            City("Чуднів", 50.0540, 28.1130, false),
            City("Андрушівка", 50.0180, 29.0190, false),
            City("Попільня", 50.0000, 29.4600, false),
            City("Олевськ", 51.2270, 27.6480, false),
            City("Хорошів", 50.5970, 28.4450, false)
        )),
        Region("Рівненськ", listOf(
            City("Рівне", 50.6199, 26.2516, true),
            City("Вараш", 51.3400, 25.8500, false),
            City("Дубно", 50.4170, 25.7500, false),
            City("Острог", 50.3300, 26.5150, false),
            City("Сарни", 51.3390, 26.6000, false),
            City("Костопіль", 50.8790, 26.4420, false),
            City("Здолбунів", 50.5230, 26.2430, false),
            City("Березне", 50.9990, 26.7440, false),
            City("Радивилів", 50.1320, 25.2560, false),
            City("Володимирець", 51.4200, 26.1450, false),
            City("Корець", 50.6150, 27.1610, false),
            City("Дубровиця", 51.5720, 26.5630, false),
            City("Млинів", 50.5170, 25.6080, false),
            City("Рокитне", 51.2780, 27.2200, false)
        )),
        Region("Чернівецьк", listOf(
            City("Чернівці", 48.2917, 25.9352, true),
            City("Сторожинець", 48.1590, 25.7150, false),
            City("Кіцмань", 48.4400, 25.7610, false),
            City("Вижниця", 48.2490, 25.1910, false),
            City("Новоселиця", 48.2240, 26.2710, false),
            City("Хотин", 48.5080, 26.4900, false),
            City("Сокиряни", 48.4480, 27.4170, false),
            City("Герца", 48.1490, 26.2610, false),
            City("Заставна", 48.5270, 25.8470, false),
            City("Глибока", 48.0870, 25.9330, false),
            City("Путила", 47.9860, 25.0930, false)
        )),
        Region("Івано-Франківськ", listOf(
            City("Івано-Франківськ", 48.9226, 24.7111, true),
            City("Калуш", 49.0430, 24.3670, false),
            City("Коломия", 48.5290, 25.0360, false),
            City("Надвірна", 48.6340, 24.5700, false),
            City("Долина", 48.9740, 24.0020, false),
            City("Бурштин", 49.2570, 24.6350, false),
            City("Косів", 48.3150, 25.0950, false),
            City("Снятин", 48.4470, 25.5700, false),
            City("Городенка", 48.6700, 25.4990, false),
            City("Рогатин", 49.4110, 24.6080, false),
            City("Тисмениця", 48.9030, 24.8490, false),
            City("Болехів", 49.0670, 23.8640, false),
            City("Галич", 49.1190, 24.7260, false),
            City("Тлумач", 48.8640, 25.0010, false)
        )),
        Region("Тернопільськ", listOf(
            City("Тернопіль", 49.5535, 25.5948, true),
            City("Чортків", 49.0170, 25.7950, false),
            City("Кременець", 50.1070, 25.7240, false),
            City("Бережани", 49.4460, 24.9350, false),
            City("Збараж", 49.6660, 25.7690, false),
            City("Зборів", 49.6600, 25.1500, false),
            City("Теребовля", 49.3040, 25.7020, false),
            City("Бучач", 49.0640, 25.3840, false),
            City("Гусятин", 49.0710, 26.2050, false),
            City("Підволочиськ", 49.5310, 26.1370, false),
            City("Козова", 49.5150, 25.1590, false),
            City("Шумськ", 50.1160, 26.1110, false),
            City("Ланівці", 49.8650, 26.0800, false),
            City("Монастириська", 49.0890, 25.1680, false)
        )),
        Region("Сумськ", listOf(
            City("Суми", 50.9077, 34.7981, true),
            City("Шостка", 51.8630, 33.4700, false),
            City("Конотоп", 51.2390, 33.2030, false),
            City("Охтирка", 50.3100, 34.8970, false),
            City("Ромни", 50.7500, 33.4870, false),
            City("Глухів", 51.6750, 33.9080, false),
            City("Лебедин", 50.5830, 34.4750, false),
            City("Кролевець", 51.5540, 33.3840, false),
            City("Путивль", 51.3310, 33.8700, false),
            City("Тростянець", 50.4810, 34.9640, false),
            City("Білопілля", 51.1470, 34.3070, false),
            City("Буринь", 51.1940, 33.8210, false),
            City("Середина-Буда", 52.1900, 34.0260, false)
        )),
        Region("Чернігівськ", listOf(
            City("Чернігів", 51.4982, 31.2893, true),
            City("Ніжин", 51.0470, 31.8780, false),
            City("Прилуки", 50.5950, 32.3880, false),
            City("Бахмач", 51.1790, 32.8260, false),
            City("Новгород-Сіверський", 52.0040, 33.2620, false),
            City("Корюківка", 51.7780, 32.2640, false),
            City("Мена", 51.5240, 32.2150, false),
            City("Сновськ", 51.8200, 31.9550, false),
            City("Борзна", 51.2510, 32.4280, false),
            City("Ічня", 50.8510, 32.3960, false),
            City("Городня", 51.8920, 31.5960, false),
            City("Семенівка", 52.1770, 32.5800, false),
            City("Козелець", 50.9160, 31.1130, false)
        )),
        Region("Донецьк", listOf(
            City("Донецьк", 48.0159, 37.8029, true),
            City("Маріуполь", 47.0971, 37.5434, false),
            City("Горлівка", 48.3380, 38.0860, false),
            City("Краматорськ", 48.7310, 37.5560, false),
            City("Слов'янськ", 48.8540, 37.6060, false),
            City("Бахмут", 48.5950, 38.0000, false),
            City("Покровськ", 48.2790, 37.1820, false),
            City("Костянтинівка", 48.5310, 37.7170, false),
            City("Дружківка", 48.6100, 37.5520, false),
            City("Харцизьк", 48.0430, 38.1450, false),
            City("Торецьк", 48.3900, 37.8470, false),
            City("Авдіївка", 48.1400, 37.7450, false),
            City("Волноваха", 47.5980, 37.4990, false),
            City("Єнакієве", 48.2310, 38.2030, false)
        )),
        Region("Луганськ", listOf(
            City("Луганськ", 48.5740, 39.3078, true),
            City("Алчевськ", 48.4690, 38.8000, false),
            City("Сєвєродонецьк", 48.9480, 38.4870, false),
            City("Лисичанськ", 48.9040, 38.4320, false),
            City("Кадіївка", 48.5690, 38.6530, false),
            City("Рубіжне", 49.0160, 38.3790, false),
            City("Сорокине", 48.2950, 39.7500, false),
            City("Довжанськ", 48.0890, 39.6520, false),
            City("Попасна", 48.6350, 38.3730, false),
            City("Брянка", 48.5130, 38.6530, false),
            City("Ровеньки", 48.0720, 39.3470, false),
            City("Кремінна", 49.0490, 38.2180, false),
            City("Сватове", 49.4100, 38.1590, false),
            City("Старобільськ", 49.2730, 38.9140, false)
        )),
        Region("Закарпатськ", listOf(
            City("Ужгород", 48.6208, 22.2879, true),
            City("Мукачево", 48.4410, 22.7130, false),
            City("Хуст", 48.1800, 23.2930, false),
            City("Берегове", 48.2050, 22.6400, false),
            City("Виноградів", 48.1410, 23.0330, false),
            City("Свалява", 48.5470, 22.9920, false),
            City("Рахів", 48.0500, 24.2000, false),
            City("Тячів", 48.0110, 23.5710, false),
            City("Іршава", 48.3160, 23.0370, false),
            City("Міжгір'я", 48.5280, 23.5070, false),
            City("Воловець", 48.7100, 23.1810, false),
            City("Великий Березний", 48.8940, 22.4600, false),
            City("Чоп", 48.4320, 22.2060, false),
            City("Перечин", 48.7340, 22.4740, false)
        )),
        Region("Волинськ", listOf(
            City("Луцьк", 50.7472, 25.3254, true),
            City("Ковель", 51.2150, 24.7080, false),
            City("Володимир", 50.8480, 24.3230, false),
            City("Нововолинськ", 50.7330, 24.1670, false),
            City("Рожище", 50.9140, 25.2700, false),
            City("Ківерці", 50.8330, 25.4590, false),
            City("Любомль", 51.2240, 24.0360, false),
            City("Камінь-Каширський", 51.6210, 24.9580, false),
            City("Горохів", 50.4980, 24.7630, false),
            City("Маневичі", 51.2940, 25.5330, false),
            City("Любешів", 51.6220, 25.4970, false),
            City("Шацьк", 51.5010, 23.9290, false),
            City("Ратне", 51.6610, 24.5290, false),
            City("Стара Вижівка", 51.4380, 24.4280, false)
        )),
        Region("Крим", listOf(
            City("Сімферополь", 44.9521, 34.1024, false),
            City("Керч", 45.3530, 36.4740, false),
            City("Євпаторія", 45.1930, 33.3660, false),
            City("Ялта", 44.5020, 34.1660, false),
            City("Феодосія", 45.0310, 35.3830, false),
            City("Джанкой", 45.7110, 34.3880, false),
            City("Алушта", 44.6760, 34.4100, false),
            City("Бахчисарай", 44.7520, 33.8600, false),
            City("Саки", 45.1340, 33.6000, false),
            City("Красноперекопськ", 45.9610, 33.7940, false),
            City("Армянськ", 46.1070, 33.6920, false),
            City("Щолкіне", 45.4260, 35.8250, false)
        )),
        Region("Севастополь", listOf(
            City("Севастополь", 44.6166, 33.5254, false)
        ))
    )

    /** All cities, flat, in the order defined above. */
    val ALL: List<City> = REGIONS.flatMap { it.cities }

    /** Ukrainian → English place-name lookup, used when translating NEPTUN's course text. */
    val uaToEn: Map<String, String> = ALL.associate { it.nameUa to it.nameEn }

    /** City (by Ukrainian name) → its oblast name stem, used to highlight a city label in red
     *  while an official air-raid alert is active for that oblast. Matched via `contains`
     *  against the alert's oblast/name (e.g. stem "Харківськ" hits "Харківська область"). */
    val cityOblast: Map<String, String> =
        REGIONS.flatMap { r -> r.cities.map { it.nameUa to r.stem } }.toMap()

    /**
     * Nearest **major** listed city within [radiusKm] of a GPS position, used to attribute a
     * follow-me location to an oblast for official-alert matching without depending on a
     * geocoder. Minor cities are map-context only and never drive attribution/banner.
     */
    fun nearestCity(lat: Double, lon: Double, radiusKm: Double = 70.0): City? {
        var best: City? = null
        var bestM = radiusKm * 1000.0
        for (c in ALL) {
            if (!c.major) continue
            val d = distanceMeters(lat, lon, c.lat, c.lon)
            if (d < bestM) {
                bestM = d
                best = c
            }
        }
        return best
    }
}

/** Oblast attribution + banner city for the focus point: the pinned city, else nearest to GPS. */
data class FocusAttribution(
    val token: String?,
    val bannerCityUa: String,
    val bannerCityEn: String
)

fun focusAttribution(followMe: Boolean, userLocation: LatLng?, pinned: City?): FocusAttribution {
    if (!followMe && pinned != null) {
        return FocusAttribution(
            token = Cities.cityOblast[pinned.nameUa],
            bannerCityUa = pinned.nameUa,
            bannerCityEn = pinned.nameEn
        )
    }
    val gps = userLocation?.let { Cities.nearestCity(it.lat, it.lon) }
    return if (gps != null) {
        FocusAttribution(
            token = Cities.cityOblast[gps.nameUa],
            bannerCityUa = gps.nameUa,
            bannerCityEn = gps.nameEn
        )
    } else {
        // No usable fix and no pinned city: don't fabricate a specific city (a leftover from
        // the app's Odesa-only origin) — the callers substitute a localized "unknown location"
        // label instead. A null token already suppresses official-alert matching.
        FocusAttribution(null, "", "")
    }
}

/** Draws city names in the current language, sized to zoom level. */
class CityLabelOverlay(
    context: Context,
    private val lang: AppLanguage,
    private val activeRegionTokens: Set<String> = emptySet()
) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint().apply {
        isAntiAlias = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }
    private val reuse = android.graphics.Point()

    private fun name(c: City) = if (lang == AppLanguage.UA) c.nameUa else c.nameEn

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val zoom = mapView.zoomLevelDouble.coerceAtLeast(0.0)
        for (c in Cities.ALL) {
            if (c.major) {
                if (zoom < 4.0) continue
            } else {
                if (zoom < 10.0) continue
            }
            mapView.projection.toPixels(GeoPoint(c.lat, c.lon), reuse)
            if (reuse.x < -240 || reuse.x > canvas.width + 240 ||
                reuse.y < -60 || reuse.y > canvas.height + 60
            ) continue
            paint.textSize = (if (c.major) {
                (10.5 + (zoom - 4.0) * 1.2).coerceIn(10.5, 17.0)
            } else {
                (8.5 + (zoom - 10.0) * 0.7).coerceIn(8.5, 13.0)
            }).toFloat() * density
            val token = Cities.cityOblast[c.nameUa]
            paint.color = if (token != null && token in activeRegionTokens) {
                Color.argb(255, 211, 47, 47)
            } else {
                Color.argb(230, 235, 235, 235)
            }
            canvas.drawText(name(c), reuse.x.toFloat(), reuse.y.toFloat() - 6f * density, paint)
        }
    }
}