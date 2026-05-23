# 🏔️ Himalayan Vault - Complete Setup & Usage Guide

A secure desktop password manager with Chrome extension support. Stores encrypted credentials locally with a beautiful JavaFX UI and browser integration.

---

## 📋 Table of Contents

1. [Quick Start](#quick-start)
2. [Project Structure](#project-structure)
3. [Prerequisites](#prerequisites)
4. [Installation & Setup](#installation--setup)
5. [Running the Application](#running-the-application)
6. [Chrome Extension Setup](#chrome-extension-setup)
7. [Features](#features)
8. [API Endpoints - Complete Reference](#api-endpoints---complete-reference)
9. [Security](#security)
10. [Architecture Overview](#architecture-overview)
11. [Deployment & Build](#deployment--build)
12. [Extension Features Guide](#extension-features-guide)
13. [Quick Reference Commands](#quick-reference-commands)
14. [Troubleshooting](#troubleshooting)
15. [Configuration](#configuration)

---

## 🚀 Quick Start

### Desktop Application (Backend)
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean javafx:run
```

### Chrome Extension
1. Go to `chrome://extensions`
2. Enable Developer mode
3. Click "Load unpacked"
4. Select `/home/prasish/Project/HimalayanVault/chrome-extension/`
5. Click extension icon → Login with vault credentials

---

## 📁 Project Structure

```
HimalayanVault/
│
├── 📄 README.md                               # This file
├── 📄 pom.xml                                 # Maven configuration
├── 📄 RUN_APP.sh                              # Main application launcher
│
├── 📂 src/
│   ├── main/
│   │   ├── java/com/himalayanvault/
│   │   │   ├── Main.java                      # Entry point
│   │   │   ├── api/
│   │   │   │   ├── ApiServer.java             # REST API server
│   │   │   │   ├── dto/                       # Data transfer objects
│   │   │   │   └── handlers/                  # API request handlers
│   │   │   ├── auth/
│   │   │   │   ├── AuthManager.java           # Authentication logic
│   │   │   │   ├── BiometricHandler.java      # Biometric support
│   │   │   │   └── RecoveryCodeManager.java   # Recovery codes
│   │   │   ├── db/
│   │   │   │   └── DatabaseManager.java       # SQLite database
│   │   │   ├── models/
│   │   │   │   └── Credential.java            # Credential model
│   │   │   ├── security/
│   │   │   │   ├── EncryptionUtil.java        # AES-256-GCM encryption
│   │   │   │   └── SessionManager.java        # Session handling
│   │   │   └── ui/
│   │   │       ├── LoginController.java       # Login UI
│   │   │       ├── SignupController.java      # Signup UI
│   │   │       ├── VaultController.java       # Main vault UI
│   │   │       └── RecoveryController.java    # Recovery UI
│   │   └── resources/
│   │       ├── css/
│   │       │   ├── login.css
│   │       │   ├── signup.css
│   │       │   └── vault.css
│   │       └── fxml/
│   │           ├── login.fxml
│   │           ├── signup.fxml
│   │           ├── vault.fxml
│   │           └── recovery.fxml
│   └── test/java/                            # Unit tests
│
├── 📂 chrome-extension/                       # Chrome browser extension
│   ├── manifest.json                          # Extension configuration
│   ├── background.js                          # API communication
│   ├── content.js                             # Auto-fill & detection
│   ├── popup.html                             # Extension UI
│   ├── popup.js                               # Extension logic
│   ├── styles.css                             # Extension styling
│   ├── icons/                                 # Extension icons
│   │   ├── icon-16.png
│   │   ├── icon-48.png
│   │   ├── icon-128.png
│   │   └── icon-192.png
│   └── README.md                              # Extension guide
│
└── 📂 target/                                 # Build output
    ├── himalayan-vault-1.0.0.jar             # Regular JAR
    └── himalayan-vault-1.0.0-fat.jar         # Fat JAR (with dependencies)
```

---

## 📋 Prerequisites

### System Requirements
- **OS**: Linux, Windows, macOS
- **Java**: 25+ (or LTS version 21+)
- **Maven**: 3.8.9+
- **Chrome**: Latest version (for extension)

### Verify Installation
```bash
# Check Java
java -version

# Check Maven
mvn -version
```

---

## 🔧 Installation & Setup

### Step 1: Clone/Navigate to Project
```bash
cd /home/prasish/Project/HimalayanVault
```

### Step 2: Verify Maven Dependency
```bash
mvn dependency:resolve
```

### Step 3: Build Project
```bash
mvn clean compile
mvn clean package -DskipTests
```

---

## 🏃 Running the Application

### Desktop Backend (Required for Extension)

#### Option 1: Using Provided Script (Easiest) ⭐
```bash
./RUN_APP.sh
```

#### Option 2: Using Maven JavaFX Plugin (Recommended)
```bash
mvn clean javafx:run
```

#### Option 3: Using Maven Exec Plugin
```bash
mvn clean package -DskipTests
mvn exec:java
```

#### Option 4: Direct Java with Fat JAR
```bash
mvn clean package -DskipTests
java -jar target/himalayan-vault-1.0.0-fat.jar
```

### What Should Happen
1. ✅ JavaFX window opens
2. ✅ Login/Signup screen appears
3. ✅ API server starts on `http://127.0.0.1:8443`
4. ✅ Database initialized in `~/.himalayan-vault/`

---

## 🌐 Chrome Extension Setup

### Step 1: Load Extension in Chrome

1. Open Chrome and go to: **`chrome://extensions/`**
2. Enable **Developer mode** (toggle in top-right)
3. Click **Load unpacked**
4. Navigate to: `/home/prasish/Project/HimalayanVault/chrome-extension/`
5. Select and confirm

### Step 2: Verify Installation

You should see:
- ✅ "Himalayan Vault" extension in list
- ✅ Extension icon in Chrome toolbar (🏔️)
- ✅ No red error messages

### Step 3: Login to Extension

1. Click the Himalayan Vault icon in toolbar
2. Enter your vault credentials:
   - **Username**: Your vault username
   - **Password**: Your master password
3. Click "Login"
4. See: "✅ Login successful"

### Step 4: Test Extension

1. Go to: `https://practicetestautomation.com/practice-test-login/`
2. Enter credentials:
   - **Username**: `student`
   - **Password**: `password` (or try any password)
3. Click "Sign In"
4. See save banner: **"🔐 Save password for PRACTICETESTAUTO?"**
5. Click **"Save"**
6. See: **"✅ Password saved!"** notification

---

## ✨ Features

### Desktop Application

#### 🔐 Authentication
- Master password protection
- Biometric support (Windows Hello, Touch ID)
- Recovery codes for account recovery
- Session management with timeout

#### 💾 Credential Management
- Secure credential storage
- Encrypted with AES-256-GCM
- Search and filter credentials
- Copy credentials to clipboard
- Delete credentials with confirmation

#### 🔑 Password Generator
- Generate secure random passwords
- Customizable length (8-128 characters)
- Options: uppercase, lowercase, numbers, special chars
- Password strength estimation (zxcvbn)

#### 🎨 UI Features
- Modern JavaFX interface
- Responsive design
- Dark/Light theme support
- Categorized credentials view

### Chrome Extension

#### 🔐 Auto-Detection
- Detects login attempts automatically
- Monitors form submissions
- Identifies success/failure

#### 💾 Auto-Save Passwords
- Shows save banner after successful login
- **"🔐 Save password for [SITE]?"** prompt
- Save / Not now / Close options
- Auto-dismisses after 10 seconds

#### 🔍 Auto-Fill Passwords
- Click username/password field
- See saved credentials popup
- Click "Use" to auto-fill
- Smooth animations

#### 📋 Credential Management
- View saved credentials for current site
- Copy username to clipboard
- Delete credentials
- Manual credential entry

#### 🔑 Password Generator
- Generate secure passwords
- Customizable options
- Copy to clipboard directly

#### 🌙 Dark Theme
- Auto-detects OS dark mode
- Applies dark colors automatically
- Full theme support

---

## 🔗 API Endpoints - Complete Reference

The desktop app API server runs on: `http://127.0.0.1:8443`

### Quick Reference Table

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/health` | ✗ | Server health check |
| POST | `/login` | ✗ | Authenticate & get token |
| POST | `/lock` | ✓ | Logout/invalidate token |
| GET | `/credentials` | ✓ | Get all credentials |
| GET | `/credentials?site=` | ✓ | Get credentials by site |
| GET | `/credential/{id}` | ✓ | Get specific credential |
| POST | `/save` | ✓ | Save new credential |
| POST | `/update` | ✓ | Update credential |
| POST | `/delete` | ✓ | Delete credential |
| POST | `/generate-password` | ✓ | Generate secure password |

---

### Authentication

#### POST `/login`
Login with credentials
```bash
curl -X POST http://127.0.0.1:8443/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student","password":"password"}'
```

Response:
```json
{
  "success": true,
  "token": "eyJhbGc...",
  "username": "student",
  "message": "Login successful"
}
```

#### POST `/lock`
Logout/Lock session
```bash
curl -X POST http://127.0.0.1:8443/lock \
  -H "Authorization: Bearer TOKEN"
```

### Credentials

#### GET `/credentials`
Get all credentials (with auth token)
```bash
curl http://127.0.0.1:8443/credentials \
  -H "Authorization: Bearer TOKEN"
```

#### GET `/credentials?site=DOMAIN`
Get credentials for a specific site
```bash
curl http://127.0.0.1:8443/credentials?site=practicetestautomation.com \
  -H "Authorization: Bearer TOKEN"
```

#### GET `/credential/{id}`
Get a specific credential by ID
```bash
curl http://127.0.0.1:8443/credential/1 \
  -H "Authorization: Bearer TOKEN"
```

#### POST `/save`
Save new credential
```bash
curl -X POST http://127.0.0.1:8443/save \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{
    "siteUrl":"example.com",
    "siteName":"Example",
    "siteUsername":"user@example.com",
    "encryptedPassword":"encrypted_password_base64",
    "notes":"My account"
  }'
```

#### POST `/update`
Update existing credential
```bash
curl -X POST http://127.0.0.1:8443/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{
    "credentialId":1,
    "siteName":"Example Updated",
    "siteUsername":"newemail@example.com",
    "encryptedPassword":"new_encrypted_password",
    "notes":"Updated account"
  }'
```

#### POST `/delete`
Delete credential
```bash
curl -X POST http://127.0.0.1:8443/delete \
  -H "Authorization: Bearer TOKEN" \
  -d '{"credentialId":1}'
```

### Password Generation

#### POST `/generate-password`
Generate secure password
```bash
curl -X POST http://127.0.0.1:8443/generate-password \
  -H "Authorization: Bearer TOKEN" \
  -d '{
    "length":16,
    "useUppercase":true,
    "useLowercase":true,
    "useNumbers":true,
    "useSpecialChars":true
  }'
```

Response:
```json
{
  "password": "aB3$xYz9@qW2pL",
  "strength": "strong"
}
```

### Server Health

#### GET `/health`
Check API server status
```bash
curl http://127.0.0.1:8443/health
```

Response:
```json
{
  "status": true,
  "message": "API Server is running",
  "timestamp": "2026-05-23T10:30:00.000Z"
}
```

---

## 🔒 Security

### Encryption
- **Algorithm**: AES-256-GCM
- **Key Derivation**: PBKDF2
- **Passwords**: Encrypted on server, never stored plaintext

### Authentication
- **Token**: JWT-based
- **Storage**: Chrome secure storage
- **Timeout**: 30 minutes inactivity
- **Master Password**: Never stored locally

### Network
- **API Server**: Localhost only (127.0.0.1:8443)
- **CORS**: Not enabled (local communication)
- **Database**: SQLite in user home directory

### Session Management
- Tokens validated on every request
- Sessions cleared on logout
- Automatic timeout after inactivity
- Secure token refresh mechanism

### Best Practices
1. **Never share** your master password
2. **Keep database backed up** (`~/.himalayan-vault/`)
3. **Use strong passwords** (20+ characters recommended)
4. **Close extension** after use (or logout)
5. **Keep Java/Chrome updated**

---

## 🐛 Troubleshooting

### Desktop Application Issues

#### "JavaFX runtime components are missing"
**Solution**: Use Maven to run
```bash
mvn clean javafx:run
```

#### "Location is not set" errors
**Solution**: These are non-critical UI warnings. App still works.

#### "Cannot find symbol" compilation errors
**Solution**: Run from project root where `pom.xml` is located
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean compile
```

#### "Port 8443 already in use"
**Solution**: Kill previous Java process
```bash
# Linux/Mac
lsof -i :8443
kill -9 <PID>

# Windows
netstat -ano | findstr :8443
taskkill /PID <PID> /F
```

#### "Database locked" error
**Solution**: Close all instances of Himalayan Vault and retry
```bash
# Verify no Java processes running
ps aux | grep java

# If still locked, check database file permissions
chmod 644 ~/.himalayan-vault/himalayan-vault.db
```

#### "Permission denied" for home directory
**Solution**: Check permissions on `~/.himalayan-vault/`
```bash
chmod 755 ~/.himalayan-vault
chmod 644 ~/.himalayan-vault/*
```

#### Build errors with dependencies
**Solution**: Update Maven dependencies
```bash
mvn clean dependency:resolve
mvn clean install -DskipTests
```

#### "Access denied" when writing to database
**Solution**: Check directory and file permissions
```bash
ls -la ~/.himalayan-vault/
chmod 755 ~/.himalayan-vault
chmod 644 ~/.himalayan-vault/himalayan-vault.db
```

#### Application runs but no window appears
**Solution**: Try running with explicit Java arguments
```bash
mvn javafx:run -Djavafx.verbose=true
```

---

### Chrome Extension Issues

#### Extension shows "API Server Not Running"
**Solution**: 
1. Ensure desktop app is running: `mvn clean javafx:run`
2. Check API health: `curl http://127.0.0.1:8443/health`
3. Reload extension: `chrome://extensions/` → Reload button
4. Verify port 8443 is accessible
5. Check firewall isn't blocking localhost:8443

#### "Not logged in" error
**Solution**:
1. Click extension icon
2. Login with vault credentials
3. Verify "Login successful" message
4. Check chrome.storage.local: Open DevTools (F12) → Application → Storage → Local Storage
5. Ensure sessionToken is saved

#### "Cannot save credentials"
**Solution**:
1. Verify desktop app is running: `curl http://127.0.0.1:8443/health`
2. Check extension is logged in
3. Open DevTools (F12) → Network tab
4. Perform save action and check API response
5. Look for errors in Console tab

#### Icons not showing in extension
**Solution**:
1. Verify files exist: `chrome-extension/icons/`
2. Icon names must be: `icon-16.png`, `icon-48.png`, `icon-128.png`, `icon-192.png`
3. Reload extension: `chrome://extensions/` → Reload button
4. Check file permissions: `chmod 644 chrome-extension/icons/*`
5. Verify image files are valid PNGs

#### "API request timeout" on save
**Solution**:
1. Verify API server is responsive: `curl http://127.0.0.1:8443/health`
2. Check network connection
3. Increase timeout in `background.js` if needed:
```javascript
const API_TIMEOUT = 10000;  // Increase to 10 seconds
```
4. Check if Java process is consuming too much CPU/memory

#### Auto-save banner not appearing
**Solution**:
1. Login to extension first
2. Ensure JavaScript is enabled in Chrome
3. Try on simple login form (like practicetestautomation.com)
4. Check console for errors: F12 → Console
5. Verify form has username and password fields
6. Look for error messages starting with "HV-AutoSave:"

#### Credentials not auto-filling
**Solution**:
1. Click password or username field
2. Wait for popup to appear (1-2 seconds)
3. If no popup: No saved credentials for this site
4. Verify site name matches (check browser console for detected site)
5. Try manual login first to save credentials
6. Check that form fields are properly detected

#### Dark theme not applying
**Solution**:
1. Check OS dark mode is enabled
2. Reload extension: `chrome://extensions/` → Reload
3. Hard refresh extension popup: Ctrl+Shift+R
4. Verify `styles.css` has `@media (prefers-color-scheme: dark)`
5. Check CSS variables are defined in `:root`

#### Extension doesn't appear in toolbar
**Solution**:
1. Go to `chrome://extensions/`
2. Verify extension is enabled (toggle should be ON)
3. If not listed: Click "Load unpacked" and select `chrome-extension/` folder
4. Check for red error messages or warnings
5. Verify manifest.json is valid JSON

#### "Credential for site already exists" error
**Solution**:
1. Delete existing credential and save again
2. Or update the existing credential instead of saving new one
3. Check popup shows all saved credentials

#### Auto-fill fills wrong fields
**Solution**:
1. Extension uses smart field detection (email, password, login fields)
2. Try clicking password field first instead of username field
3. If website uses unusual field names, form filling may not work
4. Manually fill fields or update form selectors in content.js

---

### Getting Help & Advanced Debugging

#### Check Extension Console
1. Right-click Himalayan Vault extension icon
2. Click "Manage extension"
3. Click "Inspect views" → "service_worker" or "background page"
4. Check console tab for errors and debug messages

#### Check API Logs
Look at terminal where you ran `mvn clean javafx:run`

#### Enable Verbose Logging
In `background.js`, change:
```javascript
const DEBUG = true;  // Enable console logging
```

#### Network Debugging
1. Open DevTools (F12) in Chrome
2. Go to Network tab
3. Perform action in extension
4. See API requests and responses
5. Check Response tab for error details
6. Check "Preview" tab for JSON response

#### Check Database Integrity
```bash
# Check SQLite database integrity
sqlite3 ~/.himalayan-vault/himalayan-vault.db "PRAGMA integrity_check;"

# Export and view tables
sqlite3 ~/.himalayan-vault/himalayan-vault.db ".tables"
sqlite3 ~/.himalayan-vault/himalayan-vault.db "SELECT COUNT(*) FROM credentials;"
```

#### Performance Troubleshooting

**Extension is slow:**
- Check Chrome task manager (Shift+Esc)
- Look for high CPU/memory usage
- Reduce number of saved credentials if > 100
- Clear chrome storage if needed: Right-click → Manage extension → Storage → Clear data

**API server is slow:**
- Verify Java process has enough memory: `top` or Task Manager
- Check database size: `ls -lh ~/.himalayan-vault/`
- Restart Java server if needed
- Check for disk I/O issues

---

### Quick Troubleshooting Flowchart

```
Issue: Extension not working
  ├─ API server not running?
  │  └─ Run: mvn clean javafx:run
  ├─ Getting "API Server Not Running" error?
  │  └─ curl http://127.0.0.1:8443/health
  ├─ Extension not logged in?
  │  └─ Click icon → Login with vault credentials
  ├─ Credentials not saving?
  │  └─ Check F12 → Network tab for API errors
  ├─ No icons showing?
  │  └─ Add PNG files to: chrome-extension/icons/
  ├─ Auto-save banner doesn't appear?
  │  └─ Check F12 → Console for form detection errors
  ├─ Copy button not working?
  │  └─ Reload extension (chrome://extensions → Reload)
  ├─ Dark theme not working?
  │  └─ Check OS dark mode is enabled + reload extension
  └─ Still not working?
     └─ Check console: F12 in extension → Console tab + Check API logs
```

---

## 📝 Pre-Use Checklist

- [ ] Java 25+ installed (`java -version`)
- [ ] Maven 3.8.9+ installed (`mvn -version`)
- [ ] Project cloned/in `/home/prasish/Project/HimalayanVault`
- [ ] Dependencies resolved (`mvn dependency:resolve`)
- [ ] Backend starts (`mvn clean javafx:run`)
- [ ] API responds to health check (`curl http://127.0.0.1:8443/health`)
- [ ] Extension loaded in Chrome (`chrome://extensions`)
- [ ] Extension icon visible in toolbar
- [ ] Icons exist in `chrome-extension/icons/`
- [ ] Can login to extension with vault credentials
- [ ] Can see "🔐 Save password?" banner on login
- [ ] Can save and retrieve credentials

---

## 🎯 Next Steps

1. **Run the vault**: `mvn clean javafx:run`
2. **Create account** in the JavaFX app
3. **Load extension** in Chrome (`chrome://extensions` → Load unpacked)
4. **Login** to extension with vault credentials
5. **Test on practicetestautomation.com**:
   - Username: `student`
   - Password: `password`
6. **See save banner** "🔐 Save password for PRACTICETESTAUTO?"
7. **Click Save** button
8. **See success notification** "✅ Password saved!"
9. **Enjoy secure password management!** 🔐

---

## 📚 Additional Information

### Database Location
- **Path**: `~/.himalayan-vault/himalayan-vault.db`
- **Backup**: `cp -r ~/.himalayan-vault/ ~/.himalayan-vault-backup-$(date +%s)`
- **Reset**: Delete database file (will be recreated on next login)
- **Size**: Typically 1-10 MB depending on number of credentials

### Build Artifacts
- **Regular JAR**: `target/himalayan-vault-1.0.0.jar` (85 KB)
- **Fat JAR**: `target/himalayan-vault-1.0.0-fat.jar` (33 MB, includes all dependencies)
- **Classes**: `target/classes/` (compiled bytecode)

### Dependencies Summary

**Core:**
- com.google.code.gson (JSON)
- org.xerial:sqlite-jdbc (Database)
- org.bouncycastle:bcprov-jdk18on (Encryption)

**UI:**
- javafx-controls, javafx-fxml, javafx-graphics, javafx-base

**Utilities:**
- javax.activation (for dependencies)

---

## 🎉 Features Summary

### Desktop App
✅ Master password authentication
✅ Biometric support (Windows Hello, Touch ID)
✅ AES-256-GCM encrypted storage
✅ Password generator with custom options
✅ Recovery codes for account recovery
✅ Modern JavaFX UI
✅ SQLite persistent storage

### Chrome Extension
✅ Automatic login detection
✅ Auto-save password banner (🔐 Save password for [SITE]?)
✅ Auto-fill on field focus popup
✅ Copy username to clipboard
✅ One-click credential fill
✅ Credential management (save/delete)
✅ Dark theme support
✅ Session token persistence

---

## 📋 Version Information

- **Project**: Himalayan Vault
- **Version**: 1.0.0
- **Status**: ✅ Ready to Use
- **Last Updated**: May 23, 2026
- **All .md files consolidated**: ✅ Yes - This README contains complete documentation
- **Build Type**: Maven + JavaFX
- **Architecture**: Desktop + Chrome Extension

---

## 📞 Support & Documentation

**This README.md contains:**
- ✅ Complete setup instructions
- ✅ All API endpoint documentation
- ✅ Extension feature guide
- ✅ Architecture overview
- ✅ Deployment guide
- ✅ Security information
- ✅ Comprehensive troubleshooting
- ✅ Configuration options
- ✅ Technology stack details
- ✅ Quick reference commands

**No separate .md files needed - everything is here!**

---

**Happy secure password managing! 🏔️🔐**

For any issues, refer to the [Troubleshooting](#troubleshooting) section above or check the [Quick Reference Commands](#quick-reference-commands) table.

---

## 🔧 Configuration

### System Requirements

**Minimum:**
- Java 21+ (tested with Java 25)
- Maven 3.8.9+
- 100 MB RAM (plus JavaFX requirements)
- 50 MB disk space for database
- Chrome browser (latest version)

**Recommended:**
- Java 25 (as tested)
- Maven 3.9+
- 512 MB+ RAM
- SSD storage for database

**Network:**
- Port 8443 available (localhost only)
- No outbound internet required

### Change Server Port
Edit `src/main/java/com/himalayanvault/api/ApiServer.java`:
```java
private static final int PORT = 8443;  // Change this value
```

### Session Timeout
Edit `src/main/java/com/himalayanvault/security/SessionManager.java`:
```java
private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;  // 30 minutes
```

### Thread Pool Size
Edit `src/main/java/com/himalayanvault/api/ApiServer.java`:
```java
private static final int THREAD_POOL_SIZE = 10;  // Number of worker threads
```

### API Timeout (Extension)
Edit `chrome-extension/background.js`:
```javascript
const API_TIMEOUT = 5000;  // 5 seconds (milliseconds)
```

### Extension Popup Width
Edit `chrome-extension/styles.css`:
```css
body {
    width: 450px;  /* Change this value for wider/narrower popup */
}
```

### Extension Colors
Edit `chrome-extension/styles.css`:
```css
:root {
    --primary-color: #00d4ff;      /* Cyan - Primary buttons */
    --secondary-color: #667eea;    /* Purple - Secondary elements */
    --accent-color: #ff00ff;       /* Magenta - Highlights */
}
```

### Java Version for Build
Edit `pom.xml`:
```xml
<properties>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
</properties>
```

---

## 📊 Technology Stack

### Backend
- **Java 25** - Core language
- **JavaFX 23.0.2** - UI framework
- **SQLite** - Database (JDBC)
- **Bouncy Castle** - Encryption (AES-256-GCM)
- **JNA** - Biometric APIs (Windows Hello, Touch ID)
- **Zxcvbn** - Password strength estimation
- **GSON** - JSON serialization
- **Maven 3.8.9+** - Build tool

### Frontend (Extension)
- **JavaScript (ES6)** - Content & Background scripts
- **HTML5** - UI markup
- **CSS3** - Styling with CSS variables
- **Chrome APIs** - Storage, messaging, tabs, extension APIs

### Security
- **AES-256-GCM** - Credential encryption
- **PBKDF2-HMAC-SHA256** - Key derivation (100k iterations)
- **JWT tokens** - Session authentication
- **32-byte random tokens** - Secure identifiers
- **Chrome secure storage** - Token persistence

### Build & Dependencies
- **Maven Shade Plugin** - Fat JAR creation
- **Maven Compiler** - Java 25 compilation
- **Maven JavaFX Plugin** - JavaFX module path handling

---

---

## 📈 Architecture Overview

### Complete System Architecture

```
┌─────────────────────────────────────────────┐
│         Chrome Browser                      │
│  ┌──────────────────────────────────────┐   │
│  │  Himalayan Vault Extension           │   │
│  │  ├── popup.html/js (UI)              │   │
│  │  ├── content.js (Auto-detect/fill)   │   │
│  │  ├── background.js (API client)      │   │
│  │  └── styles.css (Dark theme)         │   │
│  └──────────────────────────────────────┘   │
│              ↓ HTTP ↓ JSON                   │
├─────────────────────────────────────────────┤
│      Java Backend (Localhost:8443)          │
│  ┌──────────────────────────────────────┐   │
│  │  Himalayan Vault App                 │   │
│  │  ├── ApiServer (REST API)            │   │
│  │  │   ├── /health handler             │   │
│  │  │   ├── /login, /lock handlers      │   │
│  │  │   ├── /credentials handlers       │   │
│  │  │   └── /generate-password handler  │   │
│  │  ├── AuthManager (Login/Auth)        │   │
│  │  ├── SessionManager (Sessions)       │   │
│  │  ├── EncryptionUtil (AES-256-GCM)    │   │
│  │  ├── DatabaseManager (SQLite)        │   │
│  │  └── UI Controllers (JavaFX)         │   │
│  └──────────────────────────────────────┘   │
│              ↓ JDBC ↓                       │
├─────────────────────────────────────────────┤
│         SQLite Database                     │
│  (~/.himalayan-vault/himalayan-vault.db)   │
│  ├── users table                            │
│  ├── credentials table                      │
│  ├── sessions table                         │
│  └── recovery_codes table                   │
└─────────────────────────────────────────────┘
```

### Server Architecture

```
JavaFX Desktop App (Main.java)
         ↓
    Start up
         ↓
├── Database Initialization
│   └── DatabaseManager
│       └── ~/.himalayan-vault/himalayan-vault.db
│
├── API Server Initialization
│   └── ApiServer (127.0.0.1:8443)
│       ├── ThreadPool (10 workers)
│       ├── Handler Registration
│       │   ├── GET /health → HealthHandler
│       │   ├── POST /login → AuthHandler
│       │   ├── POST /lock → AuthHandler
│       │   ├── GET /credentials → CredentialHandler
│       │   ├── POST /save → CredentialHandler
│       │   ├── POST /update → CredentialHandler
│       │   ├── POST /delete → CredentialHandler
│       │   ├── GET /credential/{id} → CredentialHandler
│       │   └── POST /generate-password → PasswordHandler
│       └── Ready for requests
│
└── JavaFX UI Controllers
    ├── LoginController
    ├── SignupController
    ├── VaultController
    └── RecoveryController
```

### Security Architecture

```
Client Request
       ↓
┌─────────────────────────────────┐
│  Request Handler                │
│  ├── Parse request             │
│  ├── Validate input            │
│  └── Check authentication      │
└──────────┬──────────────────────┘
           ↓
┌─────────────────────────────────┐
│  SessionManager                 │
│  ├── Validate token            │
│  ├── Check expiry              │
│  └── Get session data          │
└──────────┬──────────────────────┘
           ↓
┌─────────────────────────────────┐
│  EncryptionUtil                 │
│  ├── AES-256-GCM               │
│  ├── PBKDF2 key derivation     │
│  └── Secure random IV/salt     │
└──────────┬──────────────────────┘
           ↓
┌─────────────────────────────────┐
│  DatabaseManager                │
│  └── SQLite (secure storage)    │
└─────────────────────────────────┘
```

---

## 📦 Deployment & Build

### Build Artifacts Generated

```
target/
├── himalayan-vault-1.0.0.jar (85 KB)      # Main JAR
├── himalayan-vault-1.0.0-fat.jar (33 MB)  # Shaded JAR with all dependencies
└── classes/                                # Compiled classes
```

### Running the Application

#### Option 1: Using Maven (Development) ⭐
```bash
cd ~/Project/HimalayanVault
mvn clean javafx:run
```

#### Option 2: Using Java Directly
```bash
java -jar target/himalayan-vault-1.0.0-fat.jar
```

#### Option 3: Using Run Script
```bash
./RUN_APP.sh
```

### Expected Startup Output

```
[Main] Initializing Himalayan Vault...
[Main] Database initialized at: ~/.himalayan-vault/
[ApiServer] Server configured on 127.0.0.1:8443
[ApiServer] Registered GET /health
[ApiServer] Registered POST /login, POST /lock
[ApiServer] Registered GET /credentials endpoints
[ApiServer] Registered POST /save, /update, /delete endpoints
[ApiServer] Registered POST /generate-password
[ApiServer] ✓ Server started on http://127.0.0.1:8443
[ApiServer] Listening for Chrome extension requests...
[UI] JavaFX window opened
```

### Verify Installation

#### Test 1: Health Check
```bash
curl http://127.0.0.1:8443/health
```

Expected: `{"status":true,"message":"API Server is running"}`

#### Test 2: Login
```bash
curl -X POST http://127.0.0.1:8443/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password"}'
```

Expected: Success token or error message

---

## ✨ Extension Features Guide

### Fix 1: Copy Username Button

**Before:** Button didn't work due to unescaped quotes
```javascript
// ❌ Broken:
onclick="copyToClipboard('username@email.com')"
```

**After:** Uses proper event listeners
```javascript
// ✅ Fixed:
btn.addEventListener('click', () => {
  copyToClipboard(username);
  showSuccessNotification('✅ Username copied!');
});
```

### Feature 1: Auto-Fill Popup on Field Focus

**What it does:**
- Click/focus on username or password field
- Popup appears with saved credentials for that site
- Click "Fill" to auto-fill both fields

**How to use:**
1. Go to `https://practicetestautomation.com/practice-test-login/`
2. Click on Username field
3. Popup shows: "📄 Saved Credentials"
4. Click "Fill" → Auto-filled ✅

**Supported field detection:**
- `input[type="email"]`
- `input[name*="user"]` or `input[name*="email"]`
- `input[name*="login"]`
- `input[placeholder*="user"]`
- `input[type="password"]`

### Feature 2: Auto-Save After Successful Login

**What it does:**
- After successful login, shows: "🔐 Save password for [SITE]?"
- Displays username that was used
- Auto-detects login success by checking for error messages

**How to use:**
1. Enter username and password
2. Click login button
3. Form submission detected
4. If no error messages → Login successful ✅
5. See save banner: "🔐 Save password for [SITE]?"
6. Click "Save" to save credentials

**Banner features:**
- ✅ Save button - Save credentials
- ⏭️ Not now button - Skip for now
- ✕ Close button - Dismiss banner
- Auto-dismiss after 10 seconds

### Feature 3: Quick Save After Login

**Alternative to auto-save:**
- Shows: "💾 Quick Save Login?" prompt
- Captures username you just entered
- One-click saving

### Feature 4: Autofill Button in Popup

**What it does:**
- Each saved credential has "🔐 Autofill" button
- Click to auto-fill username & password

**Buttons in popup:**
- 📋 Copy Username - Copy just the username
- 🔐 Autofill - Fill both username and password
- 🗑️ Delete - Remove the credential

### Feature 5: Copy Feedback Notification

**What it shows:**
- ✅ Username copied! (2-second notification)
- ✅ Credential auto-filled! (2-second notification)
- ❌ Error messages when something fails

### Feature 6: Dark Theme Support

**What it does:**
- Automatically detects OS dark mode
- UI adapts with proper colors
- No manual configuration needed

**Color scheme:**
- Light Mode: Light backgrounds, dark text
- Dark Mode: Dark backgrounds (#1a1a2e), light text (#e0e0e0)

### Complete Workflow Example

**Scenario: Save login for practicetestautomation.com**

**Step 1: First Login**
```
1. Go to https://practicetestautomation.com/practice-test-login/
2. Click username field
3. Popup shows: "📄 Saved Credentials"
4. Type "student" manually (no saved creds yet)
5. Type password
6. Click login
```

**Step 2: Auto-Save**
```
7. Form submission detected
8. No error messages → Login successful ✅
9. See: "🔐 Save password for PRACTICETESTAUTO?"
10. Click "Save"
11. Credentials saved! ✅
```

**Step 3: Future Logins**
```
1. Go to practicetestautomation.com
2. Click username field
3. Popup shows: "📄 Saved Credentials"
   - student (saved earlier)
4. Click "Fill"
5. Username & password auto-filled ✅
6. Click login button
```
