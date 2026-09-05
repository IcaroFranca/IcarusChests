package dev.icaro.icaruschests.gui;

import java.util.Optional;

/** Action encoded on a scrollable GUI's control-row button items. */
public enum NavAction {

    SCROLL_UP("scroll_up"),
    SCROLL_DOWN("scroll_down");

    private final String key;

    NavAction(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<NavAction> parse(String raw) {
        for (NavAction action : values()) {
            if (action.key.equals(raw)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
