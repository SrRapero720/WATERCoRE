package me.srrapero720.watercore.mixin;


import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.Unpooled;
import me.srrapero720.watercore.api.ChatDataProvider;
import me.srrapero720.watercore.internal.WaterConfig;
import me.srrapero720.watercore.custom.data.LobbyData;
import me.srrapero720.watercore.internal.WaterConsole;
import me.srrapero720.watercore.internal.WaterUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;
import java.util.*;

@Mixin(value = PlayerList.class, priority = 0)
public abstract class PlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Nullable
    public abstract CompoundTag load(ServerPlayer p_11225_);

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private RegistryAccess.Frozen registryHolder;

    @Shadow
    public abstract int getMaxPlayers();

    @Shadow
    private int viewDistance;

    @Shadow
    private int simulationDistance;

    @Shadow
    public abstract void sendPlayerPermissionLevel(ServerPlayer p_11290_);

    @Shadow
    protected abstract void updateEntireScoreboard(ServerScoreboard p_11274_, ServerPlayer p_11275_);

    @Shadow
    public abstract void broadcastMessage(Component p_11265_, ChatType p_11266_, UUID p_11267_);

    @Shadow
    public abstract boolean addPlayer(ServerPlayer player);

    @Shadow
    @Final
    private Map<UUID, ServerPlayer> playersByUUID;

    @Shadow
    public abstract void broadcastAll(Packet<?> p_11269_);

    @Shadow
    public abstract void sendLevelInfo(ServerPlayer p_11230_, ServerLevel p_11231_);

    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Shadow
    public abstract MinecraftServer getServer();

    /**
     * @author SrRapero720
     * @reason Some stuff needs to be changed in this silly function, sorry if broke things.
     */
    @Overwrite
    @Deprecated(since = "1.3.0-A", forRemoval = true)
    public void placeNewPlayer(Connection connection, ServerPlayer player) {
        var profile = player.getGameProfile();
        var profileCache = this.server.getProfileCache();

        var lobbyData = LobbyData.fetch(server);
        var lobbyLevel = WaterUtil.findLevel(server.getAllLevels(), lobbyData.getDimension());
        var lobbyLevelResource = lobbyLevel == null ? Level.OVERWORLD : lobbyLevel.dimension();

        var optional = profileCache.get(profile.getId());
        var s = optional.map(GameProfile::getName).orElse(profile.getName());
        profileCache.add(profile);

        var tag = this.load(player);
        final var levelRes = tag != null
                ? DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("Dimension"))).resultOrPartial(WaterConsole::justPrint).orElse(lobbyLevelResource)
                : lobbyLevelResource;

        // If you remove or change the name of your dimension, you know the problem... but here no notify about it
        var levelResult = this.server.getLevel(levelRes);
        final var level = levelResult != null ? levelResult : this.server.overworld();

        player.setLevel(level);
        var s1 = connection.getRemoteAddress().toString();

        // No es necesario sobreescribir a SrConsole
        LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", player.getName().getString(), s1, player.getId(), player.getX(), player.getY(), player.getZ());

        LevelData leveldata = level.getLevelData();
        player.loadGameTypes(tag);
        ServerGamePacketListenerImpl servergamepacketlistenerimpl = new ServerGamePacketListenerImpl(this.server, connection, player);
        net.minecraftforge.network.NetworkHooks.sendMCRegistryPackets(connection, "PLAY_TO_CLIENT");
        GameRules gamerules = leveldata.getGameRules();
        boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        servergamepacketlistenerimpl.send(new ClientboundLoginPacket(player.getId(), leveldata.isHardcore(), player.gameMode.getGameModeForPlayer(), player.gameMode.getPreviousGameModeForPlayer(), this.server.levelKeys(), this.registryHolder, level.dimensionTypeRegistration(), level.dimension(), BiomeManager.obfuscateSeed(level.getSeed()), this.getMaxPlayers(), this.viewDistance, this.simulationDistance, flag1, !flag, level.isDebug(), level.isFlat()));
        servergamepacketlistenerimpl.send(new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.BRAND, (new FriendlyByteBuf(Unpooled.buffer())).writeUtf(this.getServer().getServerModName())));
        servergamepacketlistenerimpl.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        servergamepacketlistenerimpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        servergamepacketlistenerimpl.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.OnDatapackSyncEvent((PlayerList) (Object) this, player));
        servergamepacketlistenerimpl.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes()));
        servergamepacketlistenerimpl.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registryHolder)));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(level.getScoreboard(), player);
        this.server.invalidateStatus();

        // CHANGED FOR WATERCORE
        var component = ChatDataProvider.parse(WaterConfig.get("JOIN_FORMAT"), player);
        player.sendMessage(component, ChatType.SYSTEM, Util.NIL_UUID);

        int timePlayed = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (timePlayed != 0) {
            servergamepacketlistenerimpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        } else {
            final var cords = lobbyData.getCords();
            final var rot = lobbyData.getRotation();
            final var mPos = new BlockPos(cords[0], cords[1], cords[2]);
            servergamepacketlistenerimpl.teleport(mPos.getX(), mPos.getY() + 1, mPos.getZ(), rot[0], rot[1]);
        }

        this.addPlayer(player);
        this.playersByUUID.put(player.getUUID(), player);
        this.broadcastAll(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player));

        for (int i = 0; i < this.players.size(); ++i) {
            player.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, this.players.get(i)));
        }

        level.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player);
        this.sendLevelInfo(player, level);
        if (!this.server.getResourcePack().isEmpty()) {
            player.sendTexturePack(this.server.getResourcePack(), this.server.getResourcePackHash(), this.server.isResourcePackRequired(), this.server.getResourcePackPrompt());
        }

        for (MobEffectInstance mobeffectinstance : player.getActiveEffects()) {
            servergamepacketlistenerimpl.send(new ClientboundUpdateMobEffectPacket(player.getId(), mobeffectinstance));
        }

        if (tag != null && tag.contains("RootVehicle", 10)) {
            CompoundTag compoundtag1 = tag.getCompound("RootVehicle");
            ServerLevel finalLevel = level;
            Entity entity1 = EntityType.loadEntityRecursive(compoundtag1.getCompound("Entity"), level, (p_11223_) ->
                    !finalLevel.addWithUUID(p_11223_) ? null : p_11223_);
            if (entity1 != null) {
                UUID uuid;
                if (compoundtag1.hasUUID("Attach")) {
                    uuid = compoundtag1.getUUID("Attach");
                } else {
                    uuid = null;
                }

                if (entity1.getUUID().equals(uuid)) {
                    player.startRiding(entity1, true);
                } else {
                    for (Entity entity : entity1.getIndirectPassengers()) {
                        if (entity.getUUID().equals(uuid)) {
                            player.startRiding(entity, true);
                            break;
                        }
                    }
                }

                if (!player.isPassenger()) {
                    LOGGER.warn("Couldn't reattach entity to player");
                    entity1.discard();

                    for (Entity entity2 : entity1.getIndirectPassengers()) {
                        entity2.discard();
                    }
                }
            }
        }

        player.initInventoryMenu();
        ForgeEventFactory.firePlayerLoggedIn(player);
    }


    /**
     * @author SrRapero720
     * @reason Mainly reason I do that is for performance, to 2 teleports on ForgeEvents become in a little stun :)
     * also, code is shit and I want to rewrite it
     */
    @Overwrite
    public ServerPlayer respawn(ServerPlayer player, boolean isBoolean) {
        this.players.remove(player);
        player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);

        // WATERCORE LOBBY DATA
        var lobbyData = LobbyData.fetch(server);
        var lobbyLevel = WaterUtil.findLevel(server.getAllLevels(), lobbyData.getDimension());
        
        var respawnPos = player.getRespawnPosition();
        var respawnAngle = player.getRespawnAngle();
        var respawnForce = player.isRespawnForced();
        
        var level = this.server.getLevel(player.getRespawnDimension());
        Optional<Vec3> optional = (level != null && respawnPos != null)
                ? Player.findRespawnPositionAndUseSpawnBlock(level, respawnPos, respawnAngle, respawnForce, isBoolean)
                : Optional.empty();

        level = level != null && optional.isPresent() ? level : (lobbyLevel == null ? this.server.getLevel(Level.OVERWORLD) : lobbyLevel.getLevel());
        var freshPlayer = new ServerPlayer(this.server, level, player.getGameProfile());

        freshPlayer.connection = player.connection;
        freshPlayer.restoreFrom(player, isBoolean);
        freshPlayer.setId(player.getId());
        freshPlayer.setMainArm(player.getMainArm());

        for(String s : player.getTags()) freshPlayer.addTag(s);

        boolean flag2 = false;
        if (optional.isPresent()) {
            var blockstate = level.getBlockState(respawnPos);
            var vec3 = optional.get();
            float f1;

            if (!blockstate.is(BlockTags.BEDS) && !blockstate.is(Blocks.RESPAWN_ANCHOR)) f1 = respawnAngle;
            else {
                var vec31 = Vec3.atBottomCenterOf(respawnPos).subtract(vec3).normalize();
                f1 = (float) Mth.wrapDegrees(Mth.atan2(vec31.z, vec31.x) * (double)(180F / (float)Math.PI) - 90.0D);
            }

            freshPlayer.moveTo(vec3.x, vec3.y, vec3.z, f1, 0.0F);
            freshPlayer.setRespawnPosition(level.dimension(), respawnPos, respawnAngle, isBoolean, false);
            flag2 = !isBoolean && blockstate.is(Blocks.RESPAWN_ANCHOR);
        } else {
            var cords = lobbyData.getCords();
            var mPos = cords != null ?  new BlockPos(cords[0], cords[1], cords[2]) : new BlockPos(0, 128, 0);
            var mRot = lobbyData.getRotation();


            freshPlayer.setPos(mPos.getX(), mPos.getY(), mPos.getZ());
            freshPlayer.setYRot(mRot[0]);
            freshPlayer.setXRot(mRot[1]);
            freshPlayer.setRespawnPosition(level.dimension(), mPos, mRot[0], respawnForce, false);

            if (respawnPos != null) {
                freshPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }
        }

        while(!level.noCollision(freshPlayer) && freshPlayer.getY() < (double) level.getMaxBuildHeight()) {
            freshPlayer.setPos(freshPlayer.getX(), freshPlayer.getY() + 1.0D, freshPlayer.getZ());
        }

        var leveldata = freshPlayer.level.getLevelData();
        var conn = freshPlayer.connection;
        // PACKETS
        conn.send(new ClientboundRespawnPacket(freshPlayer.level.dimensionTypeRegistration(), freshPlayer.level.dimension(), BiomeManager.obfuscateSeed(freshPlayer.getLevel().getSeed()), freshPlayer.gameMode.getGameModeForPlayer(), freshPlayer.gameMode.getPreviousGameModeForPlayer(), freshPlayer.getLevel().isDebug(), freshPlayer.getLevel().isFlat(), isBoolean));
        conn.teleport(freshPlayer.getX(), freshPlayer.getY(), freshPlayer.getZ(), freshPlayer.getYRot(), freshPlayer.getXRot());
        conn.send(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        conn.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        conn.send(new ClientboundSetExperiencePacket(freshPlayer.experienceProgress, freshPlayer.totalExperience, freshPlayer.experienceLevel));

        // INFO
        this.sendLevelInfo(freshPlayer, level);
        this.sendPlayerPermissionLevel(freshPlayer);
        level.addRespawnedPlayer(freshPlayer);
        this.addPlayer(freshPlayer);
        this.playersByUUID.put(freshPlayer.getUUID(), freshPlayer);

        //PLAYER DATA
        freshPlayer.initInventoryMenu();
        freshPlayer.setHealth(freshPlayer.getHealth()); // DEDUNDANT - CODED BY MOJANG LOL

        // FORGE FIRED (i really want to remove this piece of shit)
        ForgeEventFactory.firePlayerRespawnEvent(freshPlayer, isBoolean);
        if (flag2) freshPlayer.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, respawnPos.getX(), respawnPos.getY(), respawnPos.getZ(), 1.0F, 1.0F));
        return freshPlayer;
    }
}
