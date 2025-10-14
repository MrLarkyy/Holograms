package gg.aquatic.holograms.serialize

import gg.aquatic.holograms.CommonHologramLineSettings
import org.bukkit.configuration.ConfigurationSection

interface LineFactory {

    fun load(section: ConfigurationSection, commonOptions: CommonHologramLineSettings): LineSettings?

}