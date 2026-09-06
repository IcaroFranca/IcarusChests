package dev.icaro.icaruschests.gui;

import java.util.Optional;

/**
 * The two fixed, always-present control-row buttons flanking the upgrade-slot
 * columns — see {@code GuiFactory}'s layout. Unlike an upgrade slot, these
 * never depend on the chest's tier and are never empty/replaceable.
 */
public enum ControlButton {

    SEARCH("search"),
    ORGANIZE("organize");

    private final String key;

    ControlButton(String key) {
        this.key = key;
    }

    /** The PDC value tagged on this button's item (see {@code NamespacedKeys#CONTROL_BUTTON}). */
    public String key() {
        return key;
    }

    public static Optional<ControlButton> fromKey(String raw) {
        for (ControlButton button : values()) {
            if (button.key.equals(raw)) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }
}
