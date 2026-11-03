package net.omni.extraction.pets;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public record PetSession(Player owner, Entity pet) {
}
