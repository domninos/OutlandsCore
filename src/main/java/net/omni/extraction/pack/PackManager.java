package net.omni.extraction.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackManager {

    private final ExtractionPlugin plugin;
    private final int httpPort = 25393;

    // TODO have title and gui use a different gui per unicode
    private final Component title = Component.text("\uF801  \uE001");
    private final Inventory gui = Bukkit.createInventory(null, 27, title);

    private UUID packId = UUID.fromString("134280cc-103e-4452-a69d-3d2752891847");
    private HttpServer server;
    private String packUrl;

    public PackManager(ExtractionPlugin plugin) {
        this.plugin = plugin;

        ItemStack optionItem = new ItemStack(Material.COMPASS);
        ItemMeta meta = optionItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Extraction Pack"));
            optionItem.setItemMeta(meta);
        }

        gui.setItem(13, optionItem);
    }

    public void init() {
        File packFile = new File(plugin.getDataFolder(), "ExtractionPack.zip");

        if (!packFile.exists()) {
            plugin.getLogger().warning("ExtractionPack.zip not found. Place the pack in: " + packFile.getAbsolutePath());
            packUrl = null;
            return;
        }

        String host = resolveHost();
        if (host == null) {
            packUrl = null;
            return;
        }

        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(packFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read ExtractionPack.zip: " + e.getMessage());
            packUrl = null;
            return;
        }

        if (server != null) {
            server.stop(0);
            server = null;
        }

        this.packId = resolvePackId(fileBytes);
        this.packUrl = "http://" + host + ":" + httpPort + "/packs/ExtractionPack.zip?h=" + shortHash(fileBytes);

        startLocalHttpServer(packFile);
        plugin.sendConsole("<green>Initialized ExtractionPack.zip! (" + packId + ")");
    }

    /** Resolves the host clients download the pack from: config {@code pack.host} first, then
     * the {@code server-ip} from server.properties. Returns null when neither is a usable
     * address — the pack is then not offered (plain text titles) with a loud warning, and is
     * never silently served from localhost, which is unreachable for remote clients. */
    private String resolveHost() {
        String configured = plugin.getConfigUtil().getPackHost();
        if (configured != null && !configured.trim().isEmpty())
            return configured.trim();

        String serverIp = plugin.getServer().getIp();
        if (serverIp != null && !serverIp.trim().isEmpty() && !"0.0.0.0".equals(serverIp.trim()))
            return serverIp.trim();

        plugin.getLogger().warning("ExtractionPack.zip was found but is not being served: pack.host"
                + " (config.yml) is empty and server.properties server-ip isn't set. Set pack.host to your"
                + " public IP or domain so clients can download the pack.");
        return null;
    }

    /** Re-reads the pack + config id, re-hosts, and re-offers the pack to every online player.
     * Any content change flips the derived UUID so clients re-download instead of reusing a
     * cached pack with the previous UUID. */
    public void reload() {
        init();
        if (packUrl != null)
            for (Player player : Bukkit.getOnlinePlayers())
                tryResourcePack(player);
    }

    private UUID resolvePackId(byte[] fileBytes) {
        String configured = plugin.getConfigUtil().getPackId();
        if (configured != null && !configured.isBlank()) {
            try {
                return UUID.fromString(configured.trim());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid pack.id UUID '" + configured + "' — using a content-derived pack id.");
            }
        }
        return uuidFromSha256(fileBytes);
    }

    private static String shortHash(byte[] fileBytes) {
        byte[] hash = sha256(fileBytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++)
            sb.append(String.format("%02x", hash[i]));
        return sb.toString();
    }

    private static UUID uuidFromSha256(byte[] fileBytes) {
        byte[] hash = sha256(fileBytes);
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++)
            msb = (msb << 8) | (hash[i] & 0xFF);
        for (int i = 8; i < 16; i++)
            lsb = (lsb << 8) | (hash[i] & 0xFF);
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    private static byte[] sha256(byte[] fileBytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(fileBytes);
        } catch (NoSuchAlgorithmException e) {
            return fileBytes;
        }
    }


    private void startLocalHttpServer(File packFile) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), httpPort), 0);
            server.createContext("/packs/ExtractionPack.zip", exchange -> {
                byte[] fileBytes = Files.readAllBytes(packFile.toPath());
                exchange.sendResponseHeaders(200, fileBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(fileBytes);
                os.close();
            });

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Successfully hosted ExtractionPack at: " + packUrl);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to start local http server: " + e.getMessage());
        }
    }

    public void tryResourcePack(Player player) {
        if (packUrl != null)
            player.addResourcePack(packId, packUrl, null, "", true);
    }

    /** Returns the GUI's bare resource-pack texture glyph (config {@code pack.gui-textures}),
     * or an empty string when the pack isn't hosted or the key is unmapped. A lone-glyph title
     * line is centered by the client, so pure-texture GUIs place their panel dead-center. */
    public String guiTexture(String key) {
        if (packUrl == null)
            return "";

        String raw = plugin.getConfigUtil().getGuiTexture(key);
        if (raw == null || raw.isBlank())
            return "";

        return decodeUnicode(raw);
    }

    /** Builds a GUI title: the bare texture glyph when the pack hosts it (pure-texture title,
     * centered over the panel), otherwise the plain text title. */
    public String titleWithTexture(String plainTitle, String textureKey) {
        String texture = guiTexture(textureKey);
        return texture.isEmpty() ? plainTitle : texture;
    }

    private static String decodeUnicode(String value) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\\' && i + 5 < value.length()
                    && (value.charAt(i + 1) == 'u' || value.charAt(i + 1) == 'U')) {
                try {
                    sb.append((char) Integer.parseInt(value.substring(i + 2, i + 6), 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }

            sb.append(c);
        }

        return sb.toString();
    }

    public void openCustom(Player player) {
        player.openInventory(gui);
    }

    /** Runtime diagnostic for {@code /extraction admin packinfo}: hosting state plus a read of the
     * zip's font assets, so a never-applying pack becomes a checkable checklist. */
    public String diagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray>▪▪▪ Resource Pack Diagnostics ▪▪▪</dark_gray>\n");

        File packFile = new File(plugin.getDataFolder(), "ExtractionPack.zip");
        if (!packFile.exists()) {
            sb.append("<red>ExtractionPack.zip is missing:</red> ").append(packFile.getAbsolutePath()).append("\n");
            return sb.toString();
        }

        String configured = plugin.getConfigUtil().getPackHost();
        String serverIp = plugin.getServer().getIp();
        sb.append("<yellow>pack.host (config):</yellow> ").append(configured.isBlank() ? "(empty)" : configured).append("\n");
        sb.append("<yellow>server.properties server-ip:</yellow> ").append(serverIp.isBlank() ? "(empty)" : serverIp).append("\n");
        sb.append("<yellow>Served at:</yellow> ").append(packUrl == null ? "<red>NONE — not offered (plain titles)</red>" : packUrl).append("\n");
        sb.append("<yellow>Pack UUID:</yellow> ").append(packId).append("\n");
        sb.append("<yellow>Zip file:</yellow> ").append(packFile.length()).append(" bytes, modified ").append(new Date(packFile.lastModified())).append("\n");

        String[] keys = {"pack-menu", "loadout", "upgrade", "upgrade-confirm", "relics-charms",
                "relics-artifacts", "backpack", "backpack-shop", "area-editor"};
        char[] glyphs = {'\uE001', '\uE002', '\uE003', '\uE004', '\uE005', '\uE006', '\uE007', '\uE008', '\uE009'};
        StringBuilder missing = new StringBuilder();
        StringBuilder oversized = new StringBuilder();

        try (ZipFile zip = new ZipFile(packFile)) {
            ZipEntry font = zip.getEntry("assets/minecraft/font/default.json");
            sb.append("<yellow>font/default.json:</yellow> ");
            if (font == null) {
                sb.append("<red>MISSING</red>\n");
            } else {
                JsonObject root = JsonParser.parseString(readEntry(zip, font)).getAsJsonObject();
                JsonArray providers = root.has("providers") ? root.getAsJsonArray("providers") : new JsonArray();
                sb.append("providers=").append(providers.size());
                if (providers.size() <= keys.length)
                    sb.append(" <red>(only ours? merge with the full vanilla default.json)</red>");
                boolean[] found = new boolean[keys.length];
                for (JsonElement el : providers) {
                    JsonObject p = el.getAsJsonObject();
                    if (!"bitmap".equals(p.has("type") ? p.get("type").getAsString() : ""))
                        continue;
                    JsonArray chars = p.has("chars") ? p.getAsJsonArray("chars") : new JsonArray();
                    for (JsonElement c : chars) {
                        String ch = c.getAsString();
                        for (int i = 0; i < glyphs.length; i++)
                            if (ch.indexOf(glyphs[i]) >= 0)
                                found[i] = true;
                    }
                }
                for (int i = 0; i < found.length; i++)
                    if (!found[i])
                        missing.append(keys[i]).append(" ");
                if (missing.length() > 0)
                    sb.append("; missing glyphs: <red>").append(missing).append("</red>");
                else
                    sb.append("; missing glyphs: none");
                sb.append("\n");
            }

            ZipEntry mcmeta = zip.getEntry("pack.mcmeta");
            if (mcmeta == null) {
                sb.append("<red>pack.mcmeta MISSING</red> — the client refuses the pack without it\n");
            } else {
                JsonObject root = JsonParser.parseString(readEntry(zip, mcmeta)).getAsJsonObject();
                JsonObject pack = root.has("pack") ? root.getAsJsonObject("pack") : new JsonObject();
                sb.append("<yellow>pack_format:</yellow> ").append(pack.has("pack_format") ? pack.get("pack_format").getAsInt() : "?")
                        .append(" <gray>(1.21.x — see wiki; wrong value → 'incompatible')</gray>\n");
            }

            for (String key : keys) {
                String entryName = "assets/minecraft/textures/gui/gui_" + key + ".png";
                ZipEntry e = zip.getEntry(entryName);
                if (e == null) {
                    sb.append("  <red>gui_").append(key).append(".png MISSING</red> (want ").append(entryName).append(")\n");
                    continue;
                }
                int width = 0;
                int height = 0;
                try (InputStream in = zip.getInputStream(e)) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        width = img.getWidth();
                        height = img.getHeight();
                    }
                } catch (IOException ignored) {
                }
                if (width > 256 || height > 256)
                    oversized.append("gui_").append(key).append(".png ");
                sb.append("  gui_").append(key).append(".png: ").append(e.getSize()).append(" bytes, ")
                        .append(width > 0 ? width + "x" + height : "<red>unreadable image</red>")
                        .append((width > 256 || height > 256) ? " <red>(>256px, vanilla font cap)</red>" : "").append("\n");
            }
        } catch (IOException e) {
            sb.append("<red>Failed to open the zip: ").append(e.getMessage()).append("</red>\n");
        }

        if (missing.length() > 0)
            sb.append("<red>Glyphs with no provider in default.json: ").append(missing).append("</red>\n");
        if (oversized.length() > 0)
            sb.append("<red>Oversized textures (cap 256px): ").append(oversized).append("</red>\n");
        sb.append("<gray>Client checklist: pack applied (Resource Packs screen, no error) | Options > "
                + "Language > Force Unicode Font must be OFF.</gray>");

        return sb.toString();
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.sendConsole("<green>Stopped hosting ExtractionPack.zip.");
        }
    }
}
