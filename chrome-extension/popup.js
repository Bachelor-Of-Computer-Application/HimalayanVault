/**
 * Himalayan Vault Extension - Popup UI Logic
 */

const API_BASE = 'http://127.0.0.1:8443';

// DOM Elements
const loginPanel = document.getElementById('loginPanel');
const mainPanel = document.getElementById('mainPanel');
const loginForm = document.getElementById('loginForm');
const logoutBtn = document.getElementById('logoutBtn');
const credentialsList = document.getElementById('credentialsList');
const generateBtn = document.getElementById('generateBtn');
const newCredForm = document.getElementById('newCredForm');

function showSuccessNotification(message) {
  showPopupNotification(message, false);
}

function showErrorNotification(message) {
  showPopupNotification(message, true);
}

function showPopupNotification(message, isError) {
  const feedback = document.createElement('div');
  feedback.className = isError ? 'copy-feedback copy-feedback-error' : 'copy-feedback';
  feedback.textContent = ' ' + message;
  document.body.appendChild(feedback);
  setTimeout(() => feedback.remove(), 2000);
}

/**
 * Check if API server is running
 */
async function checkApiServer() {
  try {
    const response = await fetch(`${API_BASE}/health`, {
      method: 'GET',
      timeout: 3000
    });
    const data = await response.json();
    return data.status === true;
  } catch (error) {
    console.warn('API server not responding:', error.message);
    return false;
  }
}

function normalizeSiteUrl(siteUrl) {
  const value = String(siteUrl || '').trim();
  if (!value) {
    return '';
  }

  try {
    return new URL(value).origin;
  } catch (error) {
    try {
      return new URL(`https://${value}`).origin;
    } catch (innerError) {
      return value.replace(/\/+$/, '');
    }
  }
}

async function getActiveTabSiteUrl() {
  try {
    const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    const tab = tabs && tabs[0];
    if (!tab || !tab.url) {
      return '';
    }

    return normalizeSiteUrl(tab.url);
  } catch (error) {
    console.warn('Unable to resolve active tab URL:', error.message);
    return '';
  }
}

async function prefillSiteUrl() {
  const siteUrlInput = document.getElementById('siteUrl');
  if (!siteUrlInput || siteUrlInput.value) {
    return;
  }

  const activeSiteUrl = await getActiveTabSiteUrl();
  if (activeSiteUrl) {
    siteUrlInput.value = activeSiteUrl;
  }
}

// Initialize on load
document.addEventListener('DOMContentLoaded', async () => {
  console.log('Popup initialized');

  // Apply dark theme if system prefers it
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    document.documentElement.setAttribute('data-theme', 'dark');
  }

  // Check if API server is running
  const apiRunning = await checkApiServer();
  if (!apiRunning) {
    console.warn(' API server is not running');
    const errorDiv = document.getElementById('loginError');
    if (errorDiv) {
      errorDiv.innerHTML = ' <strong>API Server Not Running</strong><br><small>Start the Himalayan Vault desktop app or run:<br>java -jar target/himalayan-vault-1.0.0-fat.jar</small>';
      errorDiv.style.display = 'block';
    }
  }

  // Check login status
  const response = await chrome.runtime.sendMessage({ action: 'isLoggedIn' });

  if (response.loggedIn) {
    showMainPanel(response.username);
    loadCredentials();
  } else {
    showLoginPanel();
  }

  prefillSiteUrl();
});

// Logout
if (logoutBtn) {
  logoutBtn.addEventListener('click', async () => {
    if (confirm('Are you sure you want to logout?')) {
      const response = await chrome.runtime.sendMessage({ action: 'logout' });
      if (response.success) {
        showLoginPanel();
        if (credentialsList) credentialsList.innerHTML = '';
      }
    }
  });
}

