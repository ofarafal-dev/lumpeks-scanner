# Lumpeks Scanner 🧥📷

Aplikacja Android do szybkiej oceny ubrań i butów w lumpeksach. Robisz zdjęcie metki, a aplikacja:

1. czyta tekst z metki (OCR, **ML Kit Text Recognition on-device** — działa bez internetu),
2. rozpoznaje markę po aliasach (bez wielkości liter, z tolerancją drobnych błędów OCR),
3. pokazuje ocenę opłacalności:
   - 🟢 **bierz**
   - 🟡 **zależy od stanu**
   - 🔴 **zostaw**
   - plus jedno zdanie o marce, opcjonalną 💡 wskazówkę (np. „bierz Polo/oxford, Made in USA”)
     i osobny znaczek ⚠️ **„Sprawdź autentyczność”** przy markach często podrabianych,
4. czyta **skład** (kaszmir, wełna merino, jedwab, len, alpaka, moher = ➕; sam poliester/akryl = ➖),
5. czyta **kraj produkcji** („Made in…”, „Fabriqué en…”, „Prodotto in…”, „Hergestellt in…”, „Wyprodukowano w…” itd.).

Jeśli marka zostanie źle rozpoznana, przycisk **„Popraw markę”** pozwala wybrać ją ręcznie z listy.
Panel **„Pokaż odczytany tekst”** pokazuje surowy tekst z OCR oraz wszystkie dopasowane aliasy
(fragment tekstu, liczba błędów / tolerancja, wynik) — przydatne do strojenia aliasów i progów.

## Struktura repozytorium

```
data/brands.json                 baza marek (jedyne źródło prawdy)
app/                             aplikacja: Kotlin, Jetpack Compose, CameraX, ML Kit
.github/scripts/validate_brands.py   walidator bazy
.github/workflows/validate.yml   walidacja JSON przy każdym commicie / PR
.github/workflows/build.yml      build podpisanego APK (release) i publikacja w GitHub Releases
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
     "tip": "Najcenniejsze Beaufort/Bedale z metką Made in England.",
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
   | `tip` | *opcjonalnie*: krótka wskazówka (max 140 znaków), pokazywana na ekranie wyniku |
   | `counterfeit_warning` | `true` dla marek często podrabianych |

3. **Podbij `"version"` o 1** i zaktualizuj `"updated"` (RRRR-MM-DD) — inaczej telefony nie pobiorą zmian.
4. Commit. Workflow **Validate brands.json** sprawdzi poprawność JSON, wymagane pola, wartości `rating`,
   opcjonalne `tip`, nieznane pola (literówki), kolizje aliasów między markami oraz to, czy przy zmianie
   bazy podbito `"version"`. Czerwony ❌ w zakładce *Actions* = coś do poprawienia.

Tip: krótkie aliasy (≤ 5 znaków, np. `next`, `boss`, `hm`) są dopasowywane dokładnie; dłuższe tolerują 1–2 błędy OCR.
Przy kilku trafieniach wygrywa dłuższy alias (np. „Weekend Max Mara” przed „Max Mara”).

## Aktualizacje aplikacji

Przy starcie (wersja release) aplikacja odpytuje
`https://api.github.com/repos/ofarafal-dev/lumpeks-scanner/releases/latest`.
Tag ma postać `build-N`, a `N` to `versionCode` — jeśli jest większy niż zainstalowany, pojawia się dialog
**„Dostępna nowa wersja”**. Przycisk pobiera `lumpeks-scanner.apk` przez DownloadManager i otwiera systemowy
instalator (FileProvider + `REQUEST_INSTALL_PACKAGES`). **Instalację zawsze zatwierdzasz sam.**
Za pierwszym razem Android poprosi o zgodę „Instaluj nieznane aplikacje” dla Lumpeks Scannera — po jej
włączeniu wróć i naciśnij **„Zainstaluj”**. Brak internetu lub błąd API = sprawdzenie pomijane po cichu.

Aktualizacja działa tylko między wersjami podpisanymi tym samym kluczem (patrz niżej).

## Jak zbudować APK

### Automatycznie (GitHub Actions)

Każdy push do `main`, który zmienia `app/`, `data/` lub pliki Gradle, uruchamia workflow **Build APK**:

1. odtwarza keystore z sekretu `KEYSTORE_BASE64` do pliku tymczasowego (usuwany po buildzie),
2. buduje wariant **release** z R8 (minify + shrink resources), podpisany stałym kluczem,
3. weryfikuje podpis (`apksigner`) — build z kluczem debug kończy się błędem,
4. publikuje release `build-N` z plikiem `lumpeks-scanner.apk`.

Numeracja: `versionCode = N` (numer runu GitHub Actions), `versionName = "1.0.N"`.
Build można też odpalić ręcznie: *Actions → Build APK → Run workflow*.

### Keystore i sekrety (jednorazowo)

1. Wygeneruj keystore (JDK / Android Studio):

   ```bash
   keytool -genkeypair -v -keystore lumpeks-release.jks -alias lumpeks \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   lub w Android Studio: *Build → Generate Signed Bundle / APK → Create new…*

2. Zakoduj plik w base64:

   ```bash
   base64 -w0 lumpeks-release.jks > ks.txt          # Linux
   base64 -i lumpeks-release.jks -o ks.txt          # macOS
   ```
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("lumpeks-release.jks")) > ks.txt   # Windows
   ```

3. *Settings → Secrets and variables → Actions → New repository secret*:

   | sekret | wartość |
   |---|---|
   | `KEYSTORE_BASE64` | zawartość `ks.txt` |
   | `KEYSTORE_PASSWORD` | hasło keystore |
   | `KEY_ALIAS` | alias klucza (np. `lumpeks`) |
   | `KEY_PASSWORD` | hasło klucza |

> ⚠️ Trzymaj kopię `.jks` i haseł poza GitHubem. Utrata klucza = brak możliwości aktualizacji
> zainstalowanej aplikacji (trzeba odinstalować i zainstalować od nowa).

### Lokalnie

Wymagania: JDK 17, Android SDK (najprościej Android Studio).

```bash
# repo nie zawiera Gradle Wrappera (plik binarny) — wygeneruj go raz:
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug        # debug
./gradlew :app:assembleRelease      # release; bez zmiennych KEYSTORE_* podpisany kluczem debug
```

Podpis lokalny stałym kluczem: ustaw zmienne `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Technologia

- Kotlin 2.0, Jetpack Compose (Material 3)
- CameraX 1.3 (podgląd + zdjęcie)
- ML Kit Text Recognition 16 (model łaciński wbudowany w APK)
- bez zewnętrznych serwerów — jedyne połączenie to pobranie `brands.json` z GitHuba
