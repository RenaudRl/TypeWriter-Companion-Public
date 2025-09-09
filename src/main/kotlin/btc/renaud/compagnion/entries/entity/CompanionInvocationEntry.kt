package btc.renaud.compagnion.entries.entity

import btc.renaud.compagnion.CompanionManager
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.OnlyTags
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.entry.entity.IdleActivity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

@Entry("companion_invocation", "Spawn and manage the player's companion", Colors.RED, "mdi:account-heart")
class CompanionInvocationEntry(
    override val id: String = "",
    override val name: String = "",
    override val children: List<Ref<AudienceEntry>> = emptyList(),
    val criteria: List<Criteria> = emptyList(),
    @OnlyTags("npc_definition", "modelengine_definition")
    val definition: Ref<out EntityDefinitionEntry> = emptyRef(),
    val activity: Ref<out IndividualEntityActivityEntry> = emptyRef(),
    val minDistance: Var<Double> = ConstVar(2.0),
    val maxDistance: Var<Double> = ConstVar(4.0),
) : AudienceFilterEntry {
    override suspend fun display(): AudienceFilter =
        CompanionInvocationDisplay(ref(), criteria, definition, activity, minDistance, maxDistance)
}

class CompanionInvocationDisplay(
    ref: Ref<out AudienceFilterEntry>,
    private val criteria: List<Criteria>,
    private val definition: Ref<out EntityDefinitionEntry>,
    private val activity: Ref<out IndividualEntityActivityEntry>,
    private val minDistance: Var<Double>,
    private val maxDistance: Var<Double>,
) : AudienceFilter(ref) {

    override fun filter(player: Player): Boolean =
        criteria.isEmpty() || criteria.matches(player, context())

    override fun onPlayerFilterAdded(player: Player) {
        val def = definition.get() ?: return
        val act = activity.get()
        CompanionManager.summon(
            player,
            def,
            act ?: IdleActivity,
            minDistance.get(player, context()),
            maxDistance.get(player, context())
        )
    }

    override fun onPlayerFilterRemoved(player: Player) {
        runCatching { CompanionManager.remove(player) }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        if (!canConsider(player)) return
        CompanionManager.teleportToPlayer(player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (!canConsider(player)) return
        val def = definition.get() ?: return
        val act = activity.get()
        CompanionManager.remove(player)
        CompanionManager.summon(
            player,
            def,
            act ?: IdleActivity,
            minDistance.get(player, context()),
            maxDistance.get(player, context())
        )
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        CompanionManager.remove(event.player)
    }
}
