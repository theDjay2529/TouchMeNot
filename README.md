<img src="https://64.media.tumblr.com/a5e3d2051b37b68371ca14289d48ad35/d8f46a74f8efd112-6c/s1280x1920/0d95a7f1dc374a391a97cba3cb9abac422a3bae0.pnj" width="100%" alt="TouchMeNot Banner">  

---

# TouchMeNot  

**TouchMeNot** is a specialized LSPosed/Xposed module designed to close the security gaps Google left behind in Android. It goes beyond simple blocking by using intelligent analysis to secure your device without breaking usability.
> **The Problem:** Standard Android allows anyone to toggle Airplane Mode or access the Power Menu from a locked device.
> 
> **The Solution:** TouchMeNot blocks unauthorized Quick Settings and Power Menu access while maintaining full interaction with lock screen shortcuts and media controls.

---
  
<p align="center">
  <a href="https://github.com/theDjay2529/TouchMeNot/releases"><img src="https://img.shields.io/github/downloads/theDjay2529/TouchMeNot/total.svg?style=for-the-badge" alt="Downloads"></a>
  &nbsp;&nbsp;
  <a href="https://github.com/theDjay2529/TouchMeNot/issues/new?template=bug_report.md"><img src="https://img.shields.io/badge/bug-report-red?style=for-the-badge" alt="Bug Report"></a>
  &nbsp;&nbsp;
  <a href="https://github.com/theDjay2529/TouchMeNot/issues/new?template=feature_request.md"><img src="https://img.shields.io/badge/feature-request-blue?style=for-the-badge" alt="Feature Request"></a>
  &nbsp;&nbsp;
  <a href="https://t.me/+uJDDVqXDoMViMTA1"><img src="https://img.shields.io/badge/Join-telegram-26A5E4?style=for-the-badge" alt="Community"></a>
</p>  

---

## Navigation

