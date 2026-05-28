# Himalayan Vault - Unified Authentication UI

## Overview
This document describes the unified authentication pages created for both the Chrome Extension and JavaFX application. All authentication interfaces (Login, Signup, Forgot Password) now share the same modern, cyberpunk-inspired design with consistent functionality.

## Architecture
### Chrome Extension (`/chrome-extension/`)
The Chrome extension now includes comprehensive HTML pages with modern web styling.

#### Files Created:
1. **login.html** - User login page
2. **signup.html** - New user registration page  
3. **forgot-password.html** - Multi-step password recovery
4. **common.css** - Shared styling for all pages
5. **login.css** - Login-specific styles
6. **signup.css** - Signup-specific styles with password strength
7. **forgot-password.css** - Multi-step recovery styling
8. **login.js** - Login functionality and biometric auth
9. **signup.js** - Signup form handling with validation
10. **forgot-password.js** - Multi-step password recovery logic

#### Features:
- Responsive design (mobile, tablet, desktop)
- Password strength indicator
- Biometric authentication button
- Form validation
- Multi-step password recovery flow
- Modern gradient backgrounds with glassmorphism effects

### JavaFX Application (`/src/main/resources/`)

#### FXML Files Updated:
1. **login.fxml** - Redesigned login interface matching web design
2. **signup.fxml** - Unified signup interface
3. **recovery.fxml** - Multi-step password recovery interface

#### CSS Files Updated:
1. **login.css** - Login page styling
2. **signup.css** - Signup page styling  
3. **recovery.css** - Recovery page styling

#### Design Features:
- Consistent color scheme (#00d9ff, #64e8ff)
- Gradient backgrounds matching web version
- Modern button styling with hover effects
- Input field animations
- Password strength visualization
- Biometric authentication UI
- Error and success messaging

## Design System

### Color Palette
- **Primary Cyan**: `#00d9ff` - Main accent color
- **Secondary Cyan**: `#64e8ff` - Hover/active states
- **Background Dark**: `#0f0c29`, `#302b63`, `#24243e` - Gradient background
- **Error Red**: `#ff6b6b` - Error messages
- **Success Green**: `#51cf66` - Success messages
- **Text Muted**: `rgba(255, 255, 255, 0.7)` - Secondary text

### Typography
- Font Family: "Open Sans" (web), "Open Sans" (JavaFX)
- Heading: Bold, 24px, letter-spacing: 1px
- Body: Regular, 14px
- Labels: 12px, 400 weight

### Components

#### Input Fields
- Transparent background with bottom border
- Focus state: border width increases, text brightens
- Placeholder: muted cyan
- Padding: 10-15px

#### Buttons
- Primary: Gradient background (#00d9ff → #0a9fb8)
- Hover: Enhanced gradient with glow effect
- Active: Darker gradient
- Secondary/Cancel: Transparent with border

#### Password Strength
- 3-level system: Weak (red), Medium (yellow), Strong (green)
- Visual bar with segmented indicator
- Real-time validation

## Features Implemented

### Login Page
- Username/email input
- Password input
- Biometric authentication button
- "Forgot Password" link
- "Sign Up" link
- Form validation

### Signup Page
- Email input
- Username input
- Password input with strength meter
- Confirm password field
- Password validation rules
- "Sign In" link for existing users
- Form validation on all fields

### Forgot Password Page (Multi-Step)
**Step 1: Email Entry**
- User enters email address
- System sends recovery code

**Step 2: Code Verification**
- User enters 6-digit verification code
- Numeric-only validation

**Step 3: Password Reset**
- User enters new password
- Confirm password field
- Password strength indicator
- Validation

## Responsive Design

### Breakpoints
- **Desktop**: 1024px+ (full features)
- **Tablet**: 768px - 1024px (adjusted spacing)
- **Mobile**: 480px - 768px (stacked layout)
- **Small Mobile**: < 480px (compact layout)

### Adaptations
- Header padding reduces on smaller screens
- Form wrapper width adjusts to viewport
- Button layout changes from row to column on mobile
- Font sizes scale appropriately

## Integration Points

### Chrome Extension
- Background scripts handle authentication requests
- Pages communicate via `chrome.runtime.sendMessage()`
- Error handling with user notifications

### JavaFX Application
- Controllers handle all business logic
- FXML layouts define UI structure
- CSS provides consistent theming
- Event handlers manage user interactions

## Browser/Framework Support

### Chrome Extension
- Modern browsers (Chrome, Edge, Brave, etc.)
- ES6+ JavaScript
- CSS3 with vendor prefixes

### JavaFX
- Java 21+ (from workspace structure)
- JavaFX 21 API
- CSS3 subset support

## Security Considerations

1. **Password Fields**: Use PasswordField for masking
2. **Recovery Codes**: 6-digit numeric validation
3. **Biometric**: Check browser/system support
4. **HTTPS**: All communications should be encrypted
5. **Input Validation**: Client-side + server-side required

## File Structure Summary

```
HimalayanVault/
├── chrome-extension/
│   ├── login.html
│   ├── signup.html
│   ├── forgot-password.html
│   ├── common.css
│   ├── login.css
│   ├── signup.css
│   ├── forgot-password.css
│   ├── login.js
│   ├── signup.js
│   ├── forgot-password.js
│   └── ... (existing files)
│
└── src/main/resources/
    ├── fxml/
    │   ├── login.fxml (updated)
    │   ├── signup.fxml (updated)
    │   ├── recovery.fxml (updated)
    │   └── vault.fxml
    │
    └── css/
        ├── login.css (updated)
        ├── signup.css (updated)
        ├── recovery.css (updated)
        └── vault.css
```

## Next Steps

1. **Backend Integration**: Connect to API endpoints
2. **Testing**: Unit and integration testing
3. **Accessibility**: WCAG compliance review
4. **Performance**: Optimize animations and rendering
5. **Error Handling**: Comprehensive error messages
6. **Documentation**: User guide and FAQs

## Notes

- All pages maintain consistent branding (Himalayan Vault)
- Unified design language across platforms
- Mobile-first responsive approach
- Modern glassmorphism and gradient effects
- Accessibility features included (labels, tab order, keyboard support)
