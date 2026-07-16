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
    const result = autofillCredentials(request.credential);
    sendResponse({ success: true, ...result });
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

  const usernameField = findIdentifierField(form);
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
    'input[autocomplete*="email" i], ' +
    'input[autocomplete*="username" i], ' +
    'input[type="email"], ' +
    'input[name*="email" i], ' +
    'input[id*="email" i], ' +
    'input[placeholder*="email" i], ' +
    'input[aria-label*="email" i], ' +
    'input[name*="user" i], ' +
    'input[name*="login" i], ' +
    'input[name*="username" i], ' +
    'input[name*="account" i], ' +
    'input[name*="identifier" i], ' +
    'input[id*="user" i], ' +
    'input[id*="login" i], ' +
    'input[id*="username" i], ' +
    'input[id*="account" i], ' +
    'input[id*="identifier" i], ' +
    'input[placeholder*="user" i], ' +
    'input[placeholder*="login" i], ' +
    'input[placeholder*="username" i], ' +
    'input[placeholder*="account" i], ' +
    'input[placeholder*="identifier" i], ' +
    'input[aria-label*="user" i], ' +
    'input[aria-label*="login" i], ' +
    'input[aria-label*="username" i], ' +
    'input[aria-label*="account" i], ' +
    'input[aria-label*="identifier" i]'
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
  const rect = input.getBoundingClientRect();
  return !input.disabled
    && !input.readOnly
    && style.display !== 'none'
    && style.visibility !== 'hidden'
    && rect.width > 0
    && rect.height > 0;
}

function fillInput(input, value) {
  if (!input || !value) {
    return false;
  }

  const prototype = Object.getPrototypeOf(input);
  const valueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
  if (valueSetter) {
    valueSetter.call(input, value);
  } else {
    input.value = value;
  }

  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
}

function getCredentialIdentifier(credential) {
  if (!credential) {
    return '';
  }

  return credential.loginIdentifier
    || credential.login_identifier
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
    || '';
}

function getCredentialPassword(credential) {
  if (!credential) {
    return '';
  }

  return credential.encryptedPassword
    || credential.encrypted_password
    || credential.password
    || credential.sitePassword
    || credential.site_password
    || '';
}

function getIdentifierFieldCandidates(scope = document) {
  return [
    ...scope.querySelectorAll(
      [
        'input[autocomplete*="email" i]',
        'input[autocomplete*="username" i]',
        'input[type="email"]',
        'input[name*="email" i]',
        'input[id*="email" i]',
        'input[placeholder*="email" i]',
        'input[aria-label*="email" i]',
        'input[name*="user" i]',
        'input[name*="login" i]',
        'input[name*="account" i]',
        'input[name*="identifier" i]',
        'input[id*="user" i]',
        'input[id*="login" i]',
        'input[id*="account" i]',
        'input[id*="identifier" i]',
        'input[placeholder*="user" i]',
        'input[placeholder*="login" i]',
        'input[placeholder*="account" i]',
        'input[placeholder*="identifier" i]',
        'input[aria-label*="user" i]',
        'input[aria-label*="login" i]',
        'input[aria-label*="account" i]',
        'input[aria-label*="identifier" i]'
      ].join(', ')
    ),
    ...scope.querySelectorAll('input')
  ];
}

function isIdentifierField(input) {
  if (!isVisibleAndEnabled(input)) {
    return false;
  }

  const type = (input.getAttribute('type') || 'text').toLowerCase();
  if (['password', 'hidden', 'submit', 'button', 'checkbox', 'radio', 'file'].includes(type)) {
    return false;
  }

  if (type === 'email') {
    return true;
  }

  const text = [
    input.name,
    input.id,
    input.placeholder,
    input.autocomplete,
    input.getAttribute('aria-label')
  ].join(' ').toLowerCase();

  return /\b(e-?mail|mail|user(name)?|login|logon|account|identifier|userid|user-id)\b/.test(text)
    || ['text', 'search', 'tel', 'url'].includes(type);
}

function findFirstIdentifierField(scope = document) {
  const seen = new Set();
  for (const field of getIdentifierFieldCandidates(scope)) {
    if (!seen.has(field) && isIdentifierField(field)) {
      return field;
    }
    seen.add(field);
  }

  return null;
}

