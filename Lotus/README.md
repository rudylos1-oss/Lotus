# Lotus

Android app for a mutually agreed, private connection between Lotus and Thorin.

## What is included
- End-to-end encrypted paired messaging using the existing Lotus pairing model.
- Photos, videos, gallery attachments, voice notes, emoji, a starter sticker action and GIF files from the gallery.
- A visible location request: only Thorin can tap **Deel mijn locatie**. There is no background tracking.
- A visible camera or video action: the device owner must tap the option themselves. The camera cannot be opened remotely.
- Received and captured media are copied to the app-specific `Pictures/Lotus` directory with a `.nomedia` marker, so they do not appear in the regular gallery by default.
- Message deletion on both paired app instances after an explicit confirmation.
- Check-in and assignment notifications use a high-priority notification with sound/vibration; ordinary messages notify normally.
- Shared task synchronisation, so both devices can edit and exchange the current task set.
- A more sensitive shake-to-roll trigger.

## Privacy boundaries
Location sharing, camera/video capture, voice recording and media selection always require an action on the device concerned. The app has no hidden camera activation, background location tracking or silent remote media capture.

## Build
Open the repository in Android Studio (JDK 17+ and Android SDK API 35), allow Gradle to sync, then build the `app` module. Create a fresh release signing key in Android Studio. Do not reuse the certificate or private key that was shared outside the repository.

## Current service dependency
The original HTML app uses `ntfy.sh` as its encrypted relay default. For a production deployment, configure a private ntfy server or replace the relay with your own authenticated backend before publishing.
