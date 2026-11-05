package ru.felexwerd.kingdomprison;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class KingdomEvents {
    private static final int BORDER = 48;
    private static final int JAIL_SECONDS = 60;
    private static final Map<UUID, Integer> jailTimers = new HashMap<>();
    private static final Map<UUID, Integer> introTimers = new HashMap<>();
    private static final Set<String> builtWorlds = new HashSet<>();

    private KingdomEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        introTimers.put(player.getUUID(), 30);
        player.displayClientMessage(Component.literal("§6§lКОРОЛЕВСТВО АЛЬДРИКА"), true);
        player.sendSystemMessage(Component.literal("§7«Чужеземец, добро пожаловать во владения короля. Здесь действуют его законы.»"));
        player.sendSystemMessage(Component.literal("§eНе покидай стены и не нарушай королевский порядок."));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide) return;
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        if (Math.abs(player.getX() - spawn.getX()) > BORDER || Math.abs(player.getZ() - spawn.getZ()) > BORDER) return;
        event.setCanceled(true);
        arrest(player, "§cТы нарушил королевский закон! Гарпия доставит тебя в темницу.");
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        UUID id = player.getUUID();

        Integer intro = introTimers.get(id);
        if (intro != null) {
            if (player.tickCount % 20 == 0) {
                if (intro > 0) {
                    player.displayClientMessage(Component.literal("§6§lКОРОЛЕВСТВО АЛЬДРИКА §8| §fИстория королевства: §e" + intro + " сек."), true);
                    introTimers.put(id, intro - 1);
                } else {
                    introTimers.remove(id);
                    player.displayClientMessage(Component.literal("§aКат-сцена завершена. Следуй к замку!"), true);
                }
            }
        }

        BlockPos spawn = player.serverLevel().getSharedSpawnPos();
        double dx = player.getX() - spawn.getX();
        double dz = player.getZ() - spawn.getZ();
        if (Math.abs(dx) > BORDER || Math.abs(dz) > BORDER) {
            player.teleportTo(spawn.getX() + Math.signum(dx) * (BORDER - 2), player.getY(), spawn.getZ() + Math.signum(dz) * (BORDER - 2));
            player.displayClientMessage(Component.literal("§c§lСТОЙ! §fДальше земли короля Альдрика."), true);
        }

        Integer remaining = jailTimers.get(id);
        if (remaining != null && player.tickCount % 20 == 0) {
            if (remaining > 0) {
                player.displayClientMessage(Component.literal("§4§lАРЕСТ §8| §fДо освобождения: §c" + remaining + " сек."), true);
                jailTimers.put(id, remaining - 1);
            } else {
                jailTimers.remove(id);
                player.teleportTo(player.serverLevel(), spawn.getX() + 5.5, spawn.getY() + 1, spawn.getZ() + 5.5, player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("§aКоролевская стража отпускает тебя. Не нарушай закон снова."));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            String key = level.dimension().location().toString();
            if (builtWorlds.add(key)) buildKingdom(level);
        }
    }

    private static void arrest(ServerPlayer player, String reason) {
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos jail = spawn.offset(18, 1, 0);
        player.sendSystemMessage(Component.literal(reason));
        Vex harpy = EntityType.VEX.create(level);
        if (harpy != null) {
            harpy.moveTo(player.getX(), player.getY() + 3, player.getZ(), player.getYRot(), 0);
            harpy.setCustomName(Component.literal("§cКоролевская гарпия"));
            harpy.setCustomNameVisible(true);
            harpy.setNoAi(true);
            level.addFreshEntity(harpy);
        }
        player.teleportTo(level, jail.getX() + 0.5, jail.getY(), jail.getZ() + 0.5, 90, 0);
        jailTimers.put(player.getUUID(), JAIL_SECONDS);
        player.displayClientMessage(Component.literal("§4§lТЮРЬМА §8| §fАрест: §c1:00"), true);
    }

    private static void buildKingdom(ServerLevel level) {
        BlockPos s = level.getSharedSpawnPos();
        int y = Math.max(level.getMinBuildHeight() + 5, s.getY());
        // Perimeter walls and corner towers.
        for (int x = -BORDER; x <= BORDER; x++) for (int z = -BORDER; z <= BORDER; z++) {
            if (Math.abs(x) != BORDER && Math.abs(z) != BORDER) continue;
            for (int h = 0; h < 5; h++) {
                if (Math.abs(x) == BORDER && Math.abs(z) == BORDER) continue;
                if (x == 0 && Math.abs(z) == BORDER) continue;
                if (z == 0 && Math.abs(x) == BORDER) continue;
                level.setBlock(s.offset(x, h, z), h == 4 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        for (int x : new int[]{-BORDER, BORDER}) for (int z : new int[]{-BORDER, BORDER}) {
            for (int h=0; h<10; h++) for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++)
                if (Math.abs(dx)==2 || Math.abs(dz)==2) level.setBlock(s.offset(x+dx,h,z+dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        // Castle: central keep.
        buildBuilding(level, s.offset(-7,0,-12), 15, 10, 15, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
        // Jail at east side.
        buildBuilding(level, s.offset(13,0,-5), 11, 6, 11, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState());
        for (int x=15; x<=21; x++) for (int z=-3; z<=3; z++) {
            BlockPos p=s.offset(x,1,z);
            if (x==15 || x==21 || z==-3 || z==3) level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(),3);
        }
        level.setBlock(s.offset(18,1,4), Blocks.IRON_DOOR.defaultBlockState(),3);
        // Market stalls along the south plaza.
        for (int i=0;i<5;i++) {
            int x=-16+i*8, z=17;
            for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++)
                level.setBlock(s.offset(x+dx,0,z+dz), Blocks.OAK_PLANKS.defaultBlockState(),3);
            for (int dx : new int[]{-2,2}) for (int dz : new int[]{-2,2}) {
                level.setBlock(s.offset(x+dx,1,z+dz), Blocks.OAK_FENCE.defaultBlockState(),3);
                level.setBlock(s.offset(x+dx,3,z+dz), Blocks.RED_WOOL.defaultBlockState(),3);
            }
            for (int dx=-2;dx<=2;dx++) level.setBlock(s.offset(x+dx,4,z), Blocks.RED_WOOL.defaultBlockState(),3);
            level.setBlock(s.offset(x,1,z), Blocks.BARREL.defaultBlockState(),3);
        }
        level.setBlock(s.offset(0,0,5), Blocks.GOLD_BLOCK.defaultBlockState(),3);
        level.setBlock(s.offset(0,1,5), Blocks.LANTERN.defaultBlockState(),3);
    }

    private static void buildBuilding(ServerLevel level, BlockPos origin, int width, int height, int depth, BlockState wall, BlockState roof) {
        for (int x=0;x<width;x++) for (int z=0;z<depth;z++) {
            level.setBlock(origin.offset(x,0,z), Blocks.STONE_BRICKS.defaultBlockState(),3);
            for (int y=1;y<height;y++) {
                boolean edge=x==0||z==0||x==width-1||z==depth-1;
                if (edge) level.setBlock(origin.offset(x,y,z), wall,3);
                else if (y==height-1) level.setBlock(origin.offset(x,y,z), roof,3);
                else level.setBlock(origin.offset(x,y,z), Blocks.AIR.defaultBlockState(),3);
            }
            level.setBlock(origin.offset(x,height,z), roof,3);
        }
        level.setBlock(origin.offset(width/2,1,0), Blocks.AIR.defaultBlockState(),3);
        level.setBlock(origin.offset(width/2,2,0), Blocks.AIR.defaultBlockState(),3);
    }
}
