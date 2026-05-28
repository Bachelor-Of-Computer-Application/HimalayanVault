const fs = require('fs');

const popupHtml = fs.readFileSync('chrome-extension/popup.html', 'utf8');
const newPopupHtml = popupHtml.replace(
  /<div id="loginPanel" class="login-panel">[\s\S]*?<\/div>\n\n    <!-- Main Panel -->/,
  `<div id="loginPanel" class="login-panel">
        <div class="background-container">
            <img src="icons/background_image.png" alt="Background" class="background-image">
        </div>
        
        <!-- Logo - Top Left -->
        <div class="logo-header">
            <img src="icons/icon.png" alt="Himalayan Vault" class="logo-icon-img">
            <div class="logo-text-col">
                <span class="logo-main-text">HIMALAYAN</span>
                <span class="logo-sub-text">VAULT</span>
            </div>
        </div>

        <!-- Main Container -->
        <div class="login-container">
            <!-- Login Form -->
            <form id="loginForm" class="login-box-form">
                <h2 class="form-title">Login</h2>
                
                <!-- Email/Username Field -->
                <div class="form-group-line">
                    <input type="email" id="username" class="form-input-line" placeholder="Enter your username" required>
                </div>

                <!-- Password Field -->
                <div class="form-group-line">
                    <input type="password" id="password" class="form-input-line" placeholder="Enter your password" required>
                </div>

                <!-- Error Message -->
                <div id="loginError" class="error-msg" style="display:none;"></div>

                <!-- Login Button with Biometric Options -->
                <div class="button-group-row">
                    <button type="submit" class="login-btn-gradient">LOG IN</button>
                    <button type="button" class="biometric-btn-glow fingerprint-btn" title="Biometric Authentication">BIO</button>
                </div>

                <!-- Footer Links -->
                <div class="form-links-row">
                    <a href="#" class="link-cyan">Forgot Password</a>
                    <a href="#" class="link-cyan">Sign Up</a>
                </div>
            </form>
        </div>
    </div>

    <!-- Main Panel -->`
);

fs.writeFileSync('chrome-extension/popup.html', newPopupHtml);
