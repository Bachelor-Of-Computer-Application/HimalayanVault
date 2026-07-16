/**
 * Himalayan Vault Extension - Background Service Worker
 * Handles all communication with the Himalayan Vault API server
 */

const API_BASE = 'http://127.0.0.1:8443';
const API_TIMEOUT = 5000; // 5 seconds

// Store session token
let sessionToken = null;
let currentUsername = null;

console.log(' Himalayan Vault Service Worker initializing...');

/**
 * Restore session from storage
 */
async function restoreSession() {
  try {
    const result = await new Promise((resolve) => {
      chrome.storage.local.get(['sessionToken', 'username'], (result) => {
        resolve(result);
      });
    });

    if (result.sessionToken) {
      sessionToken = result.sessionToken;
      currentUsername = result.username;
      console.log('Restored session from storage for user:', currentUsername);
    } else {
      console.log('No stored session found');
      sessionToken = null;
      currentUsername = null;
    }
  } catch (error) {
    console.error('Error restoring session:', error);
  }
}

/**
 * Initialize extension on install/update
 */
chrome.runtime.onInstalled.addListener(() => {
  console.log(' Himalayan Vault extension installed/updated');
  restoreSession();
});

// Restore session when service worker starts
restoreSession();

/**
 * Handle messages from popup and content scripts
 */
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  console.log('Message received:', request.action, 'from', sender.id);

  // Ensure session is restored before processing messages
  restoreSession().then(() => {
    console.log('Current session token status:', !!sessionToken ? 'Active' : 'No session');
  });

  try {
    switch (request.action) {
      case 'signup':
        handleSignup(request.username, request.password)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'sendRecoveryCode':
        handleSendRecoveryCode(request.email)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'verifyRecoveryCode':
        handleVerifyRecoveryCode(request.email, request.code)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'resetPassword':
        handleResetPassword(request.email, request.newPassword)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'verifyRecoveryMnemonic':
        handleVerifyRecoveryMnemonic(request.username, request.words)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'resetPasswordWithRecovery':
        handleResetPasswordWithRecovery(request.username, request.newPassword)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'login':
        handleLogin(request.username, request.password)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true; // Keep channel open for async response

      case 'logout':
        handleLogout()
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'getCredentials':
        handleGetCredentials(request.siteUrl)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'saveCredential':
        handleSaveCredential(request.credential)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'deleteCredential':
        handleDeleteCredential(request.credentialId)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'generatePassword':
        handleGeneratePassword(request.options)
          .then(sendResponse)
          .catch(error => sendResponse({ success: false, error: error.message }));
        return true;

      case 'isLoggedIn':
        console.log(' Login status check: ' + (!!sessionToken ? 'logged in' : 'not logged in'));
        sendResponse({ loggedIn: !!sessionToken, username: currentUsername });
        return false;

      case 'getSessionToken':
        sendResponse({ token: sessionToken });
        return false;

      default:
        console.warn(' Unknown action:', request.action);
        sendResponse({ success: false, error: 'Unknown action: ' + request.action });
        return false;
    }
  } catch (error) {
    console.error(' Message handler error:', error);
    sendResponse({ success: false, error: error.message });
    return false;
  }
});

/**
 * API Call Wrapper - with error handling and timeout
 */
