package lu.kolja.matter_manipulator.common.integration.laserio;

import lu.kolja.matter_manipulator.api.BlockIntegration;
import lu.kolja.matter_manipulator.api.DirectionTransform;
import lu.kolja.matter_manipulator.api.IntegrationContext;
import lu.kolja.matter_manipulator.api.IntegrationRegistrar;
import lu.kolja.matter_manipulator.api.ItemRequirement;
import lu.kolja.matter_manipulator.api.PlacementPreparationPlan;
import lu.kolja.matter_manipulator.api.PlacementPurpose;

import com.direwolf20.laserio.common.blockentities.LaserNodeBE;
import com.direwolf20.laserio.common.blockentities.basebe.BaseLaserBE;
import com.direwolf20.laserio.common.blocks.LaserNode;
import com.direwolf20.laserio.common.containers.customhandler.CardItemHandler;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import com.direwolf20.laserio.common.items.cards.CardEnergy;
import com.direwolf20.laserio.common.items.cards.CardFluid;
import com.direwolf20.laserio.common.items.cards.CardItem;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.upgrades.OverclockerCard;
import com.direwolf20.laserio.common.items.upgrades.OverclockerNode;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class LaserIoIntegration {

    public static final ResourceLocation NODE_ID = new ResourceLocation("laserio", "node");
    public static final ResourceLocation LINKS_ID = new ResourceLocation("laserio", "links");

    static final String COLOR = "laserColor";
    static final String WRENCH_ALPHA = "wrenchAlpha";
    static final String SIDES = "sides";
    static final String CARDS = "cards";
    static final String CARD = "card";
    static final String FILTER = "filter";
    static final String OVERCLOCKERS = "overclockers";
    static final String LINKS = "links";
    static final String RENDERED = "rendered";

    private static final String CONNECTIONS = "connections";
    private static final String RENDERED_CONNECTIONS = "renderedConnections";
    private static final String MY_WORLD_POS = "myWorldPos";
    private static final String PARTNER = "partnerDimPos";
    private static final String SNEAKY = "sneaky";
    private static final String INVENTORY = "Inventory";
    private static final int CARD_SLOTS = 9;
    private static final int OVERCLOCKER_SLOT = 9;
    private static final int[] ENERGY_LIMITS = {1000, 4000, 16000, 32000, 100000};

    private LaserIoIntegration() {}

    public static void register(IntegrationRegistrar registrar) {
        var node = block("laser_node");
        var connector = block("laser_connector");
        var advancedConnector = block("laser_connector_advanced");
        registrar.block(NODE_ID, new NodeIntegration(), node, connector, advancedConnector);
        registrar.block(LINKS_ID, new LinksIntegration(), node, connector, advancedConnector);
    }

    private static Block block(String path) {
        return ForgeRegistries.BLOCKS.getHolder(new ResourceLocation("laserio", path))
                .orElseThrow(() -> new IllegalStateException("LaserIO did not register laserio:" + path))
                .value();
    }

    private static final class NodeIntegration implements BlockIntegration {

        @Override
        public CompoundTag capture(IntegrationContext.Capture context) {
            return context.blockEntity() instanceof BaseLaserBE laser ? captureNode(laser) : new CompoundTag();
        }

        @Override
        public CompoundTag transform(IntegrationContext.Transform context, CompoundTag payload) {
            var transformed = payload.copy();
            if (!payload.contains(SIDES)) {
                return transformed;
            }
            var sides = payload.getCompound(SIDES);
            var turned = new CompoundTag();
            for (var key : sides.getAllKeys()) {
                var direction = Direction.byName(key);
                var side = sides.getCompound(key).copy();
                var cards = side.getCompound(CARDS);
                for (var slot : cards.getAllKeys()) {
                    remapSneaky(cards.getCompound(slot).getCompound(CARD), context.transform());
                }
                turned.put(direction == null ? key : context.transform().transform(direction).getName(), side);
            }
            transformed.put(SIDES, turned);
            return transformed;
        }

        @Override
        public boolean transformsBlockEntity(BlockState state) {
            return state.getBlock() instanceof LaserNode;
        }

        @Override
        public CompoundTag transformBlockEntity(IntegrationContext.Transform context, CompoundTag blockEntity) {
            var inventories = new CompoundTag[6];
            for (var direction : Direction.values()) {
                var key = INVENTORY + direction.ordinal();
                if (blockEntity.contains(key, Tag.TAG_COMPOUND)) {
                    inventories[direction.ordinal()] = blockEntity.getCompound(key);
                    blockEntity.remove(key);
                }
            }
            for (var direction : Direction.values()) {
                var inventory = inventories[direction.ordinal()];
                if (inventory == null) {
                    continue;
                }
                var items = inventory.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    remapSneaky(items.getCompound(i), context.transform());
                }
                blockEntity.put(INVENTORY + context.transform().transform(direction).ordinal(), inventory);
            }
            return blockEntity;
        }

        @Override
        public PlacementPreparationPlan preparation(IntegrationContext.Preparation context, CompoundTag payload) {
            var sides = payload.getCompound(SIDES);
            if (sides.isEmpty()) {
                return PlacementPreparationPlan.unchanged(payload);
            }
            var unfunded = payload.copy();
            unfunded.remove(SIDES);
            var plan = PlacementPreparationPlan.builder(unfunded);
            for (var side : sides.getAllKeys()) {
                var sideTag = sides.getCompound(side);
                var cards = sideTag.getCompound(CARDS);
                for (var slot : cards.getAllKeys()) {
                    var entry = cards.getCompound(slot);
                    var card = stack(entry, CARD);
                    if (card.isEmpty()) {
                        continue;
                    }
                    var parent = plan.component(ItemRequirement.withFallback(card, new ItemStack(card.getItem())),
                            (prepared, acquired) -> {
                                if (!acquired.isEmpty()) {
                                    var funded = new CompoundTag();
                                    funded.put(CARD, entry.getCompound(CARD).copy());
                                    child(child(child(prepared, SIDES), side), CARDS).put(slot, funded);
                                }
                            });
                    var filter = stack(entry, FILTER);
                    if (!filter.isEmpty()) {
                        parent.component(ItemRequirement.withFallback(filter, new ItemStack(filter.getItem()))
                                        .withVariants(LaserIoIntegration::isBlankFilter),
                                (prepared, acquired) -> {
                                    if (!acquired.isEmpty()) {
                                        cardEntry(prepared, side, slot).put(FILTER, entry.getCompound(FILTER).copy());
                                    }
                                });
                    }
                    var overclockers = stack(entry, OVERCLOCKERS);
                    for (int i = 0; i < overclockers.getCount(); i++) {
                        parent.component(ItemRequirement.exact(overclockers.copyWithCount(1)),
                                (prepared, acquired) -> addOne(cardEntry(prepared, side, slot), acquired));
                    }
                }
                var overclockers = stack(sideTag, OVERCLOCKERS);
                for (int i = 0; i < overclockers.getCount(); i++) {
                    plan.component(ItemRequirement.exact(overclockers.copyWithCount(1)),
                            (prepared, acquired) -> addOne(child(child(prepared, SIDES), side), acquired));
                }
            }
            return plan.build();
        }

        @Override
        public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
            if (!(context.blockEntity() instanceof BaseLaserBE laser)) {
                return ApplyResult.REJECTED_UNCHANGED;
            }
            if (context.purpose() == PlacementPurpose.MOVE_TRANSFER) {
                return captureNode(laser).equals(payload) ? ApplyResult.APPLIED : ApplyResult.REJECTED_UNCHANGED;
            }
            if (laser instanceof LaserNodeBE node) {
                boolean reconfigure = context.purpose() == PlacementPurpose.RECONFIGURE;
                if (reconfigure && !sameItems(captureNode(node), payload)) {
                    return ApplyResult.REJECTED_UNCHANGED;
                }
                var installs = installs(payload);
                if (!reconfigure) {
                    for (var install : installs) {
                        var handler = node.nodeSideCaches[install.side().ordinal()].itemHandler;
                        if (!handler.insertItem(install.slot(), install.stack(), true).isEmpty()) {
                            return ApplyResult.REJECTED_UNCHANGED;
                        }
                    }
                }
                for (var install : installs) {
                    var handler = node.nodeSideCaches[install.side().ordinal()].itemHandler;
                    if (reconfigure) {
                        handler.setStackInSlot(install.slot(), install.stack());
                    } else {
                        handler.insertItem(install.slot(), install.stack(), false);
                    }
                }
            }
            if (payload.contains(COLOR)) {
                laser.setColor(new Color(payload.getInt(COLOR), true), payload.getInt(WRENCH_ALPHA));
            }
            return ApplyResult.APPLIED;
        }

        @Override
        public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
            return sameItems(existing, desired);
        }
    }

    private static final class LinksIntegration implements BlockIntegration {

        @Override
        public CompoundTag capture(IntegrationContext.Capture context) {
            if (!(context.blockEntity() instanceof BaseLaserBE laser)) {
                return new CompoundTag();
            }
            Predicate<BlockPos> captured = offset -> context.selection() == null
                    || context.inSelection(context.pos().offset(offset));
            return links(laser.getConnections().stream().filter(captured).toList(),
                    laser.getRenderedConnections().stream()
                            .filter(offset -> laser.getConnections().contains(offset) && captured.test(offset))
                            .toList());
        }

        @Override
        public CompoundTag transform(IntegrationContext.Transform context, CompoundTag payload) {
            if (payload.isEmpty()) {
                return payload.copy();
            }
            var transform = context.transform();
            return links(offsets(payload, LINKS).stream().map(transform::transformOffset).toList(),
                    offsets(payload, RENDERED).stream().map(transform::transformOffset).toList());
        }

        @Override
        public CompoundTag transformBlockEntity(IntegrationContext.Transform context, CompoundTag blockEntity) {
            for (var key : List.of(CONNECTIONS, RENDERED_CONNECTIONS)) {
                var list = blockEntity.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    var entry = list.getCompound(i);
                    var offset = NbtUtils.readBlockPos(entry.getCompound("pos"));
                    entry.put("pos", NbtUtils.writeBlockPos(context.transform().transformOffset(offset)));
                }
            }
            return blockEntity;
        }

        @Override
        public boolean relocatesBlockEntity(BlockState state) {
            return true;
        }

        @Override
        public CompoundTag relocateBlockEntity(IntegrationContext.Relocation context, CompoundTag blockEntity) {
            blockEntity.remove(MY_WORLD_POS);
            for (var key : List.of(CONNECTIONS, RENDERED_CONNECTIONS)) {
                var list = blockEntity.getList(key, Tag.TAG_COMPOUND);
                list.removeIf(entry -> context.destinationOf(
                        context.source().offset(NbtUtils.readBlockPos(((CompoundTag) entry).getCompound("pos")))) == null);
            }
            var partner = blockEntity.getCompound(PARTNER);
            if (blockEntity.contains(PARTNER, Tag.TAG_COMPOUND)
                    && partner.getString("level").equals(context.dimension().location().toString())) {
                var destination = context.destinationOf(NbtUtils.readBlockPos(partner.getCompound("blockpos")));
                if (destination == null) {
                    blockEntity.remove(PARTNER);
                } else {
                    partner.put("blockpos", NbtUtils.writeBlockPos(destination));
                }
            }
            return blockEntity;
        }

        @Override
        public ApplyResult apply(IntegrationContext.Apply context, CompoundTag payload) {
            if (!(context.blockEntity() instanceof BaseLaserBE laser)) {
                return ApplyResult.REJECTED_UNCHANGED;
            }
            if (context.purpose() == PlacementPurpose.MOVE_TRANSFER) {
                var restored = capture(new IntegrationContext.Capture(context.level(), context.pos(), context.state()));
                return restored.equals(payload) ? ApplyResult.APPLIED : ApplyResult.REJECTED_UNCHANGED;
            }
            var rendered = new HashSet<>(offsets(payload, RENDERED));
            for (var offset : offsets(payload, LINKS)) {
                var peerPos = context.pos().offset(offset);
                if (!context.level().isLoaded(peerPos) || laser.isNodeConnected(peerPos)
                        || !(context.level().getBlockEntity(peerPos) instanceof BaseLaserBE peer)) {
                    continue;
                }
                if (rendered.contains(offset)) {
                    laser.addConnection(peerPos, peer);
                } else {
                    peer.addConnection(context.pos(), laser);
                }
            }
            return ApplyResult.APPLIED;
        }

        @Override
        public boolean canReconfigure(IntegrationContext.Placement context, CompoundTag existing, CompoundTag desired) {
            return true;
        }

        @Override
        public Set<BlockPos> additionalChangedPositions(IntegrationContext.SideEffects context, CompoundTag payload) {
            var live = context.blockEntity() instanceof BaseLaserBE laser ? laser : null;
            var changed = new HashSet<BlockPos>();
            for (var offset : offsets(payload, LINKS)) {
                var peerPos = context.pos().offset(offset);
                if (!context.level().isLoaded(peerPos)) {
                    changed.add(peerPos);
                } else if (context.level().getBlockEntity(peerPos) instanceof BaseLaserBE
                        && (live == null || !live.isNodeConnected(peerPos))) {
                    changed.add(peerPos);
                }
            }
            return changed;
        }
    }

    private static CompoundTag captureNode(BaseLaserBE laser) {
        var tag = new CompoundTag();
        tag.putInt(COLOR, laser.getColor().getRGB());
        tag.putInt(WRENCH_ALPHA, laser.getWrenchAlpha());
        if (!(laser instanceof LaserNodeBE node)) {
            return tag;
        }
        var sides = new CompoundTag();
        for (var direction : Direction.values()) {
            var handler = node.nodeSideCaches[direction.ordinal()].itemHandler;
            var side = new CompoundTag();
            var cards = new CompoundTag();
            for (int slot = 0; slot < CARD_SLOTS; slot++) {
                var stack = handler.getStackInSlot(slot);
                if (stack.getItem() instanceof BaseCard) {
                    cards.put(Integer.toString(slot), captureCard(stack));
                }
            }
            if (!cards.isEmpty()) {
                side.put(CARDS, cards);
            }
            var overclockers = handler.getStackInSlot(OVERCLOCKER_SLOT);
            if (overclockers.getItem() instanceof OverclockerNode) {
                side.put(OVERCLOCKERS, overclockers.save(new CompoundTag()));
            }
            if (!side.isEmpty()) {
                sides.put(direction.getName(), side);
            }
        }
        if (!sides.isEmpty()) {
            tag.put(SIDES, sides);
        }
        return tag;
    }

    private static CompoundTag captureCard(ItemStack stack) {
        var entry = new CompoundTag();
        var inventory = cardInventory(stack);
        for (int i = 0; i < inventory.getSlots(); i++) {
            var held = inventory.getStackInSlot(i);
            if (held.getItem() instanceof BaseFilter) {
                entry.put(FILTER, held.save(new CompoundTag()));
            } else if (held.getItem() instanceof OverclockerCard) {
                entry.put(OVERCLOCKERS, held.save(new CompoundTag()));
            }
        }
        var card = stack.copy();
        card.removeTagKey("inv");
        entry.put(CARD, card.save(new CompoundTag()));
        return entry;
    }

    private record Install(Direction side, int slot, ItemStack stack) {}

    private static List<Install> installs(CompoundTag payload) {
        var installs = new ArrayList<Install>();
        var sides = payload.getCompound(SIDES);
        for (var key : sides.getAllKeys()) {
            var direction = Direction.byName(key);
            if (direction == null) {
                continue;
            }
            var side = sides.getCompound(key);
            var cards = side.getCompound(CARDS);
            for (var slot : cards.getAllKeys()) {
                var entry = cards.getCompound(slot);
                var card = stack(entry, CARD);
                if (card.isEmpty()) {
                    continue;
                }
                var overclockers = stack(entry, OVERCLOCKERS);
                boolean energy = card.getItem() instanceof CardEnergy;
                var inventory = cardInventory(card);
                if (!energy && entry.contains(FILTER)) {
                    inventory.setStackInSlot(0, stack(entry, FILTER));
                }
                if (!overclockers.isEmpty()) {
                    inventory.setStackInSlot(energy ? 0 : 1, overclockers);
                }
                clamp(card, overclockers);
                installs.add(new Install(direction, Integer.parseInt(slot), card));
            }
            var overclockers = stack(side, OVERCLOCKERS);
            if (!overclockers.isEmpty()) {
                installs.add(new Install(direction, OVERCLOCKER_SLOT, overclockers));
            }
        }
        return installs;
    }

    private static void clamp(ItemStack card, ItemStack overclockers) {
        var uel = uelLimits();
        int count = overclockers.getCount();
        if (card.getItem() instanceof CardItem) {
            int maximum = Math.max(count * 16, 8);
            if (CardItem.getItemExtractAmt(card) > maximum) {
                CardItem.setItemExtractAmt(card, (byte) maximum);
            }
            int minimumTicks = uel == null ? Math.max(20 - count * 5, 1)
                    : perOverclocker(uel.get("card.item_card.min_ticks_item"), count);
            if (CardItem.getExtractSpeed(card) < minimumTicks) {
                CardItem.setExtractSpeed(card, minimumTicks);
            }
        } else if (card.getItem() instanceof CardFluid) {
            int maximum = uel == null ? Math.max(count * 2000, 1000)
                    : Math.max(count * uel.getInt("card.fluid_card.multiplier_milli_buckets_fluid"),
                            uel.getInt("card.fluid_card.base_milli_buckets_fluid"));
            if (CardFluid.getFluidExtractAmt(card) > maximum) {
                CardFluid.setFluidExtractAmt(card, maximum);
            }
            int minimumTicks = uel == null ? Math.max(20 - count * 5, 1)
                    : perOverclocker(uel.get("card.fluid_card.min_ticks_fluid"), count);
            if (CardFluid.getExtractSpeed(card) < minimumTicks) {
                CardFluid.setExtractSpeed(card, minimumTicks);
            }
        } else if (card.getItem() instanceof CardEnergy) {
            int maximum = uel == null ? ENERGY_LIMITS[Math.min(count, ENERGY_LIMITS.length - 1)]
                    : uelEnergyLimit(uel, overclockers);
            if (CardEnergy.getEnergyExtractAmt(card) > maximum) {
                CardEnergy.setEnergyExtractAmt(card, maximum);
            }
        }
    }

    private static UnmodifiableConfig uelLimits() {
        for (var config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON)) {
            var data = config.getConfigData();
            if (config.getModId().equals("laserio") && data != null && data.contains("card")) {
                return data;
            }
        }
        return null;
    }

    private static int perOverclocker(List<? extends Number> values, int overclockers) {
        return values.isEmpty() ? 1 : values.get(Math.min(overclockers, values.size() - 1)).intValue();
    }

    private static int uelEnergyLimit(UnmodifiableConfig uel, ItemStack overclocker) {
        List<? extends Number> tiers = uel.get("card.energy_card.energy_overclockers.max_fe_tiers");
        var id = ForgeRegistries.ITEMS.getKey(overclocker.getItem());
        for (int tier = 1; tier <= tiers.size(); tier++) {
            if (!overclocker.isEmpty() && id.getPath().equals("energy_overclocker_card_tier_" + tier)) {
                return tiers.get(tier - 1).intValue();
            }
        }
        return uel.getInt("card.energy_card.max_fe_no_tiers");
    }

    private static CardItemHandler cardInventory(ItemStack card) {
        return card.getItem() instanceof CardEnergy ? CardEnergy.getInventory(card) : BaseCard.getInventory(card);
    }

    private static boolean sameItems(CompoundTag a, CompoundTag b) {
        return items(a).equals(items(b));
    }

    private static CompoundTag items(CompoundTag payload) {
        var items = new CompoundTag();
        var sides = payload.getCompound(SIDES);
        for (var side : sides.getAllKeys()) {
            var sideTag = sides.getCompound(side);
            var sideItems = new CompoundTag();
            sideItems.putInt(OVERCLOCKERS, stack(sideTag, OVERCLOCKERS).getCount());
            var cards = sideTag.getCompound(CARDS);
            for (var slot : cards.getAllKeys()) {
                var entry = cards.getCompound(slot);
                var card = new CompoundTag();
                card.putString(CARD, entry.getCompound(CARD).getString("id"));
                card.putString(FILTER, entry.getCompound(FILTER).getString("id"));
                card.putInt(OVERCLOCKERS, stack(entry, OVERCLOCKERS).getCount());
                sideItems.put(slot, card);
            }
            items.put(side, sideItems);
        }
        return items;
    }

    private static void remapSneaky(CompoundTag item, DirectionTransform transform) {
        var tag = item.getCompound("tag");
        if (!tag.contains(SNEAKY, Tag.TAG_BYTE)) {
            return;
        }
        byte sneaky = tag.getByte(SNEAKY);
        if (sneaky >= 0 && sneaky < Direction.values().length) {
            tag.putByte(SNEAKY, (byte) transform.transform(Direction.values()[sneaky]).ordinal());
        }
    }

    private static CompoundTag links(List<BlockPos> links, List<BlockPos> rendered) {
        var tag = new CompoundTag();
        if (links.isEmpty()) {
            return tag;
        }
        tag.putLongArray(LINKS, links.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        if (!rendered.isEmpty()) {
            tag.putLongArray(RENDERED, rendered.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        }
        return tag;
    }

    private static List<BlockPos> offsets(CompoundTag payload, String key) {
        var offsets = new ArrayList<BlockPos>();
        for (long packed : payload.getLongArray(key)) {
            offsets.add(BlockPos.of(packed));
        }
        return offsets;
    }

    static boolean isBlankFilter(ItemStack filter) {
        var tag = filter.getTag();
        if (tag == null) {
            return true;
        }
        for (var key : tag.getAllKeys()) {
            boolean blank = switch (key) {
                case "allowList" -> tag.getBoolean(key);
                case "compareNBT" -> !tag.getBoolean(key);
                case "inv" -> tag.getCompound(key).getList("Items", Tag.TAG_COMPOUND).isEmpty();
                default -> tag.get(key) instanceof CollectionTag<?> list && list.isEmpty();
            };
            if (!blank) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag cardEntry(CompoundTag prepared, String side, String slot) {
        return child(child(child(child(prepared, SIDES), side), CARDS), slot);
    }

    private static void addOne(CompoundTag holder, ItemStack acquired) {
        if (acquired.isEmpty()) {
            return;
        }
        var held = stack(holder, OVERCLOCKERS);
        holder.put(OVERCLOCKERS, (held.isEmpty() ? acquired : held.copyWithCount(held.getCount() + 1))
                .save(new CompoundTag()));
    }

    private static ItemStack stack(CompoundTag holder, String key) {
        return holder.contains(key, Tag.TAG_COMPOUND) ? ItemStack.of(holder.getCompound(key)) : ItemStack.EMPTY;
    }

    private static CompoundTag child(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            parent.put(key, new CompoundTag());
        }
        return parent.getCompound(key);
    }
}
