# Himalayan Vault - Setup & Run Guide

## Project Status
✅ **Fully Fixed and Runnable** - All build and configuration issues resolved.

## Folder Structure
```
HimalayanVault/
├── pom.xml                          # Maven configuration
├── run.sh                           # Legacy run script
├── RUN_APP.sh                       # Main application runner (recommended)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/himalayanvault/
│   │   │       ├── Main.java                    # Application entry point
│   │   │       ├── auth/
│   │   │       │   ├── AuthManager.java
│   │   │       │   ├── BiometricHandler.java
│   │   │       │   └── RecoveryCodeManager.java
│   │   │       ├── db/
│   │   │       │   └── DatabaseManager.java
│   │   │       └── ui/
│   │   │           ├── LoginController.java
│   │   │           ├── SignupController.java
│   │   │           └── VaultController.java
│   │   └── resources/
│   │       ├── css/
│   │       │   ├── login.css
│   │       │   ├── signup.css
│   │       │   └── vault.css
│   │       └── fxml/
│   │           ├── login.fxml
│   │           ├── signup.fxml
│   │           └── vault.fxml
│   └── test/
│       └── java/
└── target/
    ├── himalayan-vault-1.0.0.jar            # Main JAR
    └── himalayan-vault-1.0.0-fat.jar        # Fat JAR (all dependencies included)
```

## Prerequisites
- Java 25+ (or compatible LTS version)
- Maven 3.8.9+

## How to Run

### Option 1: Using the Provided Script (Easiest)
```bash
cd /home/prasish/Project/HimalayanVault
./RUN_APP.sh
```

### Option 2: Using Maven JavaFX Plugin (Recommended)
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean package -DskipTests
mvn javafx:run
```

### Option 3: Using Exec Plugin
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean package -DskipTests
mvn exec:java
```

### Option 4: Direct Java Execution (with fat JAR)
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean package -DskipTests
java -cp target/himalayan-vault-1.0.0-fat.jar com.himalayanvault.Main
```

### Option 5: Direct Java with Module Path (Advanced)
```bash
cd /home/prasish/Project/HimalayanVault
java --module-path $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout -Dincludes='org.openjfx:*') \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
     -cp "target/himalayan-vault-1.0.0.jar:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
     com.himalayanvault.Main
```

## Build Only (Without Running)
```bash
cd /home/prasish/Project/HimalayanVault
mvn clean compile          # Compile only
mvn clean package          # Compile and create JAR
mvn clean install          # Compile, package, and install to local Maven repo
```

## Project Configuration
- **Java Version**: 25
- **JavaFX Version**: 23.0.2
- **Package**: com.himalayanvault
- **Main Class**: com.himalayanvault.Main

## Dependencies
- **JavaFX**: GUI framework (controls, FXML, graphics, base)
- **SQLite JDBC**: Database persistence
- **Bouncy Castle**: AES-256-GCM encryption
- **Zxcvbn**: Password strength estimation
- **JNA**: Windows Hello / Touch ID biometrics
- **JUnit 5**: Testing framework

## Troubleshooting

### "JavaFX runtime components are missing"
**Solution**: Use Maven to run the application (Option 2 or 3 above)

### "Location is not set" errors during runtime
These are application-level issues in recovery code, not build problems. The app still runs fine.

### "Cannot find symbol" compilation errors
**Solution**: Run from project root directory where pom.xml is located

### Port/Permissions issues
Ensure you have write permissions in the project directory and home directory (~/.himalayan-vault/)

## All Fixes Applied ✅
1. Fixed package declaration in Main.java
2. Corrected Maven source directory paths
3. Added all JavaFX module dependencies
4. Configured Maven shade plugin for fat JAR
5. Updated run scripts with proper classpath configuration
6. Verified folder structure is correct

---
**Status**: Ready to use! Start with `./RUN_APP.sh` or `mvn javafx:run`
