package com.lealex.alchyfarm.client;

import com.lealex.alchyx.client.GhostCollector;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.ApiaryBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The figure animation files call {@code alchyfarm:swarm}: an apiary's bees, drawn as ghosts (the look of the
 * ranch's herd: GhostCollector). There are no bees in the world: a few stand-ins (real bee objects never put in the
 * world, two for each hive and six at most) are drawn by the game's own bee renderer, and fly as worked out here:
 * from a hive to a flower of the yard, a while over it, back to a hive, a while at it, and off again.
 */
public final class Swarm {
    private static final float FLY = 0.085F, TURN = 14.0F; // blocks and degrees per tick
    private static final int MOST = 6;

    private static final class Bee {
        final Entity stand;
        final Random random;
        float x, y, z, heading;
        float tx, ty, tz;
        double restUntil;
        boolean placed, toHive;

        Bee(Entity stand, int seed) {
            this.stand = stand;
            this.random = new Random(seed);
        }
    }

    private static final class Swarmed {
        Level level;
        final List<Bee> bees = new ArrayList<>();
        double time = Double.NaN;
        boolean broken;
    }

    private static final Map<Object, Swarmed> SWARMS = new WeakHashMap<>();

    private Swarm() {}

    public static void register() {
        Figures.register(Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "swarm"), Swarm::submit);
    }

    /** Where this bee goes next: a flower when it leaves a hive (anywhere in the yard if there is none), a hive otherwise. */
    private static void aim(Bee bee, ApiaryBlockEntity apiary) {
        List<BlockPos> to = bee.toHive ? apiary.hives() : apiary.flowers().isEmpty() ? apiary.field() : apiary.flowers();
        if (to.isEmpty()) return;
        BlockPos square = to.get(bee.random.nextInt(to.size()));
        if (bee.toHive) { // beside the hive, at its mouth's height, on a random side
            double angle = bee.random.nextDouble() * Math.PI * 2.0;
            bee.tx = square.getX() + (float) Math.cos(angle) * 0.75F;
            bee.tz = square.getZ() + (float) Math.sin(angle) * 0.75F;
            bee.ty = 0.35F + bee.random.nextFloat() * 0.3F;
        } else {
            bee.tx = square.getX() + (bee.random.nextFloat() - 0.5F) * 0.4F;
            bee.tz = square.getZ() + (bee.random.nextFloat() - 0.5F) * 0.4F;
            bee.ty = apiary.flowers().isEmpty() ? 1.0F + bee.random.nextFloat() * 0.6F : 0.75F + bee.random.nextFloat() * 0.25F;
        }
    }

    /** One bee's flight for this frame. */
    private static void fly(Bee bee, ApiaryBlockEntity apiary, double now, float ticks) {
        if (!bee.placed) {
            bee.toHive = true;
            aim(bee, apiary);
            bee.x = bee.tx;
            bee.y = bee.ty;
            bee.z = bee.tz;
            bee.heading = bee.random.nextFloat() * 360.0F;
            bee.restUntil = now + bee.random.nextInt(60);
            bee.placed = true;
        }
        float dx = bee.tx - bee.x, dy = bee.ty - bee.y, dz = bee.tz - bee.z;
        float far = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (far < 0.06F) {
            if (now >= bee.restUntil) {
                if (bee.restUntil < 0) { // just arrived: it stays a while
                    bee.restUntil = now + 25 + bee.random.nextInt(70);
                } else { // rested: off the other way
                    bee.toHive = !bee.toHive;
                    aim(bee, apiary);
                    bee.restUntil = -1;
                }
            }
            return;
        }
        if (bee.restUntil > 0 && now < bee.restUntil) return;
        bee.restUntil = -1;
        float moved = Math.min(far, FLY * ticks);
        bee.x += dx / far * moved;
        bee.y += dy / far * moved;
        bee.z += dz / far * moved;
        if (dx != 0.0F || dz != 0.0F) { // entity convention: heading 0 looks toward +z
            float wanted = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float most = TURN * ticks;
            bee.heading += Mth.clamp(Mth.wrapDegrees(wanted - bee.heading), -most, most);
        }
    }

    private static void submit(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!(context.owner() instanceof ApiaryBlockEntity apiary) || apiary.getLevel() == null) return;
        if (apiary.field().isEmpty() || apiary.hives().isEmpty() || !apiary.hasSoul()) return;
        Level level = apiary.getLevel();
        Swarmed swarm = SWARMS.computeIfAbsent(apiary, key -> new Swarmed());
        if (swarm.level != level) {
            swarm.level = level;
            swarm.bees.clear();
            swarm.broken = false;
        }
        if (swarm.broken) return;
        int wanted = Math.min(MOST, apiary.hives().size() * 2);
        while (swarm.bees.size() > wanted) swarm.bees.removeLast();
        while (swarm.bees.size() < wanted) {
            Entity made = EntityType.BEE.create(level, EntitySpawnReason.LOAD);
            if (made == null) {
                swarm.broken = true;
                return;
            }
            swarm.bees.add(new Bee(made, apiary.getBlockPos().hashCode() * 31 + swarm.bees.size()));
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        double now = level.getGameTime() + (double) context.partialTicks();
        float ticks = Double.isNaN(swarm.time) ? 0.0F : (float) Math.clamp(now - swarm.time, 0.0, 5.0);
        swarm.time = now;
        BlockPos post = apiary.getBlockPos();
        GhostCollector spectral = new GhostCollector(collector, Herd.HAUNTED, Herd.HAUNTED_WHITE);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F)); // the plain block frame, at the post's foot
        for (int i = 0; i < swarm.bees.size(); i++) {
            Bee bee = swarm.bees.get(i);
            Entity stand = bee.stand;
            fly(bee, apiary, now, ticks);
            float bob = Mth.sin((float) (now * 0.35) + i * 2.1F) * 0.05F;
            poseStack.pushPose();
            try {
                double wx = post.getX() + 0.5 + bee.x, wy = post.getY() + bee.y, wz = post.getZ() + 0.5 + bee.z;
                stand.setPos(wx, wy, wz); // for its light
                stand.xo = stand.xOld = wx;
                stand.yo = stand.yOld = wy;
                stand.zo = stand.zOld = wz;
                stand.setYRot(bee.heading);
                stand.yRotO = bee.heading;
                stand.tickCount = (int) (now % 24000.0);
                if (stand instanceof LivingEntity living) {
                    living.yBodyRot = living.yBodyRotO = bee.heading;
                    living.yHeadRot = living.yHeadRotO = bee.heading;
                }
                EntityRenderState state = dispatcher.extractEntity(stand, context.partialTicks());
                state.shadowPieces.clear(); // drawn at a pose, not where the stand-in "is": no shadow, no name
                state.shadowRadius = 0;
                state.nameTag = null;
                poseStack.translate(bee.x, bee.y + bob, bee.z);
                dispatcher.submit(state, camera, 0, 0, 0, poseStack, spectral); // its own renderer, through the ghost's eyes
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: an apiary's bees can't be drawn: {}", e.toString());
                swarm.broken = true; // once: left out from then on
                poseStack.popPose();
                break;
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
