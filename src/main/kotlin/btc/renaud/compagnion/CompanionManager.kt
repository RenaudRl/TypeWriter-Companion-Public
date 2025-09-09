package btc.renaud.compagnion

import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entity.toProperty
import com.typewritermc.engine.paper.entry.entries.EntityDefinitionEntry
import com.typewritermc.engine.paper.utils.Sync
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.engine.paper.utils.toVector
import com.typewritermc.engine.paper.plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Tracks companion entities for players and handles simple follow behaviour.
 */
object CompanionManager {
    private data class Companion(
        val entity: FakeEntity,
        val activity: ActivityManager<in IndividualActivityContext>?,
        var job: Job? = null,
        var following: Boolean = true,
        var minDistance: Double = 2.0,
        var maxDistance: Double = 4.0,
        var lastMovingAnimation: Boolean? = null,
    )

    private val companions = mutableMapOf<UUID, Companion>()


    /** Spawn a companion for the player if they don't already have one. */
    fun summon(
        player: Player,
        definition: EntityDefinitionEntry,
        activityCreator: ActivityCreator = IdleActivity,
        minDistance: Double = 2.0,
        maxDistance: Double = 4.0,
    ) {
        if (companions.containsKey(player.uniqueId)) return
        val target = player.location.clone().add(player.location.direction.normalize().multiply(-maxDistance)).apply {
            y = player.location.y
            direction = player.location.toVector().subtract(toVector())
        }
        val entity = definition.create(player)
        entity.spawn(target.toProperty())
        ModelEngineSupport.prepare(entity)

        val context = IndividualActivityContext(emptyRef(), player, true, entity.state)
        val activity = ActivityManager(activityCreator.create(context, target.toProperty()))
        activity.initialize(context)
        activity.tick(context)
        entity.tick()

        val companion = Companion(
            entity,
            activity,
            minDistance = minDistance,
            maxDistance = maxDistance,
        )
        companions[player.uniqueId] = companion
        ModelEngineSupport.updateAnimation(entity, player, moving = false)
        companion.lastMovingAnimation = false
        startFollow(player)
    }

    /** Remove the player's current companion if present. */
    fun remove(player: Player) {
        companions.remove(player.uniqueId)?.let {
            it.job?.cancel()
            it.activity?.dispose(IndividualActivityContext(emptyRef(), player, entityState = it.entity.state))
            it.entity.dispose()
        }
    }

    /** Teleport the companion to the player's current location immediately. */
    fun teleportToPlayer(player: Player) {
        val companion = companions[player.uniqueId] ?: return
        val target = player.location.clone().add(player.location.direction.normalize().multiply(-companion.maxDistance)).apply {
            y = player.location.y
            direction = player.location.toVector().subtract(toVector())
        }
        val active = companion.activity?.activeProperties?.filterNot { it is PositionProperty } ?: emptyList()
        companion.entity.consumeProperties(listOf(target.toProperty()) + active)
        companion.entity.tick()
        companion.lastMovingAnimation = false
        ModelEngineSupport.updateAnimation(companion.entity, player, moving = false)
    }

    /** Toggle between follow and wait mode. */
    fun toggleFollow(player: Player, minDistance: Double, maxDistance: Double) {
        val companion = companions[player.uniqueId] ?: return
        companion.minDistance = minDistance
        companion.maxDistance = maxDistance
        companion.following = !companion.following
        if (companion.following) {
            startFollow(player)
        } else {
            companion.job?.cancel()
            companion.job = null
        }
    }

    /** Check if the player currently has an active companion. */
    fun hasCompanion(player: Player): Boolean = companions.containsKey(player.uniqueId)

    fun getCompanion(player: Player): FakeEntity? = companions[player.uniqueId]?.entity

