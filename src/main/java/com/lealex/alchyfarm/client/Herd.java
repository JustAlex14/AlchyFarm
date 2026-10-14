package com.lealex.alchyfarm.client;

import com.lealex.alchyx.client.GhostCollector;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.RanchBlockEntity;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Figures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * The figure animation files call {@code alchyfarm:herd}: a ranch's animals, drawn as ghosts. The ranch keeps its
 * herd as data, not as animals in the world, and tells clients which kinds it holds. ONE ghost is drawn for each
 * kind, however many the ranch has of it: a stand-in (a real animal object of that kind, never put in the world,
 * made once and kept while the herd's kinds do not change), drawn by its own vanilla renderer.
 *
 * A ghost roams its pen as an animal would, all worked out here: it walks to some spot a few steps off in any
 * direction (never out of the pen), stops for a while now and then, and its legs move with it. When the
 * soul's errand is for its kind, it walks to the square the soul is going to and waits there for it.
 *
 * The look is GhostCollector's: everything the animal's renderer draws (its layers too: a sheep's wool) is
 * see-through, glowing and greenish. Haunted.
 */
public final class Herd {
    /** The haunted look (AlchyX GhostCollector): a sickly green, 60% solid, a quarter washed toward white. */
    public static final int HAUNTED = 0x9986F0A2;
    public static final float HAUNTED_WHITE = 0.35F;

    private static final float WALK = 0.045F, HURRY = 0.11F, TURN = 10.0F; // blocks and degrees per tick

    /** One kind's ghost and where it is (in blocks from the post's square). */
    private static final class Ghost {
        final Entity stand;
        final String kind;
        final Random random;
        float x, z, heading;
        float tx, tz;      // the spot of the pen it is walking to
        double restUntil;  // game time: it stands until then
        boolean placed;

        Ghost(Entity stand, String kind, int seed) {
            this.stand = stand;
            this.kind = kind;
            this.random = new Random(seed);
        }
    }

    private static final class Herded {
        @Nullable List<CompoundTag> looks; // the list the ghosts were made from (a herd of other kinds is a new list)
        @Nullable Level level;
        final List<Ghost> ghosts = new ArrayList<>();
        double time = Double.NaN;
    }

    private static final Map<Object, Herded> HERDS = new WeakHashMap<>();

    private Herd() {}

    public static void register() {
        Figures.register(Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "herd"), Herd::submit);
    }

    private static Herded herded(RanchBlockEntity ranch, Level level) {
        Herded herded = HERDS.computeIfAbsent(ranch, key -> new Herded());
        List<CompoundTag> looks = ranch.looks();
        if (herded.looks == looks && herded.level == level) return herded;
        // Ghosts of kinds still there keep their place; new kinds get a new ghost
        List<Ghost> before = new ArrayList<>(herded.ghosts);
        herded.looks = looks;
        herded.level = level;
        herded.ghosts.clear();
        for (int i = 0; i < looks.size(); i++) {
            CompoundTag look = looks.get(i);
            String kind = look.getStringOr("id", "");
            Ghost kept = null;
            for (Ghost ghost : before) {
                if (ghost.kind.equals(kind) && ghost.stand.level() == level) kept = ghost;
            }
            if (kept != null) {
                herded.ghosts.add(kept);
                continue;
            }
            try {
                Identifier id = Identifier.tryParse(kind);
                EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                Entity made = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
                if (made == null) continue;
                made.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), look));
                herded.ghosts.add(new Ghost(made, kind, kind.hashCode() * 31 + i));
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: a ranch animal ({}) can't be drawn: {}", kind, e.toString());
            }
        }
        return herded;
    }

    /** True if this spot is in the pen, a little clear of its fence. */
    private static boolean inPen(List<BlockPos> pen, float x, float z) {
        for (float dx = -0.3F; dx <= 0.3F; dx += 0.6F) {
            for (float dz = -0.3F; dz <= 0.3F; dz += 0.6F) {
                if (!pen.contains(new BlockPos(Math.round(x + dx), 0, Math.round(z + dz)))) return false;
            }
        }
        return true;
    }

    /**
     * One ghost's life for this frame: where it wants to be, a step toward it, the way it faces. It walks as an
     * animal does: to some spot a few steps off in any direction (one it can reach in a straight line inside the
     * pen), turning as it sets off, then stands a while, then off again. Returns its speed.
     */
    private static float roam(Ghost ghost, RanchBlockEntity ranch, int number, double now, float ticks) {
        List<BlockPos> pen = ranch.field();
        if (!ghost.placed || !pen.contains(new BlockPos(Math.round(ghost.x), 0, Math.round(ghost.z)))) {
            BlockPos home = ranch.home(number); // new, or its square left the pen
            ghost.x = ghost.tx = home.getX();
            ghost.z = ghost.tz = home.getZ();
            ghost.heading = ghost.random.nextFloat() * 360.0F;
            ghost.placed = true;
        }
        // Called by the soul: its errand is for this kind, and not over
        double since = now - ranch.taskStart();
        boolean called = ghost.kind.equals(ranch.errandKind()) && since >= 0 && since < ranch.travelTicks() + ranch.errandTicks()
                && (ranch.errandGesture() == RanchBlockEntity.HARVEST || ranch.errandGesture() == RanchBlockEntity.TEND);
        float speed = WALK;
        if (called) {
            ghost.tx = ranch.soulTarget().getX();
            ghost.tz = ranch.soulTarget().getZ();
            ghost.restUntil = 0;
            speed = HURRY;
        }
        float dx = ghost.tx - ghost.x, dz = ghost.tz - ghost.z;
        float far = Mth.sqrt(dx * dx + dz * dz);
        float moved = 0.0F;
        if (far < 0.05F) {
            if (called) { // waiting for the soul, turned to it
                BlockPos soul = ranch.soulTo();
                dx = soul.getX() - ghost.x;
                dz = soul.getZ() - ghost.z;
            } else {
                dx = dz = 0.0F;
                if (now >= ghost.restUntil) { // a rest, or off to another spot
                    if (ghost.random.nextFloat() < 0.45F) {
                        ghost.restUntil = now + 30 + ghost.random.nextInt(130);
                    } else {
                        for (int tries = 0; tries < 8; tries++) {
                            double angle = ghost.random.nextDouble() * Math.PI * 2.0;
                            float reach = 0.8F + ghost.random.nextFloat() * 2.6F;
                            float x = ghost.x + (float) Math.cos(angle) * reach, z = ghost.z + (float) Math.sin(angle) * reach;
                            boolean clear = true;
                            for (float along = 0.4F; clear && along <= reach; along += 0.4F) {
                                clear = inPen(pen, ghost.x + (x - ghost.x) / reach * along, ghost.z + (z - ghost.z) / reach * along);
                            }
                            if (!clear || !inPen(pen, x, z)) continue;
                            ghost.tx = x;
                            ghost.tz = z;
                            break;
                        }
                    }
                }
            }
        } else if (now >= ghost.restUntil) {
            // It turns toward its spot first, and only walks once it mostly faces it
            float off = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - ghost.heading));
            if (off < 55.0F || called) {
                moved = Math.min(far, speed * ticks * (called ? 1.0F : 1.0F - off / 110.0F));
                ghost.x += dx / far * moved;
                ghost.z += dz / far * moved;
            }
        }
        if (dx != 0.0F || dz != 0.0F) { // entity convention: heading 0 looks toward +z
            float wanted = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float most = TURN * ticks;
            ghost.heading += Mth.clamp(Mth.wrapDegrees(wanted - ghost.heading), -most, most);
        }
        return ticks <= 0.0F ? 0.0F : moved / ticks;
    }

    private static void submit(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!(context.owner() instanceof RanchBlockEntity ranch) || ranch.getLevel() == null || ranch.looks().isEmpty()) return;
        if (ranch.field().isEmpty()) return; // no pen: the herd is kept, not shown
        Level level = ranch.getLevel();
        Herded herded = herded(ranch, level);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        double now = level.getGameTime() + (double) context.partialTicks();
        float ticks = Double.isNaN(herded.time) ? 0.0F : (float) Math.clamp(now - herded.time, 0.0, 5.0);
        herded.time = now;
        BlockPos post = ranch.getBlockPos();
        GhostCollector spectral = new GhostCollector(collector, HAUNTED, HAUNTED_WHITE);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F)); // the plain block frame, at the post's foot
        for (int i = 0; i < herded.ghosts.size(); i++) {
            Ghost ghost = herded.ghosts.get(i);
            Entity stand = ghost.stand;
            float speed = roam(ghost, ranch, i, now, ticks);
            poseStack.pushPose();
            try {
                double wx = post.getX() + 0.5 + ghost.x, wy = post.getY(), wz = post.getZ() + 0.5 + ghost.z;
                stand.setPos(wx, wy, wz); // for its light
                stand.xo = stand.xOld = wx;
                stand.yo = stand.yOld = wy;
                stand.zo = stand.zOld = wz;
                stand.setYRot(ghost.heading);
                stand.yRotO = ghost.heading;
                stand.tickCount = (int) (now % 24000.0);
                if (stand instanceof LivingEntity living) {
                    living.yBodyRot = living.yBodyRotO = ghost.heading;
                    living.yHeadRot = living.yHeadRotO = ghost.heading + Mth.sin((float) (now * 0.05) + i * 1.3F) * 20.0F;
                    // Its legs: once per tick, by how fast it is going
                    if (ticks > 0.0F && (long) now != (long) (now - ticks)) living.walkAnimation.update(Math.min(speed * 8.0F, 1.0F), 0.4F, 1.0F);
                }
                EntityRenderState state = dispatcher.extractEntity(stand, context.partialTicks());
                state.shadowPieces.clear(); // drawn at a pose, not where the stand-in "is": no shadow, no name
                state.shadowRadius = 0;
                state.nameTag = null;
                poseStack.translate(ghost.x, 0.0F, ghost.z);
                dispatcher.submit(state, camera, 0, 0, 0, poseStack, spectral); // its own renderer, through the ghost's eyes
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: a ranch animal ({}) can't be drawn: {}", ghost.kind, e.toString());
                herded.ghosts.remove(i--); // once: it is left out until the herd changes
            } finally {
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }
}
