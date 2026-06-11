// Signup page — creates vault via API and shows recovery words before login

document.addEventListener('DOMContentLoaded', function() {
    const signupForm = document.getElementById('signupForm');
    const recoveryStep = document.getElementById('recoveryStep');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    const strengthIndicator = document.getElementById('strengthIndicator');
    const signupError = document.getElementById('signupError');
    const signupBtn = document.getElementById('signupBtn');
    const recoveryWordsGrid = document.getElementById('recoveryWordsGrid');
    const copyRecoveryBtn = document.getElementById('copyRecoveryBtn');
    const recoverySavedCheck = document.getElementById('recoverySavedCheck');
    const continueToLoginBtn = document.getElementById('continueToLoginBtn');
    const recoveryError = document.getElementById('recoveryError');

    let recoveryWords = [];

    function meetsPasswordRequirements(password) {
        return password.length >= 12
            && /[A-Z]/.test(password)
            && /[a-z]/.test(password)
            && /[0-9]/.test(password)
            && /[^A-Za-z0-9]/.test(password);
    }

    const RULE_LABELS = {
        ruleLength: '12+ characters',
        ruleCase: 'Upper and lower case',
        ruleNumber: 'Contains a number',
        ruleSpecial: 'Contains a special character'
    };

    function updatePasswordRules(password) {
        setRuleState('ruleLength', password.length >= 12);
        setRuleState('ruleCase', /[A-Z]/.test(password) && /[a-z]/.test(password));
        setRuleState('ruleNumber', /[0-9]/.test(password));
        setRuleState('ruleSpecial', /[^A-Za-z0-9]/.test(password));
    }

    function setRuleState(id, met) {
        const el = document.getElementById(id);
        if (!el) return;
        el.classList.toggle('met', met);
        el.textContent = (met ? '✓ ' : '○ ') + RULE_LABELS[id];
    }

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
        const password = passwordInput.value;
        updatePasswordRules(password);

        const strength = checkPasswordStrength(password);
        const strengthBar = strengthIndicator.querySelector('.strength-bar');
        const strengthText = strengthIndicator.querySelector('.strength-text');

        strengthBar.classList.remove('weak', 'medium', 'strong');

        if (strength <= 1) {
            strengthBar.classList.add('weak');
            strengthText.textContent = 'Password strength: weak';
        } else if (strength === 2) {
            strengthBar.classList.add('medium');
            strengthText.textContent = 'Password strength: medium';
        } else {
            strengthBar.classList.add('strong');
            strengthText.textContent = 'Password strength: strong';
        }
    }

    function showError(el, message) {
        el.textContent = message;
        el.hidden = !message;
    }

    function setLoading(loading) {
        signupBtn.disabled = loading;
        signupBtn.textContent = loading ? 'CREATING VAULT...' : 'SIGN UP';
    }

    function renderRecoveryWords(words) {
        recoveryWordsGrid.innerHTML = '';
        words.forEach((word, index) => {
            const cell = document.createElement('div');
            cell.className = 'recovery-word-cell';
            cell.innerHTML = `<span class="recovery-word-num">${index + 1}.</span><span class="recovery-word-val">${word}</span>`;
            recoveryWordsGrid.appendChild(cell);
        });
    }

    function showRecoveryStep(words) {
        recoveryWords = words;
        renderRecoveryWords(words);
        signupForm.hidden = true;
        recoveryStep.hidden = false;
    }

    if (passwordInput) {
        passwordInput.addEventListener('input', updatePasswordStrength);
        updatePasswordRules('');
    }

    if (recoverySavedCheck) {
        recoverySavedCheck.addEventListener('change', () => {
            continueToLoginBtn.disabled = !recoverySavedCheck.checked;
            showError(recoveryError, '');
        });
    }

    if (copyRecoveryBtn) {
        copyRecoveryBtn.addEventListener('click', async () => {
            if (!recoveryWords.length) return;
            const text = recoveryWords.join(' ');
            try {
                await navigator.clipboard.writeText(text);
                copyRecoveryBtn.textContent = 'COPIED!';
                setTimeout(() => { copyRecoveryBtn.textContent = 'COPY WORDS'; }, 1500);
            } catch (err) {
                showError(recoveryError, 'Could not copy to clipboard. Please copy manually.');
            }
        });
    }

    if (continueToLoginBtn) {
        continueToLoginBtn.addEventListener('click', () => {
            if (!recoverySavedCheck.checked) {
                showError(recoveryError, 'Please confirm you have saved the recovery words.');
                return;
            }
            window.location.href = 'login.html';
        });
    }

    if (signupForm) {
        signupForm.addEventListener('submit', function(e) {
            e.preventDefault();
            showError(signupError, '');

            const username = usernameInput.value.trim();
            const password = passwordInput.value;
            const confirmPassword = confirmPasswordInput.value;

            if (!username || !password || !confirmPassword) {
                showError(signupError, 'Please fill in all fields.');
                return;
            }

            if (username.length < 3) {
                showError(signupError, 'Username must be at least 3 characters.');
                return;
            }

            if (!meetsPasswordRequirements(password)) {
                showError(signupError, 'Password must be 12+ characters with upper, lower, number, and special character.');
                return;
            }

            if (password !== confirmPassword) {
                showError(signupError, 'Passwords do not match.');
                return;
            }

            setLoading(true);

            chrome.runtime.sendMessage({
                action: 'signup',
                username,
                password
            }, function(response) {
                setLoading(false);

                if (chrome.runtime.lastError) {
                    showError(signupError, 'Extension error: ' + chrome.runtime.lastError.message);
                    return;
                }

                if (response && response.success) {
                    const words = response.recoveryWords || [];
                    if (words.length === 16) {
                        showRecoveryStep(words);
                    } else {
                        showError(signupError, 'Signup succeeded but recovery words were not returned. Check the desktop app.');
                    }
                } else {
                    showError(signupError, response?.error || response?.message || 'Signup failed.');
                }
            });
        });
    }
});
