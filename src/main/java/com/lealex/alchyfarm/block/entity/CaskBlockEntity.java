package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A cask: one tank of Config fluids.caskBuckets buckets, filled only by the workstation it is joined to (fill),
 * drawn from by anything (pipes, pumps, a bucket in hand: the NeoForge fluid capability, registered in AlchyFarm).
 */
public class CaskBlockEntity extends BlockEntity {
    public static final TagKey<Fluid> HONEY_TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "honey"));
    /** What one glass bottle takes of a cask's honey. */
    public static final int BOTTLE = 250;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, Config.caskCapacity()) {
        // Nothing fills it from outside: it is an output
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }

        // A fluid that is no longer in the game is dropped when the cask is loaded, and the tank is left with no
        // place at all: it is given back its one place, empty
        @Override
        public void deserialize(ValueInput input) {
            super.deserialize(input);
            if (size() != 1) setStacks(net.minecraft.core.NonNullList.withSize(1, FluidStack.EMPTY));
        }
    };

    public CaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CASK_BE.get(), pos, state);
    }

    public FluidStacksResourceHandler tank() {
        return tank;
    }

    /** How much of this fluid still fits: none if it holds another. */
    public int room(Fluid fluid) {
        FluidResource held = tank.getResource(0);
        if (!held.isEmpty() && tank.getAmountAsInt(0) > 0 && held.getFluid() != fluid) return 0;
        return Math.max(0, Config.caskCapacity() - tank.getAmountAsInt(0));
    }

    /** The workstation pours this in, as far as it fits. Returns how much went in. */
    public int fill(Fluid fluid, int amount) {
        int fits = Math.min(amount, room(fluid));
        if (fits <= 0) return 0;
        tank.set(0, FluidResource.of(fluid), tank.getAmountAsInt(0) + fits);
        setChanged();
        return fits;
    }

    /** True if it holds at least this much honey (AlchyFarm's, or any fluid of the tag c:honey). */
    public boolean holdsHoney(int amount) {
        FluidResource held = tank.getResource(0);
        return !held.isEmpty() && tank.getAmountAsInt(0) >= amount && held.getFluid().builtInRegistryHolder().is(HONEY_TAG);
    }

    /** True if it holds at least this much of this fluid. */
    public boolean holds(Fluid fluid, int amount) {
        FluidResource held = tank.getResource(0);
        return !held.isEmpty() && tank.getAmountAsInt(0) >= amount && held.getFluid() == fluid;
    }

    /** Takes this much out (a bottle filled at it). */
    public void take(int amount) {
        tank.set(0, tank.getResource(0), Math.max(0, tank.getAmountAsInt(0) - amount));
        setChanged();
    }

    /** What it holds, in words (an empty hand on it). */
    public String says() {
        FluidResource held = tank.getResource(0);
        int amount = tank.getAmountAsInt(0);
        if (held.isEmpty() || amount <= 0) return "Cask: empty";
        return "Cask: " + amount + " / " + Config.caskCapacity() + " mB of " + held.getFluid().getFluidType().getDescription().getString();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
    }
}
