---
date: 2026-06-19T09:12:00Z
researcher: Claude Sonnet 4.6
git_commit: a2006b63b56571926f026aaff6ab7ca99b60e995
branch: MVP
repository: FiszkiWBiegu
topic: "Zamiana Box+clickable na właściwe Button composables"
tags: [research, compose, buttons, ui-refactor, frontend]
status: complete
last_updated: 2026-06-19
last_updated_by: Claude Sonnet 4.6
---

# Research: Zamiana Box+clickable na właściwe Button composables

**Date**: 2026-06-19T09:12:00Z
**Git Commit**: a2006b63b56571926f026aaff6ab7ca99b60e995
**Branch**: MVP
**Repository**: FiszkiWBiegu

## Research Question

W frontendzie stosowany jest "dziwny" wzorzec UI: `Box` (lub `Row`/`Column`) z modyfikatorem `.clickable` pełni rolę przycisku zamiast właściwych komponentów Compose. Znaleźć wszystkie takie miejsca i zaproponować zamianę na `Button`, `IconButton`, `FloatingActionButton` itp. — bez zmiany wyglądu.

## Summary

W 9 plikach znaleziono ~37 instancji fałszywych przycisków. Dzielą się na 6 kategorii, z których każda ma dedykowany composable Material3:

| Kategoria | Liczba | Docelowy composable |
|---|---|---|
| Duże CTA (full-width 56dp) | 4 | `Button` z custom `colors` + `height` |
| Małe ikony (40dp kwadrat z obramowaniem) | 5 | `IconButton` + custom `modifier` |
| Przyciski FAB (koło 60dp) | 2 | `FloatingActionButton` / `SmallFloatingActionButton` |
| Przyciski oceny w trybie nauki | 2 | `OutlinedButton` / `Button` z animowanym kolorem |
| Chipy prędkości | 4 | `FilterChip` |
| Przyciski logowania (AuthButton) | 3 | refaktor wewnątrz AuthButton na `Button` |
| Klikalne wiersze listy / elementy nawigacyjne | ~17 | **bez zmian** — `Row.clickable` to poprawny wzorzec |

**Kluczowa zasada migracji**: `Button` w Material3 przyjmuje parametry `colors`, `shape`, `modifier`, `enabled` i `contentPadding` — wystarczy je ustawić tak, żeby wizualnie odwzorować aktualny wygląd.

## Detailed Findings

### 1. Duże przyciski CTA (full-width, 56dp, primary/disabled)

Wzorzec powtarza się w 4 miejscach:

```
shared/.../screens/flashcards/FlashcardsScreen.kt:278-303   — "Słuchaj w biegu"
shared/.../screens/flashcards/CardFormScreen.kt:318-352      — "Zapisz zmiany" / "Dodaj fiszkę"
shared/.../screens/collections/CollectionFormScreen.kt:232-266 — "Zapisz zmiany" / "Dodaj kolekcję"
shared/.../screens/collections/CollectionsScreen.kt:365-388 — "Wznów" (ResumeButton)
```

