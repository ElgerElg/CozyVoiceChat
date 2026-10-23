package net.elgerelg.cozyvoicechat.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent storage for voice passthrough blocking blocks.
 * Хранит список блоков, которые полностью ГЛУШАТ локальный голос.
 */
public class VoicePassthroughData extends SavedData {

    private static final String STORAGE_ID = "cozyvoicechat_passthrough_blocks";

    private final Set<ResourceLocation> blockingBlocks = new HashSet<>();

    public static VoicePassthroughData get(MinecraftServer server) {
        ServerLevel level = server == null ? null : server.overworld();
        if (level == null) {
            throw new IllegalStateException("Cannot access voice passthrough data without an overworld");
        }
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(VoicePassthroughData::fromTag, VoicePassthroughData::new, STORAGE_ID);
    }

    public VoicePassthroughData() {
        super();
    }

    public VoicePassthroughData(CompoundTag tag) {
        super();
        load(tag);
    }

    public Set<ResourceLocation> getBlocks() {
        if (blockingBlocks.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(blockingBlocks);
    }

    public boolean addBlock(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean added = blockingBlocks.add(id);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean removeBlock(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        boolean removed = blockingBlocks.remove(id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ResourceLocation id : blockingBlocks) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("blocks", list);
        return tag;
    }

    private void load(CompoundTag tag) {
        blockingBlocks.clear();
        ListTag list = tag.getList("blocks", 8); // StringTag
        for (int i = 0; i < list.size(); i++) {
            String s = list.getString(i);
            try {
                ResourceLocation id = new ResourceLocation(s);
                blockingBlocks.add(id);
            } catch (Exception ignored) {
            }
        }
    }

    private static VoicePassthroughData fromTag(CompoundTag tag) {
        return new VoicePassthroughData(tag);
    }
}


