package com.himalayanvault.security;

import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * Copies sensitive text and clears it after a short grace period if it has not
 * already been replaced by another clipboard value.
 */
public final class ClipboardProtector {

    private static final long CLEAR_DELAY_MS = 30_000;

    private ClipboardProtector() {
    }

    public static void copySensitiveText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);

        Thread clearThread = new Thread(() -> {
            try {
                Thread.sleep(CLEAR_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(() -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                if (value.equals(clipboard.getString())) {
                    clipboard.clear();
                }
            });
        }, "sensitive-clipboard-clear");
        clearThread.setDaemon(true);
        clearThread.start();
    }
}
