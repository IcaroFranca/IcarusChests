package dev.icaro.icaruschests.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Builds a {@code PLAYER_HEAD} item bearing an arbitrary custom skin, given
 * the Base64 "textures" value from a site like minecraft-heads.com. {@code
 * Bukkit.createProfile(UUID)} returns the plain {@code org.bukkit.profile}
 * interface, which lacks {@code setProperty} — the richer Paper interface
 * the real implementation also satisfies is needed for that, hence the cast.
 */
public final class CustomHeads {

    private CustomHeads() {
    }

    public static ItemStack createHead(String base64Texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = (PlayerProfile) Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
