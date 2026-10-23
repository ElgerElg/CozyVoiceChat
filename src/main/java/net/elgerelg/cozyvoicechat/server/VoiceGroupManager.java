package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.api.server.VoiceGroup;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Голосовые группы публичного API. В памяти, очищаются при остановке сервера. */
public final class VoiceGroupManager {

    private static final Map<UUID, GroupImpl> GROUPS = new ConcurrentHashMap<>();
    private static final Map<UUID, GroupImpl> BY_PLAYER = new ConcurrentHashMap<>();

    private VoiceGroupManager() {}

    public static VoiceGroup create(String name, boolean exclusive) {
        GroupImpl g = new GroupImpl(UUID.randomUUID(), name == null ? "" : name, exclusive);
        GROUPS.put(g.id, g);
        return g;
    }

    public static VoiceGroup get(UUID id) {
        return id == null ? null : GROUPS.get(id);
    }

    public static VoiceGroup groupOf(UUID playerId) {
        return groupOfRaw(playerId);
    }

    static GroupImpl groupOfRaw(UUID playerId) {
        return playerId == null ? null : BY_PLAYER.get(playerId);
    }

    public static Collection<VoiceGroup> all() {
        return List.copyOf(GROUPS.values());
    }

    public static void clear() {
        for (GroupImpl g : GROUPS.values()) g.removed = true;
        GROUPS.clear();
        BY_PLAYER.clear();
    }

    static final class GroupImpl implements VoiceGroup {
        final UUID id;
        final String name;
        volatile boolean exclusive;
        volatile boolean removed;
        final Set<UUID> members = ConcurrentHashMap.newKeySet();

        GroupImpl(UUID id, String name, boolean exclusive) {
            this.id = id;
            this.name = name;
            this.exclusive = exclusive;
        }

        Set<UUID> membersRaw() { return members; }

        @Override public UUID getId() { return id; }
        @Override public String getName() { return name; }
        @Override public boolean isExclusive() { return exclusive; }
        @Override public void setExclusive(boolean exclusive) { this.exclusive = exclusive; }

        @Override
        public synchronized void addMember(UUID playerId) {
            if (playerId == null) throw new NullPointerException("playerId");
            if (removed) throw new IllegalStateException("group removed");
            GroupImpl prev = BY_PLAYER.put(playerId, this);
            if (prev != null && prev != this) prev.members.remove(playerId);
            members.add(playerId);
        }

        @Override
        public synchronized boolean removeMember(UUID playerId) {
            if (playerId == null || !members.remove(playerId)) return false;
            BY_PLAYER.remove(playerId, this);
            return true;
        }

        @Override public boolean hasMember(UUID playerId) { return playerId != null && members.contains(playerId); }
        @Override public Set<UUID> getMembers() { return Set.copyOf(members); }

        @Override
        public synchronized void remove() {
            if (removed) return;
            removed = true;
            GROUPS.remove(id, this);
            for (UUID m : members) BY_PLAYER.remove(m, this);
            members.clear();
        }

        @Override public boolean isRemoved() { return removed; }
    }
}
