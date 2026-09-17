package com.alesharik.digitalgrid.block.menu

import com.alesharik.digitalgrid.DigitalgridRegistry
import com.alesharik.digitalgrid.DigitalgridTags
import com.alesharik.digitalgrid.din.item.plc.component.PlcComponents
import com.alesharik.digitalgrid.recipe.ASSEMBLY_ATTACH_TYPE
import com.alesharik.digitalgrid.recipe.ASSEMBLY_CRAFT_TYPE
import com.alesharik.digitalgrid.recipe.AssemblyAttachRecipe
import com.alesharik.digitalgrid.recipe.AssemblyCraftRecipe
import com.alesharik.digitalgrid.recipe.AssemblyMatching
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.SlotItemHandler

/**
 * Menu for the Assembly Table. Owns a transient 13-slot inventory (slot 0 = main, 1-12 = materials)
 * that lives only while the GUI is open; on close every item is returned to the player. The block
 * entity stores nothing.
 *
 * Driven by two data recipe types — [ASSEMBLY_ATTACH_TYPE] (PLC + materials → PLC component) and
 * [ASSEMBLY_CRAFT_TYPE] (materials ↔ assembled item). Both are reversible: the main slot holds a
 * recipe's result while the material slots hold one reserved recipe's worth, and it makes no
 * difference which side the player supplied. Taking the result or closing consumes the reserved
 * materials (assembly); pulling a material out destroys the result (disassembly).
 */
