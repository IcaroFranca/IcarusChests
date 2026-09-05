package dev.icaro.icaruschests.persistence;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Serializes a chest's globally-indexed {@code ItemStack[]} to/from a single
 * Base64 string for storage in {@code chest_inventory.contents_b64}, using
 * Bukkit's own object stream format (stable across Paper/Spigot versions).
 *
 * <p>Kept behind this narrow interface deliberately: swapping to a different
 * format later (e.g. the newer {@code ItemStack.serializeAsBytes()}) only
 * touches this one file.
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static String serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize chest contents", e);
        }
    }

    /**
     * @param expectedSize the tier's current total capacity; the result is
     *                      always exactly this length, truncated or
     *                      null-padded relative to what was stored (handles a
     *                      tier change between save and load gracefully).
     */
    public static ItemStack[] deserialize(String base64, int expectedSize) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack[expectedSize];
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream data = new BukkitObjectInputStream(in)) {
            int storedSize = data.readInt();
            ItemStack[] contents = new ItemStack[storedSize];
            for (int i = 0; i < storedSize; i++) {
                contents[i] = (ItemStack) data.readObject();
            }
            return storedSize == expectedSize ? contents : Arrays.copyOf(contents, expectedSize);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialize chest contents", e);
        }
    }
}
