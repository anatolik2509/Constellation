# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Что это

Constellation — просмотр Java/Kotlin кода в виде графа-«созвездия»: классы — вершины,
связи — рёбра. Анализ идёт по скомпилированному байткоду (ASM). Поддерживаемый вход —
Gradle-плагин `org.example.constellation` (задача `constellation` в целевом проекте);
`DevMain` в webui — только внутренний догфуд, не поддерживаемый вход.

## Команды

```bash
./gradlew build                    # сборка + все тесты
./gradlew test -PskipPerfSmoke     # тесты без perf-smoke (тег perf-smoke: прогон на kotlin-stdlib)
./gradlew :core:test               # тесты одного модуля
./gradlew :core:test --tests "org.example.constellation.core.api.query.PredicatesTest"   # один тест-класс
```

Догфуд на самом репозитории (сначала нужна компиляция — `./gradlew classes`):

```bash
./gradlew :webui:run --args="$PWD/modules/core/build/classes/kotlin/main $PWD/modules/webui/build/classes/kotlin/main"
```

Headless-скриншот UI (работает только потому, что первая раскладка идёт с
`animate: false` — не ломать это при правке app.js):

```bash
google-chrome-stable --headless=new --virtual-time-budget=20000 --screenshot=/tmp/c.png http://127.0.0.1:8080/
```

Toolchain: Kotlin 2.4.20, Gradle wrapper 9.6, JVM toolchain 21.

## Архитектура

Три модуля: физически в `modules/`, Gradle-пути `:core`, `:webui`, `:gradle-plugin`
(маппинг в `settings.gradle.kts`). Зависимости строго в одну сторону:
`gradle-plugin → webui → core`.

### core — закрытая модель кода, не знает про UI/HTTP/Gradle

Единственный пакет, доступный внешним потребителям, — `core.api` (всё вне него
помечено `internal`, компилятор запрещает доступ извне модуля):

- `api/` — точка входа анализа: `ProjectAnalyzer().analyze(AnalysisRequest(roots,
  registry))`. `ClassRoot` несёт метки module/source set от вызывающего (плагин
  знает их из Gradle; path-sniffing только в dev-входе `DevMain`). Классы `Foo$bar$1`
  (лямбды, анонимные объекты) не становятся вершинами — их call-рёбра
  атрибутируются enclosing-классу через `EnclosingMethod` (`resolveOwners`).
- `api/model/` — замкнутая модель: `Node`, `Edge`, `CodeGraph`. Всё отсортировано по
  построению, поэтому JSON детерминирован — сохраняйте это. `MethodSignature` —
  читаемые сигнатуры (`zip(String, int[]): int`); JVM-дескрипторы существуют только
  внутри анализатора (`analyze/internal/DescriptorParser`).
- `api/query/` — компонуемые предикаты `NodePredicate`/`EdgePredicate` + фабрики и
  комбинаторы `and`/`or`/`not`, `CodeGraph.filter`. Логика фильтрации живёт только
  здесь; HTTP-слой лишь компонует предикаты из query-параметров.
- `api/traits/` — значения трейтов и `TraitRegistry` (built-ins + ServiceLoader
  через `META-INF/services/...ClassTraitProvider`); `api/traits/spi/` — контракт
  для сторонних провайдеров (`ClassTraitProvider`, `ClassContext`,
  `MethodDecl`/`StaticFieldDecl`). Built-ins `DataModel` и `StaticComponent` —
  `internal` в `traits/builtin/`. Провайдер автоматически попадает в JSON
  (`traits` у вершин, `traitCatalog` в meta) и в панель фильтров UI.
- `analyze/` — единственное место, где живёт ASM (сканер, Raw*-типы,
  `DescriptorParser`). `invokestatic` (и method references на static) даёт
  `Relation.CALLS_STATIC`, остальные вызовы — `CALLS`; пара классов может иметь
  оба ребра.

### webui — HTTP-сервер и страница

JDK `HttpServer`, слушает только 127.0.0.1; порт по умолчанию 8080 — осознанный
выбор, не менять на ephemeral без спроса. `GraphQuery` — чистый разбор query-string
в предикаты core (без HTTP-типов, тривиально тестируется); неизвестные токены →
`IllegalArgumentException` → HTTP 400. `GraphJson` — Jackson. Страница:
`resources/web/` (vanilla JS, cytoscape.js вендорена в `resources/vendor/`, без CDN).

### gradle-plugin

Плагин `org.example.constellation`: задача `constellation` зависит от компиляции
через `classesDirs` и анализирует её результаты.

## Конвенции проекта

- Одна декларация Kotlin на файл.
- Коммиты — осмысленные, на русском; не пушить без явной просьбы.
- Палитра вершин/рёбер валидирована скриптом из скилла `dataviz` (поверхность
  `#12131a`); формы вершин — обязательное второе кодирование, не убирать. Перед
  изменением цветов — перегнать валидатор.
- Владелец вызова — статический тип ресивера (вызов через интерфейс даёт ребро к
  интерфейсу, как в IDE); внешние классы аппроксимируются как `CLASS`.
- Тесты анализатора гоняются на javac-фикстурах (`FixtureCompiler`); есть
  self-dogfood (анализ собственного build-вывода) и TestKit для плагина.
- Планы — `TODO.MD`; концепт — `docs/concept.md`; выполненные спеки — `spec/`.
