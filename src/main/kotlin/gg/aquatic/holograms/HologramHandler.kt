package gg.aquatic.holograms

import gg.aquatic.eventutils.EventUtils
import gg.aquatic.eventutils.event
import gg.aquatic.holograms.serialize.LineFactory
import org.bukkit.Bukkit
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

object HologramHandler {

    val tickingHolograms = ConcurrentHashMap<Long, MutableCollection<Hologram>>()
    val waitingHolograms = ConcurrentHashMap<Long, MutableCollection<Hologram>>()

    val lineFactories = HashMap<String, LineFactory>()

    private var initialized = false
    fun initialize(plugin: JavaPlugin) {
        if (initialized) return
        initialized = true

        if (EventUtils.plugin == null) {
            EventUtils.initialize(plugin)
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            for ((chunkId, holograms) in tickingHolograms) {
                for (hologram in holograms) {
                    hologram.tick()
                }
            }
        }, 1, 1)

        event<ChunkLoadEvent> {
            val chunkId = it.chunk.chunkKey
            val toLoad = waitingHolograms.remove(chunkId) ?: return@event
            val list = tickingHolograms.getOrPut(chunkId) { ArrayList() }
            for (hologram in toLoad) {
                hologram.chunk = it.chunk
                list += hologram
            }
        }
        event<ChunkUnloadEvent> {
            val chunkId = it.chunk.chunkKey
            val toWait = tickingHolograms.remove(chunkId) ?: return@event
            val list = waitingHolograms.getOrPut(chunkId) { ArrayList() }
            for (hologram in toWait) {
                hologram.chunk = null
                list += hologram
            }
        }
    }

    fun allHolograms(): Collection<Hologram> {
        return listOf(tickingHolograms.values.flatten(), waitingHolograms.values.flatten()).flatten()
    }

    fun destroyHolograms() {
        for (hologram in allHolograms()) {
            hologram.destroy()
        }
        waitingHolograms.clear()
        tickingHolograms.clear()
    }

    fun removeHologram(hologram: Hologram) {
        for ((chunkId, holograms) in tickingHolograms) {
            holograms.remove(hologram)
        }
        for ((chunkId, holograms) in waitingHolograms) {
            holograms.remove(hologram)
        }
    }
}