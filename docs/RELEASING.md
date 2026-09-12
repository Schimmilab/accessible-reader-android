# Releasing

## Signing

Release builds are signed with a key that is **not** in this repository and never will be. The build reads four Gradle properties:

```
READER_STORE_FILE
READER_STORE_PASSWORD
READER_KEY_ALIAS
READER_KEY_PASSWORD
```

They belong in `~/.gradle/gradle.properties`, outside any repository, with mode 600. `app/build.gradle.kts` checks whether the keystore file actually exists and only then configures signing. **Without the key the release build simply comes out unsigned**, so anyone can clone this project and build it without owning a key.

The signing key of the published builds:

- RSA 4096, self-signed, valid until January 2054
- Certificate SHA-256: `8E:C3:4B:04:97:81:A9:4F:17:FC:11:D2:8E:98:63:6E:2B:A6:0E:9B:11:31:84:15:5E:85:1E:DC:32:C8:00:D8`

That fingerprint is public on purpose. It lets anyone check that a downloaded APK really came from this project:

```sh
apksigner verify --print-certs accessible-reader-0.5.0.apk
```

Signature schemes v2 and v3 are enabled. v3 matters: it carries the proof needed to rotate the key later. Without it the project would be tied to one key forever.

⚠️ **If the key is lost, no further update can ever be installed over an existing install.** Users would have to uninstall and lose their library and listening positions. The keystore needs a backup somewhere other than the machine that built it.

## Building a release

```sh
./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # TalkBack off, see TESTING.md
./gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`.

Then verify, every time, rather than trusting the build:

```sh
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -c application-debuggable   # must be 0
aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -o "versionName='[^']*'"
```

A release build must never report `application-debuggable`. A debuggable APK lets anyone with USB debugging read the app's data, which for this app means someone's books.

## Version numbers

`versionCode` and `versionName` live in `app/build.gradle.kts` and are raised together. Every version that reaches a tester gets a test protocol in `docs/TEST-<version>.md` recording the environment, what was checked and what is still open.

## Publishing

```sh
gh release create v0.5.0 app/build/outputs/apk/release/app-release.apk \
  --repo Schimmilab/accessible-reader-android \
  --title "..." --notes-file notes.md
```

Only push `main`. Never `git push --all`: the private development history lives on a local branch and must stay there, see [COLLABORATION.md](COLLABORATION.md).

Release notes should carry, in this order:

1. What changed, in plain language.
2. Whether it can be installed over the previous version. A change of signing key means it cannot, and that has to be stated prominently, because users lose their library when they uninstall.
3. The SHA-256 of the APK, so the download can be checked.
4. The known limits, honestly. Scanned PDFs not working is the first thing a new user will run into.

## Minification

`isMinifyEnabled` is deliberately off. Media3 and PDFBox use reflection, and shrinking them without tested keep rules risks breaking playback in a build that end users cannot debug. Turning it on needs a device test pass of its own, not just a green build.
