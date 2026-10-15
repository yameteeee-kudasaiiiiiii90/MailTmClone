# Mail.tm Native Android Client (v2.0)

A full-featured, native Android application built with **Kotlin** and **Jetpack Compose** that replicates the official **Mail.tm** disposable email experience using `https://api.mail.tm`.

## Key Features
- **Authentic Mail.tm Dark UI:** Styled with the Tailwind Slate (`#0F172A`, `#1E293B`) and Indigo (`#6366F1`) color scheme, including the signature one-tap copy email pill bar.
- **Random & Custom Email Creation:** Generate a random disposable address instantly or choose your own custom username, domain, and password.
- **Multi-Account Switcher:** Automatically saves up to 10 recent disposable accounts locally so you can switch back to any previous address with a single tap.
- **Persistent Session & Storage Quota Bar:** Remembers your active account across app restarts and displays real-time mailbox storage usage (`/me` endpoint).
- **Search & Unread Filtering:** Search incoming messages by sender, subject, or intro text, filter by Unread/All, and mark all messages as read.
- **Rich Message Viewer (HTML / Plain Text / Raw EML Source):** Switch seamlessly between rendered HTML (`WebView` with external link support), plain text, and raw EML source (`/sources/{id}`).
- **Attachment Downloader:** View message attachments and download them directly to your device via Android's `DownloadManager`.
- **Background Sync & Instant Local Notifications:** Uses `WorkManager` (`CoroutineWorker`) with automatic JWT token renewal to check for new emails and send local notifications even when the app is closed.

## How to Build the APK

### Option 1: Cloud Build via GitHub Actions (No Local Setup Required)
1. Extract this archive and upload all files (including the `.github` folder) to a new **GitHub** repository.
2. Open the **Actions** tab in your GitHub repository.
3. Wait ~2 minutes for the **Build Android APK** workflow to finish, then download **`MailTmClone-Debug-APK`** from the **Artifacts** section.

### Option 2: Local Build via Android Studio
1. Extract this archive and open the project folder in **Android Studio**.
2. Wait for **Gradle Sync** to finish.
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)** from the top menu.
4. Locate your APK at `app/build/outputs/apk/debug/app-debug.apk`.