**Aktualny wzorzec:**
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(MaterialTheme.shapes.large)
        .background(if (enabled) scheme.primary else scheme.surfaceVariant)
        .then(if (enabled) Modifier.clickable(onClick = ...) else Modifier),
    contentAlignment = Alignment.Center,
) {
    Row(...) { Icon(...); Text(...) }
}
```

**Zamiana na Button:**
```kotlin
Button(
    onClick = actions::onStartLearning,
    enabled = ctaEnabled,
    modifier = Modifier.fillMaxWidth().height(56.dp),
    shape = MaterialTheme.shapes.large,
    colors = ButtonDefaults.buttonColors(
        containerColor = scheme.primary,
        contentColor = scheme.onPrimary,
        disabledContainerColor = scheme.surfaceVariant,
        disabledContentColor = c.mute2,
    ),
    contentPadding = PaddingValues(horizontal = 16.dp),
) {
    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text("Słuchaj w biegu", style = MaterialTheme.typography.titleMedium)
}
```

Zalety: brak ręcznego `.clip`+`.background`, poprawna semantyka (Accessibility), wbudowany ripple, disabled działa przez `enabled=`.

> Uwaga: `Button` domyślnie ma `contentPadding = ButtonDefaults.ContentPadding` (16dp poziomo). Ustaw `contentPadding = PaddingValues(0.dp)` lub dopasuj, żeby Row wewnątrz wyglądał tak samo jak teraz.

---

### 2. Małe przyciski ikonowe (40dp kwadrat z obramowaniem)

Wzorzec back/delete w 5 miejscach:

```
shared/.../screens/collections/CollectionFormScreen.kt:122-137  — back button
shared/.../screens/collections/CollectionFormScreen.kt:146-161  — delete button
shared/.../screens/flashcards/CardFormScreen.kt:150-165         — back/close button
shared/.../screens/flashcards/CardFormScreen.kt:174-189         — delete button
shared/.../screens/flashcards/FlashcardsScreen.kt:175-194       — back button
shared/.../screens/learning/LearningScreen.kt:124-139           — back button
```

**Aktualny wzorzec:**
```kotlin
Box(
    modifier = Modifier
        .size(40.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(scheme.surface)
        .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
        .clickable(onClick = actions::onBack),
    contentAlignment = Alignment.Center,
) {
    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = scheme.onSurface)
}
```

**Zamiana na IconButton:**
```kotlin
IconButton(
    onClick = actions::onBack,
    modifier = Modifier
        .size(40.dp)
        .clip(MaterialTheme.shapes.medium)
        .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
        .background(scheme.surface),
) {
    Icon(Icons.Default.ArrowBack, contentDescription = "Wróć", tint = scheme.onSurface)
}
```

> Uwaga: `IconButton` ma domyślny rozmiar 48dp i własny padding. Ustawiając `.size(40.dp)` PRZED innymi modyfikatorami, wymuszamy rozmiar. Wewnętrznie ikona wypełni całość, więc efekt będzie identyczny.

---

### 3. Przyciski FAB (koło 60dp)

```
shared/.../screens/collections/CollectionsScreen.kt:98-111   — Add collection FAB
shared/.../screens/flashcards/FlashcardsScreen.kt:149-162    — Add flashcard FAB
```

**Aktualny wzorzec:**
```kotlin
Box(
    modifier = Modifier
        .size(60.dp)
        .clip(CircleShape)
        .background(scheme.inverseSurface.copy(alpha = 0.5f))
        .clickable { onAddClick() },
    contentAlignment = Alignment.Center,
) {
    Icon(Icons.Default.Add, contentDescription = null, tint = scheme.inverseOnSurface)
}
```

**Zamiana na FloatingActionButton:**
```kotlin
FloatingActionButton(
    onClick = { onAddClick() },
    modifier = Modifier.size(60.dp),
    shape = CircleShape,
    containerColor = scheme.inverseSurface.copy(alpha = 0.5f),
    contentColor = scheme.inverseOnSurface,
) {
    Icon(Icons.Default.Add, contentDescription = "Dodaj")
}
```

---

### 4. Przyciski oceny w trybie nauki (Don't Know / Know Well)

```
shared/.../screens/learning/LearningScreen.kt:371-395  — "Nie wiem"
shared/.../screens/learning/LearningScreen.kt:403-427  — "Wiem!"
```

Mają **animowany kolor tła** (`animateColorAsState`) i disabled state. To najbardziej złożony przypadek — zachowanie animacji musi zostać zachowane.

**Aktualny wzorzec** (skrót):
```kotlin
val bg by animateColorAsState(
    if (dontKnowPressed) Color(0xFFE53935).copy(alpha = 0.3f) else scheme.surfaceVariant
)
Box(
    modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .clip(MaterialTheme.shapes.large)
        .background(bg)
        .border(1.dp, if (isAnswerPhase) scheme.outline else scheme.outlineVariant, MaterialTheme.shapes.large)
        .then(if (isAnswerPhase) Modifier.clickable { ... } else Modifier),
    ...
)
```

Zamiana na `Button` z `animateColorAsState` wymaga:
```kotlin
val bg by animateColorAsState(...)
Button(
    onClick = { dontKnowPressed = true; onDontKnow() },
    enabled = isAnswerPhase,
    modifier = Modifier.weight(1f).height(52.dp).border(...),
    shape = MaterialTheme.shapes.large,
    colors = ButtonDefaults.buttonColors(
        containerColor = bg,
        contentColor = scheme.onSurface,
        disabledContainerColor = scheme.surfaceVariant,
        disabledContentColor = c.mute2,
    ),
    contentPadding = PaddingValues(0.dp),
    border = ...,   // Button nie ma border param — użyj modifier.border
) { Text("Nie wiem", ...) }
```

> Uwaga: `Button` nie ma parametru `border`. Obramowanie trzeba dodać przez `modifier.border(...)` przed podaniem jako `modifier` do Button.

---

### 5. Chipy prędkości (LearningScreen)

```
shared/.../screens/learning/LearningScreen.kt:316-334  — 4x chipy "0.50×", "0.75×", "1.0×", "1.25×"
```

**Zamiana na FilterChip:**
```kotlin
FilterChip(
    selected = currentSpeed == value,
    onClick = { onSetSpeed(value) },
    label = { Text(label) },
    shape = MaterialTheme.shapes.medium,
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = scheme.primary,
        selectedLabelColor = scheme.onPrimary,
        containerColor = scheme.surface,
        labelColor = scheme.onSurfaceVariant,
    ),
    border = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = currentSpeed == value,
        selectedBorderColor = scheme.primary,
        borderColor = scheme.outlineVariant,
    ),
)
```

---

### 6. AuthButton (LoginScreen)

```
shared/.../screens/login/LoginScreen.kt:127-145  — prywatny composable AuthButton
```

Wewnętrznie używa `Box`. Zamiana na `OutlinedButton`:
```kotlin
OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(60.dp),
    shape = MaterialTheme.shapes.large,
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = scheme.onSurface,
        disabledContentColor = c.mute2,
    ),
    border = BorderStroke(1.dp, scheme.outlineVariant),
) {
    Text(label)
}
```

---

### 7. CtrlButton (MediaControls)

```
shared/.../ui/components/MediaControls.kt:36-50  — CtrlButton composable
```

Już wyekstrahowany do osobnego composable. Zamiana wewnętrznej implementacji na `IconButton` z niestandardowym backgroundem:
```kotlin
@Composable
fun CtrlButton(icon: ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    val size = if (primary) 78.dp else 52.dp
    val iconSize = if (primary) 30.dp else 22.dp
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).clip(CircleShape).background(
            if (primary) scheme.primary else scheme.surface
        ),
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(iconSize))
    }
}
```

---

### 8. Klikalne wiersze listy (NIE zmieniamy)

Poniższe przypadki to klikalne elementy listy / nawigacji — wzorzec `Row.clickable` jest **poprawny** i nie powinien być zamieniany na `Button`:

```
shared/.../screens/collections/CollectionsScreen.kt:235-290  — CollectionItem (wiersz listy)
shared/.../screens/collections/CollectionsScreen.kt:301-309  — LastUsedHero (klikalny kontener)
shared/.../screens/profile/ProfileScreen.kt:162-184          — Logout menu row
shared/.../ui/components/LangSelect.kt:42-65                 — LangSelect trigger
shared/.../ui/components/LangSelect.kt:74-97                 — LangOption items
shared/.../screens/flashcards/FlashcardsScreen.kt:199-204    — Icon.clickable (menu trigger) → zamienić na IconButton
shared/.../screens/flashcards/FlashcardsScreen.kt:459-463    — Icon.clickable (per-card menu) → zamienić na IconButton
```

Wyjątek: raw `Icon.clickable` (bez kontenera) w dwóch miejscach → warto zamienić na `IconButton` dla semantyki.

## Code References

- `apps/frontend/shared/src/commonMain/.../screens/flashcards/FlashcardsScreen.kt:278-303` — główny przykład CTA (Słuchaj w biegu)
- `apps/frontend/shared/src/commonMain/.../screens/flashcards/CardFormScreen.kt:318-352` — CTA save flashcard
- `apps/frontend/shared/src/commonMain/.../screens/collections/CollectionFormScreen.kt:232-266` — CTA save collection
- `apps/frontend/shared/src/commonMain/.../screens/collections/CollectionsScreen.kt:365-388` — Resume button
- `apps/frontend/shared/src/commonMain/.../screens/learning/LearningScreen.kt:316-334` — speed chips
- `apps/frontend/shared/src/commonMain/.../screens/learning/LearningScreen.kt:371-427` — rating buttons (Don't Know / Know Well)
- `apps/frontend/shared/src/commonMain/.../ui/components/MediaControls.kt:36-50` — CtrlButton
- `apps/frontend/shared/src/commonMain/.../screens/login/LoginScreen.kt:127-145` — AuthButton

## Architecture Insights

1. **Brak wspólnego "AppButton"**: Każdy ekran reimplementuje ten sam wzorzec CTA (full-width 56dp). Po migracji warto wyekstrahować `PrimaryCtaButton` do `Components.kt`.

2. **Dlaczego oryginalnie użyto Box?**: Prawdopodobnie ze względu na pełną kontrolę nad wizualnym wyglądem (kolor disabled, obramowanie, kształt). Material3 `Button` ma wbudowane opinie o wyglądzie, ale jego `ButtonDefaults.buttonColors()` pozwala wszystko nadpisać.

3. **Kolory disabled**: Material3 automatycznie stosuje `disabledContainerColor` gdy `enabled=false` — eliminuje `if (enabled) ... else ...` w konfiguracji koloru.

4. **Semantyka Accessibility**: `Button` i `IconButton` automatycznie dodają `Role.Button` do semantics — niezbędne dla TalkBack. Aktualny `Box.clickable` bez `role = Role.Button` jest niedostępny.

## Open Questions

- Czy chipy prędkości (LearningScreen) mają wystarczająco specyficzny wygląd, żeby zamiast `FilterChip` użyć custom composable? (rozmiar paddingu 12dp/6dp)
- Czy `Button` wewnętrznie stosuje `interactionSource` na animację pressu — czy animowane kolory w przyciskach oceny (Don't Know/Know Well) wymagają dalszego dostosowania?
