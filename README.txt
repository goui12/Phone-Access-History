PHONE ACCESS HISTORY 1.0.0

Install Phone_Access_History.apk on an Android 8.0 or newer phone.
Allow installation from your file/browser app when Android asks, then open
Phone Access History once. Allow notifications. Use Battery settings in the
app to allow unrestricted battery use; some manufacturers also need auto-start
enabled or exclusion from sleeping-app lists.

Opening the app starts its logger if stopped. Its ongoing notification opens
the daily history screen. Select a date or use the previous/next day arrows.
Closing the history screen or swiping it from Recents does not stop logging.
Each screen-off/unlock cycle creates one timestamp from Android USER_PRESENT;
duplicate signals in the same cycle are ignored. Simply turning on the
screen while the phone remains locked does not count. Opening the app itself
does not create an unlock entry. Entries begin after the app is first opened.

The service is event-driven, with no polling, network, or wake locks. History
is in the app's private, credential-encrypted SQLite database. The date and
time zone at the event are retained, so travel does not regroup old days.
Cloud backup is disabled. Uninstalling or clearing storage deletes history.

The service requests restart after process death and starts after reboot.
A Direct Boot receiver attempts to monitor before the first unlock after
reboot; any event received before private storage is ready is temporarily
buffered in device-protected settings, then moved into the private database.

Android does not allow any ordinary app to promise uninterrupted operation.
Force Stop, Android's Active Apps Stop control, and vendor battery management
may stop logging. Reopen the app to resume. Events missed while the service
was stopped cannot be recovered. Notification permission denial does not
prevent the foreground service, but allowing notifications makes its state
visible. Battery settings differ by phone manufacturer.

DEVELOPMENT
Native Android Java, no third-party app libraries, no network permission.
Package: com.cameron.phoneaccess. minSdk 26, compileSdk/targetSdk 36.
Requirements: Java 17+, Python 3, Android SDK platform 36, build-tools 36.0.0.
Run ANDROID_SDK_ROOT=/path/to/sdk bash build.sh from Linux/macOS/WSL.
This uses Google's aapt2, D8, zipalign and apksigner directly; no Gradle needed.
Output: dist/Phone_Access_History.apk.

Keep the .signing directory private and backed up. It contains the personal
release signing key and password needed for updates that preserve installed
history. Do not generate a different key for future versions of this app.
