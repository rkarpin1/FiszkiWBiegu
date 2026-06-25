# Fiszki w biegu

## Opis
Projekt powstał w ramach szkolenia **10xDevs 3.0**.


## "Fiszki w biegu" co to jest ????
Pomysł jest prosty.

Nie miałeś wrażenia, że gdy biegasz, sprzątasz, myjesz naczynia i Twoje ręce są zajęte, to w tym czasie mógłbyś powtórzyć jakiś materiał np. z języka angielskiego ?<br> 
Jest sporo programów do fiszek, ale one wszystkie wymagają Twojej uwagi. Coś trzeba nacisnąć, coś wpisać, coś przeczytać.<br>
"Fiszki w biegu" to może właśnie ta aplikacja, na którą zawsze czekałeś?
<br>
<br>

https://github.com/user-attachments/assets/888e4b82-8a1c-474c-89dc-f58266dfdbac

<br>
<br>
  
## Jak to działa?

Aplikacja w wielu aspektach niczym nie różni się od innych aplikacji do fiszek.<br>
Aplikacja ma "standardowe" funkcje jak:
- tworzenie kolekcji dla wybranych języków, 
- tworzenie nowych fiszek,
- edycję, usuwanie kolekcji i fiszek,
... generalnie nuda.

To, co jest "fajne" w tej aplikacji to tryb nauki. <br>
Włączamy tryb nauki dla danej kolekcji, chowamy telefon do kieszeni i biegniemy w siną dal.

Tryb nauki działa tak (na przykładzie j.polskiego i j.angielskiego):
- odtwarzane jest zdanie w j. polskim, 
- aplikacja czeka, aż "przetłumaczysz" sobie w głowie to zdanie na angielski,
- aplikacja odtwarza zdanie w j. angielskim 3 razy i daje czas na powtórzenie Tobie tego zdania,
- po tym wszystkim aplikacja przechodzi do następnej fiszki.
- jak wszystkie fiszki zostały odtworzone, do zaczynamy od nowa.

Proste? Proste. "Fiszki w biegu" to nauczyciel, który wbija Ci wiedzę na siłę!

## Umiem, czy nie umiem ? Oto jest pytanie!

Ale, ale!? Ale... niektóre fiszki już znam, a niektóre nie chcą mi wejść do głowy!

Aplikacja posiada prosty system oceny znajomości fiszki. Im więcej "listków" przy fiszce, tym lepiej pamiętasz fiszkę.

W trakcie nauki sami możemy ocenić, jak dobrze znamy fiszkę!<br>
Na ekranie "Nauki" sprawa jest prosta. Mamy dwa przyciski "Nie wiem" oraz "Wiem!"
- gdy wybierzemy "Nie wiem", to jest to znak, że totalnie nie wiem, jak odpowiedzieć. Aplikacja jak najszybciej ponowi odtwarzanie tej fiszki.
- gdy wybierzemy "Wiem!", to znak, że fiszka jest bardzo dobrze znana. Aplikacja postara się, żeby rzadziej odtwarzać tę fiszkę.
- gdy nic nie wybierzemy, to znak, że znamy materiał, ale warto go powtarzać.  

**Ale telefon jest w kieszeni !!! Co mam naciskać ???**

My biegacze, biegamy już w słuchawkach i ręce oprócz nudnych ruchów biegowych są **WOLNE** !!!<br>
Słuchawki ... ręce .... ??? Hmmmm ... Tak zgadłeś! Wykorzystajmy słuchawki to oceny fiszki!!!<br>
Zasada jest prosta:
- przycisk na słuchawkach **"Next"** oznacza **"Wiem!"**,
- przycisk **"Prev"** - **"Nie wiem"**
- przycisk **Pause/Start** - zatrzymuje/wznawia odtwarzanie,


## Inne ciekawostki!

Są takie ...
- aplikacja w trybie nauki zawsze losuje kolejność odtwarzania fiszek, ale bierze też pod uwagę ocenę zapamiętania fiszki.
- przy tworzeniu fiszki, nie musisz wpisywać wyrażeń w obu językach. Jest dostępna opcja tłumaczenia! 
- wpisywanie fiszek na telefonie, nie jest zawsze wygodnie. Jest zatem dostępna wersja na przeglądarkę, która to świetnie robi.


## Plany ??

"Fiszki w biegu" to projekt "domowy", ale przy tworzeniu tej aplikacji rodzą się kolejne pomysły jak:
- kolekcje publiczne, 
- opcje dla trybu nauki np. ile powtórek, czy np. najpierw j.angielski, potem j.polski,
- dodanie standardowego logowania i logowania via Apple, Facebook, 
- sterowanie głosowe, czyli nie potrzebujemy rąk ! 
- import kolekcji z pliku tekstowego,
- tworzenie kolekcji tematycznych przez AI,
- aplikacja na iOS,
- zmiana domeny,
- aplikacja w sklepach Google, Apple, 

## Wow! Super! Ale gdzie demo?!

Aplikację na Android można pobrać z https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk.

Aplikację można obejrzeć na https://fiszkiwbiegu.xtaxi.eu.

Jako że aplikacja na telefon z Androidem jest "domowa", to przy instalacji pojawi się "ostrzeżenie".
Trzeba je zignorować! Kod jest dostępny, więc obiecuję, że nic niecnego nie będzie się działo :)

Aplikacja na "przeglądarkę" przeznaczona jest jedynie do wprowadzania danych.  
Z niej też można pobrać aplikację na telefon z Androidem.



# Dalej, to technikalia ...



Audio flashcard app for runners — Polish↔English vocabulary, hands-free. Kotlin Multiplatform monorepo targeting Android and Web (WebAssembly), with a Rust/Actix-web backend.

## Project Structure

```
apps/
  backend/        — Rust/Actix-web API (Supabase/PostgreSQL)
  frontend/
    androidApp/   — Android target
    webApp/       — Web target (WASM + JS)
    shared/       — Shared KMP business logic
```

## Running the Web App (WebAssembly)

All Gradle commands are run from `apps/frontend/`.

### Development server

```bash
cd apps/frontend
./gradlew :webApp:composeCompatibilityBrowserDistribution
```
Deploy a dir `webApp/build/dist/composeWebCompatibility/productionExecutable` to a web server

Starts a local dev server with hot reload. Open the URL printed in the console (typically `http://localhost:8080`).

### Production build

```bash
cd apps/frontend
./gradlew :webApp:wasmJsBrowserProductionWebpack
```

#### Web — Wasm target (faster, modern browsers)
```bash
cd apps/frontend
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```


Output lands in `apps/frontend/webApp/build/dist/wasmJs/productionExecutable/`.

### JS target (fallback)

The `webApp` module also compiles to plain JS. To run the JS dev server instead:

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

## Running the Android App

```bash
cd apps/frontend
./gradlew :androidApp:assembleDebug
```

Install the APK from `androidApp/build/outputs/apk/debug/` onto a device or emulator (Android 11+, API 30).

## Running the Backend

```bash
cd apps/backend
cargo run
```

Requires a `.env` file with `DATABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`. See `apps/backend/` for details.

## Running Tests

```bash
cd apps/frontend
./gradlew :shared:test
```
