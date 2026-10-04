package net.omni.extraction.pack;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.UUID;

public class PackManager {

    private final ExtractionPlugin plugin;
    private final String serverIp;
    private final int httpPort = 25393;

    // TODO have title and gui use a different gui per unicode
    private final Component title = Component.text("\uF801  \uE001");
    private final Inventory gui = Bukkit.createInventory(null, 27, title);

    private final UUID packId = UUID.fromString("134280cc-103e-4452-a69d-3d2752891847");
    private HttpServer server;
    private String packUrl;

    public PackManager(ExtractionPlugin plugin, String serverIp) {
        this.plugin = plugin;
        this.serverIp = serverIp;

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

        if (!packFile.exists())
            plugin.getLogger().warning("ExtractionPack.zip not found. Place the pack in: " + packFile.getAbsolutePath());
        else {

            this.packUrl = "http://" + serverIp + ":" + httpPort + "/packs/ExtractionPack.zip";

            startLocalHttpServer(packFile);
        }

        plugin.sendConsole("<green>Initialized ExtractionPack.zip! (" + packId + ")");
    }


    private void startLocalHttpServer(File packFile) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(httpPort), 0);
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

    public void openCustom(Player player) {
        player.openInventory(gui);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.sendConsole("<green>Stopped hosting ExtractionPack.zip.");
        }
    }
}