// Generate password
if (generateBtn) {
  generateBtn.addEventListener('click', async () => {
    const lengthEl = document.getElementById('pwLength');
    const length = lengthEl ? parseInt(lengthEl.value) || 16 : 16;
    const options = {
      length,
      useUppercase: !!(document.getElementById('pwUppercase') && document.getElementById('pwUppercase').checked),
      useLowercase: !!(document.getElementById('pwLowercase') && document.getElementById('pwLowercase').checked),
      useNumbers: !!(document.getElementById('pwNumbers') && document.getElementById('pwNumbers').checked),
      useSpecialChars: !!(document.getElementById('pwSpecialChars') && document.getElementById('pwSpecialChars').checked)
    };

    try {
      const response = await chrome.runtime.sendMessage({
        action: 'generatePassword',
        options
      });

      if (response && response.success) {
        const genEl = document.getElementById('generatedPassword');
        if (genEl) genEl.innerHTML = `<strong>Generated Password:</strong><br><code>${response.password}</code>`;
        copyToClipboard(response.password, 'Password copied!');
      } else {
        alert('Error: ' + (response && response.error));
      }
    } catch (error) {
      alert('Error generating password: ' + error.message);
    }
  });
}

// Quick save from recently captured login
let capturedLoginCredentials = null;

// Login form submission - capture credentials
if (loginForm) {
  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const usernameEl = document.getElementById('username');
    const passwordEl = document.getElementById('password');
    const username = usernameEl ? usernameEl.value : '';
    const password = passwordEl ? passwordEl.value : '';

    capturedLoginCredentials = { username, password };

    const errorDiv = document.getElementById('loginError');
    if (errorDiv) errorDiv.style.display = 'none';

    try {
      const response = await chrome.runtime.sendMessage({ action: 'login', username, password });
      if (response && response.success) {
        if (loginForm.reset) loginForm.reset();
        showMainPanel(username);
        loadCredentials();
      } else {
        if (errorDiv) {
          errorDiv.textContent = response && (response.error || response.message) ? (response.error || response.message) : 'Login failed';
          errorDiv.style.display = 'block';
        }
        capturedLoginCredentials = null;
      }
    } catch (error) {
      if (errorDiv) {
        errorDiv.textContent = 'Extension error: ' + error.message;
        errorDiv.style.display = 'block';
      }
      capturedLoginCredentials = null;
    }
  });
}

// Save new credential
if (newCredForm) {
  newCredForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const errorDiv = document.getElementById('saveError');
    if (errorDiv) errorDiv.style.display = 'none';

    const loginCheck = await chrome.runtime.sendMessage({ action: 'isLoggedIn' });
    if (!loginCheck || !loginCheck.loggedIn) {
      if (errorDiv) {
        errorDiv.textContent = 'You must be logged in to save credentials. Please login first.';
        errorDiv.style.display = 'block';
      }
      return;
    }

    const siteUrlInput = document.getElementById('siteUrl');
    let siteUrl = siteUrlInput ? siteUrlInput.value.trim() : '';
    if (!siteUrl) {
      siteUrl = await getActiveTabSiteUrl();
      if (siteUrlInput && siteUrl) {
        siteUrlInput.value = siteUrl;
      }
    }

    const siteUsername = document.getElementById('siteUsername') ? document.getElementById('siteUsername').value.trim() : '';
    const credential = {
      siteName: document.getElementById('siteName') ? document.getElementById('siteName').value : '',
      siteUrl: normalizeSiteUrl(siteUrl),
      loginIdentifier: siteUsername,
      siteUsername,
      siteEmail: siteUsername.includes('@') ? siteUsername : '',
      encryptedPassword: document.getElementById('sitePassword') ? document.getElementById('sitePassword').value : '',
      notes: document.getElementById('siteNotes') ? document.getElementById('siteNotes').value : '',
      category: document.getElementById('siteCategory') ? document.getElementById('siteCategory').value : '',
      tags: document.getElementById('siteTags') ? document.getElementById('siteTags').value : '',
      favorite: !!(document.getElementById('siteFavorite') && document.getElementById('siteFavorite').checked)
    };

    if (!credential.siteUrl) {
      if (errorDiv) {
        errorDiv.textContent = 'Open the site first or enter the site URL before saving.';
        errorDiv.style.display = 'block';
      }
      return;
    }

    try {
      const response = await chrome.runtime.sendMessage({ action: 'saveCredential', credential });
      if (response && response.success) {
        const successDiv = document.createElement('div');
        successDiv.className = 'success';
        successDiv.textContent = ' Credential saved successfully!';
        if (errorDiv && errorDiv.parentNode) errorDiv.parentNode.insertBefore(successDiv, errorDiv);
        setTimeout(() => successDiv.remove(), 3000);
        if (newCredForm.reset) newCredForm.reset();
        loadCredentials();
      } else {
        if (errorDiv) {
          errorDiv.textContent = 'Error: ' + (response && response.error ? response.error : 'Failed to save credential');
          errorDiv.style.display = 'block';
        }
      }
    } catch (error) {
      if (errorDiv) {
        errorDiv.textContent = 'Error saving credential: ' + error.message;
        errorDiv.style.display = 'block';
      }
    }
  });
}

