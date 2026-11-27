# Security Policy

## Supported Versions

Only the latest release on `main` receives security updates.

| Version | Supported |
| ------- | --------- |
| 1.1.x   | Yes       |
| < 1.1.0 | No        |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Instead, report vulnerabilities privately through GitHub Security Advisories:

- https://github.com/XeraBytes/sked-android/security/advisories/new

Include as much detail as possible to reproduce the issue:

- Affected version and Android API level
- Steps to reproduce or a minimal proof of concept
- Potential impact (e.g., credential exposure, arbitrary file access, update verification bypass)

You can expect an initial response within 7 days. Once confirmed and patched, credit will be given in the release notes unless you prefer to remain anonymous.

## Security & Privacy Practices

Sked is designed as an offline-first Android application:

- **Local-only schedule data:** Classes, unscheduled pool items, and saved timetable reference images stay on your device.
- **Backup exclusions:** App preferences containing user-configured API keys or GitHub tokens (`schedule_maker_prefs.xml` and `app_prefs.xml`) are excluded from Android cloud backups and device-to-device transfers.
- **API keys & tokens:** Gemini API keys and optional GitHub update tokens are never hardcoded in the repository. When configured, they are sent over HTTPS in request headers (`x-goog-api-key` for Gemini, `Authorization: Bearer` for GitHub API) rather than URL query strings.
- **LMS credentials:** Optional CCC e-GURO sign-in sends credentials only over HTTPS to `lms.ccc.edu.ph` for that session, never persists passwords to disk, keeps session cookies strictly in memory, and restricts crawled schedule URLs to the same HTTPS host.
- **App updates:** In-app updates download APKs over HTTPS from GitHub Releases, validate that the archive contains an Android package manifest, and delegate installation and signature verification to Android's system package installer.
