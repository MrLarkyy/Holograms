package gg.aquatic.holograms.line

import gg.aquatic.holograms.CommonHologramLineSettings
import gg.aquatic.holograms.HologramLine
import gg.aquatic.holograms.HologramSerializer
import gg.aquatic.holograms.SpawnedHologramLine
import gg.aquatic.holograms.serialize.LineFactory
import gg.aquatic.holograms.serialize.LineSettings
import gg.aquatic.execute.checkConditions
import gg.aquatic.execute.getSectionList
import gg.aquatic.execute.minimessage.toMMComponent
import gg.aquatic.execute.requirement.ConditionHandle
import gg.aquatic.execute.requirement.ConditionSerializer
import gg.aquatic.packetutils.PacketEntity
import gg.aquatic.packetutils.PacketEntityData
import gg.aquatic.packetutils.util.sendPacket
import gg.aquatic.packetutils.util.toNMSComponent
import gg.aquatic.replace.placeholder.PlaceholderContext
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.joml.Vector3f
import kotlin.properties.Delegates

class TextHologramLine(
    override var height: Double,
    override var filter: (Player) -> Boolean,
    override var failLine: HologramLine?,
    text: String,
    lineWidth: Int,
    scale: Float = 1.0f,
    billboard: Billboard = Billboard.CENTER,
    hasShadow: Boolean = true,
    backgroundColor: Color? = null,
    isSeeThrough: Boolean = true,
    transformationDuration: Int = 0,
    teleportInterpolation: Int,
    translation: Vector3f
) : HologramLine {
    override fun spawn(
        location: Location,
        player: Player,
        placeholderContext: PlaceholderContext<Player>,
    ): PacketEntity {
        val packetEntity =
            PacketEntity.create(location, EntityType.TEXT_DISPLAY, null)
                ?: throw Exception("Failed to create entity")
        val entityData = createInitialData(player, placeholderContext)
        val packet = ClientboundSetEntityDataPacket(packetEntity.entityId, entityData)
        packetEntity.updatePacket = packet
        return packetEntity
    }

    private val cachedData = HashMap<Int, SynchedEntityData.DataValue<*>>()

    override var teleportInterpolation: Int by Delegates.observable(teleportInterpolation) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayTeleportInterpolation(new))
    }

    override var translation: Vector3f by Delegates.observable(translation) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayTranslation(new))
    }

    override var billboard: Billboard by Delegates.observable(billboard) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayBillboard(new))
    }

    override var transformationDuration: Int by Delegates.observable(transformationDuration) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayInterpolationDuration(new))
    }

    override var scale: Float by Delegates.observable(scale) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayScale(new))
    }

    var lineWidth: Int by Delegates.observable(lineWidth) { _, old, new ->
        if (old == new) return@observable
        cacheData(PacketEntityData.displayLineWidth(new))
    }

    var hasShadow: Boolean by Delegates.observable(hasShadow) { _, old, new ->
        if (old == new) return@observable
        cacheData(
            PacketEntityData.displayTextFlags(
                TextDisplay.TextAlignment.CENTER,
                new,
                isSeeThrough,
                backgroundColor == null
            )
        )
    }

    var isSeeThrough: Boolean by Delegates.observable(isSeeThrough) { _, old, new ->
        if (old == new) return@observable
        cacheData(
            PacketEntityData.displayTextFlags(
                TextDisplay.TextAlignment.CENTER,
                hasShadow,
                new,
                backgroundColor == null
            )
        )
    }

    var backgroundColor: Color? by Delegates.observable(backgroundColor) { _, old, new ->
        if (old == new) return@observable
        if (new == null) {
            cacheData(
                PacketEntityData.displayTextFlags(
                    TextDisplay.TextAlignment.CENTER,
                    hasShadow,
                    isSeeThrough,
                    true
                )
            )
        } else {
            cacheData(
                PacketEntityData.displayTextFlags(
                    TextDisplay.TextAlignment.CENTER,
                    hasShadow,
                    isSeeThrough,
                    false
                )
            )
            cacheData(PacketEntityData.displayBackgroundColor(new.asARGB()))
        }
    }

    var text: String by Delegates.observable(text) { _, old, new ->
        if (old == new) return@observable
        textContextItem = null
    }

    private fun createInitialData(player: Player, placeholderContext: PlaceholderContext<Player>): List<SynchedEntityData.DataValue<*>> {
        val list = mutableListOf< SynchedEntityData.DataValue<*>>(
            PacketEntityData.displayInterpolationDuration(transformationDuration),
            PacketEntityData.displayTeleportInterpolation(teleportInterpolation),
            PacketEntityData.displayLineWidth(lineWidth),
            PacketEntityData.displayBillboard(billboard),
            PacketEntityData.displayTextFlags(TextDisplay.TextAlignment.CENTER, hasShadow, isSeeThrough, backgroundColor == null),
            PacketEntityData.displayTranslation(translation),
            PacketEntityData.displayScale(scale)
        )
        if (backgroundColor != null) {
            list += PacketEntityData.displayBackgroundColor(backgroundColor!!.asARGB())
        }

        val item = textContextItem
        if (item == null) {
            val item = placeholderContext.createItem(player, text.toMMComponent())
            textContextItem = item
            val data = PacketEntityData.displayText(item.latestState.value.toNMSComponent())
            list.add(data)
        } else {
            val data = PacketEntityData.displayText(item.latestState.value.toNMSComponent())
            list.add(data)
        }

        return list
    }

    private fun cacheData(data: SynchedEntityData.DataValue<*>) {
        cachedData[data.id] = data
    }

    override fun tick(spawnedHologramLine: SpawnedHologramLine) {
        val data = buildData(spawnedHologramLine)
        if (data.isEmpty()) return
        val packet = ClientboundSetEntityDataPacket(spawnedHologramLine.packetEntity.entityId, data)
        spawnedHologramLine.packetEntity.updatePacket = packet
        spawnedHologramLine.player.sendPacket(packet, false)
    }

    private var textContextItem: PlaceholderContext<Player>.ComponentItem? = null

    override fun buildData(
        placeholderContext: PlaceholderContext<Player>,
        player: Player
    ): List<SynchedEntityData.DataValue<*>> {
        val data = ArrayList<SynchedEntityData.DataValue<*>>()

        val item = textContextItem
        if (item == null) {
            val item = placeholderContext.createItem(player, text.toMMComponent())
            textContextItem = item

            data += PacketEntityData.displayText(item.latestState.value.toNMSComponent())
        } else {
            val result = item.tryUpdate(player)
            if (result.wasUpdated) {
                data += PacketEntityData.displayText(item.latestState.value.toNMSComponent())
            }
        }

        data += cachedData.values
        cachedData.clear()

        return data
    }

    class Settings(
        val height: Double,
        val text: String,
        val lineWidth: Int,
        val scale: Float = 1.0f,
        val billboard: Billboard = Billboard.CENTER,
        val conditions: List<ConditionHandle<Player>>,
        val hasShadow: Boolean,
        val backgroundColor: Color?,
        val isSeeThrough: Boolean,
        val transformationDuration: Int,
        val failLine: LineSettings?,
        val teleportInterpolation: Int,
        val translation: Vector3f
    ) : LineSettings {
        override fun create(): HologramLine {
            return TextHologramLine(
                height,
                { p ->
                    conditions.checkConditions(p)
                },
                failLine?.create(),
                text,
                lineWidth,
                scale,
                billboard,
                hasShadow,
                backgroundColor,
                isSeeThrough,
                transformationDuration,
                teleportInterpolation,
                translation
            )
        }
    }

    companion object : LineFactory {
        override fun load(section: ConfigurationSection, commonOptions: CommonHologramLineSettings): LineSettings? {
            val text = section.getString("text") ?: return null
            val height = section.getDouble("height", commonOptions.height)
            val lineWidth = section.getInt("line-width", 100)
            val scale = section.getDouble("scale", commonOptions.scale.toDouble()).toFloat()
            val billboard = section.getString("billboard")?.let {
                Billboard.valueOf(it.uppercase())
            } ?: commonOptions.billboard
            val conditions = ConditionSerializer.fromSections<Player>(section.getSectionList("view-conditions"))
            val failLine = section.getConfigurationSection("fail-line")?.let {
                HologramSerializer.loadLine(it, commonOptions)
            }
            val hasShadow = section.getBoolean("has-shadow", false)
            val backgroundColorStr = section.getString("background-color")
            val isSeeThrough = section.getBoolean("is-see-through", true)
            val transformationDuration = section.getInt("transformation-duration", commonOptions.transformationDuration)
            val backgroundColor = if (backgroundColorStr != null) {
                val args = backgroundColorStr.split(";").map { it.toIntOrNull() ?: 0 }
                Color.fromARGB(args.getOrNull(3) ?: 255, args[0], args[1], args[2])
            } else null
            val teleportInterpolation = section.getInt("teleport-interpolation", commonOptions.teleportInterpolation)
            val translation = section.getString("translation")?.let {
                val args = it.split(";")
                Vector3f(args[0].toFloat(), args[1].toFloat(), args[2].toFloat())
            } ?: commonOptions.translation

            return Settings(
                height,
                text,
                lineWidth,
                scale,
                billboard,
                conditions,
                hasShadow,
                backgroundColor,
                isSeeThrough,
                transformationDuration,
                failLine,
                teleportInterpolation,
                translation
            )
        }
    }
}