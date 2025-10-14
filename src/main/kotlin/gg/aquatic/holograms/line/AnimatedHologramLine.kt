package gg.aquatic.holograms.line

import gg.aquatic.holograms.CommonHologramLineSettings
import gg.aquatic.holograms.HologramLine
import gg.aquatic.holograms.HologramSerializer
import gg.aquatic.holograms.SpawnedHologramLine
import gg.aquatic.holograms.serialize.LineFactory
import gg.aquatic.holograms.serialize.LineSettings
import gg.aquatic.execute.checkConditions
import gg.aquatic.execute.getSectionList
import gg.aquatic.execute.requirement.ConditionHandle
import gg.aquatic.execute.requirement.ConditionSerializer
import gg.aquatic.packetutils.Packet.sendPacket
import gg.aquatic.packetutils.PacketEntity
import gg.aquatic.replace.placeholder.PlaceholderContext
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AnimatedHologramLine(
    val frames: MutableList<Pair<Int, HologramLine>>,
    override val height: Double,
    override val filter: (Player) -> Boolean,
    override val failLine: HologramLine?,
    override var scale: Float,
    override var billboard: Display.Billboard,
    override var transformationDuration: Int,
    override var teleportInterpolation: Int, override var translation: Vector3f = Vector3f(),
) : HologramLine {

    val ticks = ConcurrentHashMap<UUID, AnimationHandle>()
    override fun spawn(
        location: Location,
        player: Player,
        placeholderContext: PlaceholderContext<Player>,
    ): PacketEntity {
        return frames.first().second.spawn(location, player, placeholderContext)
    }

    override fun tick(spawnedHologramLine: SpawnedHologramLine) {
        val handle = ticks.getOrPut(spawnedHologramLine.player.uniqueId) { AnimationHandle() }
        handle.tick++

        var (stay, frame) = frames[handle.index]
        if (handle.tick >= stay) {
            handle.tick = 0
            handle.index++
            if (handle.index >= frames.size) {
                handle.index = 0
            }
            val pair = frames[handle.index]
            val previousFrame = frame
            frame = pair.second

            if (previousFrame.javaClass != frame.javaClass) {
                spawnedHologramLine.packetEntity.destroy(spawnedHologramLine.player)
                val packetEntity = frame.spawn(
                    spawnedHologramLine.currentLocation,
                    spawnedHologramLine.player,
                    spawnedHologramLine.placeholderContext
                )
                spawnedHologramLine.packetEntity = packetEntity
                spawnedHologramLine.packetEntity.sendSpawnComplete(spawnedHologramLine.player)
                return
            }
            val data = buildData(spawnedHologramLine)
            if (data.isEmpty()) return

            frame.tick(spawnedHologramLine)
            val packet = ClientboundSetEntityDataPacket(spawnedHologramLine.packetEntity.entityId, data)
            spawnedHologramLine.packetEntity.updatePacket = packet

            spawnedHologramLine.player.sendPacket(packet)
            return
        }
    }

    override fun buildData(
        placeholderContext: PlaceholderContext<Player>,
        player: Player
    ): List<SynchedEntityData.DataValue<*>> {
        return frames.first().second.buildData(placeholderContext, player)
    }

    override fun buildData(spawnedHologramLine: SpawnedHologramLine): List<SynchedEntityData.DataValue<*>> {
        return frames[ticks.getOrPut(spawnedHologramLine.player.uniqueId) { AnimationHandle() }.index].second.buildData(
            spawnedHologramLine
        )
    }

    class AnimationHandle {
        var tick: Int = -1
        var index: Int = 0
    }

    class Settings(
        val frames: MutableList<Pair<Int, LineSettings>>,
        val height: Double,
        val conditions: List<ConditionHandle<Player>>,
        val failLine: LineSettings?,
    ) : LineSettings {
        override fun create(): HologramLine {
            return AnimatedHologramLine(
                frames.map { it.first to it.second.create() }.toMutableList(),
                height,
                { p -> conditions.checkConditions(p) },
                failLine?.create(),
                0f,
                Display.Billboard.FIXED,
                0,
                0
            )
        }
    }

    companion object : LineFactory {
        override fun load(section: ConfigurationSection, commonOptions: CommonHologramLineSettings): LineSettings? {
            val frames = ArrayList<Pair<Int, LineSettings>>()
            val height = section.getDouble("height", commonOptions.height)
            val conditions = ConditionSerializer.fromSections<Player>(section.getSectionList("view-conditions"))
            val failLine = section.getConfigurationSection("fail-line")?.let {
                HologramSerializer.loadLine(it, commonOptions)
            }
            for (configurationSection in section.getSectionList("frames")) {
                val frame = HologramSerializer.loadLine(configurationSection, commonOptions) ?: continue
                val stay = configurationSection.getInt("stay", 1)
                frames.add(stay to frame)
            }
            if (frames.isEmpty()) return null
            return Settings(
                frames,
                height,
                conditions,
                failLine
            )
        }

    }
}