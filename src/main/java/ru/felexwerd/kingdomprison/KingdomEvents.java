package ru.felexwerd.kingdomprison;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.projectile.Egg;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class KingdomEvents {
    private static final int BORDER=48, JAIL_SECONDS=60, PUNISH_SECONDS=120, LEAVE_SECONDS=180;
    private static final Map<UUID,Integer> jail=new HashMap<>(), punishment=new HashMap<>(), leave=new HashMap<>(), intro=new HashMap<>();
    private static final Set<String> built=new HashSet<>();
    private static final Set<UUID> questDone=new HashSet<>(), escaped=new HashSet<>();
    private KingdomEvents() {}

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        intro.put(p.getUUID(),24);
        p.sendSystemMessage(Component.literal("§6§lКОРОЛЕВСТВО АЛЬДРИКА"));
        p.sendSystemMessage(Component.literal("§7Король Альдрик: «Соблюдай закон — и будешь свободен»."));
        p.sendSystemMessage(Component.literal("§eОсмотрись и найди короля в замке."));
    }

    @SubscribeEvent public static void kingTalk(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Villager v) || !(e.getEntity() instanceof ServerPlayer p) ||
            !v.getPersistentData().getBoolean("KingdomPrisonKing") || p.level().isClientSide) return;
        e.setCanceled(true);
        UUID id=p.getUUID();
        if (!questDone.contains(id)) {
            if (p.getInventory().countItem(Items.COBBLESTONE)<16) {
                p.sendSystemMessage(Component.literal("§6Король Альдрик: «Принеси мне 16 блоков булыжника»."));
                p.sendSystemMessage(Component.literal("§7Задание: добудь 16 булыжника и вернись ко мне."));
                return;
            }
            p.getInventory().clearOrCountMatchingItems(s->s.is(Items.COBBLESTONE),16,p.inventoryMenu.getCraftSlots());
            questDone.add(id);
            p.sendSystemMessage(Component.literal("§6Король: «Задание выполнено. Дарую выход за стены на три минуты!»"));
        } else p.sendSystemMessage(Component.literal("§6Король: «Можешь покинуть владения на три минуты. Возвращайся вовремя»."));
        leave.put(id,LEAVE_SECONDS);
    }

    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p.level().isClientSide) return;
        BlockPos s=p.serverLevel().getSharedSpawnPos(), b=e.getPos();
        if (inside(b,s.offset(13,0,-5),11,8,11) &&
            (p.serverLevel().getBlockState(b).is(Blocks.IRON_BARS)||p.serverLevel().getBlockState(b).is(Blocks.IRON_DOOR))) {
            escaped.add(p.getUUID()); e.setCanceled(false);
            p.sendSystemMessage(Component.literal("§a§lПОБЕГ ИЗ ТЕМНИЦЫ! §fТы нашёл слабое место в решётке."));
            return;
        }
        if (Math.abs(p.getX()-s.getX())<=BORDER && Math.abs(p.getZ()-s.getZ())<=BORDER) {
            if (inside(b,s.offset(13,0,-5),11,8,11)) return;
            e.setCanceled(true); arrest(p,"§cТы нарушил закон короля! Гарпия забирает тебя.");
        }
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.level().isClientSide) return;
        UUID id=p.getUUID();
        if (p.tickCount%20==0) {
            Integer t=intro.get(id);
            if (t!=null) {
                if (t>0) { p.displayClientMessage(Component.literal("§6АЛЬДРИК §8| §fВступление §e"+t+" сек."),true); intro.put(id,t-1); p.setYRot(p.getYRot()+5); }
                else { intro.remove(id); p.sendSystemMessage(Component.literal("§aВступление окончено. Найди короля Альдрика.")); }
            }
            tickTimer(p,jail,JAIL_SECONDS,"§4ТЕМНИЦА","Освобождение",true);
            Integer punish=punishment.get(id);
            if (punish!=null) {
                if (punish>0) { p.displayClientMessage(Component.literal("§c§lПУБЛИЧНОЕ НАКАЗАНИЕ §8| §fОсталось §e"+punish+" сек."),true); if(punish%3==0) egg(p); punishment.put(id,punish-1); }
                else { punishment.remove(id); arrest(p,"§cНаказание окончено. Один часовой срок в темнице."); }
            }
            Integer l=leave.get(id);
            if(l!=null) { if(l>0) { p.displayClientMessage(Component.literal("§aРАЗРЕШЕНИЕ КОРОЛЯ §8| §fЗа стенами: §e"+l+" сек."),true); leave.put(id,l-1); } else { leave.remove(id); p.sendSystemMessage(Component.literal("§cВремя разрешения истекло. Вернись за стены!")); } }
        }
        BlockPos s=p.serverLevel().getSharedSpawnPos();
        boolean outside=Math.abs(p.getX()-s.getX())>BORDER||Math.abs(p.getZ()-s.getZ())>BORDER;
        if(outside && !leave.containsKey(id) && !jail.containsKey(id) && !escaped.contains(id)) {
            int x=(int)(s.getX()+Math.signum(p.getX()-s.getX())*(BORDER-2)), z=(int)(s.getZ()+Math.signum(p.getZ()-s.getZ())*(BORDER-2));
            p.teleportTo(x,safeY(p.serverLevel(),x,z),z);
            p.displayClientMessage(Component.literal("§cГраница королевства. Попроси короля о разрешении."),true);
        }
    }

    private static void tickTimer(ServerPlayer p,Map<UUID,Integer> map,int duration,String title,String label,boolean release) {
        Integer n=map.get(p.getUUID()); if(n==null)return;
        if(n>0) { p.displayClientMessage(Component.literal(title+" §8| §f"+label+": §c"+n+" сек."),true); map.put(p.getUUID(),n-1); }
        else { map.remove(p.getUUID()); if(release) { BlockPos s=p.serverLevel().getSharedSpawnPos(); p.teleportTo(p.serverLevel(),s.getX()+5.5,safeY(p.serverLevel(),s.getX()+5,s.getZ()+5),s.getZ()+5.5,p.getYRot(),p.getXRot()); p.sendSystemMessage(Component.literal("§aСтража отпустила тебя.")); } }
    }

    private static void arrest(ServerPlayer p,String msg) {
        ServerLevel l=p.serverLevel(); BlockPos s=l.getSharedSpawnPos(), cell=s.offset(18,1,0);
        p.sendSystemMessage(Component.literal(msg));
        Entity harpy=EntityType.VEX.create(l);
        if(harpy!=null) { harpy.moveTo(p.getX(),p.getY()+2,p.getZ(),p.getYRot(),0); harpy.setCustomName(Component.literal("§cКоролевская гарпия")); harpy.setCustomNameVisible(true); l.addFreshEntity(harpy); }
        p.teleportTo(l,cell.getX()+.5,safeY(l,cell.getX(),cell.getZ()),cell.getZ()+.5,90,0);
        jail.put(p.getUUID(),JAIL_SECONDS);
    }
    private static void egg(ServerPlayer p) {
        Egg egg=new Egg(p.serverLevel(),p);
        egg.setPos(p.getX()+(p.serverLevel().random.nextDouble()-.5)*5,p.getY()+2.5,p.getZ()+(p.serverLevel().random.nextDouble()-.5)*5);
        egg.shoot(p.getX()-egg.getX(),.15,p.getZ()-egg.getZ(),.8F,8F); p.serverLevel().addFreshEntity(egg);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        for(ServerLevel l:e.getServer().getAllLevels()) {
            String k=l.dimension().location()+"@"+l.getServer().getWorldData().getLevelName();
            if(built.add(k)) build(l);
        }
    }

    private static void build(ServerLevel l) {
        BlockPos s=l.getSharedSpawnPos(); int y=safeY(l,s.getX(),s.getZ());
        // A broad levelled plaza ensures the castle, jail and market share one ground plane.
        for(int x=-25;x<=25;x++) for(int z=-25;z<=25;z++) for(int dy=-3;dy<=12;dy++) {
            BlockPos q=new BlockPos(s.getX()+x,y+dy,s.getZ()+z);
            if(dy==-1) l.setBlock(q,Blocks.DIRT.defaultBlockState(),3);
            else if(dy<-1) l.setBlock(q,Blocks.STONE.defaultBlockState(),3);
            else if(dy==0) l.setBlock(q,Blocks.GRASS_BLOCK.defaultBlockState(),3);
            else if(!l.getBlockState(q).isAir()) l.setBlock(q,Blocks.AIR.defaultBlockState(),3);
        }
        for(int x=-BORDER;x<=BORDER;x++) for(int z=-BORDER;z<=BORDER;z++) if(Math.abs(x)==BORDER||Math.abs(z)==BORDER) {
            int gy=safeY(l,s.getX()+x,s.getZ()+z);
            if(Math.abs(x)==BORDER&&Math.abs(z)==BORDER) continue;
            if((x==0&&Math.abs(z)==BORDER)||(z==0&&Math.abs(x)==BORDER))continue;
            for(int h=0;h<5;h++) l.setBlock(new BlockPos(s.getX()+x,gy+h,s.getZ()+z),h==4?Blocks.STONE_BRICK_WALL.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState(),3);
        }
        for(int x:new int[]{-BORDER,BORDER})for(int z:new int[]{-BORDER,BORDER}) { int gy=safeY(l,s.getX()+x,s.getZ()+z); for(int h=0;h<9;h++)for(int a=-2;a<=2;a++)for(int b=-2;b<=2;b++)if(Math.abs(a)==2||Math.abs(b)==2)l.setBlock(new BlockPos(s.getX()+x+a,gy+h,s.getZ()+z+b),Blocks.COBBLESTONE.defaultBlockState(),3); }
        building(l,s.offset(-7,0,-12),15,10,15,Blocks.STONE_BRICKS.defaultBlockState(),Blocks.DEEPSLATE_TILES.defaultBlockState());
        building(l,s.offset(13,0,-5),11,6,11,Blocks.COBBLED_DEEPSLATE.defaultBlockState(),Blocks.IRON_BARS.defaultBlockState());
        for(int x=15;x<=21;x++)for(int z=-3;z<=3;z++)if(x==15||x==21||z==-3||z==3)l.setBlock(s.offset(x,1,z),Blocks.IRON_BARS.defaultBlockState(),3);
        l.setBlock(s.offset(18,1,4),Blocks.IRON_DOOR.defaultBlockState(),3);
        for(int i=0;i<5;i++) {
            int x=-16+i*8,z=17;
            for(int a=-2;a<=2;a++)for(int b=-2;b<=2;b++)l.setBlock(s.offset(x+a,0,z+b),Blocks.OAK_PLANKS.defaultBlockState(),3);
            for(int a:new int[]{-2,2})for(int b:new int[]{-2,2}){l.setBlock(s.offset(x+a,1,z+b),Blocks.OAK_FENCE.defaultBlockState(),3);l.setBlock(s.offset(x+a,3,z+b),Blocks.RED_WOOL.defaultBlockState(),3);}
            for(int a=-2;a<=2;a++)l.setBlock(s.offset(x+a,4,z),Blocks.RED_WOOL.defaultBlockState(),3);
            l.setBlock(s.offset(x,1,z),Blocks.BARREL.defaultBlockState(),3);
        }
        l.setBlock(s.offset(0,0,5),Blocks.GOLD_BLOCK.defaultBlockState(),3); l.setBlock(s.offset(0,1,5),Blocks.LANTERN.defaultBlockState(),3);
        Villager king=EntityType.VILLAGER.create(l);
        if(king!=null){king.moveTo(s.getX()+.5,s.getY()+1,s.getZ()+2.5,180,0);king.setVillagerData(king.getVillagerData().setType(VillagerType.PLAINS).setProfession(VillagerProfession.CLERIC).setLevel(5));king.setCustomName(Component.literal("§6§lКороль Альдрик"));king.setCustomNameVisible(true);king.setPersistenceRequired();king.getPersistentData().putBoolean("KingdomPrisonKing",true);l.addFreshEntity(king);}
        VillagerProfession[] jobs={VillagerProfession.FARMER,VillagerProfession.LIBRARIAN,VillagerProfession.ARMORER,VillagerProfession.WEAPONSMITH,VillagerProfession.BUTCHER};
        String[] names={"§eФермер","§bКниготорговец","§7Бронник","§cОружейник","§6Мясник"};
        for(int i=0;i<5;i++){Villager v=EntityType.VILLAGER.create(l);if(v!=null){v.moveTo(s.getX()-16+i*8+.5,s.getY()+1,s.getZ()+17.5,0,0);v.setVillagerData(v.getVillagerData().setProfession(jobs[i]).setLevel(3));v.setCustomName(Component.literal(names[i]));v.setCustomNameVisible(true);v.setPersistenceRequired();l.addFreshEntity(v);}}
        for(int i=0;i<4;i++){IronGolem g=EntityType.IRON_GOLEM.create(l);if(g!=null){g.moveTo(s.getX()+(i%2==0?-5:5),s.getY()+1,s.getZ()+(i<2?-8:8),0,0);g.setCustomName(Component.literal("§7Королевская стража"));g.setCustomNameVisible(true);g.setPlayerCreated(true);g.setPersistenceRequired();l.addFreshEntity(g);}}
        for(int i=0;i<4;i++){Villager v=EntityType.VILLAGER.create(l);if(v!=null){v.moveTo(s.getX()+(i-2)*3,s.getY()+1,s.getZ()+22,0,0);v.setCustomName(Component.literal("§fЖитель королевства"));v.setPersistenceRequired();l.addFreshEntity(v);}}
    }
    private static void building(ServerLevel l,BlockPos o,int w,int h,int d,BlockState wall,BlockState roof) {
        for(int x=0;x<w;x++)for(int z=0;z<d;z++){l.setBlock(o.offset(x,0,z),Blocks.STONE_BRICKS.defaultBlockState(),3);for(int y=1;y<h;y++){boolean edge=x==0||z==0||x==w-1||z==d-1;l.setBlock(o.offset(x,y,z),edge?wall:(y==h-1?roof:Blocks.AIR.defaultBlockState()),3);}l.setBlock(o.offset(x,h,z),roof,3);}
        l.setBlock(o.offset(w/2,1,0),Blocks.AIR.defaultBlockState(),3);l.setBlock(o.offset(w/2,2,0),Blocks.AIR.defaultBlockState(),3);
    }
    private static boolean inside(BlockPos p,BlockPos o,int w,int h,int d){return p.getX()>=o.getX()&&p.getX()<o.getX()+w&&p.getY()>=o.getY()&&p.getY()<o.getY()+h&&p.getZ()>=o.getZ()&&p.getZ()<o.getZ()+d;}
    private static int safeY(ServerLevel l,int x,int z){return Math.max(l.getMinBuildHeight()+2,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z));}
}
