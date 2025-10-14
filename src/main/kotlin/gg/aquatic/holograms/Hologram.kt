package gg.aquatic.holograms

import gg.aquatic.holograms.line.TextHologramLine
import gg.aquatic.holograms.serialize.LineSettings
import gg.aquatic.execute.checkConditions
import gg.aquatic.execute.requirement.ConditionHandle
import gg.aquatic.replace.placeholder.PlaceholderContext
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

class Hologram(
    location: Location,
    val filter: (Player) -> Boolean,
    val placeholderContext: () -> PlaceholderContext<Player>,
    val viewDistance: Int,
    lines: Collection<HologramLine>,
) {

    var chunk: Chunk? = if (location.chunk.isLoaded) location.chunk else null
    var seat: Int? = null
        private set

    fun setAsPassenger(seat: Int?) {
        this.seat = seat
        viewers.forEach { (_, lines) ->
            for (line in lines.lines) {
                line.setAsPassenger(seat)
            }
        }
    }

    fun setLines(lines: Collection<HologramLine>) {
        this.lines.clear()
        this.lines.addAll(lines)

        tickRange()
        destroyLines()
        for (player in viewers.keys) {
            showOrUpdate(player)
        }
    }

    fun setLineText(lineIndex: Int, text: String) {
        val line = lines.elementAtOrNull(lineIndex) ?: return
        if (line !is TextHologramLine) return

        line.text = text
    }

    fun setTeleportInterpolation(interpolation: Int) {
        for (line in lines) {
            line.teleportInterpolation = interpolation
        }
    }

    fun setTransformationInterpolationDuration(duration: Int) {
        for (line in lines) {
            line.transformationDuration = duration
        }
    }

    fun setScale(scale: Float) {
        for (line in lines) {
            line.scale = scale
        }
    }

    var location = location
        private set

    @Volatile
    private var rangeTick = 0

    val lines = ConcurrentHashMap.newKeySet<HologramLine>().apply { addAll(lines) }
    val viewers = ConcurrentHashMap<Player, HologramViewer>()

    init {
        val chunkId = location.chunk.chunkKey
        if (this.chunk == null) {
            HologramHandler.waitingHolograms.getOrPut(chunkId) { ArrayList() }.add(this)
        } else {
            HologramHandler.tickingHolograms.getOrPut(chunkId) { ArrayList() }.add(this)
            checkPlayersRange()
            tick()
        }
    }

    fun tick() {
        tickRange()
        viewers.forEach { (player, _) ->
            // CurrentLineIndex -> Get Hologram line -> Compare Hologram Line with SpawnedHologramLine
            // If it is the same, then skip & add index, otherwise update line, add to the set & move other lines

            // First process all line text update & visibility and then apply changes

            showOrUpdate(player)
        }
    }

    fun showOrUpdate(player: Player) {
        val viewer = viewers.getOrPut(player) { HologramViewer(player, placeholderContext(), mutableSetOf()) }
        val lines = viewer.lines

        fun getVisibleLine(player: Player, hologramLine: HologramLine): HologramLine? {
            if (hologramLine.filter(player)) {
                return hologramLine
            }
            return getVisibleLine(player, hologramLine.failLine ?: return null)
        }

        val remainingLines = lines.toMutableSet()
        val newLines = mutableMapOf<HologramLine, SpawnedHologramLine?>()
        for (line in this.lines) {
            val visibleLine = getVisibleLine(player, line) ?: continue
            val spawnedLine = lines.find { it.line == visibleLine }
            newLines[visibleLine] = spawnedLine
            remainingLines.remove(spawnedLine ?: continue)
        }

        for (remainingLine in remainingLines) {
            remainingLine.destroy()
            lines.remove(remainingLine)
        }

        var height = 0.0
        for ((line, nullableSpawnedLine) in newLines) {
            val halfHeight = line.height / 2.0
            height += halfHeight
            val location = this.location.clone().add(0.0, height, 0.0)
            if (nullableSpawnedLine == null) {
                val packetEntity = line.spawn(location, player, viewer.context)
                val newLine = SpawnedHologramLine(this, player, line, location, viewer.context, packetEntity)
                lines.add(newLine)
            } else {
                nullableSpawnedLine.tick()
                if (nullableSpawnedLine.currentLocation == location) continue
                nullableSpawnedLine.move(location)
            }
        }
    }

    private fun tickRange() {
        rangeTick++
        if (rangeTick < 5) {
            return
        }
        rangeTick = 0
        checkPlayersRange()
    }

    fun checkPlayersRange() {
        val remaining = viewers.toMutableMap()
        val chunk = this.chunk
        if (chunk != null) {
            for (trackedByPlayer in chunk.trackedBy()) {
                if (!filter(trackedByPlayer)) continue
                if (trackedByPlayer.world != location.world) continue
                if (trackedByPlayer.location.distanceSquared(location) <= viewDistance * viewDistance) {
                    remaining.remove(trackedByPlayer)
                    if (viewers.containsKey(trackedByPlayer)) {
                        continue
                    }
                    showOrUpdate(trackedByPlayer)
                }
            }
        }

        for (removed in remaining) {
            removed.value.lines.forEach { it.destroy() }
            viewers.remove(removed.key)
        }
    }

    fun destroyLines() {
        viewers.forEach { (_, spawnedHologramLines) ->
            spawnedHologramLines.lines.forEach { it.destroy() }
            spawnedHologramLines.lines.clear()
        }
    }

    fun destroy() {
        HologramHandler.removeHologram(this)
        destroyLines()
        viewers.clear()
        lines.clear()

    }

    fun teleport(location: Location) {
        this.location = location
        viewers.forEach { (player, _) ->
            showOrUpdate(player)
        }
    }

    class Settings(
        val lines: List<LineSettings>,
        val conditions: List<ConditionHandle<Player>>,
        val viewDistance: Int,
    ) {
        fun create(
            location: Location,
            placeholderContext: () -> PlaceholderContext<Player>,
            filter: (Player) -> Boolean = { true },
        ): Hologram = Hologram(
            location,
            { p ->
                filter(p) && conditions.checkConditions(p)
            },
            placeholderContext,
            viewDistance,
            lines.map { it.create() }.toSet()
        )
    }

    class HologramViewer(
        val player: Player,
        val context: PlaceholderContext<Player>,
        val lines: MutableSet<SpawnedHologramLine>
    )
}