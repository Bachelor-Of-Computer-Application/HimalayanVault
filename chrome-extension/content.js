/**
 * Himalayan Vault - Content Script
 * Auto-detects login attempts and manages credential auto-fill
 * Works like Google Password Manager
 */

let capturedCredentials = null;
let lastFormTimestamp = 0;

function normalizeSiteUrl(siteUrl) {
  const value = String(siteUrl || '').trim();
  if (!value) {
    return '';
  }

  try {
    return new URL(value).origin;
  } catch (error) {
    return value.replace(/\/+$/, '');
  }
}

function getCurrentSiteSearchTerms() {
  const hostname = window.location.hostname.replace(/^www\./, '');
  const origin = window.location.origin ? normalizeSiteUrl(window.location.origin) : '';

  return [...new Set([origin, hostname].filter(Boolean))];
}

// Listen for messages from popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'autofill') {
    autofillCredentials(request.credential);
    sendResponse({ success: true });
  }
});

/**
 * Initialize form detection when page loads
 */
function initializeFormDetection() {
  // Detect form submissions
  document.addEventListener('submit', handleFormSubmit, true);

  // Detect login button clicks
  detectLoginButtons();

  // Add focus listeners to show credential popups
  initializeFormFields();
}

/**
 * Handle form submission - capture credentials
 */
function handleFormSubmit(e) {
  // Get the form
  const form = e.target;
  if (!form || form.tagName !== 'FORM') return;

  // Extract credentials from the form
  const credentials = extractFormCredentials(form);

  if (credentials && credentials.username && credentials.password) {
    capturedCredentials = credentials;
  }
}

/**
 * Detect login buttons and monitor for success
 */
function detectLoginButtons() {
  const buttons = document.querySelectorAll(
    'button[type="submit"], button[type="button"], input[type="submit"], ' +
    'a[onclick*="login"], a[onclick*="signin"], button[onclick*="login"]'
  );

  buttons.forEach(btn => {
    const text = (btn.textContent || btn.value || '').toLowerCase();
    if (text.includes('login') || text.includes('signin') || text.includes('submit')) {
      btn.addEventListener('click', () => {
        // Capture credentials from nearby form
        const form = btn.closest('form') || document.querySelector('form');
        if (form) {
          const credentials = extractFormCredentials(form);
          if (credentials && credentials.username && credentials.password) {
            capturedCredentials = credentials;
          }
        }
      });
    }
  });
}

/**
 * Extract credentials from a form
 */
function extractFormCredentials(form) {
  if (!form) {
    return null;
  }

  const usernameField = form.querySelector(
    'input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[name*="email"]'
  );
  const passwordField = form.querySelector('input[type="password"]');

  if (!passwordField) {
    return null;
  }

  return {
    username: usernameField ? usernameField.value.trim() : '',
    password: passwordField.value,
    siteUrl: normalizeSiteUrl(window.location.origin)
  };
}

function showNotification(message, isError) {
  const notification = document.createElement('div');
  notification.className = isError ? 'hv-error-notification' : 'hv-success-notification';
  notification.textContent = (isError ? ' ' : ' ') + message;
  document.body.appendChild(notification);

  setTimeout(() => {
    notification.classList.add('hv-notification-show');
  }, 10);

  setTimeout(() => {
    notification.classList.remove('hv-notification-show');
    setTimeout(() => notification.remove(), 300);
  }, 3000);
}

function showSuccessNotification(message) {
  showNotification(message, false);
}

function showErrorNotification(message) {
  showNotification(message, true);
}

/**
 * Find all password and username input fields and add listeners
 */
function initializeFormFields() {
  // Find password inputs
  const passwordInputs = document.querySelectorAll('input[type="password"]');
  passwordInputs.forEach(input => {
    if (!input.hasAttribute('data-hv-listener')) {
      input.setAttribute('data-hv-listener', 'true');
      input.addEventListener('focus', () => showCredentialsPopup(input));
    }
  });

  // Find username and email inputs
  const usernameInputs = document.querySelectorAll(
    'input[autocomplete="email"], ' +
    'input[autocomplete="username"], ' +
    'input[type="email"], ' +
    'input[name*="email" i], ' +
    'input[id*="email" i], ' +
    'input[placeholder*="email" i], ' +
    'input[name*="user" i], ' +
    'input[name*="login" i], ' +
    'input[name*="username" i], ' +
    'input[id*="user" i], ' +
    'input[id*="login" i], ' +
    'input[id*="username" i], ' +
    'input[placeholder*="user" i], ' +
    'input[placeholder*="login" i], ' +
    'input[placeholder*="username" i]'
  );
  usernameInputs.forEach(input => {
    if (!input.hasAttribute('data-hv-listener')) {
      input.setAttribute('data-hv-listener', 'true');
      input.addEventListener('focus', () => showCredentialsPopup(input));
    }
  });
}

