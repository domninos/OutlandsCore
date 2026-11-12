package net.omni.extraction.commands.chest.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chests.StandaloneChest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class ChestCommandUtil {

    private ChestCommandUtil() {
    }

    static List<String> matching(Collection<String> values, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix))
                matches.add(value);
        }

        return matches;
    }

    static List<String> chestIds(ExtractionPlugin plugin) {
        List<String> ids = new ArrayList<>();

        for (StandaloneChest chest : plugin.getStandaloneChestManager().getAll())
            ids.add(String.valueOf(chest.id()));

        return ids;
    }

}