// Load and display credentials
async function loadCredentials() {
  try {
    const response = await chrome.runtime.sendMessage({
      action: 'getCredentials',
      siteUrl: ''
    });

    if (response.success) {
      displayCredentials(response.credentials);
    } else {
      credentialsList.innerHTML = '<p class="error">' + response.error + '</p>';
    }
  } catch (error) {
    credentialsList.innerHTML = '<p class="error">Error loading credentials</p>';
  }
}

function displayCredentials(credentials) {
  if (!credentials || credentials.length === 0) {
    credentialsList.innerHTML = '<p>No credentials saved yet</p>';
    return;
  }

  credentials.forEach(cred => {
    cred.loginIdentifier = getCredentialLoginIdentifier(cred);
    cred.siteUsername = cred.loginIdentifier;
    if (cred.loginIdentifier.includes('@')) {
      cred.siteEmail = cred.loginIdentifier;
    }
  });

  credentialsList.innerHTML = credentials.map((cred, index) => `
    <div class="credential-item" data-index="${index}" data-credential-id="${cred.id}">
      <h4>${cred.favorite ? '* ' : ''}${escapeHtml(cred.siteName || cred.siteUrl || '')}</h4>
      <p><strong>URL:</strong> ${escapeHtml(cred.siteUrl || '')}</p>
      <p><strong>Username or Email:</strong> <span class="username-value">${escapeHtml(cred.loginIdentifier || '')}</span></p>
      ${cred.category ? `<p><strong>Category:</strong> ${escapeHtml(cred.category)}</p>` : ''}
      ${cred.tags ? `<p><strong>Tags:</strong> ${escapeHtml(cred.tags)}</p>` : ''}
      ${cred.notes ? `<p><strong>Notes:</strong> ${escapeHtml(cred.notes)}</p>` : ''}
      <div class="actions">
        <button class="btn btn-secondary btn-copy" data-username="${escapeHtml(cred.loginIdentifier || '')}">Copy Login</button>
        <button class="btn btn-secondary btn-autofill" data-credential-index="${index}">Autofill</button>
        <button class="btn btn-secondary btn-delete" data-credential-id="${cred.id}">Delete</button>
      </div>
    </div>
  `).join('');

  // Attach event listeners
  document.querySelectorAll('.btn-copy').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const username = btn.dataset.username;
      copyToClipboard(username, 'Username copied!');
    });
  });

  document.querySelectorAll('.btn-autofill').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const index = btn.dataset.credentialIndex;
      const visibleUsername = btn.closest('.credential-item')?.querySelector('.username-value')?.textContent.trim() || '';
      if (visibleUsername) {
        credentials[index].loginIdentifier = visibleUsername;
        credentials[index].siteUsername = visibleUsername;
        if (visibleUsername.includes('@')) {
          credentials[index].siteEmail = visibleUsername;
        }
      }
      autofillPage(credentials[index], visibleUsername);
    });
  });

  document.querySelectorAll('.btn-delete').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const credId = btn.dataset.credentialId;
      deleteCredential(credId);
    });
  });
}

function escapeHtml(text) {
  text = String(text || '');
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, m => map[m]);
}

function getCredentialLoginIdentifier(credential) {
  return String(
    credential.loginIdentifier
    || credential.siteUsername
    || credential.site_username
    || credential.siteEmail
    || credential.site_email
    || credential.username
    || credential.email
    || credential.emailAddress
    || credential.email_address
    || credential.login
    || credential.identifier
    || ''
  ).trim();
}