* [Features](#key-features)
* [Screenshots](#screenshots)
* [Installation Guide](#installation-guide)
* [Requirements](#requirements)
* [Privacy](#privacy)
* [Contributing & Feedback](#contributing--feedback)

---

## Features
- **Block Power Menu**:
> Disables the Global Actions menu and the Power + Vol-Up combo while the device is locked. Includes haptic and toast feedback to notify you of a blocked attempt.
- **Block Quick Settings tiles**:
> Blocks sensitive tiles (Internet, Airplane Mode, Bluetooth, Hotspot) and the footer power menu.
- **Zero-Hour Protection**:
> TouchMeNot is effective immediately after a restart.
- **Material You Design**:
> The app offers a Simple "Click-to-Toggle" functionality making configuration effortless. Enjoy a sleek, modern UI built with Jetpack Compose. The app features Adaptive Colors that sync perfectly with your system's wallpaper and theme.
- **Privacy First**:
> The app is completely safe. It **does not require Internet permission**, meaning no data ever leaves your device. For full details, see our [Privacy Section](#privacy).
- **Descriptive Logging**:
> Clean and organized logs are saved locally to help you troubleshoot issues.  
    **Path:** `/sdcard/Download/touchmenot_recorder.log`

---

## Screenshots
<p align="center">
  <img src="https://64.media.tumblr.com/8ef1444fc40b29e19a9ad979cfb9aa47/d8f46a74f8efd112-4f/s500x750/43a64c92f16eaf4df4520349f9ac62858252ca36.pnj" width="24%" alt="Screenshot 1">
  <img src="https://64.media.tumblr.com/c6d016e5f1896c76e29d4177e958b9be/d8f46a74f8efd112-0e/s500x750/be95c29729c40c8b479ee225ed2d74c06b8cdfd7.pnj" width="24%" alt="Screenshot 2">
  <img src="https://64.media.tumblr.com/1828d7d44c9d354bf177fb4ba1334fff/d8f46a74f8efd112-d5/s500x750/d8c74aa88841f6ef381e7908fe4bdac2405f0526.pnj" width="24%" alt="Screenshot 3">
  <img src="https://64.media.tumblr.com/ca5626fef235110aa388157d13fb56bc/d8f46a74f8efd112-2c/s1280x1920/0ad8d79216258cb5a6a63033ea91b35b1447439c.pnj" width="24%" alt="Screenshot 4">
</p>

---

## Installation Guide

Follow the steps below to correctly install and activate the module on your device.

1. **Download the Latest Release:** Grab the most recent APK from our [Releases Page](https://github.com/theDjay2529/TouchMeNot/releases).
2. **Install the App:** Sideload the downloaded APK onto your device.
3. **Configure LSPosed:** Open the LSPosed Manager and enable the TouchMeNot module. Ensure the following scopes are selected:
    * **System Framework**
    * **System UI**
      > (in case you cant find it in Lsposed: Open Lsposed > Click on our Module > Top-right 3 dots > Hide > UNCHECK System apps)
4. **Finalize:** Restart your phone to apply the hooks, then open the TouchMeNot app to toggle your desired protection blocks.

---

## Requirements

TouchMeNot is designed specifically for Google Pixel and LineageOS Devices and require a rooted environment with the LSPosed framework installed.
Although it was designed with devices running PixelOS and LineageOS in mind, you can still try it out on other devices.

### System Specifications
* **Target Devices:** Specifically intended for the Pixel Devices and Custom ROMs such as LineageOS
* **Tested Environment:** Extensively tested on a Pixel 7 Pro running multiple versions of the latest Android releases, and the Latest LineageOS (23).
* **Android Version:** Minimum SDK 26; Target/Compile SDK 36.
* **Framework Requirements:** * LSPosed/Xposed module enabled.
    * *Required Scopes:* > - System Framework
    > - System UI.

**Note**: While the module is optimized for Pixels and Custom ROMs such as LineageOS, it might function on other Android distributions too. However, these environments have not been officially tested. If you choose to install this on a non-Pixel device, your feedback is invaluable. Please report your findings via the Feature Request button or join our Telegram community to share your experience. Your contributions help expand the project's compatibility.

---

## Privacy

TouchMeNot is built with a "Security First" philosophy. Because this module handles lock screen behavior, your trust is the project's highest priority.

* **Free and Open Source (FOSS):**
  >The entire codebase is transparent and available for public review. Anyone can audit the logic to verify that it does exactly what it claims—and nothing more.
* **No Internet Access:**
  >This module does not request or possess the Android Internet permission. It is technically impossible for the app to communicate with a remote server, transmit telemetry, or leak your data over the network.
* **Zero Data Collection:**
  >There are no trackers, no analytics, and no remote calls. Your settings are stored exclusively on your device within protected storage.
* **Local Logging Only:**
  >Any debugging information is written to a local file (`/sdcard/Download/touchmenot_recorder.log`) that never leaves your device unless you choose to manually share it for support.
* **Minimal Privilege Usage:**
  >Root and Xposed privileges are used strictly to hook the System UI and Framework for blocking unauthorized actions.

<p align="center"><strong>We Respect your Privacy. Your data is yours alone.</strong></p>

---

## Contributing & Feedback

We believe in community-driven security. Whether you are a developer looking to optimize code or a user with a great idea, your input is what makes TouchMeNot better for everyone.

* **Pull Requests:** Contributions are highly encouraged! Whether it's a bug fix, performance optimization, a new protection feature, or a UI/UX polish, feel free to submit a PR or a [Feature Request template](https://github.com/theDjay2529/TouchMeNot/issues/new?template=feature_request.md).
* **Issue Tracking:** If you encounter a bug or have a suggestion, please use our provided [Bug Report template](https://github.com/theDjay2529/TouchMeNot/issues/new?template=bug_report.md) to help us address it quickly.
* **Join the Conversation:** For real-time help, feedback, or to chat with other users, join our **[Telegram Community](https://t.me/+uJDDVqXDoMViMTA1)**. We are always happy to help and hear your thoughts.

---

## Star History

<a href="https://www.star-history.com/#theDjay2529/TouchMeNot&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=theDjay2529/TouchMeNot&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=theDjay2529/TouchMeNot&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=theDjay2529/TouchMeNot&type=date&legend=top-left" />
 </picture>
</a>

---
