---
date: 2026-06-19T00:00:00+02:00
researcher: Robert Karpiński
git_commit: 3f46a1b457b0249ae39fa4f5e33556f616d0e14e
branch: MVP
repository: FiszkiWBiegu
topic: "Przyciek back stack — powrót do nauki po kliknięciu powiadomienia"
tags: [research, back-stack, MainActivity, LearningService, PendingIntent, navigation3]
status: complete
last_updated: 2026-06-19
last_updated_by: Robert Karpiński
---

# Research: Przyciek back stack — powrót do nauki po kliknięciu powiadomienia

**Date**: 2026-06-19
**Git Commit**: 3f46a1b457b0249ae39fa4f5e33556f616d0e14e
**Branch**: MVP

## Research Question

Jest problem przy obsłudze back (przyciek) w Androidzie. Nie zawsze działa to z logiką lub drzewem wywołań aplikacji. Jest to psute gdy wejdzie się w tryb nauki i potem kliknie się na powiadomienie. Pierwszy back wraca do kolekcji, ale kolejny back wraca do nauki — zamiast do okna głównego.

## Summary

**Główna przyczyna: dwie instancje MainActivity w dwóch osobnych zadaniach (Tasks) Androida.**

`MainActivity` ma `launchMode = "standard"` (brak jawnego ustawienia w manifeście). Gdy użytkownik klika powiadomienie po tym, jak aplikacja zeszła w tło, Android (12+) automatycznie dołącza `FLAG_ACTIVITY_NEW_TASK` do intencji PendingIntent. Przy `launchMode = "standard"` może to skutkować utworzeniem NOWEGO zadania z NOWĄ instancją MainActivity zamiast wznowienia istniejącej. W efekcie:
- Zadanie stare (T1): `[MainActivity(1)]` z Compose backStack `[Collections, Flashcards, Learning]`
- Zadanie nowe (T2): `[MainActivity(2)]` z Compose backStack `[Collections, Learning]`

Pierwszy back (in-app ←) w T2 czyści backStack → `[Collections]`.
Drugi back (system) zamyka T2 → Android wraca do T1 → wyświetla Learning z pierwszej instancji.

**Dodatkowa przyczyna pomocnicza: brak `setIntent(intent)` w `onNewIntent()`.**

## Detailed Findings

### 1. MainActivity — brak `launchMode` i brak `setIntent`

**Plik:** `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {
    // ...
    private var initialCollectionJson by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initialCollectionJson = intent.getStringExtra("collection_json")   // linia 21
        setContent { App(..., initialCollectionJson = initialCollectionJson) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initialCollectionJson = intent.getStringExtra("collection_json")   // linia 38
        // ❌ brak: setIntent(intent)
    }
}
```