function copyToClipboard(text, message = 'Copied to clipboard!') {
  navigator.clipboard.writeText(text).then(() => {
    setTimeout(() => {
      navigator.clipboard.readText()
        .then(current => {
          if (current === text) {
            return navigator.clipboard.writeText('');
          }
          return null;
        })
        .catch(() => { });
    }, 30000);

    // Show success feedback
    const feedback = document.createElement('div');
    feedback.className = 'copy-feedback';
    feedback.textContent = ' ' + message;
    document.body.appendChild(feedback);
    setTimeout(() => feedback.remove(), 2000);
  }).catch(err => {
    console.error('Copy failed:', err);
  });
}

async function autofillPage(credential, identifierOverride = '') {
  // Get the active tab
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });

  if (!tabs[0]) {
    alert('Could not access active tab');
    return;
  }

  const tab = tabs[0];
  const tabUrl = tab.url || '';

  if (/^(chrome|edge|about|chrome-extension):\/\//i.test(tabUrl)) {
    alert('Autofill cannot run on browser/system pages. Open the website login page and try again.');
    return;
  }

  try {
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      world: 'MAIN',
      args: [credential, identifierOverride],
      func: (credentialToFill, directIdentifier) => {
        const getIdentifier = (credential) => credential?.loginIdentifier
          || credential?.login_identifier
          || credential?.siteUsername
          || credential?.site_username
          || credential?.siteEmail
          || credential?.site_email
          || credential?.username
          || credential?.email
          || credential?.emailAddress
          || credential?.email_address
          || credential?.login
          || credential?.identifier
          || '';

        const isVisible = (input) => {
          if (!input) return false;
          const style = window.getComputedStyle(input);
          const rect = input.getBoundingClientRect();
          return !input.disabled
            && !input.readOnly
            && style.display !== 'none'
            && style.visibility !== 'hidden'
            && rect.width > 0
            && rect.height > 0;
        };

        const setValue = (input, value) => {
          if (!input || !value) return false;
          input.focus();
          input.select?.();
          if (document.execCommand && document.execCommand('insertText', false, value)) {
            input.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
          }

          const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
          if (setter) {
            setter.call(input, value);
          } else {
            input.value = value;
          }
          input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
          input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: value[value.length - 1] || '' }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
          input.blur();
          return true;
        };

        const identifier = directIdentifier || getIdentifier(credentialToFill);
        const password = credentialToFill?.encryptedPassword || credentialToFill?.encrypted_password || credentialToFill?.password || '';
        const passwordField = [...document.querySelectorAll('input[type="password"], input[name*="pass" i], input[id*="pass" i]')]
          .find(isVisible);

        const identifierSelectors = [
          'input#email',
          'input[name="email" i]',
          'input[type="email"]',
          'input[autocomplete*="email" i]',
          'input[autocomplete*="username" i]',
          'input[name*="email" i]',
          'input[id*="email" i]',
          'input[placeholder*="email" i]',
          'input[name*="user" i]',
          'input[id*="user" i]',
          'input[placeholder*="user" i]',
          'input[name*="login" i]',
          'input[id*="login" i]',
          'input[placeholder*="login" i]'
        ];

        const scopedRoots = [
          passwordField?.closest('form'),
          passwordField?.closest('[role="form"]'),
          document
        ].filter(Boolean);
        const candidates = [];
        const seen = new Set();

        for (const root of scopedRoots) {
          for (const selector of identifierSelectors) {
            for (const input of root.querySelectorAll(selector)) {
              if (input !== passwordField && isVisible(input) && !seen.has(input)) {
                candidates.push(input);
                seen.add(input);
              }
            }
          }

          if (passwordField) {
            const passwordRect = passwordField.getBoundingClientRect();
            const inputsAbovePassword = [...root.querySelectorAll('input')]
              .filter(input => {
                if (input === passwordField || !isVisible(input)) return false;
                const type = (input.getAttribute('type') || 'text').toLowerCase();
                if (['password', 'hidden', 'submit', 'button', 'checkbox', 'radio', 'file'].includes(type)) return false;
                return input.getBoundingClientRect().top <= passwordRect.top + 8;
              })
              .sort((a, b) => b.getBoundingClientRect().top - a.getBoundingClientRect().top);

            for (const input of inputsAbovePassword) {
              if (!seen.has(input)) {
                candidates.push(input);
                seen.add(input);
              }
            }
          }

          if (candidates.length > 0 && root !== document) break;
        }

        const fillAll = () => {
          const directEmailField = document.querySelector(
            'input#email, input[name="email" i], input[type="email"], input[autocomplete*="email" i], input[placeholder*="email" i]'
          );
          if (directEmailField && isVisible(directEmailField)) {
            candidates.unshift(directEmailField);
          }

          if (passwordField) {
            const passwordTop = passwordField.getBoundingClientRect().top;
            const nearestTextInputs = [...document.querySelectorAll('input')]
              .filter(input => {
                if (input === passwordField || !isVisible(input)) return false;
                const type = (input.getAttribute('type') || 'text').toLowerCase();
                return !['password', 'hidden', 'submit', 'button', 'checkbox', 'radio', 'file'].includes(type)
                  && input.getBoundingClientRect().top <= passwordTop + 8;
              })
              .sort((a, b) => b.getBoundingClientRect().top - a.getBoundingClientRect().top);
            candidates.unshift(...nearestTextInputs);
          }

          const uniqueCandidates = [...new Set(candidates)];
          uniqueCandidates.forEach(input => setValue(input, identifier));
          if (passwordField) setValue(passwordField, password);
          return uniqueCandidates.length;
        };

        const filledIdentifierCount = fillAll();
        setTimeout(fillAll, 150);
        setTimeout(fillAll, 500);

        const notification = document.createElement('div');
        notification.textContent = candidates.length > 0
          ? 'Login details filled!'
          : 'Password filled, but no email/username field was found.';
        notification.style.cssText = 'position:fixed;z-index:2147483647;right:16px;top:16px;padding:10px 12px;background:#111827;color:#fff;border-radius:6px;font:13px system-ui,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,.24)';
        document.body.appendChild(notification);
        setTimeout(() => notification.remove(), 3000);

        return {
          filledIdentifier: filledIdentifierCount > 0,
          filledPassword: !!passwordField,
          identifier,
          identifierSelectors: [...new Set(candidates)].map(input => `${input.tagName.toLowerCase()}#${input.id || ''}[name="${input.name || ''}"][type="${input.type || ''}"]`)
        };
      }
    });

    // Show success message
    const feedback = document.createElement('div');
    feedback.className = 'copy-feedback';
    const fillResult = results && results[0] ? results[0].result : null;
    feedback.textContent = fillResult && fillResult.filledIdentifier
      ? ' Credential auto-filled!'
      : ' Password filled; email field not found.';
    document.body.appendChild(feedback);
    setTimeout(() => feedback.remove(), 2000);
  } catch (error) {
    if (/receiving end does not exist/i.test(error.message || '')) {
      try {
        await chrome.scripting.executeScript({
          target: { tabId: tab.id },
          files: ['content.js']
        });

        const response = await chrome.tabs.sendMessage(tab.id, {
          action: 'autofill',
          credential: {
            ...credential,
            loginIdentifier: identifierOverride || getCredentialLoginIdentifier(credential),
            siteUsername: identifierOverride || credential.siteUsername || getCredentialLoginIdentifier(credential)
          }
        });

        if (response && response.success) {
          showSuccessNotification(response.filledPassword ? 'Credential auto-filled!' : 'Username filled; password field not found.');
          return;
        }
      } catch (retryError) {
        alert('Autofill failed after reloading the content script: ' + retryError.message);
        return;
      }
    }

    alert('Autofill failed: ' + error.message);
  }
}

async function deleteCredential(credentialId) {
  if (confirm('Are you sure you want to delete this credential?')) {
    const response = await chrome.runtime.sendMessage({
      action: 'deleteCredential',
      credentialId
    });

    if (response.success) {
      loadCredentials();
    } else {
      alert('Error: ' + response.error);
    }
  }
}

function showLoginPanel() {
  loginPanel.style.display = 'block';
  mainPanel.style.display = 'none';
}

function showMainPanel(username) {
  loginPanel.style.display = 'none';
  mainPanel.style.display = 'block';
  document.querySelector('.header h2').textContent = `Welcome, ${username}!`;
}

// Auto-focus input fields on hover
const inputFields = document.querySelectorAll('.form-group-line');
inputFields.forEach(field => {
  field.addEventListener('mouseenter', function () {
    const input = this.querySelector('input');
    if (input) {
      input.focus();
    }
  });
});