async function apiCall(endpoint, method = 'GET', body = null) {
  console.log(` API Call: ${method} ${endpoint}`);

  const options = {
    method,
    headers: {
      'Content-Type': 'application/json'
    }
  };

  if (sessionToken) {
    options.headers['Authorization'] = `Bearer ${sessionToken}`;
  }

  if (body) {
    options.body = JSON.stringify(body);
  }

  let timeoutId = null;

  try {
    // Check if API server is reachable
    console.log(` Checking if API server is running at ${API_BASE}...`);

    const controller = new AbortController();
    timeoutId = setTimeout(() => {
      console.warn(' Request timeout, aborting...');
      controller.abort();
    }, API_TIMEOUT);

    const fullUrl = `${API_BASE}${endpoint}`;
    console.log(`Full URL: ${fullUrl}`);

    const response = await fetch(fullUrl, {
      ...options,
      signal: controller.signal
    });

    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    console.log(` Response status: ${response.status}`);

    let data;
    try {
      data = await response.json();
      console.log(` Response data:`, data);
    } catch (parseError) {
      console.warn(' Failed to parse response as JSON:', parseError);
      data = { success: false, message: 'Invalid response format' };
    }

    if (!response.ok) {
      const message = data.error || data.message || `HTTP ${response.status}`;
      console.warn(` API Error (${response.status}):`, message);
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }

    console.log(`API Success: ${endpoint}`);
    return data;

  } catch (error) {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    if (error.name === 'AbortError') {
      console.error(' API Timeout: Request was aborted after', API_TIMEOUT, 'ms');
      console.error(' Is the API server running at ' + API_BASE + '?');
      throw new Error('API request timeout - Is the API server running at ' + API_BASE + '?');
    }

    if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
      console.error(' Network Error: Cannot reach API server at', API_BASE);
      console.error(' Make sure the API server is running: java -jar target/himalayan-vault-1.0.0-fat.jar');
      throw new Error('Cannot connect to API server. Is it running at ' + API_BASE + '?');
    }

    console.error(' API Error:', error.message);
    throw error;
  }
}

/**
 * Login - Authenticate with Himalayan Vault
 */
async function handleLogin(username, password) {
  console.log(` Attempting login for user: ${username}`);

  if (!username || !password) {
    console.warn(' Failed to login: Missing credentials');
    throw new Error('Username and password required');
  }

  try {
    console.log('Verifying API server is reachable...');

    const response = await apiCall('/login', 'POST', {
      username,
      password
    });

    if (!response.success) {
      console.warn('Login failed:', response.message);
      throw new Error(response.message || 'Login failed');
    }

    if (!response.token) {
      console.warn('Login response missing token');
      throw new Error('Server did not return a valid token');
    }

    sessionToken = response.token;
    currentUsername = username;

    // Store token persistently
    await chrome.storage.local.set({
      sessionToken,
      username
    });

    console.log(`Login successful for user: ${username}`);
    console.log('Session token stored and ready for API calls');

    return {
      success: true,
      message: 'Login successful',
      username
    };
  } catch (error) {
    console.error('Login error:', error.message);
    sessionToken = null;
    currentUsername = null;

    let friendlyMessage = error.message;
    if (error.status === 429) {
      // Preserve server lockout message (e.g. "Too many failed attempts...")
    } else if (friendlyMessage.includes('API server')) {
      friendlyMessage = 'Cannot reach API server. Please make sure it\'s running:\njava -jar target/himalayan-vault-1.0.0-fat.jar';
    } else if (error.status === 401 || friendlyMessage.includes('Unauthorized')) {
      friendlyMessage = friendlyMessage.includes('attempt')
        ? friendlyMessage
        : 'Invalid username or password';
    }

    return {
      success: false,
      error: friendlyMessage,
      lockedOut: error.status === 429
    };
  }
}

/**
 * Logout - Invalidate session
 */
async function handleLogout() {
  if (!sessionToken) {
    return { success: true, message: 'Not logged in' };
  }

  try {
    await apiCall('/lock', 'POST', { token: sessionToken });

    sessionToken = null;
    currentUsername = null;

    // Clear stored token
    await chrome.storage.local.remove(['sessionToken', 'username']);

    console.log('Logout successful');
    return { success: true, message: 'Logged out' };
  } catch (error) {
    console.error('Logout error:', error);
    // Clear token anyway
    sessionToken = null;
    await chrome.storage.local.remove(['sessionToken', 'username']);
    return { success: true, message: 'Logged out (with error)' };
  }
}

/**
 * Get credentials for a site
 */
