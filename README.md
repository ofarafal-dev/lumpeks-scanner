# Lumpeks Scanner 🧥📷

Aplikacja Android do szybkiej oceny ubrań i butów w lumpeksach. Robisz zdjęcie metki, a aplikacja:

1. czyta tekst z metki (OCR, **ML Kit Text Recognition on-device** — działa bez internetu),
2. rozpoznaje markę po aliasach (bez wielkości liter, z tolerancją drobnych błędów OCR),
3. pokazuje ocenę opłacalności:
   - 🟢 **bierz**
   - 🟡 **zależy od stanu**
   - 🔴 **zostaw**
   - plus jedno zdanie o marce i ⚠️ ostrzeżenie, jeśli marka jest często podrabiana,
4. czyta **skład** (kaszmir, wełna merino, jedwab, len, alpaka, moher = ➕; sam poliester/akryl = ➖),
5. czyta **kraj produkcji** („Made in…”, „Fabriqué en…”, „Prodotto in…”, „Hergestellt in…”, „Wyprodukowano w…” itd.).

Jeśli marka zostanie źle rozpoznana, przycisk **„Popraw markę”** pozwala wybrać ją ręcznie z listy.

## Struktura repozytorium

```
data/brands.json                 baza marek (jedyne źródło prawdy)
app/                             aplikacja: Kotlin, Jetpack Compose, CameraX, ML Kit
.github/scripts/validate_brands.py   walidator bazy
.github/workflows/validate.yml   walidacja JSON przy każdym commicie / PR
.github/workflows/build.yml      build APK i publikacja w GitHub Releases
```

Package: `dev.ofarafal.lumpeksscanner`, minSdk 26 (Android 8.0), targetSdk 34.

## Baza marek i aktualizacje

- `data/brands.json` jest przy buildzie dołączany do APK jako `assets/brands.json` (wersja startowa, działa offline).
  Gradle bierze plik bezpośrednio z katalogu `data/` — nie trzeba go nigdzie kopiować.
- Przy każdym starcie aplikacja próbuje pobrać
  `https://raw.githubusercontent.com/ofarafal-dev/lumpeks-scanner/main/data/brands.json`.
  Jeśli pole `"version"` jest **wyższe** niż w lokalnej kopii, baza jest podmieniana i zapisywana w pamięci telefonu.
- Bez internetu używana jest ostatnio pobrana wersja (albo wbudowana, jeśli nigdy nic nie pobrano).

**Dzięki temu nowe marki dodajesz bez budowania nowego APK** — wystarczy commit do `data/brands.json` z podbitym `version`.

### Jak dodać lub zmienić markę

1. Otwórz `data/brands.json` na GitHubie → ikona ołówka (Edit).
2. Dodaj wpis do tablicy `brands`:

   ```json
   {
     "name": "Barbour",
     "aliases": ["barbour", "barbour international"],
     "country": "UK",
     "rating": "green",
     "note": "Woskowane kurtki i pikówki, bardzo dobrze się sprzedają.",
     "counterfeit_warning": true
   }
   ```

   | pole | opis |
   |---|---|
   | `name` | nazwa wyświetlana (unikalna) |
   | `aliases` | warianty zapisu z metki, małymi literami; znaki specjalne i polskie litery są ignorowane (`"levi s"`, `"levis"`) |
   | `country` | kraj pochodzenia marki (UK, FR, IT, DE, US, SE, ES, PL…) |
   | `rating` | `green` / `yellow` / `red` |
   | `note` | jedno krótkie zdanie po polsku |
   | `counterfeit_warning` | `true` dla marek często podrabianych |

3. **Podbij `"version"` o 1** i zaktualizuj `"updated"` (RRRR-MM-DD) — inaczej telefony nie pobiorą zmian.
4. Commit. Workflow **Validate brands.json** sprawdzi poprawność JSON, wymagane pola, wartości `rating`
   oraz kolizje aliasów między markami. Czerwony ❌ w zakładce *Actions* = coś do poprawienia.

Tip: krótkie aliasy (≤ 5 znaków, np. `next`, `boss`, `hm`) są dopasowywane dokładnie; dłuższe tolerują 1–2 błędy OCR.
Przy kilku trafieniach wygrywa dłuższy alias (np. „Weekend Max Mara” przed „Max Mara”).

## Jak zbudować APK

### Automatycznie (GitHub Actions)

Każdy push do `main`, który zmienia `app/`, `data/` lub pliki Gradle, uruchamia workflow **Build APK**.
Gotowy plik `lumpeks-scanner-<numer>.apk` pojawia się w **Releases** (jako pre-release `build-<numer>`).
Build można też odpalić ręcznie: *Actions → Build APK → Run workflow*.

> APK jest podpisany kluczem *debug* generowanym w CI — przy instalacji nowego buildu może być potrzebne
> odinstalowanie poprzedniego. Stały podpis wymaga własnego keystore w GitHub Secrets (do zrobienia później).

### Lokalnie

Wymagania: JDK 17, Android SDK (najprościej Android Studio).

```bash
# repo nie zawiera Gradle Wrappera (plik binarny) — wygeneruj go raz:
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
# wynik: app/build/outputs/apk/debug/app-debug.apk
```

W Android Studio: *File → Open* → katalog repo → Run.

## Technologia

- Kotlin 2.0, Jetpack Compose (Material 3)
- CameraX 1.3 (podgląd + zdjęcie)
- ML Kit Text Recognition 16 (model łaciński wbudowany w APK)
- bez zewnętrznych serwerów — jedyne połączenie to pobranie `brands.json` z GitHuba
