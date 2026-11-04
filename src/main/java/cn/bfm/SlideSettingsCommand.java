package cn.bfm;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-authorized in-game controls for every slide setting. */
final class SlideSettingsCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("settings").requires(source -> source.hasPermission(2))
            .executes(ctx -> show(ctx.getSource()))
            .then(toggle("enabled", BfmConfig.SLIDE_ENABLED::get, BfmConfig.SLIDE_ENABLED::set))
            .then(toggle("require_sprint", BfmConfig.SLIDE_REQUIRE_SPRINT::get, BfmConfig.SLIDE_REQUIRE_SPRINT::set))
            .then(toggle("sprint_sneak_starts_slide", BfmConfig.SLIDE_ON_SNEAK::get, BfmConfig.SLIDE_ON_SNEAK::set))
            .then(toggle("particles", BfmConfig.SLIDE_PARTICLES::get, BfmConfig.SLIDE_PARTICLES::set))
            .then(Commands.literal("style")
                .then(Commands.literal("crouch").executes(ctx -> style(ctx.getSource(), true)))
                .then(Commands.literal("reclined").executes(ctx -> style(ctx.getSource(), false))))
            .then(decimal("speed", .20, .80, BfmConfig.SLIDE_SPEED::set))
            .then(decimal("distance", .5, 16, BfmConfig.SLIDE_DISTANCE::set))
            .then(decimal("friction", .70, 1, BfmConfig.SLIDE_FRICTION::set))
            .then(decimal("camera_drop", 0, 1.4, BfmConfig.SLIDE_CAMERA_DROP::set))
            .then(integer("max_ticks", 6, 60, BfmConfig.SLIDE_MAX_TICKS::set))
            .then(integer("cooldown_ticks", 0, 200, BfmConfig.SLIDE_COOLDOWN_TICKS::set));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String key, Supplier<Boolean> current, Consumer<Boolean> set) {
        return Commands.literal(key)
            .executes(ctx -> {set.accept(!current.get()); return save(ctx.getSource(), key, current.get());})
            .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                set.accept(BoolArgumentType.getBool(ctx, "value"));
                return save(ctx.getSource(), key, current.get());
            }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> decimal(String key, double min, double max, Consumer<Double> set) {
        return Commands.literal(key).then(Commands.argument("value", DoubleArgumentType.doubleArg(min,max)).executes(ctx -> {
            double value = DoubleArgumentType.getDouble(ctx,"value");
            set.accept(value);
            return save(ctx.getSource(),key,value);
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> integer(String key, int min, int max, Consumer<Integer> set) {
        return Commands.literal(key).then(Commands.argument("value", IntegerArgumentType.integer(min,max)).executes(ctx -> {
            int value = IntegerArgumentType.getInteger(ctx,"value");
            set.accept(value);
            return save(ctx.getSource(),key,value);
        }));
    }

    private static int style(CommandSourceStack source, boolean crouch) {
        BfmConfig.SLIDE_CROUCH_STYLE.set(crouch);
        return save(source,"style",crouch ? "蹲滑" : "后仰滑铲");
    }

    private static int save(CommandSourceStack source, String key, Object value) {
        BfmConfig.SLIDE_ENABLED.save();
        for (var player : source.getServer().getPlayerList().getPlayers()) BfmNetwork.sendEyeSettings(player);
        source.sendSuccess(() -> Component.literal("滑铲设置  " + key + " = " + value)
                .append(Component.literal("  ")).append(button("[返回设置]", "", ClickEvent.Action.RUN_COMMAND)), false);
        return 1;
    }

    private static int show(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("滑铲设置 · 当前 " + (BfmConfig.SLIDE_CROUCH_STYLE.get() ? "蹲滑 " : "后仰 "))
            .append(button("[蹲滑]", "style crouch", ClickEvent.Action.RUN_COMMAND))
            .append(Component.literal(" "))
            .append(button("[后仰]", "style reclined", ClickEvent.Action.RUN_COMMAND)),false);
        source.sendSuccess(() -> Component.empty()
            .append(switchButton("滑铲", "enabled", BfmConfig.SLIDE_ENABLED.get()))
            .append(switchButton("需要冲刺", "require_sprint", BfmConfig.SLIDE_REQUIRE_SPRINT.get()))
            .append(switchButton("冲刺时按蹲触发", "sprint_sneak_starts_slide", BfmConfig.SLIDE_ON_SNEAK.get()))
            .append(switchButton("滑铲特效", "particles", BfmConfig.SLIDE_PARTICLES.get())), false);
        source.sendSuccess(() -> Component.empty()
            .append(numberButton("速度", "speed", BfmConfig.SLIDE_SPEED.get()))
            .append(numberButton("距离", "distance", BfmConfig.SLIDE_DISTANCE.get()))
            .append(numberButton("速度保留率", "friction", BfmConfig.SLIDE_FRICTION.get()))
            .append(numberButton("镜头下降", "camera_drop", BfmConfig.SLIDE_CAMERA_DROP.get())),false);
        source.sendSuccess(() -> Component.empty()
            .append(numberButton("最长时长（刻）", "max_ticks", BfmConfig.SLIDE_MAX_TICKS.get()))
            .append(numberButton("冷却（刻）", "cooldown_ticks", BfmConfig.SLIDE_COOLDOWN_TICKS.get())),false);
        return 1;
    }

    private static Component switchButton(String label, String key, boolean enabled) {
        return button("[" + label + " " + (enabled ? "开" : "关") + "] ", key, ClickEvent.Action.RUN_COMMAND);
    }

    private static Component numberButton(String label, String key, Number value) {
        return button("[" + label + " " + value + "] ", key + " " + value, ClickEvent.Action.SUGGEST_COMMAND);
    }

    private static Component button(String label, String command, ClickEvent.Action action) {
        String text = command.isEmpty() ? "/bfm settings" : "/bfm settings " + command;
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(action,text)));
    }

    private SlideSettingsCommand() {}
}