async function handleGetCredentials(siteUrl) {
  if (!sessionToken) {
    throw new Error('Not logged in');
  }

  try {
    const endpoint = siteUrl
      ? `/credentials?site=${encodeURIComponent(siteUrl)}`
      : '/credentials';
    const response = await apiCall(endpoint);

    if (!response.success) {
      throw new Error(response.message || 'Failed to get credentials');
    }

    console.log(`Retrieved ${response.credentials.length} credentials${siteUrl ? ` for ${siteUrl}` : ''}`);
    return {
      success: true,
      credentials: response.credentials || []
    };
  } catch (error) {
    console.error('Get credentials error:', error);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Save a new credential
 */
async function handleSaveCredential(credential) {
  if (!sessionToken) {
    throw new Error('Not logged in');
  }

  const siteUrl = credential.siteUrl || credential.siteName || '';
  const loginIdentifier = String(
    credential.loginIdentifier
    || credential.siteUsername
    || credential.siteEmail
    || credential.email
    || ''
  ).trim();

  if (!siteUrl || !loginIdentifier || !credential.encryptedPassword) {
    throw new Error('Site URL, username/email, and password required');
  }

  try {
    const response = await apiCall('/save', 'POST', {
      token: sessionToken,
      siteUrl,
      siteName: credential.siteName || siteUrl,
      siteUsername: loginIdentifier,
      siteEmail: credential.siteEmail || (loginIdentifier.includes('@') ? loginIdentifier : ''),
      encryptedPassword: credential.encryptedPassword,
      notes: credential.notes || '',
      category: credential.category || '',
      tags: credential.tags || '',
      favorite: !!credential.favorite
    });

    if (!response.success) {
      throw new Error(response.message || 'Failed to save credential');
    }

    console.log('Credential saved successfully');
    return {
      success: true,
      message: response.message
    };
  } catch (error) {
    console.error('Save credential error:', error);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Delete a credential
 */
async function handleDeleteCredential(credentialId) {
  if (!sessionToken) {
    throw new Error('Not logged in');
  }

  if (!credentialId) {
    throw new Error('Credential ID required');
  }

  try {
    const response = await apiCall('/delete', 'POST', {
      token: sessionToken,
      credentialId
    });

    if (!response.success) {
      throw new Error(response.message || 'Failed to delete credential');
    }

    console.log(`Credential ${credentialId} deleted`);
    return {
      success: true,
      message: response.message
    };
  } catch (error) {
    console.error('Delete credential error:', error);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Generate a secure password
 */
async function handleGeneratePassword(options = {}) {
  if (!sessionToken) {
    throw new Error('Not logged in');
  }

  try {
    const response = await apiCall('/generate-password', 'POST', {
      length: options.length || 16,
      useUppercase: options.useUppercase !== false,
      useLowercase: options.useLowercase !== false,
      useNumbers: options.useNumbers !== false,
      useSpecialChars: options.useSpecialChars !== false
    });

    if (!response.success) {
      throw new Error(response.message || 'Failed to generate password');
    }

    return {
      success: true,
      password: response.password
    };
  } catch (error) {
    console.error('Generate password error:', error);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Check API server health
 */
async function checkApiHealth() {
  try {
    console.log(' Checking API health...');
    const response = await apiCall('/health');
    const isHealthy = response.status === true;
    if (isHealthy) {
      console.log(' API server is healthy');
    } else {
      console.warn(' API server returned unhealthy status');
    }
    return isHealthy;
  } catch (error) {
    console.error(' Health check failed:', error.message);
    return false;
  }
}

// Initialize health check with alarm listener
console.log(' Setting up health check alarm...');
if (chrome.alarms) {
  chrome.alarms.create('healthCheck', { periodInMinutes: 5 });
  console.log(' Health check alarm created (every 5 minutes)');

  chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === 'healthCheck') {
      console.log(' Health check alarm triggered');
      checkApiHealth().then(healthy => {
        if (!healthy) {
          console.warn(' API server appears to be down');
        }
      });
    }
  });
} else {
  console.warn(' chrome.alarms not available - health checks disabled');
}

console.log(' Himalayan Vault Service Worker ready!');

// --- Simple in-memory recovery code store for extension-only flows ---
const recoveryCodes = {}; // email -> { code, expires }

function meetsSignupPasswordRequirements(password) {
  return password.length >= 12
    && /[A-Z]/.test(password)
    && /[a-z]/.test(password)
    && /[0-9]/.test(password)
    && /[^A-Za-z0-9]/.test(password);
}

async function handleSignup(username, password) {
  console.log('Signup request for', username);
  if (!username || !password) {
    throw new Error('Username and password are required');
  }

  if (username.length < 3) {
    throw new Error('Username must be at least 3 characters');
  }
  if (!meetsSignupPasswordRequirements(password)) {
    throw new Error('Password must be 12+ characters with upper, lower, number, and special character');
  }

  const response = await apiCall('/signup', 'POST', { username, password });

  if (!response.success) {
    throw new Error(response.message || 'Signup failed');
  }

  if (!response.recoveryWords || response.recoveryWords.length !== 16) {
    throw new Error('Signup succeeded but recovery words were not returned by the server');
  }

  console.log('Signup successful for', username);
  return {
    success: true,
    message: response.message || 'Signup successful',
    recoveryWords: response.recoveryWords
  };
}

async function handleSendRecoveryCode(email) {
  console.log(' Sending recovery code to', email);
  if (!email || !validateEmail(email)) {
    throw new Error('Valid email required');
  }

  const code = String(Math.floor(100000 + Math.random() * 900000));
  const expires = Date.now() + 15 * 60 * 1000; // 15 minutes
  recoveryCodes[email] = { code, expires };

  // In production this would email the code. Here we log it for debugging.
  console.log(` Recovery code for ${email}: ${code} (expires in 15m)`);

  await new Promise(r => setTimeout(r, 300));
  return { success: true, message: 'Recovery code sent' };
}

async function handleVerifyRecoveryCode(email, code) {
  console.log(' Verifying recovery code for', email);
  if (!email || !code) throw new Error('Email and code required');

  const record = recoveryCodes[email];
  if (!record) return { success: false, error: 'No recovery code found for this email' };
  if (Date.now() > record.expires) {
    delete recoveryCodes[email];
    return { success: false, error: 'Recovery code expired' };
  }

  if (record.code !== String(code)) return { success: false, error: 'Invalid code' };

  // Mark verified (could set a short-lived token); here we reuse the code store
  recoveryCodes[email].verified = true;
  return { success: true, message: 'Code verified' };
}

async function handleResetPassword(email, newPassword) {
  console.log(' Reset password for', email);
  if (!email || !newPassword) throw new Error('Email and new password required');

  const record = recoveryCodes[email];
  if (!record || !record.verified) return { success: false, error: 'Email not verified' };

  // In real system we'd update the database. Here we just clear the code and simulate success.
  delete recoveryCodes[email];
  await new Promise(r => setTimeout(r, 300));
  console.log(' Password reset simulated for', email);
  return { success: true, message: 'Password reset successful' };
}

async function handleVerifyRecoveryMnemonic(username, words) {
  if (!username || !words || !Array.isArray(words)) {
    throw new Error('Username and 16 recovery words are required');
  }

  try {
    const response = await apiCall('/recovery/verify', 'POST', {
      username,
      words
    });

    if (!response.success) {
      throw new Error(response.message || 'Recovery words verification failed');
    }

    return {
      success: true,
      message: response.message || 'Recovery words verified'
    };
  } catch (error) {
    return {
      success: false,
      error: error.message,
      lockedOut: error.status === 429
    };
  }
}

async function handleResetPasswordWithRecovery(username, newPassword) {
  if (!username || !newPassword) {
    throw new Error('Username and new password required');
  }

  const response = await apiCall('/recovery/reset', 'POST', {
    username,
    newPassword
  });

  if (!response.success) {
    throw new Error(response.message || 'Password reset failed');
  }

  return {
    success: true,
    message: response.message || 'Password reset successful'
  };
}

function validateEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
}
