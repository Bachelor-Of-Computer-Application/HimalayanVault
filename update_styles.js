const fs = require('fs');

let css = fs.readFileSync('chrome-extension/styles.css', 'utf8');

const newCSS = `/* Popup Base Resets */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    width: 400px;
    height: 600px;
    font-family: 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
    background: #0f1729; /* Fallback */
    padding: 0;
    color: #fff;
    overflow-x: hidden;
}

:root {
    --primary-color: #00d4ff;
    --secondary-color: #667eea;
    --accent-color: #ff00ff;
    --dark-bg: #0f1729;
    --card-bg: rgba(20, 30, 60, 0.8);
    --text-primary: #ffffff;
    --text-secondary: #a0aec0;
}

/* Background elements inside login panel */
.login-panel {
    width: 100%;
    height: 100%;
    position: relative;
    display: flex;
    flex-direction: column;
}

.background-container {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
    background: rgba(10, 8, 18, 0.5); /* Overlay */
}

.background-container::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(10, 8, 18, 0.5);
}

.background-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
    z-index: 0;
    position: absolute;
    top: 0;
    left: 0;
}

/* Logo Header */
.logo-header {
    position: relative;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 15px 20px;
}

.logo-icon-img {
    width: 24px;
    height: 24px;
}

.logo-text-col {
    display: flex;
    flex-direction: column;
}

.logo-main-text {
    font-size: 14px;
    font-weight: 800;
    letter-spacing: 2px;
    color: #fff;
    line-height: 1;
}

.logo-sub-text {
    font-size: 10px;
    color: #00e6ff;
    letter-spacing: 4px;
    line-height: 1;
}

/* Glassmorphism Container */
.login-container {
    position: relative;
    z-index: 10;
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
}

.login-box-form {
    width: 100%;
    max-width: 320px;
    background: rgba(15, 23, 42, 0.6);
    backdrop-filter: blur(25px);
    -webkit-backdrop-filter: blur(25px);
    border: 1px solid rgba(0, 217, 255, 0.1);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5), 
                inset 0 0 32px rgba(0, 217, 255, 0.05);
    border-radius: 20px;
    padding: 30px 25px;
}

.form-title {
    text-align: center;
    font-size: 18px;
    margin-bottom: 25px;
    color: white;
}

/* Underline Inputs */
.form-group-line {
    margin-bottom: 20px;
}

.form-input-line {
    width: 100%;
    padding: 10px 0;
    background: transparent;
    border: none;
    border-bottom: 2px solid rgba(0, 217, 255, 0.4);
    color: #fff;
    font-size: 14px;
    transition: all 0.3s ease;
}

.form-input-line:focus {
    outline: none;
    border-bottom-color: #00e6ff;
    box-shadow: 0 5px 10px -5px rgba(0, 217, 255, 0.3);
}

/* Gradient Button and Icon */
.button-group-row {
    display: flex;
    gap: 15px;
    margin-top: 25px;
    margin-bottom: 20px;
}

.login-btn-gradient {
    flex: 1;
    padding: 12px 20px;
    background: linear-gradient(135deg, #00e6ff 0%, #0099cc 100%);
    border: none;
    border-radius: 10px;
    color: #000;
    font-size: 13px;
    font-weight: 800;
    cursor: pointer;
    box-shadow: 0 4px 15px rgba(0, 217, 255, 0.4);
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: all 0.3s ease;
}

.login-btn-gradient:hover {
    box-shadow: 0 6px 20px rgba(0, 217, 255, 0.6);
    transform: translateY(-1px);
}

.biometric-btn-glow {
    width: 42px;
    height: 42px;
    border-radius: 10px;
    background: rgba(0, 217, 255, 0.1);
    border: 1px solid rgba(0, 217, 255, 0.3);
    color: #00e6ff;
    font-size: 20px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
}

.biometric-btn-glow:hover {
    background: rgba(0, 217, 255, 0.2);
    box-shadow: 0 0 15px rgba(0, 217, 255, 0.4);
}

/* Links */
.form-links-row {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
}

.link-cyan {
    color: #00d9ff;
    text-decoration: none;
    transition: opacity 0.3s ease;
}

.link-cyan:hover {
    opacity: 0.8;
}
` + css.substring(css.indexOf('/* Main Panel */'));

fs.writeFileSync('chrome-extension/styles.css', newCSS);
