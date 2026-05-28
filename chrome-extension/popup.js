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
        navigator.clipboard.writeText(response.password).catch(() => {});
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
        showQuickSaveOption();
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

// Show quick save prompt after login
function showQuickSaveOption() {
  const mainPanel = document.getElementById('mainPanel');
  const existingQuickSave = document.querySelector('.quick-save-prompt');
  if (existingQuickSave) existingQuickSave.remove();
  
  const tabs = chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    if (tabs[0]) {
      const hostname = new URL(tabs[0].url).hostname;
      
      const promptDiv = document.createElement('div');
      promptDiv.className = 'quick-save-prompt';
      promptDiv.innerHTML = `
        <div class="quick-save-content">
          <h4>Quick Save Login?</h4>
          <p>Save the login credentials for <strong>${hostname}</strong>?</p>
          <div class="quick-save-actions">
            <button class="btn btn-primary" id="quickSaveYes">Yes, Save</button>
            <button class="btn btn-secondary" id="quickSaveNo">No, Later</button>
          </div>
        </div>
      `;
      mainPanel.insertBefore(promptDiv, mainPanel.firstChild);
      
      document.getElementById('quickSaveYes').addEventListener('click', () => {
        quickSaveLoginCredentials(hostname);
        promptDiv.remove();
      });
      
      document.getElementById('quickSaveNo').addEventListener('click', () => {
        promptDiv.remove();
      });
    }
  });
}

// Quick save the login credentials
async function quickSaveLoginCredentials(hostname) {
  if (!capturedLoginCredentials) return;
  
  const credential = {
    siteName: hostname.replace('www.', '').toUpperCase(),
    siteUrl: hostname,
    siteUsername: capturedLoginCredentials.username,
    encryptedPassword: capturedLoginCredentials.password,
    notes: 'Auto-saved from login'
  };
  
  try {
    const response = await chrome.runtime.sendMessage({
      action: 'saveCredential',
      credential
    });
    
    if (response.success) {
      const feedback = document.createElement('div');
      feedback.className = 'copy-feedback';
      feedback.textContent = ' Credentials saved!';
      document.body.appendChild(feedback);
      setTimeout(() => feedback.remove(), 2000);
      
      loadCredentials();
      capturedLoginCredentials = null;
    } else {
      alert('Failed to save: ' + response.error);
    }
  } catch (error) {
    alert('Error: ' + error.message);
  }
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

    const credential = {
      siteName: document.getElementById('siteName') ? document.getElementById('siteName').value : '',
      siteUrl: document.getElementById('siteUrl') ? document.getElementById('siteUrl').value : '',
      siteUsername: document.getElementById('siteUsername') ? document.getElementById('siteUsername').value : '',
      encryptedPassword: document.getElementById('sitePassword') ? document.getElementById('sitePassword').value : '',
      notes: document.getElementById('siteNotes') ? document.getElementById('siteNotes').value : ''
    };

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
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  const url = new URL(tabs[0].url).hostname;
  
  try {
    const response = await chrome.runtime.sendMessage({
      action: 'getCredentials',
      siteUrl: url
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
    credentialsList.innerHTML = '<p>No credentials saved for this site</p>';
    return;
  }
  
  credentialsList.innerHTML = credentials.map((cred, index) => `
    <div class="credential-item" data-index="${index}" data-credential-id="${cred.id}">
      <h4>${cred.siteName}</h4>
      <p><strong>URL:</strong> ${cred.siteUrl}</p>
      <p><strong>Username:</strong> <span class="username-value">${cred.siteUsername}</span></p>
      ${cred.notes ? `<p><strong>Notes:</strong> ${cred.notes}</p>` : ''}
      <div class="actions">
        <button class="btn btn-secondary btn-copy" data-username="${escapeHtml(cred.siteUsername)}">Copy Username</button>
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
      autofillPage(credentials[index]);
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
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.replace(/[&<>"']/g, m => map[m]);
}

function copyToClipboard(text, message = 'Copied to clipboard!') {
  navigator.clipboard.writeText(text).then(() => {
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

async function autofillPage(credential) {
  // Get the active tab
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  
  if (!tabs[0]) {
    alert('Could not access active tab');
    return;
  }
  
  // Send autofill command to content script
  try {
    await chrome.tabs.sendMessage(tabs[0].id, {
      action: 'autofill',
      credential: credential
    });
    
    // Show success message
    const feedback = document.createElement('div');
    feedback.className = 'copy-feedback';
    feedback.textContent = ' Credential auto-filled!';
    document.body.appendChild(feedback);
    setTimeout(() => feedback.remove(), 2000);
  } catch (error) {
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
