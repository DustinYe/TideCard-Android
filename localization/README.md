# Translation catalogs

Edit `messages.json`, then run `python scripts/generate-locales.py` from the repository root. The generator uses only Python's standard library. Commit the generated Android resource XML alongside the catalog.

The source keys are canonical Simplified Chinese text, including messages returned by the separate backend and stored by previous app versions. `I18n.tr` resolves individual UI strings; `I18n.message` performs a longest-match single pass over retained canonical backend messages. Do not translate protocol keys, SIM operator data, ISO codes, license bodies or release notes supplied by GitHub.

Android resource defaults are English, `values-zh` provides Simplified Chinese, and `values-b+zh+Hant` provides Traditional Chinese. All languages ship inside the APK and bundle. AppLanguage uses LocaleManager on API 33+ and a local preference/context override on API 30–32. Changes are confined to this app.

Update releases must use a stable `vMAJOR.MINOR.PATCH` tag and exactly one `TideCard-MAJOR.MINOR.PATCH.apk` asset with GitHub's SHA-256 digest. Candidate APKs must have a higher version code and the same current signing certificate set. Signing-key rotation would require a future explicit update to the certificate policy.

Read-only device checks: `adb shell am instrument -w -r -e localizationOnly true app.tidecard.test/app.tidecard.test.SmokeRunner`. An optional `-e candidate` path can point to a local higher-version 2.4.2 fixture, built with `-PtideVersionCode=13 -PtideVersionName=2.4.2`; it is a test fixture and must not be published as a release. A sibling `update-test-wrong-signer.apk` enables a real different-signature rejection test. These tests do not perform carrier writes, grant installation permissions or confirm an installation.
