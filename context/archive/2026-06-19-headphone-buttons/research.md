---
date: 2026-06-19T14:00:00Z
researcher: Robert Karpiński / Claude Sonnet 4.6
git_commit: 76fa7c15593334dfeedc1c4f8c5d3f0665c95377
branch: MVP
repository: FiszkiWBiegu
topic: "Przyciski słuchawek nie docierają do LearningService — diagnoza przez porównanie z RunPlayer"
tags: [research, LearningService, TtsPlayer, MediaSession, ExoPlayer, ForwardingPlayer, headphone-buttons]
status: complete
last_updated: 2026-06-19
last_updated_by: Claude Sonnet 4.6
---

# Research: Przyciski słuchawek nie docierają do LearningService

**Date**: 2026-06-19  
**Git Commit**: 76fa7c15593334dfeedc1c4f8c5d3f0665c95377  
**Branch**: MVP  
**Repository**: FiszkiWBiegu

## Research Question

Przyciski NEXT/PREV na słuchawkach (Bluetooth AVRCP + przewodowe) nie reagują w trybie nauki mimo wielokrotnych prób naprawy przez AI (REPEAT_MODE_ALL, onMediaButtonEvent callback). Referencja: `D:\Projekty\android\RunPlayer` — aplikacja gdzie to działa prawidłowo na każdym testowanym telefonie.

## Summary

**Główna przyczyna**: `TtsPlayer` (rozszerza `SimpleBasePlayer`) nigdy nie wydaje dźwięku przez standardowy system audio Android — tym zajmuje się `TextToSpeech` z osobnym focusem audio. System Android nie wie, że `TtsPlayer` jest aktywnym odtwarzaczem. Bluetooth AVRCP routuje przyciski do sesji, która jest ostatnim prawdziwym właścicielem audio focusu — często jest to silnik TTS lub inna aplikacja, nie nasza `MediaSession`.

**Rozwiązanie**: Zastąpić `TtsPlayer` (SimpleBasePlayer) przez `ForwardingPlayer` owijający `ExoPlayer` — dokładnie tak jak w RunPlayer. ExoPlayer gra plik audio (choćby ciszę w pętli), dzięki czemu: (1) automatycznie zarządza audio focusem, (2) MediaSession jest zarejestrowana jako "aktywna", (3) przyciski słuchawek docierają do `ForwardingPlayer.seekToNext()` i `seekToPrevious()`.

---

## Detailed Findings

### Finding 1 — RunPlayer: ExoPlayer + ForwardingPlayer, gra prawdziwe audio

**Plik**: `D:\Projekty\android\RunPlayer\app\src\main\java\pl\rkarpinski\runplayer\MainService.kt`

```kotlin
// MainService.kt:67-72 — ExoPlayer z audio focusem
exoPlayer = ExoPlayer.Builder(this).build().apply {
    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    setAudioAttributes(audioAttributes, true) // true = automatyczny audio focus
    repeatMode = Player.REPEAT_MODE_ONE
}

// MainService.kt:87-102 — ForwardingPlayer z overrideami seekToNext
player = object : ForwardingPlayer(exoPlayer) {
    override fun seekToNext() { onNextTrack() }
    override fun seekToNextMediaItem() { onNextTrack() }
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
}

// MainService.kt:114-117 — MediaSession budowany na ForwardingPlayer
mediaSession = MediaSession.Builder(this, player)
    .setSessionActivity(pendingIntent)
    .setCallback(CustomMediaSessionCallback())
    .build()

// MainService.kt:212-217 — ExoPlayer faktycznie gra plik audio
private fun prepareMediaPlayer() {
    updateMetadata()   // ustawia MediaItem z URI do .mp3
    setRate(track)
    player.prepare()   // ExoPlayer → STATE_READY
    // player.play() wołany po prepare gdy session aktywna
}
```

**Kluczowy fakt**: ExoPlayer gra rzeczywiste pliki `.mp3` (`res/raw/a180`, `res/raw/a120`). Wywołanie `player.play()` powoduje że ExoPlayer:
1. Żąda audio focusu (`AUDIOFOCUS_GAIN`) od systemu
2. Zaczyna wypychać audio do systemu
3. System rejestruje tę sesję jako "aktywną"
4. Przyciski słuchawek (AVRCP/KeyEvent) są rutowane do tej MediaSession

Brak `onMediaButtonEvent()` override — Media3 obsługuje to automatycznie przez `ForwardingPlayer.seekToNext()`.

---

### Finding 2 — FiszkiWBiegu: TtsPlayer (fake player) bez audio focusu

**Plik**: `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt`