    private fun startFollow(player: Player) {
        val companion = companions[player.uniqueId] ?: return
        companion.job?.cancel()
        companion.job = Dispatchers.Sync.launch {
            var lastPlayerLoc = player.location.clone()
            while (companion.following) {
                if (!player.isOnline) {
                    // Give the server a moment in case the player is temporarily marked offline
                    delay(50L)
                    if (!player.isOnline) {
                        companion.activity?.dispose(
                            IndividualActivityContext(emptyRef(), player, entityState = companion.entity.state)
                        )
                        companion.entity.dispose()
                        companions.remove(player.uniqueId)
                        break
                    }
                    continue
                }
                val playerLoc = player.location
                val playerDelta = playerLoc.toVector().subtract(lastPlayerLoc.toVector())
                val moving = playerDelta.lengthSquared() > 0.0001
                val playerSpeed = playerDelta.length()
                lastPlayerLoc = playerLoc.clone()

                companion.activity?.tick(
                    IndividualActivityContext(emptyRef(), player, true, companion.entity.state)
                )
                val active = companion.activity?.activeProperties?.filterNot { it is PositionProperty } ?: emptyList()

                val current = run {
                    companion.entity.property(PositionProperty::class)?.toBukkitLocation()
                        ?: try {
                            val clazz = Class.forName("entries.entity.NamedModelEngineEntity")
                            if (clazz.isInstance(companion.entity)) {
                                val method = clazz.getMethod("getEntity")
                                val base = method.invoke(companion.entity) as? FakeEntity
                                base?.property(PositionProperty::class)?.toBukkitLocation()
                            } else null
                        } catch (_: Throwable) {
                            null
                        }
                } ?: playerLoc

                // Handle world changes by immediately teleporting the companion
                if (current.world != playerLoc.world) {
                    teleportToPlayer(player)
                    continue
                }

                val distance = current.distance(playerLoc)
                // Teleport the companion if it falls too far behind the player
                if (distance > companion.maxDistance * 8) {
                    teleportToPlayer(player)
                    continue
                }

                val targetDistance = if (moving) companion.maxDistance else companion.minDistance
                val toPlayer = playerLoc.toVector().subtract(current.toVector())
                val desired = playerLoc.clone().subtract(
                    if (toPlayer.lengthSquared() == 0.0) {
                        playerLoc.direction.normalize().multiply(targetDistance)
                    } else {
                        toPlayer.normalize().multiply(targetDistance)
                    }
                )

                var moveVec = desired.toVector().subtract(current.toVector())
                val baseSpeed = companion.entity.state.speed.toDouble()
                var speed = if (moving) kotlin.math.max(playerSpeed, baseSpeed) else baseSpeed
                // Increase speed when the companion is far from the player
                if (distance > companion.maxDistance * 2) {
                    speed *= 1.5
                }
                if (moveVec.length() > speed) {
                    moveVec = moveVec.normalize().multiply(speed)
                }
                val next = current.clone().add(moveVec).apply {
                    this.direction = playerLoc.toVector().subtract(toVector())
                }

                // Attempt basic obstacle avoidance by stepping up blocks
                next.world?.let { world ->
                    val block = world.getBlockAt(next.blockX, next.blockY, next.blockZ)
                    if (!block.isPassable) {
                        var stepped = false
                        for (i in 1..2) {
                            val stepBlock = world.getBlockAt(next.blockX, next.blockY + i, next.blockZ)
                            val headBlock = world.getBlockAt(next.blockX, next.blockY + i + 1, next.blockZ)
                            if (stepBlock.isPassable && headBlock.isPassable) {
                                next.y = (next.blockY + i).toDouble()
                                stepped = true
                                break
                            }
                        }
                        if (!stepped) {
                            next.x = current.x
                            next.y = current.y
                            next.z = current.z
                        }
                    }

                    // Apply simple gravity so the companion stays on the ground
                    var newY = next.y
                    while (newY > world.minHeight &&
                        world.getBlockAt(next.blockX, (newY - 1).toInt(), next.blockZ).isPassable
                    ) {
                        newY -= 1.0
                    }
                    next.y = (newY - 1).toInt() + 1.0
                }

                val companionMoving = next.distanceSquared(current) > 0.0001
                if (companionMoving) {
                    // Continuously trigger the walk animation while moving so that
                    // short animations don't revert to idle mid-walk.
                    ModelEngineSupport.updateAnimation(companion.entity, player, true)
                    companion.lastMovingAnimation = true
                } else if (companion.lastMovingAnimation != false) {
                    companion.lastMovingAnimation = false
                    ModelEngineSupport.updateAnimation(companion.entity, player, false)
                }

                // Detect when the companion is stuck against an obstacle
                if (next.distanceSquared(current) < 0.0001 && distance > companion.minDistance) {
                    if (distance > companion.maxDistance * 4) {
                        teleportToPlayer(player)
                        continue
                    }
                }

                // Always move and tick the companion entity directly so that
                // wrapper entities (such as NamedModelEngineEntity) can update
                // both their model and any attached displays like nameplates.
                companion.entity.consumeProperties(listOf(next.toProperty()) + active)
                companion.entity.tick()
                try {
                    val clazz = Class.forName("entries.entity.NamedModelEngineEntity")
                    if (clazz.isInstance(companion.entity)) {
                        val method = clazz.getMethod("getEntity")
                        val base = method.invoke(companion.entity)
                        if (base is FakeEntity) base.tick()
                    }
                } catch (_: Throwable) {
                }
                // Run roughly once per server tick
                delay(50L)
    }
}

}



}
