# aparte-titler for Kotlin and Android

**Name a chat conversation on the device — no API call, no network, 133 KB.**

Give it the first message of a conversation, get back a short title made of words from that message:

```kotlin
import dev.apartejs.titler.latin.TitlerLatin

val titler = TitlerLatin.load()
titler.title("Write me a cover letter for a junior data analyst position at a bank")
// "letter junior data analyst position bank"
```

Built for chat apps, note-taking and voice-memo apps, and on-device LLM clients that need a conversation title without spending a request on it.

- **On-device and private** — the text never leaves the phone.
- **Works offline**, with no server and no key to manage.
- **Small** — 133 KB for 17 languages, or 41 KB for one.
- **Fast** — about 8 ms per title on one desktop CPU core, no GPU, no NNAPI, no TensorFlow Lite.
- **Safe from prompt injection by construction** — it only ever copies words from the message, so an instruction hidden in a message can at worst produce a poor title, never be followed.
- **No dependency** but the Kotlin standard library. Usable from Java too.

**17 languages**: English, French, Spanish, German, Portuguese, Italian, Dutch, Polish, Swedish, Danish, Finnish, Czech, Romanian, Norwegian, Hungarian, Croatian, Lithuanian.

- Presentation and live demo: **[apartejs.dev/models/titler](https://apartejs.dev/models/titler/)**
- Models, scores and benchmark: [huggingface.co/apartejs/aparte-titler](https://huggingface.co/apartejs/aparte-titler)
- Also for iOS and macOS: [aparte-titler-swift](https://github.com/apartejs/aparte-titler-swift), and for JavaScript: [`@aparte/titler`](https://www.npmjs.com/package/@aparte/titler) on npm

## Install

From Maven Central, in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.apartejs:titler-latin:1.2.0")
}
```

Two artifacts:

| artifact | what it contains |
|---|---|
| `dev.apartejs:titler-latin` | the runtime and the 17-language model (133 KB), ready to use |
| `dev.apartejs:titler` | the runtime alone — bring any model file from [Hugging Face](https://huggingface.co/apartejs/aparte-titler) |

Android 5.0 (API 21) and later, any JVM 8 or later, and Kotlin 2.0 or later (or Java). No ProGuard or R8 rule needed.

## Use

Load the model once and keep the `Titler`. It is immutable and safe to share across threads. Loading takes a few milliseconds: on Android, do it off the main thread.

```kotlin
val titler = TitlerLatin.load()

titler.title("Peux-tu m'expliquer la photosynthèse chez les plantes ?")
titler.title("Kannst du mir helfen, einen Geburtstagsbrief für meine Oma zu schreiben?")
```

Give it **only the user's first message** — never a system prompt or the conversation history.

**One language, smaller.** Each language also has its own 41 KB model. Download `v1.2/titler-v1.2-<lang>-int3.bin` from [Hugging Face](https://huggingface.co/apartejs/aparte-titler/tree/main/v1.2), put it in your app's `src/main/assets/`, and load it with the runtime alone:

```kotlin
import dev.apartejs.titler.Titler

val titler = context.assets.open("titler-v1.2-fr-int3.bin").use { Titler.load(it) }
```

**Options.**

```kotlin
titler.title(message, budget = 6)                 // exactly the six best words, as version 1.0 did
titler.title(message, stopWords = setOf("salut"))  // words never to keep, as long as three remain
titler.words(message)                              // every word with its score from 0 to 1
```

By default the title keeps, among the six best-scored words, those the model is at least half sure of — never fewer than three — and every word of a message that has four or fewer.

**From Java.**

```java
Titler titler = TitlerLatin.load();
String title = titler.title(message);
```

## How good is it

On a benchmark of real chat messages in 17 languages, the 17-language model scores nearly twice a naive baseline (the first five words of the message), and on the six languages where the comparison can be made fairly it reaches 94 % of the 2.4 GB language model it was distilled from — a model 18,000 times heavier. The full tables, the benchmark and its limits are on the [model card](https://huggingface.co/apartejs/aparte-titler).

## Same titles everywhere

This library gives **exactly** the titles of the JavaScript runtime, of the Swift package and of the Python reference implementation — not approximately: the tests run 1,496 messages in five languages, in both decodings, and compare every title character for character, at the four precisions of the model files (int3, int4, int8, fp32), on the JVM and on Android emulators. A title computed on the server, in the browser, on an iPhone or on an Android phone is the same title.

```bash
./gradlew test
APARTE_TITLER_ROOT=../aparte-titler ./gradlew test   # also int4, int8 and fp32
```

## License

MIT © 2026 Paul Richez. The model was distilled from titles written by [gemma 4 e2b](https://huggingface.co/google/gemma-4-E2B-it); the training data and its licences are described in [the dataset](https://huggingface.co/datasets/apartejs/aparte-titler-data).
