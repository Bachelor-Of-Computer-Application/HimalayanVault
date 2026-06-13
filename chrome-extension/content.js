/**
 * Himalayan Vault - Content Script
 * Auto-detects login attempts and manages credential auto-fill
 * Works like Google Password Manager
 */

let capturedCredentials = null;
let lastFormTimestamp = 0;

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

    // After a short delay (form processing), show save prompt
    setTimeout(() => {
      checkLoginSuccess();
    }, 500);
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

            // Check for success after delay
            setTimeout(() => {
              checkLoginSuccess();
            }, 1000);
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
  const credentials = {
    username: '',
    password: ''
  };

  // Find username field (email inputs intentionally excluded)
  const usernameSelectors = [
    'input[name*="user"]',
    'input[name*="login"]',
    'input[name*="username"]',
    'input[placeholder*="user"]'
  ];

  for (const selector of usernameSelectors) {
    const input = form.querySelector(selector);
    if (input && input.value) {
      credentials.username = input.value;
      break;
    }
  }

  // Find password field
  const passwordInput = form.querySelector('input[type="password"]');
  if (passwordInput && passwordInput.value) {
    credentials.password = passwordInput.value;
  }

  return credentials.username && credentials.password ? credentials : null;
}

/**
 * Check if login was successful
 */
function checkLoginSuccess() {
  if (!capturedCredentials) return;

  // Indicators that login succeeded:
  // 1. Page changed (URL changed)
  // 2. Error messages disappeared
  // 3. Dashboard/content loaded

  const hasError = document.querySelector(
    '.error, .alert-danger, [class*="error"], [class*="invalid"], .form-error'
  );

  if (!hasError) {
    // Login appears successful - show save prompt
    showSavePrompt();
  }
}

/**
 * Show credential save prompt (like Google Password Manager)
 */
function showSavePrompt() {
  // Don't show multiple prompts
  if (document.querySelector('.hv-save-banner')) {
    return;
  }

  const hostname = window.location.hostname.replace('www.', '');
  const siteName = hostname.split('.')[0].toUpperCase();

  // Create save banner
  const banner = document.createElement('div');
  banner.className = 'hv-save-banner';
  banner.innerHTML = `
    <div class="hv-banner-content">
      <div class="hv-banner-icon"></div>
      <div class="hv-banner-text">
        <div class="hv-banner-title">Save password for ${siteName}?</div>
        <div class="hv-banner-subtitle">${capturedCredentials.username}</div>
      </div>
      <div class="hv-banner-actions">
        <button class="hv-btn-skip">Not now</button>
        <button class="hv-btn-save">Save</button>
      </div>
      <button class="hv-banner-close">&times;</button>
    </div>
  `;

  document.body.appendChild(banner);

  // Trigger animation
  setTimeout(() => {
    banner.classList.add('hv-banner-show');
  }, 10);

  // Handle save button
  banner.querySelector('.hv-btn-save').addEventListener('click', () => {
    saveCredentials(hostname, siteName);
    closeSavePrompt(banner);
  });

  // Handle not now button
  banner.querySelector('.hv-btn-skip').addEventListener('click', () => {
    closeSavePrompt(banner);
    capturedCredentials = null;
  });

  // Handle close button
  banner.querySelector('.hv-banner-close').addEventListener('click', () => {
    closeSavePrompt(banner);
  });

  // Auto-close after 10 seconds
  setTimeout(() => {
    if (banner.parentNode) {
      closeSavePrompt(banner);
    }
  }, 10000);
}

/**
 * Close save prompt
 */
function closeSavePrompt(banner) {
  banner.classList.remove('hv-banner-show');
  setTimeout(() => {
    if (banner.parentNode) {
      banner.remove();
    }
  }, 300);
}

/**
 * Save credentials to vault
 */
function saveCredentials(hostname, siteName) {
  if (!capturedCredentials) return;

  const credential = {
    siteName: siteName,
    siteUrl: hostname,
    siteUsername: capturedCredentials.username,
    encryptedPassword: capturedCredentials.password,
    notes: 'Auto-saved from login',
    category: '',
    tags: '',
    favorite: false
  };

  // Send to background script to save
  chrome.runtime.sendMessage({
    action: 'saveCredential',
    credential: credential
  }, (response) => {
    if (chrome.runtime.lastError) {
      showNotification('Failed to save: ' + chrome.runtime.lastError.message, true);
      return;
    }
    if (response && response.success) {
      showNotification('Password saved!', false);
      capturedCredentials = null;
    } else {
      const message = response && (response.error || response.message)
        ? (response.error || response.message)
        : 'Failed to save credential';
      showNotification(message, true);
    }
  });
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

  // Find username inputs (exclude email inputs)
  const usernameInputs = document.querySelectorAll(
    'input[type="text"][name*="user"], ' +
    'input[type="text"][name*="login"], ' +
    'input[type="text"][name*="username"], ' +
    'input[type="text"][placeholder*="user"]'
  );
  usernameInputs.forEach(input => {
    if (!input.hasAttribute('data-hv-listener')) {
      input.setAttribute('data-hv-listener', 'true');
      input.addEventListener('focus', () => showCredentialsPopup(input));
    }
  });
}

/**
 * Show popup with saved credentials when field is focused
 */
function showCredentialsPopup(inputField) {
  // Remove any existing popup
  const existingPopup = document.querySelector('.hv-credentials-popup');
  if (existingPopup) existingPopup.remove();

  // Get current site
  const hostname = window.location.hostname.replace('www.', '');

  // Request credentials from background
  chrome.runtime.sendMessage(
    { action: 'getCredentials', siteUrl: hostname },
    (response) => {
      if (response && response.success && response.credentials && response.credentials.length > 0) {
        // Create popup
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

        // Position popup near input
        const rect = inputField.getBoundingClientRect();
        popup.style.top = (rect.bottom + window.scrollY + 5) + 'px';
        popup.style.left = (rect.left + window.scrollX) + 'px';

        document.body.appendChild(popup);

        // Add click listeners to fill buttons
        popup.querySelectorAll('.hv-popup-fill').forEach(btn => {
          btn.addEventListener('click', (e) => {
            e.preventDefault();
            const username = btn.closest('.hv-popup-item').dataset.username;
            autofillCredentials(response.credentials.find(c => c.siteUsername === username));
            popup.remove();
          });
        });

        // Close popup when clicking outside
        setTimeout(() => {
          const closeListener = (e) => {
            if (popup.parentNode && !popup.contains(e.target) && e.target !== inputField) {
              popup.remove();
              document.removeEventListener('click', closeListener);
            }
          };
          document.addEventListener('click', closeListener);
        }, 100);
      }
    }
  );
}

/**
 * Autofill username and password fields
 */
function autofillCredentials(credential) {
  // Find username input
  const usernameInputs = document.querySelectorAll(
    'input[type="text"][name*="user"], ' +
    'input[type="text"][name*="login"], ' +
    'input[type="text"][name*="username"], ' +
    'input[type="text"][placeholder*="user"]'
  );

  if (usernameInputs.length > 0) {
    usernameInputs[0].value = credential.siteUsername;
    usernameInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
    usernameInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
  }

  // Find password input
  const passwordInputs = document.querySelectorAll('input[type="password"]');
  if (passwordInputs.length > 0) {
    passwordInputs[0].value = credential.encryptedPassword || '';
    passwordInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
    passwordInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
  }

  showNotification('Password filled!', false);
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

observer.observe(document.body, {
  childList: true,
  subtree: true
});
