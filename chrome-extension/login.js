// Login page functionality

document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.getElementById('loginForm');
    const biometricBtn = document.getElementById('biometricBtn');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');

    // Form submission
    if (loginForm) {
        loginForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            const username = usernameInput.value.trim();
            const password = passwordInput.value;

            if (!username || !password) {
                showNotification('Please fill in all fields', 'error');
                return;
            }

            // Send login request to background script
            chrome.runtime.sendMessage({
                action: 'login',
                username: username,
                password: password
            }, function(response) {
                if (response && response.success) {
                    showNotification('Login successful!', 'success');
                    setTimeout(() => {
                        window.location.href = 'popup.html';
                    }, 1000);
                } else {
                    showNotification(response?.error || 'Login failed', 'error');
                }
            });
        });
    }

    // Biometric authentication
    if (biometricBtn) {
        biometricBtn.addEventListener('click', function(e) {
            e.preventDefault();
            
            if (window.PublicKeyCredential) {
                chrome.runtime.sendMessage({
                    action: 'biometricAuth'
                }, function(response) {
                    if (response && response.success) {
                        showNotification('Biometric authentication successful!', 'success');
                        setTimeout(() => {
                            window.location.href = 'popup.html';
                        }, 1000);
                    } else {
                        showNotification(response?.error || 'Biometric authentication failed', 'error');
                    }
                });
            } else {
                showNotification('Biometric authentication not supported', 'error');
            }
        });
    }

    // Auto-focus input fields on hover
    const inputFields = document.querySelectorAll('.input-field');
    inputFields.forEach(field => {
        field.addEventListener('mouseenter', function() {
            const input = this.querySelector('input');
            if (input) {
                input.focus();
            }
        });
    });
});

// Notification system
function showNotification(message, type = 'info') {
    // You can enhance this with a toast notification
    console.log(`[${type.toUpperCase()}] ${message}`);
    alert(message);
}
