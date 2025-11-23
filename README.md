# Java Chat Application

---

A real-time chat application built with Java Swing and socket programming, featuring text messaging, media sharing, voice notes, and group voice calls with a WhatsApp-inspired interface.

![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=java) ![Socket Programming](https://img.shields.io/badge/Socket-Programming-blue) ![Java Swing](https://img.shields.io/badge/Java%20Swing-GUI-green) ![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- **Real-time Messaging**: Instant text message delivery with WhatsApp-style chat bubbles
- **Media Sharing**: Send and receive images with automatic compression
- **Voice Notes**: Hold-to-record voice messages with playback controls
- **Voice Calls**: One-to-one voice calling with call signaling and management
- **Group Voice Calls**: Multi-user voice conferences connecting all participants
- **Modern UI**: Elegant red-themed interface inspired by WhatsApp
- **System Notifications**: Live call status updates and connection notifications
- **Call Ringtone**: Digital phone ringtone for incoming calls

## Architecture

### Client

- **GUI Framework**: Java Swing with custom-styled components
- **Networking**: Socket-based TCP communication
- **Audio Processing**: Java Sound API for recording, playback, and streaming
- **Image Handling**: Automatic image compression and scaling
- **Threading**: Asynchronous message handling and audio streaming

### Server

- **Multi-threading**: Concurrent handling of multiple client connections
- **Message Routing**: Smart unicast and multicast message delivery
- **Call Management**: Call signaling (request, accept, decline, end)
- **State Management**: Thread-safe client registry using `ConcurrentHashMap`
- **System Messages**: Server-side notifications for call events

## Prerequisites

- **Java Development Kit (JDK)**: Version 8 or higher
- **Operating System**: Windows, macOS, or Linux
- **Microphone**: Required for voice features
- **IDE** (optional): IntelliJ IDEA, Eclipse, or any Java IDE

## Installation

1. **Clone the repository**:

   ```bash
   git clone https://github.com/mohamed-ramadan-me/Java-Chat-App.git
   cd "Java Chat App"
   ```

2. **Compile the server**:

   ```bash
   cd server/src
   javac Server.java
   ```

3. **Compile the client**:

   ```bash
   cd Client/src
   javac Client.java
   ```

## Usage

### Starting the Server

1. Navigate to the server directory:

   ```bash
   cd server/src
   ```

2. Run the server:

   ```bash
   java Server
   ```

   The server will start listening on **port 8889** and display connection logs.

### Starting the Client

1. Open a new terminal and navigate to the client directory:

   ```bash
   cd Client/src
   ```

2. Run the client:

   ```bash
   java Client
   ```

3. **Testing Multi-user Functionality**: Launch multiple client instances to simulate a multi-user chat environment.

### Using Voice Features

- **Voice Notes**: Press and hold the 🎙️ Record button, then release to send
- **Voice Calls**: Click the 📞 Call button and enter the target user ID
- **Group Calls**: Click the 📢 Group Call button to call all connected users
- **End Call**: Click the 📵 End button during an active call

## Protocol Specification

The application uses a custom binary protocol over TCP for efficient message transmission.

### Message Format

```
[Type (1 byte)] [Target/Sender ID (4 bytes)] [Length (4 bytes)] [Body (variable)]
```

### Message Types

| Type | Description         | Usage                                |
|------|---------------------|--------------------------------------|
| `1`  | Text message        | Standard chat messages               |
| `2`  | Image message       | Image file transmission              |
| `3`  | Voice note          | Recorded audio messages              |
| `4`  | Voice stream chunk  | Real-time voice call audio           |
| `6`  | Call request        | Initiate one-to-one voice call       |
| `7`  | Call accept         | Accept incoming voice call           |
| `8`  | Call decline        | Decline incoming voice call          |
| `9`  | Call end            | Terminate active voice call          |
| `10` | Group call request  | Initiate group voice call            |

### Routing Logic

- **Target ID = 0**: Broadcast to all connected clients
- **Target ID > 0**: Direct message to specific client

## Configuration

### Default Settings

- **Server IP**: `127.0.0.1` (localhost)
- **Server Port**: `8889`
- **Audio Format**: 16kHz, 16-bit, Mono
- **Ringtone**: Digital phone ring (800Hz + 1000Hz)

### Customization

To connect to a remote server, modify the `SERVER_IP` constant in `Client.java`:

```java
private static final String SERVER_IP = "your.server.ip";
```

To change the server port, update both `Server.java` and `Client.java`:

```java
private static final int PORT = 8889;        // Server.java
private static final int SERVER_PORT = 8889; // Client.java
```

## Project Structure

```
Java Chat App/
├── Client/
│   ├── .idea/                    # IntelliJ IDEA project files
│   ├── src/
│   │   ├── Client.java          # Main client application (692 lines)
│   │   └── *.class              # Compiled class files
│   ├── out/                      # Build output directory
│   └── Client.iml                # IntelliJ module file
├── server/
│   ├── .idea/                    # IntelliJ IDEA project files
│   ├── src/
│   │   ├── Server.java          # Main server application (180 lines)
│   │   └── *.class              # Compiled class files
│   ├── out/                      # Build output directory
│   └── server.iml                # IntelliJ module file
├── .gitignore                    # Consolidated gitignore (IDE & build files)
├── kill_java.txt                 # Windows utility script
└── README.md                     # This file
```

## Utilities

### kill_java.txt

A Windows command utility to terminate all running Java processes:

```batch
taskkill /F /IM java.exe
```

**Usage**: Copy the command and run it in Command Prompt or PowerShell to stop all client and server instances.

## Troubleshooting

### Port Already in Use

**Problem**: `java.net.BindException: Address already in use`

**Solution**:

- Kill existing Java processes using `taskkill /F /IM java.exe` (Windows)
- Or modify the `PORT` constant in both `Server.java` and `Client.java`

### Connection Refused

**Problem**: Client cannot connect to server

**Solution**:

- Ensure the server is running before starting the client
- Verify the `SERVER_IP` and `SERVER_PORT` match in both files
- Check firewall settings allow connections on port 8889

### Audio Issues

**Problem**: Voice features not working

**Solutions**:

- **Microphone Access**: Verify your system has a working microphone
- **Permissions**: Check audio permissions in your operating system settings
- **Audio Line Issues**: Try restarting the application if audio becomes garbled

### Call Connection Issues

**Problem**: Calls not connecting between clients

**Solution**:

- Ensure both clients are connected to the same server
- Use the correct User ID (displayed in system messages)
- Check that no audio line is already in use

## Development

### Key Classes

#### Client.java

- `Client`: Main client class extending `JFrame`
- Audio handling: `startRecording()`, `stopRecording()`, `streamVoice()`
- Call management: `initiateCall()`, `initiateGroupCall()`, `handleIncomingCall()`
- UI components: `addMessageBubble()`, `addImageBubble()`, `addAudioBubble()`

#### Server.java

- `Server`: Main server class with static methods
- `ClientHandler`: Inner class implementing `Runnable` for each client
- Message routing: `broadcast()`, `sendTo()`, `broadcastSystemMessage()`

### UI Color Scheme

```java
APP_RED = #DC3545           // Primary buttons and sent messages
APP_DARK_RED = #8B0000      // Header background
APP_LIGHT_RED = #FFDAE0     // Accents
APP_BUBBLE_SENT = #DC3545   // Sent message bubbles
APP_BUBBLE_RECEIVED = #FFFFFF // Received message bubbles
```

## License

This project is open-source and available for educational purposes.

## Author

Built as a demonstration of Java networking, GUI programming, and real-time audio streaming capabilities.
