# DeepStrike → NeoForge 1.21.1 — рабочий процесс

Порт мода DeepStrike (Forge 1.20.1) на Minecraft 1.21.1 / NeoForge 21.1.265.
Исходники — `source/`, готовый jar — `dsm-0.9.0-neoforge-1.21.1.jar`.
Целевая сборка игрока — CurseForge-инстанс `Drone` (superbwarfare 0.8.9.2, geckolib 4.9.3, Xaero).

**Plan on high. Delegate on medium. Keep Opus on call.**

## Сборка

- `source/` — Gradle-проект (ModDevGradle, NeoForge 21.1.265). Команды запускать из `source/`: `./gradlew build`, `./gradlew compileJava`, `./gradlew runGts`.
- JDK 21 лежит в `tools/jdk-21`. `gradlew` / `gradlew.bat` берут её сами, если `JAVA_HOME` не задан; демон Gradle — через `org.gradle.java.home` в `source/gradle.properties`.
- Сторонние jar для компиляции и тестового сервера — в `libs/` (`build.gradle` ссылается на `../libs`).
- Готовый jar: `source/build/libs/dsm-0.9.0-neoforge-1.21.1.jar`.

## Роли

| Роль | Модель · effort | Что делает | Доступ |
|---|---|---|---|
| main session | Sonnet · high | План → решения → постановка задач, финальная проверка | всё |
| `advisor` | Opus · high · по вызову | Ревью плана, разбор повторных сбоев, проверка перед «готово» | только чтение, код не меняет |
| `worker` | Sonnet · medium | Правки кода, компиляция, тесты | read + write |
| `explorer` | Haiku · medium | Поиск в коде, связи функций, нужные файлы | read only |
| `researcher` | Haiku · medium | Документация NeoForge/MC/SBW/GeckoLib, источники | docs + web |

## Цикл задачи

1. **План (main, high).** Разбить задачу на шаги. Для нетривиальной задачи — вызвать `advisor` *перед планом*: архитектура, риски, порядок шагов.
2. **Маршрутизация** (правила ниже, не отдельный агент).
3. **Диспетчер.** До 3 исполнителей параллельно (`worker` / `explorer` / `researcher`), effort medium. Два `worker` никогда не правят одни и те же файлы.
4. **Возврат в main (high): review = verify.**
   - сверить изменения (diff исходников);
   - `./gradlew compileJava` без ошибок;
   - для серверной логики — GameTest через `./gradlew runGts`;
   - вызвать `advisor` *перед «готово»*: задача выполнена, нет регрессий, diff проверен;
   - итог пользователю — по вердикту advisor.

`advisor` вызывается только в трёх точках: **перед планом**, **при ошибке/split**, **перед «готово»**. На рутинные задачи (одна правка, переименование, копирование jar) — не вызывать. Советы advisor применяет main или worker.

## Маршрутизация (routing layer)

Перед каждым шагом ответить на три вопроса:

- **which file** — какой файл читать. Если неизвестно → `explorer`, а не чтение наугад.
- **which tool** — какой инструмент. Поиск по коду → `explorer`; API NeoForge/Minecraft → исходники `mcsrc/` или `researcher`; правка → `worker`.
- **retry or stop** — повторить или остановиться.
  - **sharp** (причина ясна) → действовать.
  - **split** (тот же сбой второй раз подряд, или два правдоподобных объяснения) → остановиться и вызвать `advisor`. Не повторять ту же попытку в третий раз.

## Факты проекта, которые нужны каждому агенту

- Код декомпилирован (Vineflower) и переведён из SRG в имена Mojang. Аннотации `@Override` потеряны: смена сигнатуры в 1.21 **молча** ломает переопределение. После правок метода-хука проверять, что он реально переопределяет родителя.
- Слой совместимости — `dev.sbw.deepstrike.compat`: `SimpleChannel` поверх payload-ов NeoForge, `TickEvent` (Forge-фазы поверх Pre/Post), `ForgeChunkManager` → `TicketController`, `NbtCompat` (NBT предметов → `custom_data`), `DistExecutor`.
- Миксины: `deepstrike.mixins.json`, `defaultRequire = 1` — неверная цель роняет игру при запуске. Цели сверять с байткодом (`javap -c`), а не по памяти.
- Ракеты вблизи (до 256 блоков) рисуются настоящей сущностью; дальше — реплика из пакетов `RemoteFlights`.
- Несработавшие ракеты (dud, шанс 2–15%) — штатное поведение оригинала, не баг.
- Jar в `mods` **нельзя заменять при запущенной игре** (`javaw.exe`): это даёт `NoClassDefFoundError`. Перед копированием проверить процесс.
- Мод под лицензией All Rights Reserved — порт только для личного использования.
