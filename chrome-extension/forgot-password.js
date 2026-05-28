// Forgot Password page functionality

document.addEventListener('DOMContentLoaded', function() {
    const emailStep = document.getElementById('emailStep');
    const verifyStep = document.getElementById('verifyStep');
    const resetStep = document.getElementById('resetStep');
    
    const sendCodeBtn = document.getElementById('sendCodeBtn');
    const verifyCodeBtn = document.getElementById('verifyCodeBtn');
    const resetPasswordBtn = document.getElementById('resetPasswordBtn');
    const backToEmailBtn = document.getElementById('backToEmailBtn');
    const backToLoginBtn = document.getElementById('backToLoginBtn');
    
    const emailInput = document.getElementById('email');
    const verificationCodeInput = document.getElementById('verificationCode');
    const newPasswordInput = document.getElementById('newPassword');
    const confirmNewPasswordInput = document.getElementById('confirmNewPassword');
    const strengthIndicator = document.getElementById('strengthIndicator');

    let userEmail = '';

    // Step 1: Send Recovery Code
    if (sendCodeBtn) {
        sendCodeBtn.addEventListener('click', function() {
            const email = emailInput.value.trim();

            if (!email || !validateEmail(email)) {
                showNotification('Please enter a valid email address', 'error');
                return;
            }

            userEmail = email;

            try {
                console.log('→ sendRecoveryCode', { email });
                chrome.runtime.sendMessage({ action: 'sendRecoveryCode', email: email }, function(response) {
                    console.log('← sendRecoveryCode response', response);
                    if (response && response.success) {
                        showNotification('Recovery code sent to your email!', 'success');
                        transitionToStep('verify');
                    } else {
                        showNotification(response?.error || 'Failed to send recovery code', 'error');
                    }
                });
            } catch (err) {
                console.error('sendRecoveryCode error', err);
                showNotification('Extension error: ' + err.message, 'error');
            }
        });
    }

    // Step 2: Verify Code
    if (verifyCodeBtn) {
        verifyCodeBtn.addEventListener('click', function() {
            const code = verificationCodeInput.value.trim();

            if (code.length !== 6 || !/^\d+$/.test(code)) {
                showNotification('Please enter a valid 6-digit code', 'error');
                return;
            }

            try {
                console.log('→ verifyRecoveryCode', { email: userEmail, code });
                chrome.runtime.sendMessage({ action: 'verifyRecoveryCode', email: userEmail, code: code }, function(response) {
                    console.log('← verifyRecoveryCode response', response);
                    if (response && response.success) {
                        showNotification('Code verified!', 'success');
                        transitionToStep('reset');
                    } else {
                        showNotification(response?.error || 'Invalid verification code', 'error');
                    }
                });
            } catch (err) {
                console.error('verifyRecoveryCode error', err);
                showNotification('Extension error: ' + err.message, 'error');
            }
        });
    }

    // Step 3: Reset Password
    if (resetPasswordBtn) {
        resetPasswordBtn.addEventListener('click', function() {
            const newPassword = newPasswordInput.value;
            const confirmNewPassword = confirmNewPasswordInput.value;

            if (!newPassword || !confirmNewPassword) {
                showNotification('Please fill in all password fields', 'error');
                return;
            }

            if (newPassword.length < 8) {
                showNotification('Password must be at least 8 characters', 'error');
                return;
            }

            if (newPassword !== confirmNewPassword) {
                showNotification('Passwords do not match', 'error');
                return;
            }

            try {
                console.log('→ resetPassword', { email: userEmail });
                chrome.runtime.sendMessage({ action: 'resetPassword', email: userEmail, newPassword: newPassword }, function(response) {
                    console.log('← resetPassword response', response);
                    if (response && response.success) {
                        showNotification('Password reset successful! Redirecting to login...', 'success');
                        setTimeout(() => {
                            window.location.href = 'login.html';
                        }, 1200);
                    } else {
                        showNotification(response?.error || 'Password reset failed', 'error');
                    }
                });
            } catch (err) {
                console.error('resetPassword error', err);
                showNotification('Extension error: ' + err.message, 'error');
            }
        });
    }

    // Back buttons
    if (backToEmailBtn) {
        backToEmailBtn.addEventListener('click', function() {
            transitionToStep('email');
        });
    }

    if (backToLoginBtn) {
        backToLoginBtn.addEventListener('click', function() {
            window.location.href = 'login.html';
        });
    }

    // Password strength checker
    function checkPasswordStrength(password) {
        let strength = 0;
        
        if (password.length >= 8) strength++;
        if (password.length >= 12) strength++;
        if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++;
        if (/[0-9]/.test(password)) strength++;
        if (/[^a-zA-Z0-9]/.test(password)) strength++;

        return Math.min(strength, 3);
    }

    function updatePasswordStrength() {
        const password = newPasswordInput.value;
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
    if (newPasswordInput) {
        newPasswordInput.addEventListener('input', updatePasswordStrength);
    }

    // Transition between steps
    function transitionToStep(step) {
        emailStep.style.display = step === 'email' ? 'block' : 'none';
        verifyStep.style.display = step === 'verify' ? 'block' : 'none';
        resetStep.style.display = step === 'reset' ? 'block' : 'none';

        // Clear inputs when transitioning
        if (step === 'verify') {
            verificationCodeInput.value = '';
            verificationCodeInput.focus();
        } else if (step === 'reset') {
            newPasswordInput.value = '';
            confirmNewPasswordInput.value = '';
            newPasswordInput.focus();
        }
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

    // Allow numeric input only for verification code
    if (verificationCodeInput) {
        verificationCodeInput.addEventListener('input', function(e) {
            this.value = this.value.replace(/[^0-9]/g, '').slice(0, 6);
        });
    }
});

function validateEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

function showNotification(message, type = 'info') {
    console.log(`[${type.toUpperCase()}] ${message}`);
    alert(message);
}
