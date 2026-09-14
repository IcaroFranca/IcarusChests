package dev.icaro.icaruschests.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds a {@code PLAYER_HEAD} item bearing an arbitrary custom skin, given
 * the Base64 "textures" value from a site like minecraft-heads.com. {@code
 * Bukkit.createProfile(UUID)} returns the plain {@code org.bukkit.profile}
 * interface, which lacks {@code setProperty} — the richer Paper interface
 * the real implementation also satisfies is needed for that, hence the cast.
 *
 * <p>The profile's UUID is derived deterministically from the texture value
 * itself (not random) — {@code GameProfile.equals()} compares by UUID, so a
 * random one would make two heads built from the very same texture compare
 * as different items. That matters here: an upgrade item's exact identity is
 * used as a crafting ingredient (see {@code UpgradeRegistry}'s "previous
 * tier" recipes), and {@code ItemStack.isSimilar()}/exact-choice matching
 * needs every head built from a given texture to be equal to every other.
 */
public final class CustomHeads {

    private CustomHeads() {
    }

    public static ItemStack createHead(String base64Texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        UUID profileId = UUID.nameUUIDFromBytes(base64Texture.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = (PlayerProfile) Bukkit.createProfile(profileId);
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
