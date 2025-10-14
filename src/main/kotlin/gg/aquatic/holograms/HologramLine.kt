package gg.aquatic.holograms

import gg.aquatic.packetutils.PacketEntity
import gg.aquatic.replace.placeholder.PlaceholderContext
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Location
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Player
import org.joml.Vector3f

interface HologramLine {

    var scale: Float
    var billboard: Billboard
    var transformationDuration: Int
    var teleportInterpolation: Int
    var translation: Vector3f

    val height: Double
    val filter: (Player) -> Boolean

    val failLine: HologramLine?

    fun getVisibleLine(player: Player): HologramLine? =
        if (filter(player)) {
            this
        } else {
            failLine?.getVisibleLine(player)
        }

    fun spawn(location: Location, player: Player, placeholderContext: PlaceholderContext<Player>): PacketEntity
    fun tick(spawnedHologramLine: SpawnedHologramLine)
    fun buildData(placeholderContext: PlaceholderContext<Player>, player: Player): List<SynchedEntityData.DataValue<*>>

    fun buildData(spawnedHologramLine: SpawnedHologramLine): List<SynchedEntityData.DataValue<*>> {
        return buildData(spawnedHologramLine.placeholderContext, spawnedHologramLine.player)
    }
}