function isVisibleAndEnabled(input) {
  if (!input) {
    return false;
  }

  const style = window.getComputedStyle(input);
  return !input.disabled && !input.readOnly && style.display !== 'none' && style.visibility !== 'hidden';
}

function fillInput(input, value) {
  if (!input || !value) {
    return false;
  }

  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
}

function getIdentifierFieldCandidates() {
  return [
    'input[autocomplete="email"]',
    'input[type="email"]',
    'input[name*="email" i]',
    'input[id*="email" i]',
    'input[placeholder*="email" i]',
    'input[autocomplete="username"]',
    'input[name*="user" i]',
    'input[name*="login" i]',
    'input[name*="username" i]',
    'input[id*="user" i]',
    'input[id*="login" i]',
    'input[id*="username" i]',
    'input[placeholder*="user" i]',
    'input[type="text"]'
  ];
}

function findIdentifierField() {
  const selectors = getIdentifierFieldCandidates();

  for (const selector of selectors) {
    const fields = document.querySelectorAll(selector);
    for (const field of fields) {
      if (isVisibleAndEnabled(field)) {
        return field;
      }
    }
  }

  return null;
}

/**
 * Show popup with saved credentials when field is focused
 */
function showCredentialsPopup(inputField) {
  // Remove any existing popup
  const existingPopup = document.querySelector('.hv-credentials-popup');
  if (existingPopup) existingPopup.remove();

  const siteSearchTerms = getCurrentSiteSearchTerms();

  const requestCredentials = (index) => {
    if (index >= siteSearchTerms.length) {
      return;
    }

    chrome.runtime.sendMessage(
      { action: 'getCredentials', siteUrl: siteSearchTerms[index] },
      (response) => {
        if (response && response.success && response.credentials && response.credentials.length > 0) {
          const popup = document.createElement('div');
          popup.className = 'hv-credentials-popup';
          popup.innerHTML = `
            <div class="hv-popup-content">
              <div class="hv-popup-header">Saved Passwords</div>
              <div class="hv-popup-list">
                ${response.credentials.map((cred, idx) => `
                  <div class="hv-popup-item" data-index="${idx}" data-username="${cred.siteUsername}">
                    <span class="hv-popup-username">${cred.siteUsername}</span>
                    <button class="hv-popup-fill">Use</button>
                  </div>
                `).join('')}
              </div>
              <div class="hv-popup-footer">Himalayan Vault</div>
            </div>
          `;

          const rect = inputField.getBoundingClientRect();
          popup.style.top = (rect.bottom + window.scrollY + 5) + 'px';
          popup.style.left = (rect.left + window.scrollX) + 'px';

          document.body.appendChild(popup);

          popup.querySelectorAll('.hv-popup-fill').forEach(btn => {
            btn.addEventListener('click', (e) => {
              e.preventDefault();
              const username = btn.closest('.hv-popup-item').dataset.username;
              autofillCredentials(response.credentials.find(c => c.siteUsername === username));
              popup.remove();
            });
          });

          setTimeout(() => {
            const closeListener = (e) => {
              if (popup.parentNode && !popup.contains(e.target) && e.target !== inputField) {
                popup.remove();
                document.removeEventListener('click', closeListener);
              }
            };
            document.addEventListener('click', closeListener);
          }, 100);
          return;
        }

        requestCredentials(index + 1);
      }
    );
  };

  requestCredentials(0);
}

/**
 * Autofill username and password fields
 */
function autofillCredentials(credential) {
  const identifier = credential.siteUsername || credential.email || '';

  const identifierField = findIdentifierField();
  if (identifierField && fillInput(identifierField, identifier)) {
    identifierField.focus();
  }

  // Find password input
  const passwordInputs = document.querySelectorAll('input[type="password"]');
  if (passwordInputs.length > 0) {
    fillInput(passwordInputs[0], credential.encryptedPassword || '');
  }

  showNotification('Login details filled!', false);
}

// Initialize when page loads
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initializeFormDetection);
} else {
  initializeFormDetection();
}

// Watch for dynamically added form fields
const observer = new MutationObserver(() => {
  initializeFormFields();
  detectLoginButtons();
});

function startObserver() {
  if (!document.body) {
    return;
  }

  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
}

if (document.body) {
  startObserver();
} else {
  document.addEventListener('DOMContentLoaded', startObserver, { once: true });
}
