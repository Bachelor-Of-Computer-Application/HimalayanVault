// Forgot Password page functionality

document.addEventListener('DOMContentLoaded', function() {
    const step1 = document.getElementById('step1');
    const step2 = document.getElementById('step2');
    const step3 = document.getElementById('step3');

    const step1NextBtn = document.getElementById('step1NextBtn');
    const step2NextBtn = document.getElementById('step2NextBtn');
    const step2BackBtn = document.getElementById('step2BackBtn');
    const step3BackBtn = document.getElementById('step3BackBtn');
    const completeResetBtn = document.getElementById('completeResetBtn');

    const usernameInput = document.getElementById('username');
    const recoveryWordsInput = document.getElementById('recoveryWords');
    const newPasswordInput = document.getElementById('newPassword');
    const confirmNewPasswordInput = document.getElementById('confirmNewPassword');
    const strengthIndicator = document.getElementById('strengthIndicator');

    let currentUsername = '';

    // Step 1: Verify recovery words (username + 16 words)
    if (step1NextBtn) {
        step1NextBtn.addEventListener('click', function() {
            const username = usernameInput.value.trim();
            const words = recoveryWordsInput.value.trim().toLowerCase();

            if (!username) {
                showNotification('Please enter your username', 'error');
                return;
            }
            if (!words) {
                showNotification('Please enter your 16 recovery words', 'error');
                return;
            }

            const arr = words.split(/\s+/).filter(Boolean);
            if (arr.length !== 16) {
                showNotification('Please enter exactly 16 recovery words', 'error');
                return;
            }

            try {
                console.log('→ verifyRecoveryMnemonic', { username, words: arr });
                chrome.runtime.sendMessage({ action: 'verifyRecoveryMnemonic', username: username, words: arr }, function(response) {
                    console.log('← verifyRecoveryMnemonic', response);
                    if (response && response.success) {
                        currentUsername = username;
                        showNotification('Recovery words verified', 'success');
                        transitionToStep('step2');
                    } else {
                        showNotification(response?.error || 'Recovery words verification failed', 'error');
                    }
                });
            } catch (err) {
                console.error('verifyRecoveryMnemonic error', err);
                showNotification('Extension error: ' + err.message, 'error');
            }
        });
    }

    // Step 2: Set New Password
    if (step2NextBtn) {
        step2NextBtn.addEventListener('click', function() {
            const pwd = newPasswordInput.value;
            const con = confirmNewPasswordInput.value;

            if (!pwd) { showNotification('Please enter a new password', 'error'); return; }
            if (pwd.length < 12) { showNotification('Password must be at least 12 characters', 'error'); return; }
            if (pwd !== con) { showNotification('Passwords do not match', 'error'); return; }

            // move to confirmation
            const short = pwd.substring(0, Math.min(3, pwd.length)) + '***';
            const msg = document.getElementById('confirmationMsg');
            if (msg) msg.textContent = 'New password: ' + short;
            transitionToStep('step3');
        });
    }

    if (step2BackBtn) step2BackBtn.addEventListener('click', () => transitionToStep('step1'));

    // Step 3: Complete reset by calling background handler
    if (completeResetBtn) {
        completeResetBtn.addEventListener('click', function() {
            const newPassword = newPasswordInput.value;
            try {
                console.log('→ resetPasswordWithRecovery', { username: currentUsername });
                chrome.runtime.sendMessage({ action: 'resetPasswordWithRecovery', username: currentUsername, newPassword }, function(response) {
                    console.log('← resetPasswordWithRecovery', response);
                    if (response && response.success) {
                        showNotification('Password reset successful! Redirecting to login...', 'success');
                        setTimeout(() => { window.location.href = 'login.html'; }, 1200);
                    } else {
                        showNotification(response?.error || 'Password reset failed', 'error');
                    }
                });
            } catch (err) {
                console.error('resetPasswordWithRecovery error', err);
                showNotification('Extension error: ' + err.message, 'error');
            }
        });
    }

    if (step3BackBtn) step3BackBtn.addEventListener('click', () => transitionToStep('step2'));

    // Password strength checker
    function checkPasswordStrength(password) {
        let sc = 0;
        if (password.length >= 8) sc++;
        if (password.length >= 12) sc++;
        if (/[A-Z]/.test(password) && /[a-z]/.test(password) && /[0-9]/.test(password)) sc++;
        if (/[^A-Za-z0-9]/.test(password)) sc++;
        return Math.min(sc, 3);
    }

    function updatePasswordStrength() {
        const password = newPasswordInput.value;
        const strength = checkPasswordStrength(password);
        const bar = strengthIndicator.querySelector('.strength-bar');
        const text = strengthIndicator.querySelector('.strength-text');
        bar.classList.remove('weak','medium','strong');
        if (strength === 0) { bar.classList.add('weak'); text.textContent = 'Password strength: weak'; }
        else if (strength === 1) { bar.classList.add('medium'); text.textContent = 'Password strength: medium'; }
        else { bar.classList.add('strong'); text.textContent = 'Password strength: strong'; }
    }

    // Real-time password strength update
    if (newPasswordInput) {
        newPasswordInput.addEventListener('input', updatePasswordStrength);
    }

    // Transition between steps (step1, step2, step3)
    function transitionToStep(step) {
        step1.style.display = step === 'step1' ? 'block' : 'none';
        step2.style.display = step === 'step2' ? 'block' : 'none';
        step3.style.display = step === 'step3' ? 'block' : 'none';

        if (step === 'step2') {
            newPasswordInput.value = '';
            confirmNewPasswordInput.value = '';
            newPasswordInput.focus();
        }
        if (step === 'step3') {
            // nothing
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

    transitionToStep('step1');
});

function validateEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

function showNotification(message, type = 'info') {
    console.log(`[${type.toUpperCase()}] ${message}`);
    alert(message);
}
