package dev.alexnijjar.extractinator.common.block;

import dev.alexnijjar.extractinator.common.config.ExtractinatorConfig;
import dev.alexnijjar.extractinator.common.recipe.ExtractinatorRecipe;
import dev.alexnijjar.extractinator.common.registry.ModBlockEntityTypes;
import dev.alexnijjar.extractinator.common.registry.ModRecipeTypes;
import dev.alexnijjar.extractinator.common.util.ModUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtractinatorBlockEntity extends BlockEntity implements ExtractinatorContainer {
    private final NonNullList<ItemStack> inventory;
    protected int remainingUsages;
    @Nullable
    private ExtractinatorRecipe recipe;
    private ItemStack prevInput = ItemStack.EMPTY;

    public ExtractinatorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntityTypes.EXTRACTINATOR.get(), blockPos, blockState);
        inventory = NonNullList.withSize(33, ItemStack.EMPTY);
    }

    public void serverTick() {
        if (level.getGameTime() % ExtractinatorConfig.extractTicks == 0) {
            extractinate();
        }
    }

    protected void extractinate() {
        if (this.level != null) {
            dispenseItems();
            placeBlockAbove();
            extractBlockAbove();
            setChanged();
        }
    }

    protected void placeBlockAbove() {
        BlockState above = level.getBlockState(this.getBlockPos().above());
        ItemStack input = inventory.get(0);
        Block toPlace = Block.byItem(input.getItem());
        if (!(toPlace == Blocks.AIR)) {
            if (above.isAir() || Blocks.WATER.equals(above.getBlock())) {
                level.setBlock(this.getBlockPos().above(), toPlace.defaultBlockState(), Block.UPDATE_NONE);
            } else {
                level.playSound(null, this.getBlockPos(), SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
                List<ItemStack> outputs = ModUtils.extractItem(this.recipe, level);
                if (!outputs.isEmpty()) {
                    outputs.forEach(this::addItem);
                }
            }
            getItem(0).shrink(1);
        }
    }

    protected void extractBlockAbove() {
        BlockState above = level.getBlockState(this.getBlockPos().above());
        if (above.isAir()) return;
        ItemStack stack = above.getBlock().asItem().getDefaultInstance();
        if (ModUtils.isValidInput(this.recipe, stack)) {
            level.destroyBlock(this.getBlockPos().above(), false);
            List<ItemStack> outputs = ModUtils.extractItem(this.recipe, level);
            damage();
            if (!outputs.isEmpty()) {
                outputs.forEach(this::addItem);
            }
        }
    }

    protected void dispenseItems() {
        for (int i = 1; i < getInventory().size(); i++) {
            ItemStack stack = this.getItem(i);
            if (stack.isEmpty()) continue;
            if (!level.getBlockState(getBlockPos().above()).isAir()) continue;
            BlockPos pos = this.getBlockPos();
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5f, pos.getY() + 2.0f, pos.getZ() + 0.5f, stack.copy());
            itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().scale(1.5f));
            stack.setCount(0);
            level.addFreshEntity(itemEntity);
            break;
        }
    }

    public void damage() {
        if (this.level != null) {
            if (ExtractinatorConfig.extractinatorDurability > 0) {
                this.remainingUsages--;
                if (this.remainingUsages == 0) {
                    level.playSound(null, this.getBlockPos(), SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1, 1);
                    level.destroyBlock(this.getBlockPos(), false);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, this.inventory);
        tag.putInt("RemainingUsages", remainingUsages);
    }

    @Override
    public void load(CompoundTag tag) {
        ContainerHelper.loadAllItems(tag, this.inventory);
        this.remainingUsages = tag.getInt("RemainingUsages");
    }

    @Override
    public NonNullList<ItemStack> getInventory() {
        return this.inventory;
    }

    @Override
    public boolean isValidInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!this.prevInput.sameItem(stack)) {
            recipe = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.EXTRACTINATOR_RECIPE.get()).stream().filter(r -> r.matches(stack)).findFirst().orElse(null);
        }
        this.prevInput = stack;
        return ModUtils.isValidInput(recipe, stack);
    }
}