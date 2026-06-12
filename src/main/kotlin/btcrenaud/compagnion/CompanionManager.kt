package btcrenaud.compagnion

import com.typewritermc.core.entries.Ref
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
 *
 * Each player can have multiple companions at once, each identified by the
 * [Ref] string of its [EntityDefinitionEntry].  Different definitions = different
 * companions; the same definition re-summoned replaces the previous one.
 */
object CompanionManager {
    data class Companion(
        val entity: FakeEntity,
        val definitionRef: String,
        val activity: ActivityManager<in IndividualActivityContext>?,
        var job: Job? = null,
        var following: Boolean = true,
        var minDistance: Double = 2.0,
        var maxDistance: Double = 4.0,
        var lastMovingAnimation: Boolean? = null,
    )

    // player UUID → (definition ref string → companion)
    private val companions = mutableMapOf<UUID, MutableMap<String, Companion>>()

    // ── helpers ──────────────────────────────────────────────────────────

    private fun companionsFor(player: Player): MutableMap<String, Companion> =
        companions.getOrPut(player.uniqueId) { mutableMapOf() }

    /** All companions for a player. */
    fun getAllCompanions(player: Player): List<Companion> =
        companions[player.uniqueId]?.values?.toList() ?: emptyList()

    /** A specific companion by definition ref. */
    fun getCompanion(player: Player, definitionRef: String): Companion? =
        companions[player.uniqueId]?.get(definitionRef)

    /** The first companion (backwards-compatible single-companion access). */
    fun getCompanion(player: Player): FakeEntity? =
        companions[player.uniqueId]?.values?.firstOrNull()?.entity

    /** Whether the player has at least one companion. */
    fun hasCompanion(player: Player): Boolean =
        companions[player.uniqueId]?.isNotEmpty() == true

    /** Number of active companions for the player. */
    fun companionCount(player: Player): Int =
        companions[player.uniqueId]?.size ?: 0

    // ── lifecycle ────────────────────────────────────────────────────────

    /**
     * Spawn a companion for the player.
     *
     * If a companion already exists for the same [definition] ref it is
     * removed first so the new one replaces it cleanly.
     */
    fun summon(
        player: Player,
        definition: EntityDefinitionEntry,
        definitionRef: String,
        activityCreator: ActivityCreator = IdleActivity,
        minDistance: Double = 2.0,
        maxDistance: Double = 4.0,
    ) {
        val playerCompanions = companionsFor(player)

        // Replace existing companion for the same definition
        playerCompanions[definitionRef]?.let { existing ->
            destroyCompanion(player, existing)
        }

        val target = player.location.clone().add(
            player.location.direction.normalize().multiply(-maxDistance)
        ).apply {
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
            entity = entity,
            definitionRef = definitionRef,
            activity = activity,
            minDistance = minDistance,
            maxDistance = maxDistance,
        )
        playerCompanions[definitionRef] = companion
        ModelEngineSupport.updateAnimation(entity, player, moving = false)
        companion.lastMovingAnimation = false
        startFollow(player, definitionRef)
    }

    /** Remove a specific companion by definition ref. */
    fun remove(player: Player, definitionRef: String) {
        val playerCompanions = companions[player.uniqueId] ?: return
        playerCompanions.remove(definitionRef)?.let { companion ->
            destroyCompanion(player, companion)
        }
        if (playerCompanions.isEmpty()) companions.remove(player.uniqueId)
    }

    /** Remove all companions for a player. */
    fun removeAll(player: Player) {
        val playerCompanions = companions.remove(player.uniqueId) ?: return
        playerCompanions.values.forEach { destroyCompanion(player, it) }
    }

    // ── per-companion operations ─────────────────────────────────────────

    fun teleportToPlayer(player: Player, definitionRef: String) {
        val companion = companions[player.uniqueId]?.get(definitionRef) ?: return
        val target = player.location.clone().add(
            player.location.direction.normalize().multiply(-companion.maxDistance)
        ).apply {
            y = player.location.y
            direction = player.location.toVector().subtract(toVector())
        }
        val active = companion.activity?.activeProperties?.filterNot { it is PositionProperty } ?: emptyList()
        companion.entity.consumeProperties(listOf(target.toProperty()) + active)
        companion.entity.tick()
        companion.lastMovingAnimation = false
        ModelEngineSupport.updateAnimation(companion.entity, player, moving = false)
    }

    fun toggleFollow(player: Player, definitionRef: String, minDistance: Double, maxDistance: Double) {
        val companion = companions[player.uniqueId]?.get(definitionRef) ?: return
        companion.minDistance = minDistance
        companion.maxDistance = maxDistance
        companion.following = !companion.following
        if (companion.following) {
            startFollow(player, definitionRef)
        } else {
            companion.job?.cancel()
            companion.job = null
        }
    }

    // ── internal ─────────────────────────────────────────────────────────

    private fun destroyCompanion(player: Player, companion: Companion) {
        companion.job?.cancel()
        companion.activity?.dispose(IndividualActivityContext(emptyRef(), player, entityState = companion.entity.state))
        companion.entity.dispose()
    }

    private fun startFollow(player: Player, definitionRef: String) {
        val companion = companions[player.uniqueId]?.get(definitionRef) ?: return
        companion.job?.cancel()
        companion.job = Dispatchers.Sync.launch {
            var lastPlayerLoc = player.location.clone()
            while (companion.following) {
                if (!player.isOnline) {
                    delay(50L)
                    if (!player.isOnline) {
                        companion.activity?.dispose(
                            IndividualActivityContext(emptyRef(), player, entityState = companion.entity.state)
                        )
                        companion.entity.dispose()
                        companions[player.uniqueId]?.remove(definitionRef)
                        if (companions[player.uniqueId]?.isEmpty() == true) companions.remove(player.uniqueId)
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

                if (current.world != playerLoc.world) {
                    teleportToPlayer(player, definitionRef)
                    continue
                }

                val distance = current.distance(playerLoc)
                if (distance > companion.maxDistance * 8) {
                    teleportToPlayer(player, definitionRef)
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
                if (distance > companion.maxDistance * 2) {
                    speed *= 1.5
                }
                if (moveVec.length() > speed) {
                    moveVec = moveVec.normalize().multiply(speed)
                }
                val next = current.clone().add(moveVec).apply {
                    this.direction = playerLoc.toVector().subtract(toVector())
                }

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
                    ModelEngineSupport.updateAnimation(companion.entity, player, true)
                    companion.lastMovingAnimation = true
                } else if (companion.lastMovingAnimation != false) {
                    companion.lastMovingAnimation = false
                    ModelEngineSupport.updateAnimation(companion.entity, player, false)
                }

                if (next.distanceSquared(current) < 0.0001 && distance > companion.minDistance) {
                    if (distance > companion.maxDistance * 4) {
                        teleportToPlayer(player, definitionRef)
                        continue
                    }
                }

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
                delay(50L)
            }
        }
    }
}
