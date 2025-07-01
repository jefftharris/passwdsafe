# PasswdSafe on Android

Port of the [Password Safe](https://pwsafe.org) application to Android

## Chromebook Support

PasswdSafe and PasswdSafe Sync are available from the Google Play store on
Chromebooks.  Some features such as YubiKeys and the alternate keyboard may not
be available.

## File Support

Password files can be stored locally or in a cloud service.  The following
tables show the support for various locations of files on phones/tables or
Chromebooks and how they are usable with PasswdSafe.  Support is as listed on
Jan 21, 2024 with the latest version of PasswdSafe.

### Phones and Tablets

| Source           | PasswdSafe Files Open | From Provider App | PasswdSafe Sync |
|------------------|-----------------------|-------------------|-----------------|
| Internal Files   | read-write            | N/A               | N/A             |
| Box              | read-write            | read-write        | read-write      |
| Dropbox          | read-only (1)         | read-write        | read-write      |
| Google Drive     | read-write            | read-write        | read-write (2)  |
| OneDrive         | read-only (3)         | read-only (3)     | read-write      |
| Google Files app | N/A                   | read-only (4)     | N/A             |
| Proton Drive     | read-only (1)         | N/A               | N/A             |

1. App file provider doesn't support writes
2. Special handling for [Drive sync
   files](https://sourceforge.net/p/passwdsafe/wiki/SyncGoogleDrive/)
3. OneDrive provider throws 'Not a whole file' error
4. Writes fail with a permission denial error from the Google Files app

### Chromebook

| Source                                                  | Support    |
|---------------------------------------------------------|------------|
| PasswdSafe chooser - Local files                        | read-write |
| PasswdSafe chooser - USB files                          | read-write |
| PasswdSafe chooser - Google Drive files                 | read-only  |
| PasswdSafe chooser - OneDrive files (from OneDrive app) | read-write |
| Google Drive App                                        | read-write |
| OneDrive App                                            | read-only  |

## Cloud Service Sync

Start by uploading .psafe3 files to your Box, Dropbox, Google Drive, or OneDrive
account using the service’s native app or website. PasswdSafe Sync should then
sync the files to your phone or tablet.

* Dropbox – Individual Dropbox files can be chosen to synchronize.
* Box – Box files should be placed in the top folder or any folder tagged with
  ‘passwdsafe’ so it shows in a search result.
* Google Drive – Google Drive files can be [used or
  synced](https://sourceforge.net/p/passwdsafe/wiki/SyncGoogleDrive/).
* OneDrive - Individual OneDrive files can be chosen to synchronize

## PasswdSafe Keyboard

The PasswdSafe keyboard can be used to enter fields directly into other apps
without using the system clipboard.  The process of enabling the keyboard on
Android 8 is given below.  Other versions are similar.

1. Open the device's settings
2. Locate the Languages and Input settings
3. Under Keyboard and Inputs, select Virtual keyboard
4. Click Manage keyboards
5. Enable the PasswdSafe option

To switch between the default Google keyboard and the PasswdSafe keyboard, look
for a keyboard icon in the bottom button bar, the top notification area, or on
the current keyboard.  Clicking the icon should allow you to choose the
PasswdSafe keyboard.

## YubiKey Support

PasswdSafe is compatible with USB and NFC based YubiKeys.  NFC based YubiKeys
require NFC communication enabled in the device.  USB based YubiKeys should be
usable in recent version of Android on phones and tables.  Chromebooks do not
allow access to USB devices, so the keys are not supported.

Prior to using a YubiKey with PasswdSafe, the key needs to be programmed for
Password Safe, and a password needs to be set with the YubiKey by the PC
program.  Help is available in the PC program for the setup.

To use a YubiKey, follow these steps:

1. If using a NFC-enabled YubiKey (e.g. a NEO), enable NFC support in the device
   settings
2. Enter the user password to the file
3. Choose which slot on the YubiKey has the challenge-response key. Usually slot
   2 is used for Password Safe.
4. Check the YubiKey option
5. Click OK
6. Within 30 seconds, activate the YubiKey
    * For NFC, press the YubiKey to the back of the phone or tablet.  There will
      be sounds as the key is being read.  If the device doesn't respond with a
      rising tone, try moving the key around.  A bit of trial-and-error is
      needed to find the correct location.  The button on the YubiKey itself
      does not need to be pressed.
    * For USB, insert the YubiKey and allow PasswdSafe access to the key. The
      key can be inserted at any time while opening the file.  If set in the
      configuration of the YubiKey, the button may need to be pressed after
      clicking OK.  The indicator on the button should blink if a press is
      required.
7. Once the key is found and activated, it will be used to authenticate with
   your password.  The file will open automatically.

## Saving Passwords

File passwords can be saved on devices with a biometrics scanner and Android 6.0
or higher.  The passwords are encrypted using AES-256 keys that are protected by
a biometrics scan.

* To save a password, first ensure a biometrics scan is registered with the
  device in the security settings.  Then, check the save option, enter the
  password, and click OK. After the password is verified, scan the biometric
  credential when prompted. The password will be encrypted and saved, and the
  file will open.
* To use a saved password, scan the biometric credential instead of entering a
  password.  If the scan is successful, the password is decrypted and used to
  open the file.  If a YubiKey is used, the key will be read after the biometric
  credential is scanned.
* To remove a saved password, uncheck the save option and open the file. The
  saved password will be erased from the device.  All saved passwords can be
  removed from the password preferences.

# Links

* [PasswdSafe on Google Play](https://play.google.com/store/apps/details?id=com.jefftharris.passwdsafe)
* [PasswdSafe Sync on Google Play](https://play.google.com/store/apps/details?id=com.jefftharris.passwdsafe.sync)
* [PasswdSafe on Amazon](http://www.amazon.com/gp/product/B008K4WNRG)
* [Password Safe for the PC](http://pwsafe.org)
* [Privacy Policy](https://sourceforge.net/p/passwdsafe/wiki/PrivacyPolicy/)
