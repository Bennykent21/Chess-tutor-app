# Releasing Chess Tutor

A release is a version tag pushed to GitHub. CI builds the APK, signs it with the release key, and publishes it on the [Releases page](https://github.com/Bennykent21/Chess-tutor-app/releases) with its SHA-256 checksum and the notes from `CHANGELOG.md`.

## One time: the release key

Android installs an update only when it is signed with the same key as the version on the phone. Every release must therefore be signed with **one key, kept forever**. If it is lost, people cannot update and must uninstall (losing their progress) to install a newer version.

1. Make the key (Windows PowerShell, using the Java that comes with Android Studio). Choose a strong password and write it down.

   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore "$env:USERPROFILE\chess-tutor-release.jks" -alias chess-tutor `
     -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Chess Tutor"
   ```

2. Back up `chess-tutor-release.jks` and its password somewhere safe outside this PC (for example a password manager). Never commit it.

3. Add four repository secrets on GitHub (**Settings > Secrets and variables > Actions > New repository secret**):

   | Name | Value |
   |---|---|
   | `RELEASE_KEYSTORE_B64` | the key file in base64: run `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\chess-tutor-release.jks")) \| Set-Clipboard`, then paste |
   | `RELEASE_STORE_PASSWORD` | the password from step 1 |
   | `RELEASE_KEY_ALIAS` | `chess-tutor` |
   | `RELEASE_KEY_PASSWORD` | the same password |

A tag build stops with "No release key" if any of the four is missing, so a release can never go out signed with the test key.

## Each release

1. Add a section to `CHANGELOG.md` headed `## [1.2.3] - YYYY-MM-DD` with what changed, and merge it into `main`.
2. Tag that commit on `main` and push the tag:

   ```bash
   git switch main && git pull
   git tag v1.2.3
   git push origin v1.2.3
   ```

3. Wait for the Android CI run on the tag. Its `release` job publishes `chess-tutor-1.2.3.apk`.

The version name is the tag without the `v`; the version code is `major * 10000 + minor * 100 + patch` (1.2.3 is 10203), so each release installs over the previous one.

## Test builds

Every push to a branch builds test APKs signed with a throwaway test key (`CI_DEBUG_KEYSTORE_B64`, see the workflow). They are for trying changes on a phone, never for publishing. The test "Chess Tutor" release build and the published app share a package name but not a key, so a phone with the test build installed must uninstall it before installing a published release.
