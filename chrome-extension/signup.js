// Signup page functionality

document.addEventListener('DOMContentLoaded', function() {
    const signupForm = document.getElementById('signupForm');
    const emailInput = document.getElementById('email');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    const strengthIndicator = document.getElementById('strengthIndicator');

    // Password strength checker
    function checkPasswordStrength(password) {
        let strength = 0;
        
        if (password.length >= 8) strength++;
        if (password.length >= 12) strength++;
        if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++;
        if (/[0-9]/.test(password)) strength++;
        if (/[^a-zA-Z0-9]/.test(password)) strength++;

        return Math.min(strength, 3); // 1: weak, 2: medium, 3: strong
    }

    function updatePasswordStrength() {
        const password = passwordInput.value;
        const strength = checkPasswordStrength(password);
        const strengthBar = strengthIndicator.querySelector('.strength-bar');
        const strengthText = strengthIndicator.querySelector('.strength-text');

        strengthBar.classList.remove('weak', 'medium', 'strong');
        
        switch(strength) {
            case 0:
                strengthBar.classList.add('weak');
                strengthText.textContent = 'Password strength: weak';
                break;
            case 1:
                strengthBar.classList.add('medium');
                strengthText.textContent = 'Password strength: medium';
                break;
            case 2:
            case 3:
                strengthBar.classList.add('strong');
                strengthText.textContent = 'Password strength: strong';
                break;
        }
    }

    // Real-time password strength update
    if (passwordInput) {
        passwordInput.addEventListener('input', updatePasswordStrength);
    }

    // Form submission
    if (signupForm) {
        signupForm.addEventListener('submit', function(e) {
            e.preventDefault();

            const email = emailInput.value.trim();
            const username = usernameInput.value.trim();
            const password = passwordInput.value;
            const confirmPassword = confirmPasswordInput.value;

            // Validation
            if (!email || !username || !password || !confirmPassword) {
                showNotification('Please fill in all fields', 'error');
                return;
            }

            if (!validateEmail(email)) {
                showNotification('Please enter a valid email address', 'error');
                return;
            }

            if (username.length < 3) {
                showNotification('Username must be at least 3 characters', 'error');
                return;
            }

            if (password.length < 8) {
                showNotification('Password must be at least 8 characters', 'error');
                return;
            }

            if (password !== confirmPassword) {
                showNotification('Passwords do not match', 'error');
                return;
            }

            // Send signup request
            try {
                console.log('→ signup request', { email, username });
                chrome.runtime.sendMessage({
                    action: 'signup',
                    email: email,
                    username: username,
                    password: password
                }, function(response) {
                    console.log('← signup response', response);
                    if (response && response.success) {
                        showNotification('Signup successful! Redirecting to login...', 'success');
                        setTimeout(() => {
                            window.location.href = 'login.html';
                        }, 1200);
                    } else {
                        showNotification(response?.error || 'Signup failed', 'error');
                    }
                });
            } catch (err) {
                console.error('signup sendMessage error', err);
                showNotification('Extension error: ' + err.message, 'error');
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

function validateEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

function showNotification(message, type = 'info') {
    console.log(`[${type.toUpperCase()}] ${message}`);
    alert(message);
}
