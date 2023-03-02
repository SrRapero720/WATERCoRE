package me.srrapero720.watercore;

import me.srrapero720.watercore.api.MinecraftChatColor;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import me.srrapero720.watercore.custom.config.WaterConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class SrUtil {
    public static int toTicks(final double sec) { return (int) (sec * 20); }
    public static boolean isModOnline(String id) { return ModList.get().isLoaded(id); }

    @Contract(pure = true)
    public static @NotNull String getBroadcastPrefix() { return "§e§l[§bWATERC§eo§bRE§e§l] §f"; }

    @Contract("_, _ -> new")
    public static @NotNull Component createJoinMessage(String profile, String alias) {
        return connectionMessageBuilder(MinecraftChatColor.parse(WaterConfig.get("JOIN_MESSAGE")), profile, alias);
    }

    @Contract("_, _ -> new")
    public static @NotNull Component createLeaveMessage(String profile, String alias) {
        return connectionMessageBuilder(MinecraftChatColor.parse(WaterConfig.get("LEAVE_MESSAGE")), profile, alias);
    }

    public static Component createChatMessage(String player, String msg) {
        String format = WaterConfig.get("CHAT_FORMAT");
        return new TextComponent(format.replaceAll("%player%", player).replaceAll("%username%", player) + msg);

    }

    public static void fetchPerms() {
        var api = LuckPermsProvider.get();
    }


    public static Component connectionMessageBuilder(String message, String profile, @Nullable String alias) {
        return new TextComponent(message.replaceAll("%player%", profile)
                .replaceAll("%alias%", alias != null ? alias : "<unknown player>")
                .replaceAll("%player-alias%", "(" +profile + " aka " + alias + ")"));
    }

    public static int fixAngle(double input) { return fixAngle(Math.round(input)); }
    public static int fixAngle(float input) { return fixAngle(Math.round(input)); }
    public static int fixAngle(int input) {
        var angle = input;

        if (angle >= 0 && angle <= 45) angle = 0;
        else if (angle >= 45 && angle <= 90) angle = 90;
        else if (angle >= 90 && angle <= 135) angle = 90;
        else if (angle >= 135 && angle <= 180) angle = 180;
        else if (angle >= -180 && angle <= -135) angle = -180;
        else if (angle >= -135 && angle <= -90) angle = -90;
        else if (angle >= -90 && angle <= -45) angle = -90;
        else if (angle >= -45 && angle <= 0) angle = 0;

        return angle;
    }

    public static File getGameDir() {
        return FMLEnvironment.dist == Dist.CLIENT ? Minecraft.getInstance().gameDirectory : new File("");
    }

    /* THANKS STACKOVERFLOW
    * https://stackoverflow.com/questions/5051395/java-float-123-129456-to-123-12-without-rounding
    */
    public static float twoDecimal(double number) { return twoDecimal(Double.toString(number)); }
    public static float twoDecimal(float number) { return twoDecimal(Float.toString(number)); }
    public static float twoDecimal(String number) {
        StringBuilder sbFloat = new StringBuilder(number);
        int start = sbFloat.indexOf(".");
        if (start < 0) {
            return Float.parseFloat(sbFloat.toString());
        }
        int end = start+3;
        if((end)>(sbFloat.length()-1)) end = sbFloat.length();

        String twoPlaces = sbFloat.substring(start, end);
        sbFloat.replace(start, sbFloat.length(), twoPlaces);
        return Float.parseFloat(sbFloat.toString());
    }
}