package btc.renaud.compagnion.entries.action

import btc.renaud.compagnion.CompanionManager
import com.github.retrooper.packetevents.protocol.player.InteractionHand
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.EntryListener
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.entries.EventEntry
import com.typewritermc.engine.paper.entry.triggerAllFor
import com.typewritermc.engine.paper.events.AsyncFakeEntityInteract

@Entry("companion_interact", "Triggers when the player clicks their companion", Colors.RED, "mdi:account-child-circle")
class CompanionInteractActionEntry(
    override val id: String = "",
    override val name: String = "",
    val criteria: List<Criteria> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
) : EventEntry

@EntryListener(CompanionInteractActionEntry::class)
fun onCompanionInteract(event: AsyncFakeEntityInteract, query: Query<CompanionInteractActionEntry>) {
    if (event.hand != InteractionHand.MAIN_HAND ||
        event.action != WrapperPlayClientInteractEntity.InteractAction.INTERACT) return

    val player = event.player
    val companion = CompanionManager.getCompanion(player) ?: return
    if (event.entityId != companion.entityId) return
    val entries = query.find().filter { it.criteria.matches(player, context()) }.toList()
    if (entries.isEmpty()) return
    entries.triggerAllFor(player, context())
}


