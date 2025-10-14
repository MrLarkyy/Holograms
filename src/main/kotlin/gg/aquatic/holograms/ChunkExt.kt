package gg.aquatic.holograms

import net.minecraft.world.level.ChunkPos
import org.bukkit.Chunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Player

fun Player.isChunkTracked(chunk: Chunk): Boolean {
    return chunk.trackedBy(this)
}

fun Chunk.trackedBy(): Collection<Player> {
    val craftWorld = this.world as CraftWorld
    return craftWorld.handle.chunkSource.chunkMap.getPlayers(ChunkPos(this.x, this.z), false)
        .map { it.bukkitEntity as Player }
}

fun Chunk.trackedBy(player: Player): Boolean {
    return trackedBy().contains(player)
}