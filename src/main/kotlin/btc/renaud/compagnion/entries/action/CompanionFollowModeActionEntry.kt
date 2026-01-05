package btc.renaud.compagnion.entries.action

import btc.renaud.compagnion.CompanionManager
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.*

@Entry("companion_follow_mode", "Toggle companion follow or wait mode", Colors.RED, "mdi:account-switch")
class CompanionFollowModeActionEntry(
    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
    val minDistance: Var<Double> = ConstVar(2.0),
    val maxDistance: Var<Double> = ConstVar(4.0),
) : ActionEntry {
    override fun ActionTrigger.execute() {
        CompanionManager.toggleFollow(
            player,
            minDistance.get(player, context),
            maxDistance.get(player, context)
        )
    }
}

