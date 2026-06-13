package com.himalayanvault.ui;

import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import com.himalayanvault.security.PasswordStrength;
import com.himalayanvault.security.PasswordStrength.Level;

/**
 * Updates a 4-segment strength bar and label from {@link PasswordStrength}.
 */
public final class PasswordStrengthMeter {

    private final Rectangle[] segments;
    private final Label strengthLabel;

    public PasswordStrengthMeter(Rectangle seg1, Rectangle seg2, Rectangle seg3, Rectangle seg4, Label strengthLabel) {
        this.segments = new Rectangle[] { seg1, seg2, seg3, seg4 };
        this.strengthLabel = strengthLabel;
    }

    public void update(String password) {
        Level level = PasswordStrength.level(password);
        int score = PasswordStrength.score(password);
        String color = PasswordStrength.color(level);

        if (segments[0] != null) {
            for (int i = 0; i < segments.length; i++) {
                Rectangle segment = segments[i];
                if (segment != null) {
                    segment.setFill(Color.web(i < score ? color : PasswordStrength.COLOR_EMPTY));
                }
            }
        }

        if (strengthLabel != null) {
            String label = PasswordStrength.label(level);
            if (label.isEmpty()) {
                strengthLabel.setText("");
            } else {
                strengthLabel.setText("Strength: " + label);
                strengthLabel.setStyle("-fx-text-fill: " + color + ";");
            }
        }
    }

    public void clear() {
        update("");
    }
}
