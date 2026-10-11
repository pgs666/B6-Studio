# Contributing

B6 Studio is a device-focused Android application. Keep changes limited to B6/B6X behavior supported by protocol evidence or a reproducible device test.

Before opening a pull request:

```sh
./gradlew :app:assembleDebug :app:lintDebug
./tests/run.sh
```

Do not commit APKs, `local.properties`, Gradle build directories, captured logs containing device identifiers, or proprietary APKs/DEX files. Describe the tested Android version, model, firmware, and commands sent to hardware.
