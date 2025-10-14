package gg.aquatic.holograms

import gg.aquatic.packetutils.Packet.sendPacket
import gg.aquatic.packetutils.PacketEntity
import gg.aquatic.packetutils.seat.IdSeat
import gg.aquatic.replace.placeholder.PlaceholderContext
import org.bukkit.Location
import org.bukkit.entity.Player

class SpawnedHologramLine(
    val hologram: Hologram,
    val player: Player,
    val line: HologramLine,
    location: Location,
    val placeholderContext: PlaceholderContext<Player>,
    var packetEntity: PacketEntity,
    var seat: Int? = null
) {

    init {
        packetEntity.seat = seat?.let { IdSeat(it, packetEntity.entityId) }
        packetEntity.sendSpawnComplete(player)
    }

    internal fun setAsPassenger(seat: Int?) {
        if (seat == null && this.seat == null) {
            return
        }
        if (seat == null) {
            val seat = IdSeat(this.seat!!, this.packetEntity.entityId)
            packetEntity.seat = seat
        } else {
            val seat = IdSeat(seat, this.packetEntity.entityId)
            packetEntity.seat = seat
        }
        this.seat = seat
        packetEntity.seat?.let {
            player.sendPacket(it.packet(), false)
        }
    }

    var currentLocation: Location = location
        private set

    fun tick() {
        line.tick(this)
    }

    fun move(location: Location) {
        currentLocation = location
        packetEntity.teleport(location, player)
    }

    fun destroy() {
        packetEntity.destroy(player)
    }
}