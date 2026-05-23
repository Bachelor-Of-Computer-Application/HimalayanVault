/**
 * Himalayan Vault Extension - Background Service Worker
 * Handles all communication with the Himalayan Vault API server
 */

const API_BASE = 'http://127.0.0.1:8443';
const API_TIMEOUT = 5000; // 5 seconds

// Store session token
let sessionToken = null;
let currentUsername = null;

console.log('🚀 Himalayan Vault Service Worker initializing...');

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
      console.log('✅ Restored session from storage for user:', currentUsername);
    } else {
      console.log('ℹ️ No stored session found');
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
  console.log('✅ Himalayan Vault extension installed/updated');
  restoreSession();
});

// Restore session when service worker starts
restoreSession();

/**
 * Handle messages from popup and content scripts
 */
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  console.log('📨 Message received:', request.action, 'from', sender.id);
  
  // Ensure session is restored before processing messages
  restoreSession().then(() => {
    console.log('Current session token status:', !!sessionToken ? 'Active' : 'No session');
  });
  
  try {
    switch (request.action) {
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
        console.log('🔐 Login status check: ' + (!!sessionToken ? 'logged in' : 'not logged in'));
        sendResponse({ loggedIn: !!sessionToken, username: currentUsername });
        return false;
        
      case 'getSessionToken':
        sendResponse({ token: sessionToken });
        return false;
        
      default:
        console.warn('⚠️ Unknown action:', request.action);
        sendResponse({ success: false, error: 'Unknown action: ' + request.action });
        return false;
    }
  } catch (error) {
    console.error('❌ Message handler error:', error);
    sendResponse({ success: false, error: error.message });
    return false;
  }
});

/**
 * API Call Wrapper - with error handling and timeout
 */
async function apiCall(endpoint, method = 'GET', body = null) {
  console.log(`🌐 API Call: ${method} ${endpoint}`);
  
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
    console.log(`🔍 Checking if API server is running at ${API_BASE}...`);
    
    const controller = new AbortController();
    timeoutId = setTimeout(() => {
      console.warn('⚠️ Request timeout, aborting...');
      controller.abort();
    }, API_TIMEOUT);
    
    const fullUrl = `${API_BASE}${endpoint}`;
    console.log(`📍 Full URL: ${fullUrl}`);
    
    const response = await fetch(fullUrl, {
      ...options,
      signal: controller.signal
    });
    
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    
    console.log(`📦 Response status: ${response.status}`);
    
    let data;
    try {
      data = await response.json();
      console.log(`📄 Response data:`, data);
    } catch (parseError) {
      console.warn('⚠️ Failed to parse response as JSON:', parseError);
      data = { success: false, message: 'Invalid response format' };
    }
    
    if (!response.ok) {
      console.warn(`⚠️ API Error (${response.status}):`, data.error || data.message);
      throw new Error(data.error || data.message || `HTTP ${response.status}`);
    }
    
    console.log(`✅ API Success: ${endpoint}`);
    return data;
    
  } catch (error) {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    
    if (error.name === 'AbortError') {
      console.error('❌ API Timeout: Request was aborted after', API_TIMEOUT, 'ms');
      console.error('⚠️ Is the API server running at ' + API_BASE + '?');
      throw new Error('API request timeout - Is the API server running at ' + API_BASE + '?');
    }
    
    if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
      console.error('❌ Network Error: Cannot reach API server at', API_BASE);
      console.error('📝 Make sure the API server is running: java -jar target/himalayan-vault-1.0.0-fat.jar');
      throw new Error('Cannot connect to API server. Is it running at ' + API_BASE + '?');
    }
    
    console.error('❌ API Error:', error.message);
    throw error;
  }
}

/**
 * Login - Authenticate with Himalayan Vault
 */
async function handleLogin(username, password) {
  console.log(`🔑 Attempting login for user: ${username}`);
  
  if (!username || !password) {
    console.warn('⚠️ Login attempt with missing credentials');
    throw new Error('Username and password required');
  }
  
  try {
    console.log(`🔍 Verifying API server is reachable...`);
    
    const response = await apiCall('/login', 'POST', {
      username,
      password
    });
    
    if (!response.success) {
      console.warn('❌ Login failed:', response.message);
      throw new Error(response.message || 'Login failed');
    }
    
    if (!response.token) {
      console.warn('❌ Login response missing token');
      throw new Error('Server did not return a valid token');
    }
    
    sessionToken = response.token;
    currentUsername = username;
    
    // Store token persistently
    await chrome.storage.local.set({
      sessionToken,
      username
    });
    
    console.log(`✅ Login successful for user: ${username}`);
    console.log(`✅ Session token stored and ready for API calls`);
    
    return {
      success: true,
      message: 'Login successful',
      username
    };
  } catch (error) {
    console.error('❌ Login error:', error.message);
    sessionToken = null;
    currentUsername = null;
    
    // Provide helpful error messages
    let friendlyMessage = error.message;
    if (friendlyMessage.includes('API server')) {
      friendlyMessage = 'Cannot reach API server. Please make sure it\'s running:\njava -jar target/himalayan-vault-1.0.0-fat.jar';
    } else if (friendlyMessage.includes('401') || friendlyMessage.includes('Unauthorized')) {
      friendlyMessage = 'Invalid username or password';
    }
    
    return {
      success: false,
      error: friendlyMessage
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
  
  if (!siteUrl) {
    throw new Error('Site URL required');
  }
  
  try {
    const endpoint = `/credentials?site=${encodeURIComponent(siteUrl)}`;
    const response = await apiCall(endpoint);
    
    if (!response.success) {
      throw new Error(response.message || 'Failed to get credentials');
    }
    
    console.log(`Retrieved ${response.credentials.length} credentials for ${siteUrl}`);
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
  
  if (!credential.siteUrl || !credential.siteUsername || !credential.encryptedPassword) {
    throw new Error('Site URL, username, and password required');
  }
  
  try {
    const response = await apiCall('/save', 'POST', {
      token: sessionToken,
      siteUrl: credential.siteUrl,
      siteName: credential.siteName || credential.siteUrl,
      siteUsername: credential.siteUsername,
      encryptedPassword: credential.encryptedPassword,
      notes: credential.notes || ''
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
    console.log('💚 Checking API health...');
    const response = await apiCall('/health');
    const isHealthy = response.status === true;
    if (isHealthy) {
      console.log('✅ API server is healthy');
    } else {
      console.warn('⚠️ API server returned unhealthy status');
    }
    return isHealthy;
  } catch (error) {
    console.error('❌ Health check failed:', error.message);
    return false;
  }
}

// Initialize health check with alarm listener
console.log('🔔 Setting up health check alarm...');
if (chrome.alarms) {
  chrome.alarms.create('healthCheck', { periodInMinutes: 5 });
  console.log('✅ Health check alarm created (every 5 minutes)');

  chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === 'healthCheck') {
      console.log('🔔 Health check alarm triggered');
      checkApiHealth().then(healthy => {
        if (!healthy) {
          console.warn('⚠️ API server appears to be down');
        }
      });
    }
  });
} else {
  console.warn('⚠️ chrome.alarms not available - health checks disabled');
}

console.log('✅ Himalayan Vault Service Worker ready!');
