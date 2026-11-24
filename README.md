# ScreenMentor — Android source project

ScreenMentor is an Android prototype for a voice-first visual assistant. It captures the screen only after the user approves Android's screen-sharing dialog, streams screen frames and microphone audio to Gemini Live, and plays the assistant's spoken response.

## Included modes

- **Assist:** one practical next action at a time for the app currently on screen.
- **Learn:** step-by-step explanations.
- **Chess coach:** visual chessboard discussion and move ideas; the assistant is instructed to ask when a square/piece is unclear.
- **Chess training:** the user chooses a move first, then receives feedback, hints, and explanations.

The chess modes in this source do **not** include Stockfish or another verified chess engine. A vision model can misread a piece or square, so do not treat its chess evaluation as engine-verified.

## Requirements

- Android Studio (recent stable version)
- Android SDK 35
- JDK 17
- A real Android phone (Android 8.0 / API 26 or later)
- A Gemini API key with access to the Gemini Live API and a supported Live model

## Configure the API

1. Copy `local.properties.example` to `local.properties` in the project root.
2. Replace `YOUR_KEY_HERE` with your Gemini API key.
3. `LIVE_MODEL` defaults to `gemini-3.8-live`. If that model is unavailable to your API project, set `LIVE_MODEL` to another currently supported Gemini Live model available in Google AI Studio.
4. Sync the project in Android Studio.

**Security warning:** this prototype compiles the API key into the APK. Anyone who receives the APK may be able to extract it. This is for personal testing only. Before distributing the app, replace the embedded key with a backend that issues short-lived Gemini Live ephemeral tokens.

## Build an APK

1. Open the `ScreenMentor` folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Android Studio will show the APK location when the build succeeds. The usual debug output is `app/build/outputs/apk/debug/app-debug.apk`.
5. Install it on your phone, open it, choose a mode, tap **Start screen sharing**, and approve both microphone and Android screen-capture permissions.

For a release APK, use **Build → Generate Signed Bundle / APK** and configure your own signing key.

## Important limitations

- The app sends a compressed screen image when the display changes, with a periodic refresh. It is not a full-frame-rate video recorder.
- Live API model names, availability, quotas, and billing can change. Check the current Gemini Live documentation and Google AI Studio before building.
- Network access is required. Voice/image requests may consume quota or incur charges depending on the Google AI plan.
- The app cannot tap or control other apps; it only gives spoken guidance.
- Test on your own device. Android permission and foreground-service behavior can differ by Android version and manufacturer.
