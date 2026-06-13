package com.himalayanvault.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.himalayanvault.auth.AuthLockoutManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Disables password fields and submit actions while a user is in lockout.
 */
public final class LockoutUiHelper {

    private final AuthLockoutManager lockout = AuthLockoutManager.getInstance();
    private final Supplier<String> usernameSupplier;
    private final Label messageLabel;
    private final Parent messageContainer;
    private final List<Control> lockedControls = new ArrayList<>();
    private Timeline countdown;

    public LockoutUiHelper(Supplier<String> usernameSupplier, Label messageLabel, Parent messageContainer) {
        this.usernameSupplier = usernameSupplier;
        this.messageLabel = messageLabel;
        this.messageContainer = messageContainer;
    }

    public LockoutUiHelper(Supplier<String> usernameSupplier, Label messageLabel) {
        this(usernameSupplier, messageLabel, null);
    }

    public void register(Control... controls) {
        for (Control control : controls) {
            if (control != null && !lockedControls.contains(control)) {
                lockedControls.add(control);
            }
        }
    }

    public void start() {
        refresh();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    public void stop() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    public void refresh() {
        String username = usernameSupplier.get();
        boolean locked = username != null && !username.isBlank() && lockout.isLocked(username);

        Platform.runLater(() -> {
            for (Control control : lockedControls) {
                control.setDisable(locked);
            }
            if (messageLabel != null) {
                if (locked) {
                    messageLabel.setText(lockout.lockoutMessage(username));
                } else if (messageLabel.getText() != null
                        && messageLabel.getText().startsWith("Too many failed attempts.")) {
                    messageLabel.setText("");
                }
            }
            if (messageContainer != null) {
                messageContainer.setVisible(locked || (messageLabel != null
                        && messageLabel.getText() != null
                        && !messageLabel.getText().isBlank()));
                messageContainer.setManaged(messageContainer.isVisible());
            }
        });
    }

    public boolean ensureNotLocked() {
        String username = usernameSupplier.get();
        if (username == null || username.isBlank()) {
            return true;
        }
        if (lockout.isLocked(username)) {
            refresh();
            return false;
        }
        return true;
    }

    public void showFailure(String username, String prefix) {
        if (username == null || username.isBlank()) {
            return;
        }
        lockout.recordFailure(username);
        if (messageLabel != null) {
            String message = lockout.isLocked(username)
                    ? lockout.lockoutMessage(username)
                    : prefix + " " + lockout.failureMessage(username);
            Platform.runLater(() -> {
                messageLabel.setText(message.trim());
                messageLabel.setVisible(true);
                messageLabel.setManaged(true);
            });
        }
        refresh();
    }

    public void clearFailure(String username) {
        if (username != null && !username.isBlank()) {
            lockout.recordSuccess(username);
        }
        refresh();
    }
}