```kotlin
// TtsPlayer.kt:1-86 — CAŁY plik
class TtsPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

    private var isPlayWhenReady = false
    // ... tylko zarządza stanem notyfikacji, nigdy nie wydaje dźwięku

    override fun getState(): State = State.Builder()
        .setAvailableCommands(...)
        .setPlayWhenReady(isPlayWhenReady, ...)
        .setPlaybackState(STATE_READY)   // zawsze READY — niezależnie od stanu TTS
        .setRepeatMode(Player.REPEAT_MODE_ALL)  // dodane jako próba naprawy
        .setPlaylist(ImmutableList.of(...))
        .build()

    // handleSeek() wywoływane przez SimpleBasePlayer.seekToNext()
    // ALE: seekToNext() może nie wywołać handleSeek() jeśli player nie ma "audio"
    override fun handleSeek(...): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, ... -> onSeekToNext?.invoke()
            ...
        }
    }
}
```

**Dlaczego to nie działa**:
- `TtsPlayer` nigdy nie żąda audio focusu
- `TextToSpeech` w `LearningService` żąda focusu samodzielnie (wewnętrznie) gdy mówi
- Dla systemu Android: właścicielem audio jest `TextToSpeech` (Android TTS engine), nie nasza MediaSession
- Bluetooth AVRCP (i część wdrożeń wired headphone handling) rutuje przyciski do sesji powiązanej z audio focusem lub do "ostatnio aktywnej" sesji
- Nasza MediaSession NIGDY nie miała prawdziwego audio focusu → nie jest traktowana jako aktywna

---

### Finding 3 — Próby naprawy przez AI nie działały bo atakują zły problem

Dotychczasowe zmiany AI:
1. `REPEAT_MODE_ALL` w `TtsPlayer.getState()` — próba naprawy `SimpleBasePlayer.seekToNext()` nie wywołującego `handleSeek()`. Prawdziwy problem jest wyżej (audio focus), więc to nie pomogło.
2. `onMediaButtonEvent()` callback w `MediaSession.Builder` — przechwytuje `ACTION_MEDIA_BUTTON` Intent. ALE: na Android 11+ Bluetooth AVRCP idzie przez inną ścieżkę (bezpośrednie wywołanie `player.seekToNext()` lub `player.seekToNextMediaItem()`), nie przez `ACTION_MEDIA_BUTTON` broadcast. Bez audio focusu system może nie rutować tych zdarzeń do naszej sesji w ogóle.

---

### Finding 4 — Różnica architektoniczna: ForwardingPlayer vs SimpleBasePlayer

| Aspekt | RunPlayer (działa) | FiszkiWBiegu (nie działa) |
|---|---|---|
| Klasa playera | `ForwardingPlayer(ExoPlayer)` | `SimpleBasePlayer` (custom) |
| Audio focus | Automatyczny przez ExoPlayer (`setAudioAttributes(..., true)`) | Brak |
| Rzeczywiste audio | Tak — pliki `.mp3` przez ExoPlayer | Nie — audio przez TTS osobno |
| seekToNext override | Bezpośredni override metody | Pośredni: SimpleBasePlayer → handleSeek() → callback |
| Media3 uzna sesję za aktywną | Tak (ExoPlayer gra audio) | Niepewne (TTS ma focus, nie ExoPlayer/TtsPlayer) |
| onMediaButtonEvent | Nie potrzebny | Dodany, ale może nie być wywoływany |

---

### Finding 5 — Co w TtsPlayer jest nadmiarowe po wdrożeniu ExoPlayer

Jeśli zastąpimy `TtsPlayer` przez `ForwardingPlayer(ExoPlayer)`, to:
- Cały `TtsPlayer.kt` staje się zbędny (można usunąć)
- `REPEAT_MODE_ALL` nie będzie potrzebny (ExoPlayer sam zarządza stanem)
- `onMediaButtonEvent()` override w `LearningService` nie będzie potrzebny (Media3 + ForwardingPlayer obsługuje to automatycznie)
- `onSeekToNext` / `onSeekToPrevious` callbacki w `LearningService.onCreate()` nie będą potrzebne

---

## Code References

- `D:\Projekty\android\RunPlayer\app\src\main\java\pl\rkarpinski\runplayer\MainService.kt:67-102` — ExoPlayer + ForwardingPlayer setup
- `D:\Projekty\android\RunPlayer\app\src\main\java\pl\rkarpinski\runplayer\MainService.kt:212-217` — prepareMediaPlayer() → ExoPlayer.prepare()
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/TtsPlayer.kt:1-86` — cały TtsPlayer (fake SimpleBasePlayer)
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:184-198` — onCreate() z MediaSession.Builder
- `apps/frontend/androidApp/src/main/kotlin/pl/rkarpinski/fiszkiwbiegu/LearningService.kt:406-416` — rateCard() — docelowy handler dla NEXT/PREV

---

## Architecture Insights

### Dlaczego ForwardingPlayer działa a SimpleBasePlayer nie

