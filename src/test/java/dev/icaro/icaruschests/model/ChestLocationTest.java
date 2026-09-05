package dev.icaro.icaruschests.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestLocationTest {

    @Test
    void encodeThenDecodeRoundTrips() {
        ChestLocation original = new ChestLocation(UUID.randomUUID(), 12, -64, -8);

        Optional<ChestLocation> decoded = ChestLocation.decode(original.encode());

        assertEquals(Optional.of(original), decoded);
    }

    @Test
    void decodeRejectsGarbage() {
        assertTrue(ChestLocation.decode("not-a-location").isEmpty());
        assertTrue(ChestLocation.decode(null).isEmpty());
        assertTrue(ChestLocation.decode("").isEmpty());
    }

    @Test
    void decodeRejectsWrongFieldCount() {
        UUID worldId = UUID.randomUUID();
        assertTrue(ChestLocation.decode(worldId + ";1;2").isEmpty(), "missing a coordinate");
        assertTrue(ChestLocation.decode(worldId + ";1;2;3;4").isEmpty(), "extra field");
    }

    @Test
    void decodeRejectsNonNumericCoordinates() {
        UUID worldId = UUID.randomUUID();
        assertTrue(ChestLocation.decode(worldId + ";x;2;3").isEmpty());
    }

    @Test
    void negativeCoordinatesRoundTrip() {
        // Regression guard: a naive split on '-' instead of ';' would mangle these.
        ChestLocation original = new ChestLocation(UUID.randomUUID(), -100, -64, -200);
        assertEquals(Optional.of(original), ChestLocation.decode(original.encode()));
    }
}
