package dev.icaro.icaruschests.gui;

import java.util.Optional;

/** Action encoded on a paginated GUI's navigation button items. */
public enum NavAction {

    PREVIOUS("prev"),
    NEXT("next");

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
