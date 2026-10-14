package com.lealex.alchyfarm.client;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.PondBlockEntity;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Errands;
import com.lealex.alchyx.client.animation.Figures;
import com.lealex.alchyx.client.RiftDecal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The figure animation files call {@code alchyfarm:farmhand}: the lost soul working one of the estate's workstations
 * (a crop plot, a ranch, an orchard, a pond, a mushroom farm). The file places it
 * on the stake. It is drawn where the plot's last errand has it (AlchyX's Errands works that out from the clock):
 * gliding from one square to another, then at work there (sowing, harvesting with the crop shown in its hands,
 * casting bone meal). Between errands it wanders about its ground by itself (worked out here, nothing is sent for
 * it), and its ways bend a little. When it first comes, a
 * rift (AlchyX's RiftDecal) opens over its square and it grows out of it.
 */
public final class Farmhand {
    /** A little see-through (ARGB multiplied into the skin, which is already obsidian and amethyst: dark reads thinner). */
    public static final int TINT = 0xC8FFFFFF;
    // One skin for each workstation's soul (tools/entity_textures.py), by the look its block entity names
    private static final Map<String, Identifier> SKINS = new HashMap<>();
    // Its rift outline: GLOW_FRAMES pictures for each look (the same number as in the script), one every GLOW_TICKS
    private static final Map<String, Identifier> GLOWS = new HashMap<>();
    private static final int GLOW_FRAMES = 8;
    private static final float GLOW_TICKS = 5.0F;

    private static Identifier skin(String look) {
        return SKINS.computeIfAbsent(look, name -> Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/entity/lost_soul_" + name + ".png"));
    }
    private static final float TURN = 9.0F; // degrees per tick it turns toward where it is going
    private static final float STROLL_TICKS = 18.0F; // ticks a block takes when it wanders by itself (unhurried)
    private static final float HURRY_TICKS = 7.0F;   // ticks a block takes at most when an errand calls it from where it wandered
    private static final float WANDER_AFTER = 40.0F; // ticks it stands after an errand before it wanders off
    private static final float WANDER_REACH = 3.5F;  // how far from the square of its last errand it wanders, in blocks
    private static final float CHOP_BACK = 0.55F; // blocks it stands back from the trunk's square while it swings
    private static final float HANDS = 0.6F, AXE = 1.5F; // how far down the arms the hands are, and the axe's size
    private static final float AXE_TILT = 65.0F;         // degrees the handle stands off the arms' line
    private static final float ROD = 1.5F, ROD_TILT = 60.0F; // the fishing rod's size, and its angle off the arms' line
    // The rod's tip in the item's own frame: the top right pixel of its picture (14, 1), which an item lying on the
    // ground shows at half size, two sixteenths up (item/generated's "ground" display)
    private static final float TIP_X = 0.5F * (14.5F / 16.0F - 0.5F), TIP_Y = 0.5F * (14.5F / 16.0F - 0.5F) + 0.125F;
    private static final Identifier CAST_ROD = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "cast_rod");
    // The two rods as drawn (with its bit of line, and cast), made again every few seconds as Errands.held does
    private static final ItemStackRenderState[] RODS = new ItemStackRenderState[2];
    private static long rodsMade = Long.MIN_VALUE;

    private static ItemStackRenderState rod(Level level, boolean cast) {
        long time = level.getGameTime();
        if (RODS[0] == null || time - rodsMade > 100 || time < rodsMade) {
            rodsMade = time;
            for (int i = 0; i < 2; i++) {
                ItemStack stack = new ItemStack(Items.FISHING_ROD);
                if (i == 1) stack.set(DataComponents.ITEM_MODEL, CAST_ROD);
                RODS[i] = new ItemStackRenderState();
                Minecraft.getInstance().getItemModelResolver().updateForTopItem(RODS[i], stack, ItemDisplayContext.GROUND, level, null, 0);
            }
        }
        return RODS[cast ? 1 : 0];
    }
    private static final float FLOAT_HEIGHT = -0.2F; // the water's surface, from the figure's feet (it hovers over the post's level)
    private static final float REEL = 0.04F;         // the share of a fishing errand the float takes to come back
    private static final RenderType FLOAT = RenderTypes.entityCutoutCull(Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png"));
    // Its arrival: a rift opens over its square, it comes out of it, the rift closes (ticks since it came)
    private static final float RIFT_OPEN = 12.0F, OUT_FROM = 8.0F, OUT_UNTIL = 30.0F, RIFT_CLOSING = 34.0F, ARRIVAL = 50.0F;
    private static final float RIFT_HEIGHT = 1.0F, RIFT_SIZE = 1.9F;
    private static final int RIFT_GLOW = 0xB98CFF;

    /** What the figure remembers of one workstation between frames. */
    // The cast's motes: the evoker's own spell particle, in the rift's violet; and how far under its point a portal mote ends
    private static final net.minecraft.core.particles.ColorParticleOption SPELL = net.minecraft.core.particles.ColorParticleOption.create(
            net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0.62F, 0.36F, 0.95F);
    private static final double PORTAL_SINK = 0.2;

    private static final class Roam {
        float x, z;                       // where it was last drawn, in blocks from the post
        boolean placed;
        long errand = Long.MIN_VALUE;     // the start of the workstation's errand it last saw
        float fromX, fromZ;               // where it was when that errand began
        Errands.@Nullable Errand stroll;  // its own wandering, while the workstation has nothing for it
        final Random random = new Random();
        long sparked = Long.MIN_VALUE;    // the tick its cast last threw motes
    }

    private static final Map<Object, Roam> ROAMS = new WeakHashMap<>();

    /**
     * True if this spot is on the workstation's ground, a little clear of its edge, and free to stand on: nothing
     * there to walk into (a trunk, a cactus; crops and mushrooms are walked through).
     */
    private static boolean onGround(SoulPlotBlockEntity plot, List<BlockPos> field, float x, float z) {
        for (float dx = -0.3F; dx <= 0.3F; dx += 0.6F) {
            for (float dz = -0.3F; dz <= 0.3F; dz += 0.6F) {
                BlockPos square = new BlockPos(Math.round(x + dx), 0, Math.round(z + dz));
                if (!field.contains(square)) return false;
                BlockPos pos = plot.getBlockPos().offset(square);
                if (plot.getLevel() != null && !plot.getLevel().getBlockState(pos).getCollisionShape(plot.getLevel(), pos).isEmpty()) return false;
            }
        }
        return true;
    }

    /**
     * The next leg of its own wandering: to some spot of the ground a few blocks off that it can reach in a straight
     * line without leaving the ground or walking into anything, at a stroller's pace, then a rest there. If it finds none, a rest where it is.
     */
    private static Errands.Errand wander(SoulPlotBlockEntity plot, Roam roam, BlockPos anchor, double now) {
        List<BlockPos> field = plot.field();
        long start = (long) Math.floor(now);
        int rest = 25 + roam.random.nextInt(75);
        // The middle of its ground: what it turns to when it stops, so it never stands staring at its fence or wall
        float midX = 0.0F, midZ = 0.0F;
        for (BlockPos square : field) {
            midX += square.getX();
            midZ += square.getZ();
        }
        midX /= field.size();
        midZ /= field.size();
        for (int tries = 0; tries < 8; tries++) {
            BlockPos square = field.get(roam.random.nextInt(field.size()));
            // Rather not into a corner or a dead end: a square with ground on three sides at least, if it finds one
            int open = 0;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (field.contains(square.relative(side))) open++;
            }
            if (open < 3 && tries < 5) continue;
            float x = square.getX() + (roam.random.nextFloat() - 0.5F) * 0.5F, z = square.getZ() + (roam.random.nextFloat() - 0.5F) * 0.5F;
            float dx = x - roam.x, dz = z - roam.z, far = Mth.sqrt(dx * dx + dz * dz);
            if (far < 0.8F || far > 5.0F) continue;
            // Never far from where the workstation last sent it: its next errand is timed from there
            if ((x - anchor.getX()) * (x - anchor.getX()) + (z - anchor.getZ()) * (z - anchor.getZ()) > WANDER_REACH * WANDER_REACH) continue;
            boolean clear = onGround(plot, field, x, z);
            for (float along = 0.4F; clear && along < far; along += 0.4F) {
                clear = onGround(plot, field, roam.x + dx / far * along, roam.z + dz / far * along);
            }
            if (!clear) continue;
            // Where it looks once there: over its ground, toward the middle or, from the middle itself, some square
            float lookX = midX + (roam.random.nextFloat() - 0.5F) * 1.5F, lookZ = midZ + (roam.random.nextFloat() - 0.5F) * 1.5F;
            if ((lookX - x) * (lookX - x) + (lookZ - z) * (lookZ - z) < 1.0F) {
                BlockPos other = field.get(roam.random.nextInt(field.size()));
                lookX = other.getX();
                lookZ = other.getZ();
            }
            return new Errands.Errand(roam.x, roam.z, x, z, lookX, lookZ, start, Math.max(10, Math.round(far * STROLL_TICKS)), rest);
        }
        return new Errands.Errand(roam.x, roam.z, roam.x, roam.z, roam.x, roam.z, start, 0, rest);
    }

    private static float rise(float t, float from, float to) {
        float share = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return share * share * (3.0F - 2.0F * share);
    }

    // Baked on first use (the model layers are loaded by then): the villager's body, or the bogged skeleton's
    private static final Map<String, SoulModel> MODELS = new HashMap<>();

    private static SoulModel model(String look) {
        return MODELS.computeIfAbsent(look, name -> new SoulModel(Minecraft.getInstance().getEntityModels()
                .bakeLayer("bogged".equals(name) ? ModelLayers.BOGGED : ModelLayers.ZOMBIE_VILLAGER)));
    }

    private Farmhand() {}

    public static void register() {
        Figures.register(Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "farmhand"), Farmhand::submit);
    }

    private static void submit(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        SoulModel.Pose pose = new SoulModel.Pose();
        pose.age = context.time();
        pose.glide = 0.0F;
        pose.work = -1.0F;
        pose.action = 0;
        String look = context.owner() instanceof SoulPlotBlockEntity station ? station.soulLook() : "farmer";
        SoulModel model = model(look);

        // On a plot in the world (elsewhere, e.g. in a miniature, it just floats where it is put)
        if (context.owner() instanceof SoulPlotBlockEntity plot && plot.getLevel() != null) {
            double now = plot.getLevel().getGameTime() + (double) context.partialTicks();
            BlockPos from = plot.soulFrom(), to = plot.soulTo(), target = plot.soulTarget();
            int gesture = plot.errandGesture();
            // The plain block frame (x and z are squares from the post), to find the water from the figure's own
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F));
            Matrix4f blockFrame = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
            // Squares are counted from the stake, where the file puts the figure
            // Where it was last drawn is remembered: an errand starts from there (not from the square the server last
            // sent it to), and with no errand it wanders about by itself
            Roam roam = ROAMS.computeIfAbsent(plot, key -> new Roam());
            if (!roam.placed) {
                roam.x = to.getX();
                roam.z = to.getZ();
                roam.placed = true;
            }
            if (plot.taskStart() != roam.errand) {
                roam.errand = plot.taskStart();
                roam.fromX = roam.x;
                roam.fromZ = roam.z;
                roam.stroll = null;
            }
            float gap = Mth.sqrt((to.getX() - roam.fromX) * (to.getX() - roam.fromX) + (to.getZ() - roam.fromZ) * (to.getZ() - roam.fromZ));
            // The workstation times the way from the square it last sent the figure to. The figure may have wandered
            // off it: it then takes the time its own way needs, at a brisk walk (never a dart across the ground), and
            // its gesture starts that much later than the workstation's own count
            int travel = gap < 0.3F ? plot.travelTicks() : Math.max(plot.travelTicks(), Mth.ceil(gap * HURRY_TICKS));
            boolean busy = now - plot.taskStart() < travel + plot.errandTicks() || now - plot.soulSince() < ARRIVAL;
            Errands.Errand errand;
            // It only wanders off once it has stood idle a moment: between two errands that follow each other it stays
            // where it is (or it set off, was called back, and looked like it shook)
            boolean rested = now - plot.taskStart() > travel + plot.errandTicks() + WANDER_AFTER;
            boolean top = plot.maxed(); // at the top: before the post, its back to it, casting without pause
            boolean own = !busy && rested && plot.wanders() && !top && plot.field().size() > 1;
            if (own) {
                if (roam.stroll == null || now >= roam.stroll.start() + roam.stroll.travel() + roam.stroll.work()) {
                    roam.stroll = wander(plot, roam, to, now);
                }
                errand = roam.stroll;
            } else {
                // (it faces what it works on; at the top, straight away from the post, which is at 0, 0)
                boolean away = top && (to.getX() != 0 || to.getZ() != 0);
                errand = new Errands.Errand(roam.fromX, roam.fromZ, to.getX(), to.getZ(), away ? to.getX() * 2.0F : target.getX(),
                        away ? to.getZ() * 2.0F : target.getZ(), plot.taskStart(), travel, plot.errandTicks());
            }
            Errands.Moment moment = Errands.at(plot, errand, now, TURN);
            // Its way bends a little to one side, more the longer it is: nothing glides along a ruler
            float wayX = errand.toX() - errand.fromX(), wayZ = errand.toZ() - errand.fromZ(), long_ = Mth.sqrt(wayX * wayX + wayZ * wayZ);
            // (only where the bent way stays on its ground: not into a wall, a fence or a trunk)
            float most = Math.min(0.5F, long_ * 0.14F) * ((errand.start() & 1L) == 0L ? 1.0F : -1.0F);
            if (long_ > 0.6F && onGround(plot, plot.field(), (errand.fromX() + errand.toX()) / 2.0F - wayZ / long_ * most,
                    (errand.fromZ() + errand.toZ()) / 2.0F + wayX / long_ * most)) {
                float bend = most * moment.glide();
                moment = new Errands.Moment(moment.x() - wayZ / long_ * bend, moment.z() + wayX / long_ * bend, moment.heading(), moment.glide(), moment.work());
            }
            roam.x = moment.x();
            roam.z = moment.z();
            pose.glide = moment.glide();
            pose.work = own ? -1.0F : moment.work();
            pose.action = own || moment.work() < 0.0F ? 0 : gesture;
            if (top && now - plot.taskStart() >= travel) { // arrived: the cast is held, errand or not
                pose.action = SoulPlotBlockEntity.CAST;
                pose.work = 0.5F;
            }
            pose.swings = plot.swings();
            pose.age = (float) (now % 24000.0);

            // Felling: it steps back from the trunk for the swing, or its arms and its axe would be inside the wood
            // (a square's middle is one block from the trunk's, its arms reach more than half of that)
            if (pose.action == SoulPlotBlockEntity.CHOP) {
                float awayX = to.getX() - target.getX(), awayZ = to.getZ() - target.getZ();
                float far = Mth.sqrt(awayX * awayX + awayZ * awayZ);
                if (far > 0.01F) {
                    float back = CHOP_BACK * SoulModel.chopHold(pose.work, pose.swings) / far;
                    moment = new Errands.Moment(moment.x() + awayX * back, moment.z() + awayZ * back, moment.heading(), moment.glide(), moment.work());
                }
            }

            float arrived = (float) (now - plot.soulSince());
            float out = 1.0F;
            if (arrived >= 0.0F && arrived < ARRIVAL) {
                // The rift faces the camera by itself: it wants the plain block frame, not the figure's turned one
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F));
                poseStack.translate(moment.x(), RIFT_HEIGHT, moment.z());
                RiftDecal.submit(poseStack, collector, camera, RIFT_SIZE, 0,
                        rise(arrived, 0.0F, RIFT_OPEN) * (1.0F - rise(arrived, RIFT_CLOSING, ARRIVAL)), RIFT_GLOW);
                poseStack.popPose();
                out = rise(arrived, OUT_FROM, OUT_UNTIL);
                if (out <= 0.0F) return; // still on the other side
            }
            // By magic: while it casts, a small rift hangs open in front of it, at its hands (the server sends what the
            // work gives flying into it). Like the arrival's, it wants the plain block frame.
            if (pose.action == SoulPlotBlockEntity.CAST && pose.work >= 0.0F) {
                float open = rise(pose.work, 0.0F, 0.16F) * (1.0F - rise(pose.work, 0.84F, 1.0F));
                float lifted = rise(1.0F - Mth.sqrt(moment.x() * moment.x() + moment.z() * moment.z()), 0.0F, 0.7F);
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F));
                float faces = moment.heading() * Mth.DEG_TO_RAD; // (its front, as Errands.at works its heading out)
                poseStack.translate(moment.x() - Mth.sin(faces) * SoulPlotBlockEntity.CAST_AHEAD, SoulPlotBlockEntity.CAST_RIFT + lifted,
                        moment.z() + Mth.cos(faces) * SoulPlotBlockEntity.CAST_AHEAD);
                RiftDecal.submit(poseStack, collector, camera, 0.95F, 1, open, RIFT_GLOW);
                poseStack.popPose();
                // The cast is seen: each tick two motes are drawn into the rift from around it, and now and then a
                // spark leaves its raised hands (the game's own particles, made here: nothing is sent for them)
                long tick = (long) now;
                if (open > 0.3F && tick != roam.sparked) {
                    roam.sparked = tick;
                    net.minecraft.world.level.Level world = plot.getLevel();
                    double soulX = plot.getBlockPos().getX() + 0.5 + moment.x(), soulZ = plot.getBlockPos().getZ() + 0.5 + moment.z();
                    double riftX = soulX - Mth.sin(faces) * SoulPlotBlockEntity.CAST_AHEAD, riftZ = soulZ + Mth.cos(faces) * SoulPlotBlockEntity.CAST_AHEAD;
                    double riftY = plot.getBlockPos().getY() + SoulPlotBlockEntity.CAST_RIFT + lifted;
                    // The rift drinks: motes start round it and fall into its very middle. (The game's portal mote
                    // ends about a third of a block UNDER the point it is given: the point is set that much higher.)
                    for (int i = 0; i < 2; i++) {
                        world.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, riftX, riftY + PORTAL_SINK, riftZ,
                                (roam.random.nextDouble() - 0.5) * 1.6, (roam.random.nextDouble() - 0.5) * 1.2 - PORTAL_SINK, (roam.random.nextDouble() - 0.5) * 1.6);
                    }
                    // The cast itself, as an evoker's: a violet spell mote rising from each raised hand, every tick
                    float side = (moment.heading() + 90.0F) * Mth.DEG_TO_RAD; // (across its front)
                    double handsY = plot.getBlockPos().getY() + 2.0 + lifted;
                    for (int hand = -1; hand <= 1; hand += 2) {
                        world.addParticle(SPELL, soulX - Mth.sin(side) * 0.85 * hand, handsY, soulZ + Mth.cos(side) * 0.85 * hand,
                                0.0, 0.0, 0.0);
                    }
                }
            }
            // On the post's own square it floats on top of the post: it rises as it comes to it, sinks as it leaves
            float onPost = rise(1.0F - Mth.sqrt(moment.x() * moment.x() + moment.z() * moment.z()), 0.0F, 0.7F);
            if (onPost > 0.0F) poseStack.translate(0.0F, onPost, 0.0F);
            Errands.place(poseStack, context, moment);
            if (out < 1.0F) { // it grows out of the rift's middle
                poseStack.translate(0.0F, RIFT_HEIGHT * (1.0F - out), 0.0F);
                poseStack.scale(out, out, out);
            }

            // Felling: a wooden axe in its hands, turning about its shoulders with its arms (in this frame its front is
            // toward -z, and an arm hanging down points to -y: turning about x by the arms' angle raises it forward)
            if (pose.action == SoulPlotBlockEntity.CHOP && pose.work >= 0.0F) {
                float taken = SoulModel.chopHold(pose.work, pose.swings);
                if (taken > 0.01F) {
                    poseStack.pushPose();
                    poseStack.translate(0.0F, 1.36F, 0.0F);
                    // Half a turn more: "up" is then along the arm, away from the shoulder, so the axe's handle is
                    // in the hands and its head beyond them (the other way round it was held by the head)
                    poseStack.mulPose(Axis.XP.rotation(SoulModel.chopArms(pose.work, pose.swings) + (float) Math.PI));
                    // Out to the hands and a little beyond: the middle of the axe. An item's picture has its handle
                    // on the slant (bottom left to top right), so it is turned an eighth of a turn in its own plane
                    // to lay the handle along the arms (seen in his screenshot of 2026-10-09: it lay across them,
                    // from the hands back to the face).
                    // Second screenshot: along the arms it read as a stick held out. An axe is gripped across the
                    // fists: from the hands the handle goes up and a little forward, the head at its end. So: out to
                    // the hands, tilted most of a quarter turn from the arms' line, half a handle further along that
                    // tilt (the item is drawn about its middle), then the eighth of a turn for the picture's slant.
                    poseStack.translate(0.0F, HANDS, 0.0F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(AXE_TILT));
                    poseStack.translate(0.0F, 0.3F * AXE, 0.0F);
                    // The picture is shown from its other side (spin 270, not 90) so the blade is on the trunk's side
                    // of the handle, not the soul's: seen from there its slant runs the other way, hence -45.
                    poseStack.mulPose(Axis.XP.rotationDegrees(-45.0F));
                    Errands.held(poseStack, collector, plot, plot.getLevel(), plot.swingTool(), 0.0F, -0.27F * AXE, 0.0F, 270.0F, AXE * taken);
                    poseStack.popPose();
                }
            }

            // Fishing: a rod in its hands, laid as the axe is but pointing forward and up, out over the water
            if (pose.action == SoulPlotBlockEntity.FISH && pose.work >= 0.0F) {
                float taken = SoulModel.fishHold(pose.work);
                if (taken > 0.01F) {
                    Matrix4f figureFrame = new Matrix4f(poseStack.last().pose());
                    poseStack.pushPose();
                    poseStack.translate(0.0F, 1.36F, 0.0F);
                    poseStack.mulPose(Axis.XP.rotation(SoulModel.fishArms(pose.work) + (float) Math.PI));
                    poseStack.translate(0.0F, HANDS, 0.0F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(ROD_TILT));
                    poseStack.translate(0.0F, 0.3F * ROD, 0.0F);
                    // The rod's picture is shown from the side the axe's is not (spin 90 and +45): the author saw it
                    // "reversed, like the axe before" with the axe's own 270 and -45 (its line hung on the wrong side)
                    poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
                    // Laid as Errands.held lays an item, but drawn here: the pose is kept to find the rod's tip in
                    // it, and while the float is out the rod is the one without its bit of line (vanilla's "cast"
                    // picture, through the item model alchyfarm:cast_rod)
                    poseStack.translate(0.0F, -0.27F * ROD, 0.0F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                    poseStack.scale(ROD * taken, ROD * taken, ROD * taken);
                    Matrix4f rodFrame = new Matrix4f(poseStack.last().pose());
                    boolean floatOut = pose.work >= PondBlockEntity.CAST_MOMENT * 0.65F && pose.work <= PondBlockEntity.CATCH_MOMENT + REEL;
                    rod(plot.getLevel(), floatOut).submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                    poseStack.popPose();
                    // Its line and its float, drawn in the figure's frame: the rod's tip and the water's square are
                    // brought into it from the frames they are known in
                    Matrix4f toFigure = figureFrame.invert(new Matrix4f());
                    Vector3f tip = new Matrix4f(toFigure).mul(rodFrame).transformPosition(new Vector3f(TIP_X, TIP_Y, 0.0F));
                    Vector3f water = new Matrix4f(toFigure).mul(blockFrame).transformPosition(new Vector3f(
                            target.getX() + (plot instanceof PondBlockEntity pond ? pond.castX() : 0.0F), FLOAT_HEIGHT,
                            target.getZ() + (plot instanceof PondBlockEntity pond ? pond.castZ() : 0.0F)));
                    fishingLine(poseStack, collector, camera, tip, water, pose.work, pose.age, moment.heading());
                }
            }

            // The crop it has just pulled, between its two hands
            float holding = SoulModel.holding(pose.action, pose.work);
            Item held = plot.held();
            if (holding > 0.01F && held != null) {
                Errands.held(poseStack, collector, plot, plot.getLevel(), held, 0.0F, 1.02F + 0.1F * holding, -0.6F,
                        pose.age * 1.5F, 0.7F * holding);
            }
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(model, pose, poseStack, model.renderType(skin(look)),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, TINT, null, 0, null);

        // Its rift outline: the same body drawn again with two frames of the glow, one fading into the next
        float turn = pose.age / GLOW_TICKS;
        int frame = Math.floorMod((int) Math.floor(turn), GLOW_FRAMES);
        float into = turn - (float) Math.floor(turn);
        glow(model, collector, poseStack, pose, look, frame, 1.0F - into);
        glow(model, collector, poseStack, pose, look, (frame + 1) % GLOW_FRAMES, into);
    }

    /**
     * The fishing line and its float, in the figure's frame. The float leaves the rod's tip as the rod is thrown,
     * flies in an arc to the water, sits there bobbing, jerks under at the bite, and comes back to the tip.
     */
    private static void fishingLine(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, Vector3f tip, Vector3f water,
                                    float work, float age, float heading) {
        float thrown = PondBlockEntity.CAST_MOMENT * 0.65F, landed = PondBlockEntity.CAST_MOMENT, bite = PondBlockEntity.CATCH_MOMENT;
        if (work < thrown || work > bite + REEL) return;
        float out = work < landed ? (work - thrown) / (landed - thrown) : work <= bite ? 1.0F : 1.0F - (work - bite) / REEL;
        float arc = 4.0F * out * (1.0F - out);
        float fx = tip.x + (water.x - tip.x) * out, fz = tip.z + (water.z - tip.z) * out;
        float lift = tip.y + (water.y - tip.y) * out + arc * 0.9F;
        if (out >= 1.0F) {
            lift += Mth.sin(age * 0.15F) * 0.02F;
            if (work > bite - 0.05F) lift -= 0.09F * Math.abs(Mth.sin(age * 0.9F)); // something pulls at it
        }
        float fy = lift;

        // The float: vanilla's fishing hook picture, turned to the camera (out of the figure's own turn first)
        poseStack.pushPose();
        poseStack.translate(fx, fy, fz);
        poseStack.mulPose(Axis.YP.rotationDegrees(heading - 180.0F));
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.4F, 0.4F, 0.4F);
        collector.submitCustomGeometry(poseStack, FLOAT, (at, buffer) -> {
            floatCorner(buffer, at, 0.0F, 0, 0, 1);
            floatCorner(buffer, at, 1.0F, 0, 1, 1);
            floatCorner(buffer, at, 1.0F, 1, 1, 0);
            floatCorner(buffer, at, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();

        // The line: from the tip to the float, hanging a little when it is out
        float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        float sag = 0.3F * out;
        float[] ends = {tip.x, tip.y, tip.z, fx, fy + 0.08F, fz};
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (at, buffer) -> {
            for (int i = 0; i < LINE_PARTS; i++) {
                float a = i / (float) LINE_PARTS, b = (i + 1) / (float) LINE_PARTS;
                linePoint(buffer, at, ends, sag, a, b, width);
                linePoint(buffer, at, ends, sag, b, a, width);
            }
        });
    }

    private static final int LINE_PARTS = 12;

    private static void floatCorner(VertexConsumer buffer, PoseStack.Pose at, float x, int y, int u, int v) {
        buffer.addVertex(at, x - 0.5F, y - 0.5F, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(at, 0.0F, 1.0F, 0.0F);
    }

    /** One end of a part of the line: the point at share a of the way, aimed at the point at share b. */
    private static void linePoint(VertexConsumer buffer, PoseStack.Pose at, float[] ends, float sag, float a, float b, float width) {
        float x = ends[0] + (ends[3] - ends[0]) * a, y = ends[1] + (ends[4] - ends[1]) * a - sag * 4.0F * a * (1.0F - a), z = ends[2] + (ends[5] - ends[2]) * a;
        float nx = ends[0] + (ends[3] - ends[0]) * b - x, ny = ends[1] + (ends[4] - ends[1]) * b - sag * 4.0F * b * (1.0F - b) - y, nz = ends[2] + (ends[5] - ends[2]) * b - z;
        float length = Math.max(1.0E-4F, Mth.sqrt(nx * nx + ny * ny + nz * nz));
        buffer.addVertex(at, x, y, z).setColor(0xFF000000).setNormal(at, nx / length, ny / length, nz / length).setLineWidth(width);
    }

    private static void glow(SoulModel model, SubmitNodeCollector collector, PoseStack poseStack, SoulModel.Pose pose, String look, int frame, float strength) {
        int alpha = Math.round(255.0F * Mth.clamp(strength, 0.0F, 1.0F));
        if (alpha < 4) return;
        Identifier picture = GLOWS.computeIfAbsent(look + "_glow_" + frame,
                name -> Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/entity/lost_soul_" + name + ".png"));
        collector.submitModel(model, pose, poseStack, RenderTypes.entityTranslucentEmissive(picture),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF, null, 0, null);
    }
}
