# Changelog

## [Unreleased]

- Network: removed duplicate reconnect trigger from onAvailable, added generation guard for stale socket callbacks / Мережа: прибрано дубльований reconnect з onAvailable, додано generation guard для застарілих callback-ів
- Offline: persist reconnect start millis across process kills so 3/6/10/20-min milestones survive restarts / Офлайн: зберігається reconnect start millis між перезапусками для коректних 3/6/10/20-хвилинних віх
- UI: header pill now immediately reflects connection drops (mirror rule enforcement) / Інтерфейс: пілл одразу відображає втрату з'єднання (узгодження mirror rule)
- Widget: skip snapshot pipeline when no widgets are placed (battery optimization) / Віджет: пропуск pipeline коли віджети не встановлені (оптимізація батареї)
- Map: marker smoothing loop no longer freezes on overlay changes / Мапа: loop згладжування маркерів більше не замерзає при зміні overlay
- Test MIG: deterministic launch base per test fire (no per-frame random) / Тестовий MIG: визначена база запуску на кожен тест (без per-frame random)
- Logs: removed runBlocking from IO-thread persistence in ConnectionLog/DebugLog / Логи: прибрано runBlocking з IO-thread persistence
- Code: deleted unused alertEpoch, cached threat-toggle pref keys, O(1) city lookups / Код: видалено alertEpoch, кешовано threat-toggle ключі, O(1) пошук міст

- Strings: flyby animation description routed through Strings (was English-only) / Строки: опис анімації польоту через Strings (був англійською)
- Data: Neptun URLs centralized to single constants / Дані: URL Neptun зcentralізовано в одних константах
- Data: Ukraine bounding boxes & Odesa coords merged to shared GeoConstants / Дані: межі України та координати Одеси об'єднано в GeoConstants
- ConnectionStatus: neptun.in.ua label uses Strings.connNeptunLabel / Статус з'єднання: мітка neptun.in.ua через Strings
- Logs: DecisionCard redesigned — title shows "Entered zone" (accent color = zone), second row has type · distance + date right-aligned, day/night + ringer on third row with notified badge right-aligned (green ✓ Notified / amber suppressed); "left zone" entries now show threat type + city. / Логи: DecisionCard перероблено — заголовок «Entered zone» (колір = зона), другий рядок: тип · відстань + дата праворуч, день/ніч + сирена в третьому з міткою сповіщення праворуч (✓ Notified / пригнічено); «left zone» тепер показує тип загрози + місто