function findPasswordField() {
  const passwordInputs = document.querySelectorAll('input[type="password"]');
  for (const field of passwordInputs) {
    if (isVisibleAndEnabled(field)) {
      return field;
    }
  }

  return passwordInputs[0] || null;
}

function findIdentifierField(scope = document) {
  const passwordField = scope === document ? findPasswordField() : scope.querySelector('input[type="password"]');

  if (passwordField) {
    const form = passwordField.closest('form');
    const formIdentifierField = form ? findFirstIdentifierField(form) : null;
    if (formIdentifierField && formIdentifierField !== passwordField) {
      return formIdentifierField;
    }

    const container = passwordField.closest('form, [role="form"], main, section, div');
    const containerIdentifierField = container ? findFirstIdentifierField(container) : null;
    if (containerIdentifierField && containerIdentifierField !== passwordField) {
      return containerIdentifierField;
    }
  }

  return findFirstIdentifierField(scope);
}

function getFieldCenter(input) {
  const rect = input.getBoundingClientRect();
  return {
    x: rect.left + (rect.width / 2),
    y: rect.top + (rect.height / 2)
  };
}

function isFillableTextInput(input) {
  if (!isVisibleAndEnabled(input)) {
    return false;
  }

  const type = (input.getAttribute('type') || 'text').toLowerCase();
  return !['password', 'hidden', 'submit', 'button', 'checkbox', 'radio', 'file'].includes(type);
}

function findIdentifierFieldsNearPassword(passwordField) {
  if (!passwordField) {
    return [findIdentifierField()].filter(Boolean);
  }

  const scopes = [
    passwordField.closest('form'),
    passwordField.closest('[role="form"]'),
    passwordField.closest('main'),
    passwordField.closest('section'),
    passwordField.parentElement,
    document
  ].filter(Boolean);
  const passwordCenter = getFieldCenter(passwordField);
  const candidates = [];
  const seen = new Set();

  for (const scope of scopes) {
    const fields = [...scope.querySelectorAll('input')].filter(input => input !== passwordField && isFillableTextInput(input));

    for (const field of fields) {
      if (!seen.has(field) && isIdentifierField(field)) {
        candidates.push(field);
        seen.add(field);
      }
    }

    const beforePassword = fields
      .filter(field => getFieldCenter(field).y <= passwordCenter.y + 8)
      .sort((a, b) => {
        const aCenter = getFieldCenter(a);
        const bCenter = getFieldCenter(b);
        return Math.abs(aCenter.y - passwordCenter.y) - Math.abs(bCenter.y - passwordCenter.y)
          || Math.abs(aCenter.x - passwordCenter.x) - Math.abs(bCenter.x - passwordCenter.x);
      });

    for (const field of beforePassword) {
      if (!seen.has(field)) {
        candidates.push(field);
        seen.add(field);
      }
    }

    if (candidates.length > 0 && scope !== document) {
      break;
    }
  }

  return candidates;
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
                  <div class="hv-popup-item" data-index="${idx}">
                    <span class="hv-popup-username">${getCredentialIdentifier(cred)}</span>
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
              const index = Number(btn.closest('.hv-popup-item').dataset.index);
              autofillCredentials(response.credentials[index]);
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

      if (chrome.runtime.lastError) {
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
  const identifier = getCredentialIdentifier(credential);
  const password = getCredentialPassword(credential);
  const passwordField = findPasswordField();
  const identifierFields = findIdentifierFieldsNearPassword(passwordField);
  let filledIdentifier = false;
  let filledPassword = false;

  for (const identifierField of identifierFields) {
    if (fillInput(identifierField, identifier)) {
      if (!filledIdentifier) {
        identifierField.focus();
      }
      filledIdentifier = true;
    }
  }

  if (passwordField) {
    filledPassword = fillInput(passwordField, password);
  }

  showNotification(
    filledPassword
      ? 'Login details filled!'
      : 'Username filled, but no password field was found.',
    !filledPassword
  );

  return {
    filledIdentifier,
    filledPassword
  };
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
