package ru.felexwerd.kingdomprison;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

@Mod(KingdomPrisonMod.MOD_ID)
public class KingdomPrisonMod {
    public static final String MOD_ID = "kingdomprison";
    public KingdomPrisonMod() {
        MinecraftForge.EVENT_BUS.register(KingdomEvents.class);
    }
}