**AndroidManifest.xml (MainActivity):**
```xml
<activity
    android:exported="true"
    android:name=".MainActivity">
    <!-- ❌ brak android:launchMode="singleTop" -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- `launchMode` nie jest ustawiony → domyślna wartość: `"standard"`
- Przy `"standard"`, `FLAG_ACTIVITY_NEW_TASK` (dodawany przez system dla powiadomień) może stworzyć nowe zadanie z nową instancją

### 2. PendingIntent powiadomienia w LearningService

**Plik:** `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:265-275`

```kotlin
private fun updateSessionActivity() {
    val intent = Intent(this, MainActivity::class.java).apply {
        putExtra(EXTRA_COLLECTION_JSON, collectionJson)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP           // ← tylko SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    mediaSession.setSessionActivity(pendingIntent)
}
```

Brak `FLAG_ACTIVITY_CLEAR_TOP` i `FLAG_ACTIVITY_NEW_TASK` na intencji. Android 12+ dołącza `FLAG_ACTIVITY_NEW_TASK` automatycznie do PendingIntent z powiadomień. Bez `singleTop` launchMode na Activity + z `NEW_TASK` → nowe zadanie.

### 3. Compose backStack i nawigacja (App.kt)

**Plik:** `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt`

```kotlin
// Inicjalizacja backStack (linie 90-101):
val backStack = remember {
    val list = mutableStateListOf<Route>()
    if (authRepository.isLoggedIn()) {
        list.add(Route.Collections)
        if (initial is Route.Learning) list.add(initial)
    } else {
        list.add(initial)
    }
    list
}

// LaunchedEffect na zmianę initialCollectionJson (linie 103-124):
LaunchedEffect(initialCollectionJson) {
    val collection = initialCollectionJson?.let { ... }
    if (collection != null) {
        val targetRoute = Route.Learning(collection)
        if (backStack.lastOrNull() != targetRoute) {  // porównanie data class
            if (authRepository.isLoggedIn()) {
                backStack.clear()
                backStack.add(Route.Collections)
                backStack.add(targetRoute)
            }
        }
    }
}

// LearningScreen onBack (linie 231-238):
entry<Route.Learning> { route ->
    LearningScreen(
        collection = route.collection,
        onBack = {
            backStack.clear()
            backStack.add(Route.Collections)   // czyszczenie + Collections
        },
    )
}
```

## Precyzyjne odtworzenie Buga

```
1. App uruchomiona normalnie (nie z powiadomienia)
   Android Task T1: [MainActivity(1)]
   Compose backStack: [Collections]

2. Użytkownik nawiguje: Collections → Flashcards → Learning
   Compose backStack: [Collections, Flashcards, Learning]
   LearningService działa, powiadomienie widoczne

3. Użytkownik naciska HOME lub przełącza aplikację
   T1 → background
   Compose backStack: [Collections, Flashcards, Learning] (zachowana w pamięci)

4. Użytkownik klika powiadomienie
   PendingIntent fires: Intent(MainActivity, FLAG_ACTIVITY_SINGLE_TOP)
   System (Android 12+) dodaje FLAG_ACTIVITY_NEW_TASK
   launchMode="standard" + FLAG_ACTIVITY_NEW_TASK → NOWE ZADANIE T2
   
   Android Task T2: [MainActivity(2)]
   MainActivity(2).onCreate() z collection_json
   initialCollectionJson = "json..."
   Compose backStack (T2): [Collections, Learning]

5. Użytkownik widzi Learning w T2
   Pierwszy back (← in-app):
     onBack → backStack.clear(); add(Collections)
     Compose backStack (T2): [Collections]
   → POPRAWNIE widać Collections ✓

6. Użytkownik naciska systemowy back
   NavDisplay: backStack.size == 1 → BackHandler disabled
   Activity finish → T2 się zamyka
   Android WRACA DO T1: [MainActivity(1)]
   Compose backStack (T1): [Collections, Flashcards, Learning]
   → WYŚWIETLA Learning! ✗ BUG
```

## Code References

- `apps/frontend/androidApp/src/main/AndroidManifest.xml:18-26` — MainActivity bez `launchMode`
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/MainActivity.kt:36-39` — `onNewIntent` bez `setIntent(intent)`
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:265-275` — `updateSessionActivity()` — tylko `FLAG_ACTIVITY_SINGLE_TOP`
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt:90-101` — inicjalizacja backStack
- `apps/frontend/shared/src/commonMain/kotlin/pl/rkarpinski/fiszkiwbiegu/App.kt:103-124` — `LaunchedEffect(initialCollectionJson)`

## Fix — wymagane zmiany (2 pliki)

### Fix 1: AndroidManifest.xml — dodaj `launchMode="singleTop"`

```xml
<activity
    android:exported="true"
    android:name=".MainActivity"
    android:launchMode="singleTop">
```

`singleTop` gwarantuje: jeśli instancja MainActivity jest na wierzchołku DOWOLNEGO zadania z tą aktywnością, system wywoła `onNewIntent()` zamiast tworzyć nową. Eliminuje scenariusz z dwoma zadaniami.

### Fix 2: MainActivity.kt — dodaj `setIntent(intent)` w `onNewIntent`

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)   // ← dodać
    initialCollectionJson = intent.getStringExtra("collection_json")
}
```

Bez `setIntent()`, `getIntent()` wciąż zwraca stary intent z `onCreate`. Może powodować subtelne błędy przy rotacji ekranu lub odtworzeniu Activity.

### Opcjonalnie — Fix 3: LearningService — dodaj `FLAG_ACTIVITY_CLEAR_TOP`

```kotlin
flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
```

Defensywne uzupełnienie: `CLEAR_TOP` czyści wszystko nad MainActivity jeśli istnieje (choć przy `singleTop` w manifeście nie jest wymagane).

## Architecture Insights

- Aplikacja jest single-Activity (jedna MainActivity) — dla takich aplikacji `launchMode="singleTop"` jest standardem; `singleTask` jest zbyt agresywny (może powodować problemy z innymi scenariuszami uruchamiania).
- Compose `backStack` jako `mutableStateListOf` jest wewnętrznym stanem danej instancji Activity — przy dwóch instancjach każda ma własny backStack, co leży u podstaw buga.
- `LaunchedEffect(initialCollectionJson)` z porównaniem `data class` działa poprawnie pod względem logiki Compose, ale nie rozwiązuje problemu na poziomie Androida.

## Open Questions

- Czy po poprawce `singleTop` LaunchedEffect (linie 103-124 w App.kt) może redundantnie przebudowywać backStack gdy user wraca z powiadomienia będąc już na LearningScreen? (Odpowiedź: nie — bo porównanie `backStack.lastOrNull() != targetRoute` zwróci false przy tym samym collection)
