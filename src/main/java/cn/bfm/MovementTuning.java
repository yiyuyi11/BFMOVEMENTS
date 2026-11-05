package cn.bfm;

import net.minecraft.network.FriendlyByteBuf;

/** Movement settings shared with clients for local prediction. */
public record MovementTuning(int flags, double slideSpeed, double slideMinSpeed, double slideDistance, double slideFriction,
        int slideTicks, int slideCooldown, double diveSpeed, double diveDistance, double diveUp) {
    public boolean slide() { return (flags & 1) != 0; }
    public boolean prone() { return (flags & 2) != 0; }
    public boolean dive() { return (flags & 4) != 0; }
    public boolean crouchSlide() { return (flags & 8) != 0; }

    public boolean slideOnSneak() { return (flags & 16) != 0; }
    public boolean slideParticles() { return (flags & 32) != 0; }
    public boolean slideRequireSprint() { return (flags & 64) != 0; }

    public static MovementTuning config() {
        return new MovementTuning((BfmConfig.SLIDE_ENABLED.get()?1:0) | (BfmConfig.PRONE_ENABLED.get()?2:0)
                | (BfmConfig.DIVE_ENABLED.get()?4:0) | (BfmConfig.SLIDE_CROUCH_STYLE.get()?8:0)
                | (BfmConfig.SLIDE_ON_SNEAK.get()?16:0) | (BfmConfig.SLIDE_PARTICLES.get()?32:0)
                | (BfmConfig.SLIDE_REQUIRE_SPRINT.get()?64:0),
                BfmConfig.SLIDE_SPEED.get(),BfmConfig.SLIDE_MIN_SPEED.get(),BfmConfig.SLIDE_DISTANCE.get(),BfmConfig.SLIDE_FRICTION.get(),
                BfmConfig.SLIDE_MAX_TICKS.get(),BfmConfig.SLIDE_COOLDOWN_TICKS.get(),BfmConfig.DIVE_SPEED.get(),BfmConfig.DIVE_DISTANCE.get(),BfmConfig.DIVE_UP.get());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(flags);
        buf.writeDouble(slideSpeed);
        buf.writeDouble(slideMinSpeed);
        buf.writeDouble(slideDistance);
        buf.writeDouble(slideFriction);
        buf.writeVarInt(slideTicks);
        buf.writeVarInt(slideCooldown);
        buf.writeDouble(diveSpeed);
        buf.writeDouble(diveDistance);
        buf.writeDouble(diveUp);
    }

    public static MovementTuning read(FriendlyByteBuf buf) {
        return new MovementTuning(buf.readUnsignedByte(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),
                buf.readVarInt(),buf.readVarInt(),buf.readDouble(),buf.readDouble(),buf.readDouble());
    }
}
