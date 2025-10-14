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
import gg.aquatic.items.AquaticItem
import gg.aquatic.packetutils.PacketEntity
import gg.aquatic.packetutils.PacketEntityData
import gg.aquatic.packetutils.util.sendPacket
import gg.aquatic.replace.placeholder.PlaceholderContext
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.joml.Vector3f
import kotlin.properties.Delegates

class ItemHologramLine(
    item: ItemStack,
    override var height: Double = 0.3,
    scale: Float = 1.0f,
    billboard: Billboard = Billboard.CENTER,
    itemDisplayTransform: ItemDisplayTransform,
    override val filter: (Player) -> Boolean,
    override var failLine: HologramLine?,
    override var transformationDuration: Int,
    override var teleportInterpolation: Int, override var translation: Vector3f,
) : HologramLine {

    private val cachedData = HashMap<Int, SynchedEntityData.DataValue<*>>()

    override var billboard: Billboard by Delegates.observable(billboard) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayBillboard(new))
    }
    override var scale: Float by Delegates.observable(scale) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayScale(new))
    }

    var item: ItemStack by Delegates.observable(item) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayItem(new))
    }

    var itemDisplayTransform: ItemDisplayTransform by Delegates.observable(itemDisplayTransform) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayItemTransform(new))
    }

    private fun createInitialData(): List<SynchedEntityData.DataValue<*>> {
        return listOf(
            PacketEntityData.displayItem(item),
            PacketEntityData.displayBillboard(billboard),
            PacketEntityData.displayItemTransform(itemDisplayTransform),
            PacketEntityData.displayScale(scale),
            PacketEntityData.displayTeleportInterpolation(teleportInterpolation),
            PacketEntityData.displayInterpolationDuration(transformationDuration)
        )
    }

    private fun cacheData(data: SynchedEntityData.DataValue<*>) {
        cachedData[data.id] = data
    }

    override fun spawn(
        location: Location,
        player: Player,
        placeholderContext: PlaceholderContext<Player>
    ): PacketEntity {
        val packetEntity = PacketEntity.create(location, EntityType.ITEM_DISPLAY, null)
            ?: throw Exception("Failed to create entity")
        val entityData = createInitialData()
        val packet = ClientboundSetEntityDataPacket(packetEntity.entityId, entityData)
        packetEntity.updatePacket = packet
        return packetEntity
    }

    override fun tick(spawnedHologramLine: SpawnedHologramLine) {
        val entityData = buildData(spawnedHologramLine)
        if (entityData.isEmpty()) return

        val packet = ClientboundSetEntityDataPacket(spawnedHologramLine.packetEntity.entityId, entityData)
        spawnedHologramLine.packetEntity.updatePacket = packet
        spawnedHologramLine.player.sendPacket(packet, false)
    }

    override fun buildData(placeholderContext: PlaceholderContext<Player>, player: Player): List<SynchedEntityData.DataValue<*>> {
        val data = ArrayList<SynchedEntityData.DataValue<*>>()

        data += cachedData.values.toList()
        cachedData.clear()

        return data
    }

    class Settings(
        val item: ItemStack,
        val height: Double = 0.3,
        val scale: Float = 1.0f,
        val billboard: Billboard = Billboard.CENTER,
        val itemDisplayTransform: ItemDisplayTransform,
        val conditions: List<ConditionHandle<Player>>,
        val failLine: LineSettings?,
        val transformationDuration: Int,
        val teleportInterpolation: Int,
        val translation: Vector3f
    ) : LineSettings {
        override fun create(): HologramLine {
            return ItemHologramLine(
                item,
                height,
                scale,
                billboard,
                itemDisplayTransform,
                { p ->
                    conditions.checkConditions(p)
                },
                failLine?.create(),
                transformationDuration,
                teleportInterpolation,
                translation
            )
        }
    }

    companion object : LineFactory {
        override fun load(section: ConfigurationSection, commonOptions: CommonHologramLineSettings): LineSettings? {
            val item = AquaticItem.loadFromYml(section.getConfigurationSection("item")) ?: return null
            val height = section.getDouble("height", commonOptions.height)
            val scale = section.getDouble("scale", commonOptions.scale.toDouble()).toFloat()
            val billboard = section.getString("billboard")?.let {
                Billboard.valueOf(it.uppercase())
            } ?: commonOptions.billboard
            val itemDisplayTransform =
                ItemDisplayTransform.valueOf(section.getString("item-display-transform", "NONE")!!.uppercase())
            val conditions = ConditionSerializer.fromSections<Player>(section.getSectionList("view-conditions"))
            val failLine = section.getConfigurationSection("fail-line")?.let {
                HologramSerializer.loadLine(it, commonOptions)
            }
            val translation = section.getString("translation")?.let {
                val args = it.split(";")
                Vector3f(args[0].toFloat(), args[1].toFloat(), args[2].toFloat())
            } ?: commonOptions.translation
            val transformationDuration = section.getInt("transformation-duration", commonOptions.transformationDuration)
            val teleportInterpolation = section.getInt("teleport-interpolation", commonOptions.teleportInterpolation)
            return Settings(
                item.getItem(),
                height,
                scale,
                billboard,
                itemDisplayTransform,
                conditions,
                failLine,
                transformationDuration,
                teleportInterpolation,
                translation
            )
        }
    }

}