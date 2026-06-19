This is a Kotlin Multiplatform project targeting Android, iOS, Web.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

#### Android
```bash
./gradlew :androidApp:assembleDebug
```

#### Web — Wasm target (faster, modern browsers)
```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

#### Web — JS target (slower, supports older browsers)
```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

#### Web — production build (Wasm) → webApp/build/dist/wasmJs/productionExecutable/
```bash
./gradlew :webApp:wasmJsBrowserProductionWebpack
```


iOS: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

#### Shared (all platforms)
```bash
./gradlew :shared:test
```

#### Android
```bash
./gradlew :shared:testAndroidHostTest
```

#### Web — Wasm
```bash
./gradlew :shared:wasmJsTest
```

#### Web — JS
```bash
./gradlew :shared:jsTest
```

#### iOS
```bash
./gradlew :shared:iosSimulatorArm64Test
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).