`ForwardingPlayer.seekToNext()` to bezpośredni override — gdy Media3 wywołuje tę metodę na playerze, wykonuje się nasz kod natychmiast. Nie ma warunków, state machine, ani sprawdzania czy jest "następny element".

`SimpleBasePlayer.seekToNext()` jest metodą `final` z logiką warunkową:
```java
if (hasNextMediaItem(state)) {
    seekToDefaultPositionInternal(getNextMediaItemIndex(state), COMMAND_SEEK_TO_NEXT);
}
```
Mogą być edge case'y gdzie `handleSeek()` nie jest wołany. To jest DRUGI problem — ale mniej ważny niż audio focus.

### Jak Android rutuje przyciski słuchawek (Android 11+)

Na Android 11+ (minSdk=30) system preferuje sesję, która:
1. Jest w stanie "playing" (`playWhenReady=true` + `STATE_READY/BUFFERING`) ORAZ
2. Ma audio focus (AUDIOFOCUS_GAIN lub AUDIOFOCUS_GAIN_TRANSIENT)

Nasza `TtsPlayer` spełnia warunek 1 (gdy TTS mówi, `setPlaying(true)` jest wywoływane), ale **nie spełnia warunku 2** — audio focus ma silnik TTS, nie nasza MediaSession.

---

## Recommended Fix

### Plan implementacji (wzorowany na RunPlayer)

**Krok 1**: Dodaj plik ciszy do zasobów
- `apps/frontend/androidApp/src/main/res/raw/silence.mp3` — 1-sekundowy cichy plik audio, odtwarzany w pętli przez ExoPlayer
- Zapewnia że ExoPlayer jest w STATE_READY i ma audio focus

**Krok 2**: Zamień `TtsPlayer` na `ExoPlayer` + `ForwardingPlayer` w `LearningService`

```kotlin
// W onCreate():
val exoPlayer = ExoPlayer.Builder(this)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build(),
        true  // handleAudioFocus = true — kluczowy parametr!
    )
    .build().also { player ->
        val silenceUri = Uri.parse("android.resource://$packageName/${R.raw.silence}")
        player.setMediaItem(MediaItem.fromUri(silenceUri))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
    }

val player = object : ForwardingPlayer(exoPlayer) {
    override fun seekToNext() = rateCard(Rating.KNOW_WELL)
    override fun seekToNextMediaItem() = rateCard(Rating.KNOW_WELL)
    override fun seekToPrevious() = rateCard(Rating.DONT_KNOW)
    override fun seekToPreviousMediaItem() = rateCard(Rating.DONT_KNOW)
    
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
}

mediaSession = MediaSession.Builder(this, player).build()
```

**Krok 3**: Zarządzanie stanem play/pause przez ExoPlayer
- `resume()`: `exoPlayer.play()` (zamiast `ttsPlayer.setPlaying(true)`)
- `pause()`: `exoPlayer.pause()` (zamiast `ttsPlayer.setPlaying(false)`)
- `updateCurrentItem()`: zaktualizować metadata na ExoPlayerze (dla notyfikacji)

**Krok 4**: Usuń `TtsPlayer.kt` — cały plik zbędny

**Krok 5**: Usuń z `LearningService.onCreate()`:
- `player.onSeekToNext = ...`
- `player.onSeekToPrevious = ...`
- `MediaSession.Callback` z `onMediaButtonEvent()` (nie potrzebny)

### Co zostaje bez zmian

- `notificationProvider` i cały system notyfikacji — działa i pozostaje
- `TextToSpeech` i `speakAndWait()` — audio przez TTS bez zmian
- `playLoop()`, `rateCard()`, `applyRating()` — logika biznesowa bez zmian
- `AndroidManifest.xml` — bez zmian
- Zależność `media3-exoplayer` — już zadeklarowana? Sprawdzić `build.gradle`

### Zależności do sprawdzenia

```kotlin
// shared/build.gradle.kts lub androidApp/build.gradle.kts
implementation("androidx.media3:media3-exoplayer:1.10.1")
```

Jeśli `media3-exoplayer` nie jest dodana, trzeba ją dodać.

---

## Open Questions

1. Czy `media3-exoplayer` jest już w zależnościach `androidApp`? (sprawdzić `build.gradle.kts`)
2. Czy plik `silence.mp3` jest potrzebny, czy ExoPlayer z `AudioAttributes` i `setPlayWhenReady(true)` bez media item też nabędzie audio focus?
3. Czy TTS przy mówieniu będzie "dukować" ExoPlayer (duck/pause)? Trzeba przetestować koegzystencję audio TTS + "cichy" ExoPlayer.
4. Jak zaktualizować metadata w notyfikacji jeśli `ForwardingPlayer` deleguje do ExoPlayer — czy `exoPlayer.replaceMediaItem()` z nową `MediaMetadata` wystarczy?
