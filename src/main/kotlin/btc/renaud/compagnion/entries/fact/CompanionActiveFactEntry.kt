package btc.renaud.compagnion.entries.fact

import btc.renaud.compagnion.CompanionManager
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.entries.GroupEntry
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.facts.FactData
import org.bukkit.entity.Player

@Entry("companion_active", "If the player currently has a companion", Colors.PURPLE, "mdi:account-question")
class CompanionActiveFactEntry(
    override val id: String = "",
    override val name: String = "",
    override val comment: String = "",
    override val group: Ref<GroupEntry> = emptyRef(),
) : ReadableFactEntry {
    override fun readSinglePlayer(player: Player): FactData {
        return if (CompanionManager.hasCompanion(player)) FactData(1) else FactData(0)
    }
}
