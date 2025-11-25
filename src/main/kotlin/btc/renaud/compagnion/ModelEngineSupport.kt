package btc.renaud.compagnion

import com.typewritermc.engine.paper.entry.entity.FakeEntity
import org.bukkit.entity.Player

/**
 * Handles optional integration with the ModelEngine extension.
 *
 * All calls rely on reflection so the extension remains optional
 * and the code still runs when ModelEngine is absent.
 */
object ModelEngineSupport {
    /**
     * Ensure ModelEngine entities are actively ticked so their models update.
     */
    fun prepare(entity: FakeEntity) {
        try {
            val modelEngineClass = Class.forName("entries.entity.ModelEngineEntity")
            val namedModelEngineClass = Class.forName("entries.entity.NamedModelEngineEntity")
            when {
                modelEngineClass.isInstance(entity) -> {
                    val field = modelEngineClass.getDeclaredField("tickInThread")
                    field.isAccessible = true
                    field.setBoolean(entity, true)
                }
                namedModelEngineClass.isInstance(entity) -> {
                    val method = namedModelEngineClass.getMethod("getEntity")
                    val base = method.invoke(entity)
                    if (modelEngineClass.isInstance(base)) {
                        val field = modelEngineClass.getDeclaredField("tickInThread")
                        field.isAccessible = true
                        field.setBoolean(base, true)
                    }
                }
            }
        } catch (_: Throwable) {
            // ModelEngine not present or changed API - ignore
        }
    }

    /**
     * Play the idle or walk animation depending on movement state.
     */
    fun updateAnimation(entity: FakeEntity, player: Player, moving: Boolean) {
        try {
            val modelClass = Class.forName("entries.entity.ModelEngineEntity")
            val namedClass = Class.forName("entries.entity.NamedModelEngineEntity")
            val animatableClass = Class.forName("entries.entity.AnimatableEntity")
            val base = when {
                modelClass.isInstance(entity) -> entity
                namedClass.isInstance(entity) -> {
                    val method = namedClass.getMethod("getEntity")
                    method.invoke(entity)
                }
                else -> return
            }
            if (!animatableClass.isInstance(base)) return

            // Force the entity's stance so ModelEngine swaps to the walk state
            try {
                val entityField = modelClass.getDeclaredField("entity")
                entityField.isAccessible = true
                val dummy = entityField.get(base)
                val setWalking = dummy.javaClass.getMethod("setWalking", Boolean::class.javaPrimitiveType)
                val setStrafing = dummy.javaClass.getMethod("setStrafing", Boolean::class.javaPrimitiveType)
                setWalking.invoke(dummy, moving)
                setStrafing.invoke(dummy, moving)

                val walkingField = modelClass.getDeclaredField("walking")
                walkingField.isAccessible = true
                walkingField.setBoolean(base, moving)
            } catch (_: Throwable) {
                // Ignore; ModelEngine internals changed
            }

            val settings = modelClass.getMethod("getDefaultAnimationSettings").invoke(base)
            val settingsClass = settings.javaClass
            val idleVar = settingsClass.getMethod("getIdle").invoke(settings)
            val walkVar = settingsClass.getMethod("getWalk").invoke(settings)
            val varClass = Class.forName("com.typewritermc.engine.paper.entry.entries.Var")
            val getMethod = varClass.getMethod("get", Player::class.java)
            val animationName = (if (moving) {
                getMethod.invoke(walkVar, player)
            } else {
                getMethod.invoke(idleVar, player)
            } as? String) ?: return

            val playMethod = animatableClass.getMethod(
                "playAnimation",
                String::class.java,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            playMethod.invoke(base, animationName, 0.0, 0.0, 1.0, true)
        } catch (_: Throwable) {
            // Ignore; ModelEngine not installed or incompatible
        }
    }
}


