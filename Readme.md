# Link To Linux 

## Development Notice
This project is currently in active development. It is not yet ready for production use, and may contain bugs or incomplete features. If you encounter any issues, please report them on the GitHub repository. Your feedback is invaluable in helping us improve the application. Thank you for your understanding and support!

## Overview

[Link To Linux](https://github.com/HyperAfnan/link-to-linux) is a multi-platform application that seemlessly bridges your Android device with your Linux desktop. By creating a dedicated Wi-Fi Direct network, it bypasses your home router, entirely to provide a secure, ultra-fast and completely offline ecosystem

## Features

*   **Router-less Connection:** Connect directly via Wi-Fi Direct for zero-latency, high-bandwidth communication.
*   **High-Speed File Transfer:** Stream massive files and directories instantly from Android to Linux.
*   **Shared Clipboard:** Copy text on your phone and paste it directly into your Linux terminal (supports X11 and Wayland).
*   **Notification Syncing:** Receive Android notifications on your Linux desktop, keeping you informed without picking up your phone.
*   **Multimedia Remote Control:** Use your phone to view and control Linux desktop media players

## Requirements

### Linux Desktop:
- `wpa_supplicant` 
- `xclip` (for X11 clipboard support) and 'wl-clipboard' (for Wayland clipboard support)
- `notify-send` (for Linux notifications)
- `playerctl` (for multimedia control)

### Android Device:
- Android 10.0 or higher
- Wi-Fi Direct support

## Installation

### Linux Client

1. Clone the repository:
   ```bash
   git clone https://github.com/HyperAfnan/link-to-linux.git
   cd link-to-linux/linux
   ```
2. Install frontend dependencies:
   ```bash
   npm install
   ```
3. Build the application:
   ```bash
   npm run build
   ```
4. Run the application:
   ```bash
   npm start
   ```

### Android App
1. Clone the repository:
   ```bash
   git clone https://github.com/HyperAfnan/link-to-linux.git
   cd link-to-linux/android
   ```
2. Open the project in Android Studio.
3. Build and run the app on your Android device.

## License 
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
