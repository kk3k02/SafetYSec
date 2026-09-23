# SafetYSec

SafetYSec is an Android safety monitoring application developed in Kotlin using Jetpack Compose.

The application supports two user roles:

- **Protected user** – shares location and can generate emergency alerts.
- **Monitor** – monitors protected users and receives alerts.

The application uses Firebase for authentication and data storage and uses Android device features such as GPS, camera, microphone and motion sensors.

## Requirements

To build and run the project locally, install:

- **Android Studio**
- **JDK 11 or newer**
- **Android SDK 36**
- Android SDK Platform Tools
- An Android emulator or a physical Android device

The project uses the included **Gradle Wrapper**, so Gradle does not need to be installed separately.

Main project versions:

- Kotlin: `1.9.24`
- Android Gradle Plugin: `8.7.2`
- Gradle: `8.13`
- Compile SDK: `36`
- Target SDK: `36`
- Minimum SDK: `24`

## Clone the repository

```bash
git clone https://github.com/kk3k02/SafetYSec.git
cd SafetYSec
```

## Firebase configuration

SafetYSec uses:

- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Firebase App Check

A valid Firebase configuration file is required before the application can be built and used.

Download `google-services.json` from the Firebase project and place it in:

```text
SafetYSec/
└── app/
    └── google-services.json
```

The final path should therefore be:

```text
app/google-services.json
```

Do not commit private Firebase configuration or other credentials to a public repository unless they are intended to be public.

### Firebase App Check when using an emulator

Debug builds use the Firebase **Debug App Check Provider**, allowing the application to communicate with Firebase when running locally.

After starting the application, check Android Studio **Logcat** for the Firebase App Check debug token.

Add this token in:

```text
Firebase Console
→ App Check
→ Apps
→ Manage debug tokens
```

This may be necessary for Firebase requests to work correctly from the emulator.

Release builds use **Play Integrity** instead.

## Build from Android Studio

1. Open Android Studio.
2. Select **Open**.
3. Select the cloned `SafetYSec` directory.
4. Wait for Gradle synchronization to finish.
5. Make sure Android SDK 36 is installed.
6. Add `app/google-services.json`.
7. Select an emulator or connected Android device.
8. Click **Run ▶**.

Android Studio will build and install the application automatically.

## Recommended testing environment

For the most reliable testing experience, we recommend running SafetYSec on a **physical Android device**.

Although most of the application can also be tested using the Android Emulator, a physical device significantly simplifies testing of features that depend on real-world movement, device sensors and actual GPS location changes.

Using a physical device is especially recommended when testing:

- fall detection and motion monitoring,
- entering or leaving a defined safe zone,
- real-time GPS location tracking,
- speed and movement detection,
- emergency recording using the camera and microphone.

A physical Android device provides realistic accelerometer, motion and GPS data, which makes it easier to test scenarios such as physically leaving a safe zone or simulating a fall.

The Android Emulator can still be used for general interface testing, authentication, Firebase communication and basic location simulation.

## Running with an Android emulator

The Android Emulator included with Android Studio can be used for local testing.

### Create an emulator

In Android Studio:

```text
Tools
→ Device Manager
→ Add a new device
```

Select a phone profile, for example:

```text
Pixel 7
```

Then install a compatible Android system image, preferably:

```text
Android API 35 or API 36
```

A **Google APIs** or **Google Play** system image is recommended because the application uses Google Play Services and Firebase.

Start the emulator and run the application from Android Studio.

## Required permissions

On first launch, Android will request permissions used by SafetYSec, including:

- Camera
- Microphone
- Precise / approximate location
- Notifications where required

Grant the permissions to test all application functionality.

Location services should also be enabled on the emulator or physical device.

## Simulating GPS location

SafetYSec uses Android location services for location and movement monitoring.

To simulate a location in Android Emulator:

1. Start the emulator.
2. Open the emulator's **Extended Controls** using the `...` button.
3. Select **Location**.
4. Enter coordinates or choose a location on the map.
5. Click **Set location**.

Routes can also be simulated using the emulator location controls.

This allows basic testing of features related to location changes and safe zones.

However, testing these features on a physical device is recommended because it provides more realistic GPS updates and makes it easier to simulate scenarios such as walking outside a configured safe zone.

## Camera and microphone

SafetYSec uses the camera and microphone for emergency recording functionality.

The Android Emulator can emulate these devices, although behaviour may differ from a physical Android phone.

For complete testing of:

- emergency video recording,
- microphone input,
- motion sensors,
- fall detection,
- real GPS behaviour,

a physical Android device is recommended.

## Running on a physical device

To run SafetYSec on a physical Android phone:

1. Enable **Developer options** on the device.
2. Enable **USB debugging**.
3. Connect the phone to the computer using USB.
4. Accept the USB debugging authorization prompt on the phone.

Verify that the device is detected:

```bash
adb devices
```

The device should appear in the list.

Then select the physical device in Android Studio and click:

```text
Run ▶
```

The device must run Android 7.0 / API 24 or newer.

Testing on a physical device is the recommended approach when verifying sensor- and location-dependent functionality.

## Tests

Run local unit tests with:

### Linux / macOS

```bash
./gradlew test
```

### Windows

```powershell
gradlew.bat test
```

Run Android instrumentation tests on a connected emulator or physical device with:

### Linux / macOS

```bash
./gradlew connectedAndroidTest
```

### Windows

```powershell
gradlew.bat connectedAndroidTest
```

## Troubleshooting

### `google-services.json` is missing

Make sure the Firebase configuration file exists at:

```text
app/google-services.json
```

Then synchronize Gradle again.

### Firebase requests fail on the emulator

Check Android Studio Logcat for a Firebase App Check debug token and register it in Firebase Console.

### Gradle synchronization fails

Make sure:

- Android SDK 36 is installed.
- Android Studio is using a compatible JDK.
- An internet connection is available for downloading Gradle dependencies.

You can also try:

```bash
./gradlew clean
```

and then rebuild the project.

### Location is not updating

Make sure:

- Location permission has been granted.
- GPS/location services are enabled.
- A location has been configured in Android Emulator Extended Controls when using an emulator.

For testing real location changes and safe-zone monitoring, use a physical Android device.

### Fall detection is difficult to test in the emulator

Fall detection depends on motion sensor data.

While emulator sensor controls can be used for basic testing, using a physical Android device is strongly recommended because it provides real accelerometer and movement data.

### Camera or microphone does not work

Check application permissions in:

```text
Settings
→ Apps
→ SafetYSec
→ Permissions
```

For hardware-dependent functionality, use a physical Android device when possible.

## Project structure

```text
SafetYSec/
├── app/                         # Android application module
│   ├── src/main/
│   │   ├── java/               # Kotlin source code
│   │   ├── res/                # Android resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml       # Dependency/version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```
## Notes

The Android Emulator is suitable for basic application testing and development.

However, due to the nature of SafetYSec and its use of location and motion sensors, a **physical Android device is the recommended environment for functional testing**, especially for scenarios involving fall detection, movement monitoring and leaving a configured safe zone.
