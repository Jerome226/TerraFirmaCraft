/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.capabilities.forge;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import net.dries007.tfc.common.items.VesselItem;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.Helpers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A capability instance which is attached to all items, in order to store (cached) and manipulate anvil working/forging data.
 * This instance is lazily initialized upon first getCapability() query, and saves all data directly to the stack tag.
 */
public final class Forging implements ICapabilityProvider
{
    private static final String KEY = "tfc:forging";

    public static void addTooltipInfo(ItemStack stack, List<Component> tooltips)
    {
        stack.getCapability(ForgingCapability.CAPABILITY).ifPresent(cap -> {
            if (cap.getSteps().any())
            {
                tooltips.add(new TranslatableComponent("tfc.tooltip.anvil_has_been_worked"));
            }
        });
    }

    private final LazyOptional<Forging> capability;
    private final ItemStack stack;

    private final ForgeSteps steps;

    private int work, target;
    @Nullable private AnvilRecipe recipe;
    @Nullable private ResourceLocation uninitializedRecipe;

    private boolean initialized;

    public Forging(ItemStack stack)
    {
        this.capability = LazyOptional.of(() -> this);
        this.stack = stack;

        this.work = 0;
        this.recipe = null;
        this.steps = new ForgeSteps();
    }

    public int getWork()
    {
        return work;
    }

    public int getWorkTarget()
    {
        return target == -1 ? 0 : target;
    }

    public void setWork(int work)
    {
        this.work = work;
        save();
    }

    @Nullable
    public AnvilRecipe getRecipe(Level level)
    {
        if (uninitializedRecipe != null)
        {
            recipe = Helpers.getRecipes(level, TFCRecipeTypes.ANVIL).get(uninitializedRecipe);
            uninitializedRecipe = null;
        }
        return recipe;
    }

    public void setRecipe(@Nullable AnvilRecipe recipe, AnvilRecipe.Inventory inventory)
    {
        this.recipe = recipe;
        this.target = recipe == null ? -1 : recipe.computeTarget(inventory);
        save();
    }

    public ForgeSteps getSteps()
    {
        return steps;
    }

    public boolean matches(ForgeRule[] rules)
    {
        for (ForgeRule rule : rules)
        {
            if (!matches(rule))
            {
                return false;
            }
        }
        return true;
    }

    public boolean matches(ForgeRule rule)
    {
        return rule.matches(steps);
    }

    public void addStep(@Nullable ForgeStep step)
    {
        steps.addStep(step);
        if (step != null)
        {
            work += step.step();
        }
        save();
    }

    /**
     * This will clear the current recipe, if the item has not been additionally worked.
     * Used when removing an item from an anvil, as it makes the item stackable again - despite the fact we <strong>must</strong> persist the recipe on the item stack, even if it has not been worked.
     * todo: actually call this from removing item from an anvil
     */
    public void clearRecipeIfNotWorked()
    {
        if (!steps.any())
        {
            recipe = null;
            uninitializedRecipe = null;
        }
        save();
    }

    /**
     * @see VesselItem.VesselCapability#load()
     */
    @NotNull
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgingCapability.CAPABILITY)
        {
            load();
            return capability.cast();
        }
        return LazyOptional.empty();
    }

    private void load()
    {
        if (!initialized)
        {
            initialized = true;

            final CompoundTag tag = stack.getTagElement(KEY);
            if (tag != null)
            {
                work = tag.getInt("work");
                target = tag.getInt("target");

                steps.read(tag);

                uninitializedRecipe = tag.contains("recipe", Tag.TAG_STRING) ? new ResourceLocation(tag.getString("recipe")) : null;
                recipe = null;
            }
        }
    }

    private void save()
    {
        if (!steps.any() && recipe == null && uninitializedRecipe == null)
        {
            // No defining data, so don't save anything
            stack.removeTagKey(KEY);
        }
        else
        {
            final CompoundTag tag = stack.getOrCreateTagElement(KEY);

            tag.putInt("work", work);
            tag.putInt("target", target);

            steps.write(tag);

            if (recipe != null)
            {
                tag.putString("recipe", recipe.getId().toString());
            }
            else if (uninitializedRecipe != null)
            {
                tag.putString("recipe", uninitializedRecipe.toString());
            }
        }
    }
}