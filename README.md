# Twiniverse: Portable 3D Reconstruction System

**Twiniverse** is a comprehensive 3D reconstruction solution that bridges mobile data acquisition with cloud-based neural rendering. It allows users to turn 2D images or videos into high-fidelity 3D GLB models within seconds.

## 🌟 Key Features

*   **Multi-Modal Capture**: Supports image sequences and video scanning via CameraX.
*   **Instant Reconstruction**: Powered by the **VGGT** algorithm, achieving 10s-level inference speed.
*   **Visual Management**: Built-in 3D viewer based on **Babylon.js** for seamless interaction (zoom, pan, rotate).
*   **Cloud Synchronization**: Secure TCP-based communication protocol for data transmission and model retrieval.
*   **User System**: VIP privilege management and secure file isolation.

## 🛠 Tech Stack

*   **Client**: Based on Android,with Java, CameraX, WebView (Babylon.js), Retrofit-like socket communication.
*   **Server**: Based on python,with PyTorch (VGGT), SQLAlchemy, Socket.
*   **Output Format**: GLB (glTF Binary) for standardized 3D assets.

## 📂 Project Structure

```text
-[Twiniverse]
  -[Client-Twiniverse] (Android Client Source Code)
  -[Server-VGGT] (Python Backend & Algorithms)
  -[README.md]
  -[LICENSE]
```

## 🚀 Getting Started

Prerequisites
- Android Studio Koala or newer.
- Python 3.8+ with PyTorch and CUDA support (for server).

Installation
1. Clone the repository.
2. Open the Client-Twiniverse directory in Android Studio.
3. Configure CommunicationManager.java with your server IP.
4. Build and run on an Android device (Min SDK 24).
5. Configure the python environment and download the model.pt files by referring to the VGGT-readme.
6. Run Manager.py to start the server,or Monitor.py to oversee system resources.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
