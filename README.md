# Himalayan Vault - Complete Setup and Usage Guide

A secure desktop password manager with Chrome extension support. Stores encrypted credentials locally with a JavaFX user interface and browser integration.

---

# Table of Contents

1. Quick Start
2. Project Structure
3. Prerequisites
4. Installation and Setup
5. Running the Application
6. Chrome Extension Setup
7. Features
8. API Endpoints
9. Security
10. Architecture Overview
11. Deployment and Build
12. Extension Features Guide
13. Quick Reference Commands
14. Troubleshooting
15. Configuration
16. Technology Stack

---

# Quick Start

## Desktop Application

```bash
cd /home/prasish/Project/HimalayanVault
mvn clean javafx:run
```

## Chrome Extension

1. Open `chrome://extensions`
2. Enable **Developer mode**
3. Click **Load unpacked**
4. Select:

```text
/home/prasish/Project/HimalayanVault/chrome-extension/
```

5. Click the extension icon.
6. Log in using your vault credentials.

---

# Project Structure

```text
HimalayanVault/
│
├── README.md
├── pom.xml
├── RUN_APP.sh
│
├── src/
│   ├── main/
│   │   ├── java/com/himalayanvault/
│   │   │   ├── Main.java
│   │   │   ├── api/
│   │   │   ├── auth/
│   │   │   ├── db/
│   │   │   ├── models/
│   │   │   ├── security/
│   │   │   └── ui/
│   │   └── resources/
│   │       ├── css/
│   │       └── fxml/
│   └── test/
│
├── chrome-extension/
│   ├── manifest.json
│   ├── background.js
│   ├── content.js
│   ├── popup.html
│   ├── popup.js
│   ├── styles.css
│   ├── icons/
│   └── README.md
│
└── target/
```

---

# Prerequisites

## System Requirements

* Linux, Windows, or macOS
* Java 21 or later (Java 25 recommended)
* Maven 3.8.9 or later
* Google Chrome (latest version)

## Verify Installation

```bash
java -version
mvn -version
```

---

# Installation and Setup

## Navigate to the Project

```bash
cd /home/prasish/Project/HimalayanVault
```

## Resolve Dependencies

```bash
mvn dependency:resolve
```

## Build

```bash
mvn clean compile
mvn clean package -DskipTests
```

---

# Running the Application

## Option 1 (Recommended)

```bash
mvn clean javafx:run
```

## Option 2

```bash
./RUN_APP.sh
```

## Option 3

```bash
mvn clean package -DskipTests
mvn exec:java
```

## Option 4

```bash
java -jar target/himalayan-vault-1.0.0-fat.jar
```

### Expected Startup

* JavaFX window opens.
* Login or Signup page appears.
* API server starts on `127.0.0.1:8443`.
* SQLite database is initialized.

---

# Chrome Extension Setup

## Installation

1. Open `chrome://extensions`
2. Enable Developer Mode.
3. Click **Load unpacked**.
4. Select the `chrome-extension` folder.
5. Verify the extension appears without errors.

## Login

1. Click the extension icon.
2. Enter your vault username.
3. Enter your master password.
4. Click **Login**.

## Test

Visit:

```
https://practicetestautomation.com/practice-test-login/
```

Log in using test credentials and verify the save prompt appears.

---

# Features

## Desktop Application

### Authentication

* Master password protection
* Biometric authentication
* Recovery codes
* Automatic session timeout

### Credential Management

* Store encrypted credentials
* Search credentials
* Copy username and password
* Edit credentials
* Delete credentials

### Password Generator

* Length: 8–128 characters
* Uppercase
* Lowercase
* Numbers
* Symbols
* Password strength estimation

### User Interface

* JavaFX interface
* Dark and light themes
* Categorized credential management

---

## Chrome Extension

### Automatic Login Detection

Detects successful login attempts.

### Password Saving

Displays a save prompt after successful login.

### Autofill

Suggests stored credentials when login fields are focused.

### Credential Management

* View credentials
* Copy usernames
* Delete credentials
* Add credentials manually

### Password Generator

Generate and copy secure passwords.

### Theme Support

Supports both light and dark mode.

---

# API Endpoints

Base URL

```
http://127.0.0.1:8443
```