class AssemblyTableMenu(
    containerId: Int,
    playerInventory: Inventory,
    private val access: ContainerLevelAccess,
) : AbstractContainerMenu(DigitalgridRegistry.Menus.ASSEMBLY_TABLE.get(), containerId) {

    private val level: Level = playerInventory.player.level()

    val inventory = object : ItemStackHandler(SLOT_COUNT) {
        override fun getSlotLimit(slot: Int): Int = if (slot == MAIN_SLOT) 1 else super.getSlotLimit(slot)
        override fun onContentsChanged(slot: Int) = handleInventoryChange()
    }

    private val installed = mutableListOf<ResourceLocation>()
    private var craftRecipeId: ResourceLocation? = null
    private var lastMain: ItemStack = ItemStack.EMPTY
    private var updating = false

    init {
        addSlot(SlotItemHandler(inventory, 0, 29, 37))
        for (row in 0..2) {
            for (col in 0..3) {
                addSlot(SlotItemHandler(inventory, 1 + col + row * 4, 85 + col * 18, 17 + row * 18))
            }
        }
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }
    }

    override fun stillValid(player: Player): Boolean =
        stillValid(access, player, DigitalgridRegistry.Blocks.ASSEMBLY_TABLE)

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val originalStack = stack.copy()
        if (index < TABLE_SLOTS) {
            if (!moveItemStackTo(stack, TABLE_SLOTS, slots.size, true)) return ItemStack.EMPTY
        } else {
            val isPlc = stack.`is`(DigitalgridTags.Items.ASSEMBLABLE)
            val moved = if (isPlc) {
                moveItemStackTo(stack, 0, 1, false)
            } else {
                moveItemStackTo(stack, 1, TABLE_SLOTS, false)
            }
            if (!moved) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        return originalStack
    }

    override fun removed(player: Player) {
        super.removed(player)
        if (!level.isClientSide) onContainerClosed(player)
    }

    // ---- Logic ported from the former AssemblyTableBlockEntity (dupe-safe, unchanged semantics) ----

    fun onContainerClosed(player: Player) {
        updating = true
        try {
            craftRecipeId?.let { id -> resolveCraft(id)?.let(::consumeCraft) }
            craftRecipeId = null
            consumeInstalled()
            for (i in 0 until inventory.slots) {
                val stack = inventory.getStackInSlot(i)
                if (!stack.isEmpty) {
                    inventory.setStackInSlot(i, ItemStack.EMPTY)
                    player.inventory.placeItemBackInInventory(stack)
                }
            }
            lastMain = ItemStack.EMPTY
        } finally {
            updating = false
        }
    }

    private fun handleInventoryChange() {
        if (level.isClientSide || updating) return
        updating = true
        try {
            val main = inventory.getStackInSlot(MAIN_SLOT)
            if (!ItemStack.matches(main, lastMain)) {
                craftRecipeId?.let { id -> resolveCraft(id)?.let(::consumeCraft) }
                craftRecipeId = null
                consumeInstalled()
                if (main.`is`(DigitalgridTags.Items.ASSEMBLABLE)) materializeExisting(main)
                else if (!main.isEmpty) materializeCraft(main)
            }
            reconcile(main)
            lastMain = inventory.getStackInSlot(MAIN_SLOT).copy()
        } finally {
            updating = false
        }
    }

    private fun reconcile(main: ItemStack) {
        if (main.`is`(DigitalgridTags.Items.ASSEMBLABLE)) {
            craftRecipeId = null
            syncComponents(main)
            return
        }
        installed.clear()
        val preview = craftRecipeId
        if (preview != null) {
            val recipe = resolveCraft(preview)
            if (recipe != null && craftSatisfied(recipe)) return
            inventory.setStackInSlot(MAIN_SLOT, ItemStack.EMPTY)
            craftRecipeId = null
        }
        if (inventory.getStackInSlot(MAIN_SLOT).isEmpty) tryCraft()
    }

    private fun syncComponents(main: ItemStack) {
        val recipes = attachRecipesById()
        val counts = collectMaterialCounts()
        val componentType = DigitalgridRegistry.DataComponents.PLC_COMPONENTS.get()
        val current = (main.get(componentType)?.ids ?: emptyList()).toMutableList()
        val reserved = mutableMapOf<Item, Int>()

        val missing = installed.filter { id ->
            val req = recipes[id]?.let { AssemblyMatching.requirements(it.ingredients) } ?: return@filter false
            !AssemblyMatching.canCover(req, counts, reserved)
        }
        missing.forEach { id ->
            installed.remove(id)
            current.remove(id)
        }

        for ((id, recipe) in recipes) {
            if (id in current) continue
            val req = AssemblyMatching.requirements(recipe.ingredients)
            if (AssemblyMatching.canCover(req, counts, reserved)) {
                current.add(id)
                installed.add(id)
            }
        }

        if ((main.get(componentType)?.ids ?: emptyList()) != current) {
            if (current.isEmpty()) main.remove(componentType)
            else main.set(componentType, PlcComponents(current.toList()))
            inventory.setStackInSlot(MAIN_SLOT, main)
        }
    }

    private fun materializeExisting(main: ItemStack) {
        val ids = main.get(DigitalgridRegistry.DataComponents.PLC_COMPONENTS.get())?.ids ?: return
        if (ids.isEmpty()) return
        val recipes = attachRecipesById()
        val toPlace = mutableListOf<ItemStack>()
        val materialized = mutableListOf<ResourceLocation>()
        for (id in ids) {
            val recipe = recipes[id] ?: continue
            materialized.add(id)
            for (ingredient in recipe.ingredients) {
                val representative = ingredient.items.firstOrNull() ?: continue
                toPlace.add(representative.copy())
            }
        }
        if (toPlace.isEmpty()) return
        val emptySlots = MATERIAL_SLOTS.filter { inventory.getStackInSlot(it).isEmpty }
        if (toPlace.size > emptySlots.size) return
        toPlace.forEachIndexed { i, stack -> inventory.setStackInSlot(emptySlots[i], stack) }
        installed.addAll(materialized)
    }

    /**
     * Reverse of [tryCraft]: the player put a finished item in the main slot, so conjure the
     * ingredients of the recipe that produces it into the empty material slots and reserve them.
     *
     * From here the state is indistinguishable from having just assembled that item — pulling a
     * material out destroys it (disassembly), while taking it or closing consumes the materials.
     * Both directions therefore fall out of [reconcile] and [consumeCraft] unchanged.
     */
    private fun materializeCraft(main: ItemStack) {
        val holder = level.recipeManager.getAllRecipesFor(ASSEMBLY_CRAFT_TYPE).firstOrNull {
            val result = it.value().result
            ItemStack.isSameItemSameComponents(main, result) && main.count >= result.count
        } ?: return
        val toPlace = mutableListOf<ItemStack>()
        for (counted in holder.value().ingredients) {
            val representative = counted.ingredient.items.firstOrNull() ?: return
            var remaining = counted.count
            while (remaining > 0) {
                val stack = representative.copy()
                stack.count = minOf(remaining, stack.maxStackSize)
                remaining -= stack.count
                toPlace.add(stack)
            }
        }
        if (toPlace.isEmpty()) return
        val emptySlots = MATERIAL_SLOTS.filter { inventory.getStackInSlot(it).isEmpty }
        if (toPlace.size > emptySlots.size) return
        toPlace.forEachIndexed { i, stack -> inventory.setStackInSlot(emptySlots[i], stack) }
        craftRecipeId = holder.id()
    }

    private fun tryCraft() {
        val counts = collectMaterialCounts()
        for (holder in level.recipeManager.getAllRecipesFor(ASSEMBLY_CRAFT_TYPE)) {
            val recipe = holder.value()
            if (AssemblyMatching.canCover(
                    AssemblyMatching.requirements(recipe.ingredients), counts, mutableMapOf()
                )) {
                inventory.setStackInSlot(MAIN_SLOT, recipe.result.copy())
                craftRecipeId = holder.id()
                return
            }
        }
    }

    private fun craftSatisfied(recipe: AssemblyCraftRecipe): Boolean =
        AssemblyMatching.canCover(
            AssemblyMatching.requirements(recipe.ingredients), collectMaterialCounts(), mutableMapOf()
        )

    private fun consumeCraft(recipe: AssemblyCraftRecipe) {
        for (counted in recipe.ingredients) {
            consumeIngredient(counted.ingredient, counted.count)
        }
    }

    private fun consumeInstalled() {
        if (installed.isEmpty()) return
        val recipes = attachRecipesById()
        for (id in installed) {
            val recipe = recipes[id] ?: continue
            for (ingredient in recipe.ingredients) consumeIngredient(ingredient, 1)
        }
        installed.clear()
    }

    private fun consumeIngredient(ingredient: Ingredient, count: Int) {
        var remaining = count
        for (slot in MATERIAL_SLOTS) {
            if (remaining <= 0) break
            val stack = inventory.getStackInSlot(slot)
            if (stack.isEmpty || !ingredient.test(stack)) continue
            val take = minOf(remaining, stack.count)
            stack.shrink(take)
            remaining -= take
            inventory.setStackInSlot(slot, if (stack.isEmpty) ItemStack.EMPTY else stack)
        }
    }

    private fun collectMaterialCounts(): MutableMap<Item, Int> {
        val counts = mutableMapOf<Item, Int>()
        for (slot in MATERIAL_SLOTS) {
            val stack = inventory.getStackInSlot(slot)
            if (!stack.isEmpty) counts.merge(stack.item, stack.count, Int::plus)
        }
        return counts
    }

    private fun attachRecipesById(): Map<ResourceLocation, AssemblyAttachRecipe> =
        level.recipeManager.getAllRecipesFor(ASSEMBLY_ATTACH_TYPE)
            .associateBy({ it.value().component }, { it.value() })

    private fun resolveCraft(id: ResourceLocation): AssemblyCraftRecipe? =
        level.recipeManager.byKey(id).orElse(null)?.value() as? AssemblyCraftRecipe

    companion object {
        const val TABLE_SLOTS = 13
        const val MAIN_SLOT = 0
        val MATERIAL_SLOTS = 1..12
        const val SLOT_COUNT = 13

        fun clientFactory(
            containerId: Int,
            playerInventory: Inventory,
            extraData: RegistryFriendlyByteBuf,
        ): AssemblyTableMenu {
            val pos = extraData.readBlockPos()
            return AssemblyTableMenu(
                containerId, playerInventory,
                ContainerLevelAccess.create(playerInventory.player.level(), pos),
            )
        }
    }
}
