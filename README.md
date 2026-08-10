# Weather

A desktop weather app in Java 21 and JavaFX 21. Type a city, get current conditions and a five-day
forecast from [Open-Meteo](https://open-meteo.com) — no API key required.

The point of this project is the architecture, not the feature count: strict layer separation, a
swappable API client behind an interface, domain records that never touch JSON, and network calls
that can't freeze the UI.

> **Screenshot:** run the app, capture the window, save it as `docs/screenshot.png`, and uncomment
> the line below.
>
> <!-- ![The app showing current conditions and a five-day forecast](docs/screenshot.png) -->

---

## Features

- Search a city by pressing **Enter** or clicking **Search**
- Current temperature, feels-like, humidity, wind speed, and conditions as text plus an icon
- Five-day forecast strip with daily highs and lows
- **°C / °F** toggle that switches wind units (km/h ↔ mph) along with temperature
- Clickable history of the last five cities
- Loading indicator during requests; the window stays responsive throughout
- Plain-language error messages for city-not-found, no connection, timeout, bad response, and rate
  limiting — never a stack trace

---

## Running it

Requires **JDK 21+**. If you don't have JDK 21 specifically, Gradle downloads it for you — the build
declares a toolchain and `settings.gradle.kts` enables the foojay resolver.

```bash
./gradlew run
```

### Standalone jar

```bash
./gradlew shadowJar
java -jar build/libs/weather-app-1.0.0-all.jar
```

The fat jar bundles the Linux JavaFX natives, so it runs without a JavaFX SDK installed. Its entry
point is `Launcher`, not `Main`: when the JavaFX runtime sits on the classpath instead of the module
path, the JVM refuses to start a main class that extends `Application`, and launching from a class
that doesn't extend it side-steps that check.

Running the jar on a JDK newer than 21 prints warnings about native access and `sun.misc.Unsafe`.
They're harmless; `--enable-native-access=ALL-UNNAMED` silences the first one.

### Tests

```bash
./gradlew test                                              # unit tests, offline, ~2s
RUN_INTEGRATION_TESTS=true ./gradlew test --tests '*IT'     # also hits the real Open-Meteo API
```

118 tests. The one that talks to the network is disabled by default so the normal run stays fast and
deterministic; the JavaFX view test is skipped automatically when there's no `DISPLAY`.

---

## Architecture

```
                         ┌──────────────────────────────┐
                         │   weather-view.fxml + CSS    │   view: structure and style only
                         └──────────────┬───────────────┘
                                        │  fx:id / onAction
                         ┌──────────────▼───────────────┐
                         │      WeatherController       │   when to ask, where to put the answer
                         └──────────────┬───────────────┘
                                        │  CompletableFuture on a background thread
                                        │  ── results return via Platform.runLater ──
                         ┌──────────────▼───────────────┐
                         │        WeatherService        │   orchestration, caching, unit conversion
                         │   ┌──────────────────────┐   │
                         │   │  WeatherCache (10m)  │   │   canonical metric snapshots only
                         │   └──────────────────────┘   │
                         └──────────────┬───────────────┘
                                        │  depends on the interface, never the implementation
                         ┌──────────────▼───────────────┐
                         │   «interface» WeatherClient  │   geocode(String) / fetchWeather(Location)
                         └──────────────┬───────────────┘
                                        │
                         ┌──────────────▼───────────────┐
                         │       OpenMeteoClient        │   HttpClient, Jackson, DTO → domain
                         │   ┌──────────────────────┐   │
                         │   │   api/dto/*Dto       │   │   wire shapes, thrown away at this line
                         │   └──────────────────────┘   │
                         └──────────────┬───────────────┘
                                        │  HTTPS
                         ┌──────────────▼───────────────┐
                         │      Open-Meteo REST API     │
                         └──────────────────────────────┘

        model/     immutable records: Location, WeatherData, DailyForecast, WeatherCondition, Units
        exception/ WeatherApiException ├─ CityNotFoundException
                                       ├─ MalformedResponseException
                                       └─ ApiUnavailableException ─ RateLimitException
        util/      Config (application.properties), WeatherCodeMapper (WMO), ErrorMessages
```

### Why each layer exists

| Layer | Why it's separate |
|---|---|
| **model** | Immutable records with no annotations and no framework types. Values are stored canonically — Celsius, km/h — and converted only on the way to the screen, so one representation can't drift from another. |
| **api** | The only code that knows Open-Meteo exists. Behind an interface so the provider is swappable and so every layer above can be tested against a mock instead of a socket. |
| **api/dto** | The wire format, mirrored field for field. Open-Meteo returns the daily forecast as *parallel arrays* — `time[i]`, `weather_code[i]`, `temperature_2m_max[i]` — and the DTO absorbs that shape so the domain never has to. If the provider renames a field, the change stops here. |
| **service** | The use case: geocode, then forecast, cached, converted. Synchronous by design so its tests need no executors or latches. Knows nothing about JavaFX and nothing about HTTP. |
| **controller** | Event handlers and rendering. Decides *when* to call the service and *where* to put the result — no parsing, no business rules. |
| **exception** | One vocabulary for failure. `IOException`, `InterruptedException` and HTTP status codes are translated at the client boundary, so upper layers decide what to tell the user without knowing how the data was fetched. |
| **util** | Stateless helpers: config loading, the WMO code mapper, and the exception-to-sentence mapping — all pure functions, all unit tested. |

### Three decisions worth explaining

**Canonical units.** `WeatherData` leaves the client in metric and carries the `Units` it's expressed
in. `convertedTo()` throws if you try to convert something that isn't canonical, and the cache
*refuses* to store non-metric data. That forecloses the double-conversion bug — 20 °C becoming
68 °F becoming 154 °F — and means the cache holds one entry per city rather than one per city per
unit system.

**Threading.** JavaFX renders and dispatches events on a single thread. A blocking HTTP call on it
freezes the window for the full 10-second timeout, and touching a `Label` from a background thread
throws or silently corrupts the scene graph. So requests run on a daemon executor and results come
back through `Platform.runLater`. A request counter discards stale answers, so searching *Tokyo*
then *Berlin* can't leave Tokyo's slower response on screen.

**Testing time.** The cache takes an injected `Clock`, so TTL expiry is tested by advancing a fake
clock rather than by sleeping for ten minutes.

---

## Configuration

Everything environment-shaped lives in `src/main/resources/application.properties` and is read
through `Config`. No URL or timeout is written as a literal anywhere else. Any value can be
overridden at runtime with a matching system property:

```bash
./gradlew run -Dopenmeteo.forecast.url=http://localhost:8080/forecast
java -Dcache.ttl.minutes=1 -jar build/libs/weather-app-1.0.0-all.jar
```

(Gradle runs the app in a separate JVM that doesn't inherit `-D` flags, so `build.gradle.kts`
forwards the keys above to it explicitly.)

| Key | Default | Meaning |
|---|---|---|
| `openmeteo.geocoding.url` | `https://geocoding-api.open-meteo.com/v1/search` | City → coordinates |
| `openmeteo.forecast.url` | `https://api.open-meteo.com/v1/forecast` | Coordinates → weather |
| `http.connect.timeout.seconds` | `5` | TCP connect budget |
| `http.request.timeout.seconds` | `10` | Whole-request budget |
| `cache.ttl.minutes` | `10` | How long a city stays fresh |
| `forecast.days` | `5` | Days in the forecast strip |
| `history.size` | `5` | Cities kept in the history bar |

---

## Project layout

```
src/main/java/com/example/weather/
├── Main.java                     JavaFX entry point and composition root
├── Launcher.java                 fat-jar entry point
├── model/                        Location, WeatherData, DailyForecast, WeatherCondition, Units
├── api/
│   ├── WeatherClient.java        the interface everything depends on
│   ├── OpenMeteoClient.java      HttpClient + Jackson + DTO → domain mapping
│   └── dto/                      GeocodingResponseDto, ForecastResponseDto, …
├── service/
│   ├── WeatherService.java       geocode → forecast, caching, unit conversion
│   ├── WeatherCache.java         10-minute TTL, injected Clock
│   └── SearchHistory.java        last five cities, de-duplicated
├── controller/
│   └── WeatherController.java    view binding and async wiring
├── exception/                    WeatherApiException and friends
└── util/                         Config, WeatherCodeMapper, ErrorMessages

src/main/resources/
├── application.properties
├── logback.xml
└── com/example/weather/view/     weather-view.fxml, styles.css

src/test/
├── java/…                        unit tests, one live-API integration test
└── resources/                    JSON fixtures captured from the real API
```

## Stack

Java 21 · JavaFX 21 · Gradle (Kotlin DSL) · Jackson · SLF4J + Logback · JUnit 5 · Mockito · AssertJ

Weather data by [Open-Meteo](https://open-meteo.com), used under CC BY 4.0.
