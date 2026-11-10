# android-core

Shared Android code for Playtivity, Scientific Calculator, and CarLyrics.

Today the shared module is `:update`: the whole self-update flow, from GitHub release
selection through the system package installer. The apps keep their own UI, copy, channels,
and domain code.

## Tag policy

All three apps publish nightlies as:

```
nightly-YYYYMMDD-<BUILD>
```

`<BUILD>` is Unix epoch seconds at build time and is the APK's Android `versionCode`.
Stable releases, where an app has them, are `v<major>.<minor>.<patch>`.

The parser also reads the old formats so installed builds can move across without a manual
update:

| App | Legacy tag |
|---|---|
| Playtivity | `nightly-YYYYMMDD-HHMMSS` |
| Scientific Calculator | `nightly-YYYYMMDD-<counter>` |
| CarLyrics | `nightly_YYYYMMDD_<unix seconds>` |

Old tags without a build number (`nightly_YYYYMMDD`, `nightly_YYYYMMDD_<small counter>`)
never update.

## Use

Add the module, then construct one client in the app's dependency holder:

```kotlin
val updates by lazy {
    UpdateClient(
        context = this,
        config = UpdateConfig(
            repo = "mliem2k/your-app",
            installReceiver = InstallResultReceiver::class.java,
            channels = listOf(Channel.NIGHTLY),
            updatesEnabled = !BuildConfig.DEBUG,
        ),
    )
}
```

Declare a three-line receiver:

```kotlin
class InstallResultReceiver : com.mliem.android.update.InstallResultReceiver() {
    override fun updateClient(context: Context) =
        (context.applicationContext as YourApp).updates
}
```

and point the manifest at it:

```xml
<receiver android:name=".InstallResultReceiver" android:exported="false" />
```

The app also needs `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions.

Observe `client.state` for progress, call `client.check()` for a background check,
`client.checkNow()` when the caller wants the result, and `client.downloadAndInstall()`
or `client.install(info)` to install.

## What the shared code enforces

1. Only HTTPS release assets.
2. The APK must be for the installed package and a higher `versionCode`.
3. The SHA-256 GitHub reports must match the downloaded file; releases without a digest
   are refused unless the app explicitly opts out.
4. The size GitHub reports must match the download.
5. Only one install runs at a time, and a check inside the configured interval is skipped
   unless forced.
6. The installer callback is an explicit, package-scoped receiver.

## Develop

This repository is normally checked out beside the app repository. The apps include it as a
Gradle composite build and substitute `com.mliem.android:update` with `:update`.
Set `ANDROID_CORE_DIR` to point at another checkout.

```sh
./gradlew :update:test
```

Tag a core commit; the apps build against the checkout their `ANDROID_CORE_DIR` names
(by default, this repository beside them).