| Method | Endpoint           | Authentication | Description          |
| ------ | ------------------ | -------------- | -------------------- |
| GET    | /health            | No             | Server status        |
| POST   | /login             | No             | Login                |
| POST   | /lock              | Yes            | Logout               |
| GET    | /credentials       | Yes            | Retrieve credentials |
| GET    | /credentials?site= | Yes            | Search by website    |
| GET    | /credential/{id}   | Yes            | Retrieve credential  |
| POST   | /save              | Yes            | Save credential      |
| POST   | /update            | Yes            | Update credential    |
| POST   | /delete            | Yes            | Delete credential    |
| POST   | /generate-password | Yes            | Generate password    |

---

# Security

## Encryption

* AES-256-GCM
* PBKDF2-HMAC-SHA256
* Random IV and salt

## Authentication

* JWT session tokens
* Session timeout after 30 minutes
* Secure token validation

## Network

* Localhost only
* SQLite local storage
* No external server communication

## Best Practices

* Use a strong master password.
* Backup the database regularly.
* Keep Java and Chrome updated.
* Lock the vault when not in use.

---

# Architecture Overview

```
Chrome Extension
        │
        │ HTTP
        ▼
JavaFX Desktop Application
        │
        ▼
REST API Server
        │
        ▼
Encryption Layer
        │
        ▼
SQLite Database
```

---

# Deployment and Build

## Build

```bash
mvn clean package -DskipTests
```

Generated files

```text
target/
├── himalayan-vault-1.0.0.jar
├── himalayan-vault-1.0.0-fat.jar
└── classes/
```

---

# Extension Features Guide

## Copy Username

Copies the username to the clipboard.

## Autofill

Automatically fills username and password fields.

## Automatic Password Saving

After a successful login, a prompt appears asking whether the credentials should be saved.

## Password Generator

Creates secure passwords and copies them directly to the clipboard.

## Theme Support

Automatically switches between light and dark themes based on the operating system.

---

# Troubleshooting

## JavaFX Runtime Missing

```bash
mvn clean javafx:run
```

## Port Already in Use

Linux

```bash
lsof -i :8443
kill -9 <PID>
```

Windows

```cmd
netstat -ano | findstr :8443
taskkill /PID <PID> /F
```

---

## Database Locked

```bash
ps aux | grep java
chmod 644 ~/.himalayan-vault/himalayan-vault.db
```

---

## API Server Not Running

```bash
curl http://127.0.0.1:8443/health
```

If unavailable, restart the application.

---

## Extension Cannot Connect

* Verify the desktop application is running.
* Reload the extension.
* Log in again.
* Inspect the extension console for errors.

---

## Database Integrity

```bash
sqlite3 ~/.himalayan-vault/himalayan-vault.db "PRAGMA integrity_check;"
```

---

# Pre-Use Checklist

* Java installed
* Maven installed
* Project compiled
* Backend starts successfully
* API health check succeeds
* Chrome extension installed
* Extension login successful
* Credentials can be saved
* Credentials can be autofilled

---

# Database

Location

```text
~/.himalayan-vault/himalayan-vault.db
```

Backup

```bash
cp -r ~/.himalayan-vault ~/.himalayan-vault-backup-$(date +%s)
```

---

# Configuration

## Server Port

```java
private static final int PORT = 8443;
```

## Session Timeout

```java
private static final long SESSION_TIMEOUT_MS =
30 * 60 * 1000;
```

## Thread Pool

```java
private static final int THREAD_POOL_SIZE = 10;
```

## Extension Timeout

```javascript
const API_TIMEOUT = 5000;
```

## Popup Width

```css
body {
    width: 450px;
}
```

---

# Technology Stack

## Backend

* Java 25
* JavaFX
* SQLite
* Bouncy Castle
* Gson
* Maven
* JNA
* zxcvbn

## Chrome Extension

* JavaScript
* HTML5
* CSS3
* Chrome Extension APIs

## Security

* AES-256-GCM
* PBKDF2-HMAC-SHA256
* JWT
* Secure Random

---

# Version

Project: Himalayan Vault

Version: 1.0.0

Status: Ready for Deployment

Build System: Maven

Architecture: JavaFX Desktop Application with Chrome Extension

---

# Documentation Summary

This document includes:

* Installation Guide
* Build Instructions
* API Documentation
* Extension Setup
* Security Overview
* Architecture
* Troubleshooting
* Configuration
* Technology Stack

It serves as the complete reference for setting up, running, maintaining, and extending the Himalayan Vault project.
