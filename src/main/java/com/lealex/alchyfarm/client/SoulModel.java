package com.lealex.alchyfarm.client;

import com.lealex.alchyfarm.block.entity.PondBlockEntity;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A lost soul at work on a field: vanilla's zombie villager body (a villager's head and straw hat, but with arms and
 * legs of its own) with AlchyFarm's ghost skin, posed by hand. The mushroom farm's soul is the same poses on the
 * bogged skeleton's body (the same parts under the same names). It floats, so its legs only trail behind it. It has
 * three gestures: sowing (bent to the soil in front of it, pressing the seed in with one hand), harvesting (bent over
 * the plant, both hands at it, then upright with the crop held in both hands and looked over) and casting bone meal
 * (a bag held at the chest, the other hand sweeping).
 *
 * Every number of the pose is the resting float plus shares of the glide and of the gesture, and every share rises
 * from zero and falls back to zero smoothly, so nothing ever jumps from one frame to the next.
 */
public class SoulModel extends Model<SoulModel.Pose> {

    /** What the pose depends on, filled by the figure each frame. */
    public static final class Pose {
        public float age;    // ticks, with partial tick
        public float glide;  // 0 at rest, 1 in the middle of a glide to another square
        public float work;   // 0 to 1 through the gesture, or -1 when there is none
        public int action;   // SoulPlotBlockEntity.SOW, HARVEST, both (harvest first) or 0
        public int swings = 3; // how many swings a CHOP gesture has
    }

    private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

    public SoulModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    /** 0 below from, 1 above to, a smooth S in between. */
    private static float rise(float t, float from, float to) {
        float share = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return share * share * (3.0F - 2.0F * share);
    }

    /** Up between a and b, held, down between c and d. */
    private static float window(float t, float a, float b, float c, float d) {
        return rise(t, a, b) * (1.0F - rise(t, c, d));
    }

    @Override
    public void setupAnim(Pose pose) {
        super.setupAnim(pose); // back to the rest pose
        float age = pose.age;
        float sway = Mth.sin(age * 0.07F), drift = Mth.sin(age * 0.045F + 1.3F);
        float glide = Mth.clamp(pose.glide, 0.0F, 1.0F);

        // At rest it hangs in the air and looks the field over, legs a little behind, arms loose
        head.xRot = 0.18F + drift * 0.05F;
        head.yRot = sway * 0.22F * (1.0F - glide);
        rightLeg.xRot = 0.26F + sway * 0.05F + glide * 0.30F;
        leftLeg.xRot = 0.18F - sway * 0.05F + glide * 0.26F;
        rightArm.xRot = -0.08F + drift * 0.04F + glide * 0.42F;
        leftArm.xRot = -0.05F - drift * 0.04F + glide * 0.36F;
        rightArm.zRot = 0.07F + sway * 0.02F;
        leftArm.zRot = -0.07F - sway * 0.02F;
        // Carried forward: it leans into the glide
        lean(glide * 0.22F);

        if (pose.work < 0.0F || pose.action == 0) return;
        float t = Mth.clamp(pose.work, 0.0F, 1.0F);
        int action = pose.action;
        if (action == (SoulPlotBlockEntity.SOW | SoulPlotBlockEntity.HARVEST)) { // one after the other
            float cut = SoulPlotBlockEntity.HARVEST_SHARE;
            action = t < cut ? SoulPlotBlockEntity.HARVEST : SoulPlotBlockEntity.SOW;
            t = t < cut ? t / cut : (t - cut) / (1.0F - cut);
        }
        if (action == SoulPlotBlockEntity.HARVEST) {
            // Down over the plant, both hands tugging at it. Then it straightens with the crop held in front of it in
            // both hands, and takes its time looking it over before letting its arms fall.
            float down = window(t, 0.0F, 0.15F, SoulPlotBlockEntity.HARVEST_MOMENT - 0.04F, SoulPlotBlockEntity.HARVEST_MOMENT + 0.1F);
            float hold = holding(t);
            float tug = Mth.sin(age * 0.8F) * 0.2F;
            lean(down);
            rightArm.xRot += (-1.2F + tug) * down;
            leftArm.xRot += (-1.2F - tug) * down;
            rightArm.yRot = -0.25F * down - 0.5F * hold;
            leftArm.yRot = 0.25F * down + 0.5F * hold;
            rightArm.xRot += -1.25F * hold;
            leftArm.xRot += -1.25F * hold;
            head.xRot += 0.22F * hold;
            head.zRot = Mth.sin(age * 0.11F) * 0.1F * hold; // turning it over in its mind
            head.yRot *= 1.0F - hold;
        } else if (action == SoulPlotBlockEntity.CHOP) {
            // The axe (drawn by the figure, which follows chopArms) swung with both arms from over its right shoulder
            // down across its body, three times. Its body turns with each swing.
            float hold = chopHold(t, pose.swings), swing = chopSwing(t, pose.swings);
            lean(0.2F * hold);
            body.yRot = (0.35F + swing * 0.5F) * hold;
            rightArm.xRot += (-1.9F - swing * 0.9F) * hold;
            leftArm.xRot += (-1.7F - swing * 0.9F) * hold;
            rightArm.yRot = (-0.35F + swing * 0.3F) * hold;
            leftArm.yRot = (0.75F + swing * 0.3F) * hold;
            head.xRot += 0.1F * hold;
        } else if (action == SoulPlotBlockEntity.TEND) {
            // Its own time: bent a little to what it came to see, one hand on its knee, the other reaching down to
            // it and patting (an animal's back, a plant's leaves)
            float down = window(t, 0.0F, 0.25F, 0.8F, 1.0F);
            float pat = Mth.sin(t * (float) Math.PI * 6.0F) * 0.12F * down;
            lean(0.55F * down);
            rightArm.xRot += (-0.9F + pat) * down;
            rightArm.yRot = -0.15F * down;
            leftArm.xRot += -0.3F * down;
            leftArm.yRot = 0.3F * down;
            head.xRot += 0.2F * down;
            head.zRot = Mth.sin(age * 0.1F) * 0.08F * down;
        } else if (action == SoulPlotBlockEntity.FISH) {
            // Fishing: the rod (drawn by the figure, which follows fishArms) held out in both hands, drawn back and
            // flicked forward for the cast, then still but for a slow breath, pulled up sharply at the bite, and the
            // catch held up in both hands and looked over
            float hold = fishHold(t), shown = holding(SoulPlotBlockEntity.FISH, t);
            float arms = fishArms(t) + Mth.sin(age * 0.09F) * 0.03F * hold;
            lean(0.12F * hold - 0.35F * fishBack(t)); // it leans back with the rod, then throws
            rightArm.xRot += -arms - 1.25F * shown;
            leftArm.xRot += -arms - 1.25F * shown;
            rightArm.yRot = -0.3F * hold - 0.5F * shown;
            leftArm.yRot = 0.3F * hold + 0.5F * shown;
            head.xRot += 0.1F * hold + 0.22F * shown;
            head.yRot *= 1.0F - Math.max(hold, shown);
        } else if (action == SoulPlotBlockEntity.CAST) {
            // Magic: as an evoker casts. Both arms thrown up and out, hands working in small circles against each
            // other, the body straight (no bend, no tool), eyes on the rift open in front of it
            float hold = window(t, 0.0F, 0.14F, 0.86F, 1.0F);
            float wave = Mth.cos(age * 0.6662F) * 0.25F;
            rightArm.zRot += 2.2F * hold;
            leftArm.zRot += -2.2F * hold;
            rightArm.xRot += (wave - 0.25F) * hold;
            leftArm.xRot += (-wave - 0.25F) * hold;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            head.xRot += -0.1F * hold;
            head.yRot *= 1.0F - hold;
        } else if (action == SoulPlotBlockEntity.FERTILIZE) {
            // Bone meal for the whole field: a bag held at the chest, the other hand casting it in two wide sweeps
            float hold = window(t, 0.0F, 0.2F, 0.8F, 1.0F);
            float sweep = t * (float) Math.PI * 4.0F;
            lean(0.25F * hold);
            leftArm.xRot += -0.95F * hold;
            leftArm.yRot = 0.55F * hold;
            rightArm.xRot += (-1.0F + Mth.cos(sweep) * 0.4F) * hold;
            rightArm.yRot = (0.3F - Mth.sin(sweep) * 0.75F) * hold;
            head.xRot += 0.2F * hold;
        } else {
            // Sowing: it bends right down to the soil in front of it, one hand on its knee, and presses the seed in
            // with the other, twice, before straightening
            float down = window(t, 0.0F, 0.28F, 0.78F, 1.0F);
            float press = Math.max(0.0F, Mth.sin(t * (float) Math.PI * 4.0F - 1.2F)) * down;
            lean(1.15F * down);
            rightArm.xRot += (-0.75F - 0.3F * press) * down;
            rightArm.y += 1.6F * press;
            rightArm.yRot = -0.12F * down;
            leftArm.xRot += -0.35F * down;
            leftArm.yRot = 0.3F * down;
            head.xRot += 0.15F * down;
        }
    }

    /** How far the axe gesture is taken up, t being the time through a chop (0 to 1): 0 at both ends. */
    public static float chopHold(float t, int swings) {
        float edge = 0.3F / Math.max(1, swings); // a third of a swing to take the tool up, and to let it down
        return window(t, 0.0F, edge, 1.0F - edge, 1.0F);
    }

    /**
     * Where a swing is at time t of a chop of this many swings: from 0, drawn back to 0.25, fast down to -1 (it
     * lands at 0.62 of the swing), held there a moment, then back up to 0 for the next. It ends where it starts, so
     * one swing runs into the next without a jump.
     */
    public static float chopSwing(float t, int swings) {
        float beat = (t * Math.max(1, swings)) % 1.0F;
        if (beat < 0.45F) return rise(beat, 0.0F, 0.45F) * 0.25F;
        if (beat < 0.72F) return 0.25F - rise(beat, 0.45F, 0.62F) * 1.25F;
        return -1.0F + rise(beat, 0.72F, 1.0F);
    }

    /** The angle of the arms from hanging straight down, in radians, at time t of a chop (the figure's tool follows it). */
    public static float chopArms(float t, int swings) {
        return (1.8F + chopSwing(t, swings) * 0.9F) * chopHold(t, swings);
    }

    /** How far the rod is held out, t being the time through a fishing errand (0 to 1): 0 once the catch is out. */
    public static float fishHold(float t) {
        return window(t, 0.0F, 0.03F, PondBlockEntity.CATCH_MOMENT + 0.01F, PondBlockEntity.CATCH_MOMENT + 0.05F);
    }

    /** The cast, at time t of a fishing errand: the rod drawn back over the shoulder (up to 1), then thrown forward (0). */
    public static float fishBack(float t) {
        return window(t, 0.0F, PondBlockEntity.CAST_MOMENT * 0.5F, PondBlockEntity.CAST_MOMENT * 0.65F, PondBlockEntity.CAST_MOMENT);
    }

    /** The angle of the arms from hanging straight down, in radians, at time t of a fishing errand (the rod follows it). */
    public static float fishArms(float t) {
        float back = fishBack(t);
        float pull = window(t, PondBlockEntity.CATCH_MOMENT - 0.05F, PondBlockEntity.CATCH_MOMENT - 0.02F,
                PondBlockEntity.CATCH_MOMENT, PondBlockEntity.CATCH_MOMENT + 0.03F);
        return (1.05F + 1.1F * back + 0.6F * pull) * fishHold(t);
    }

    /** How much the crop is held up, t being the time through a harvest (0 to 1). The figure draws the crop by it. */
    public static float holding(float t) {
        return window(t, SoulPlotBlockEntity.HARVEST_MOMENT, SoulPlotBlockEntity.HARVEST_MOMENT + 0.14F, 0.9F, 1.0F);
    }

    /** The same, for a whole errand: nothing unless it harvests. */
    public static float holding(int action, float work) {
        if (action == SoulPlotBlockEntity.FISH && work >= 0.0F) { // the catch, once the rod is down
            return window(Mth.clamp(work, 0.0F, 1.0F), PondBlockEntity.CATCH_MOMENT + 0.03F, PondBlockEntity.CATCH_MOMENT + 0.06F, 0.97F, 1.0F);
        }
        if ((action & SoulPlotBlockEntity.HARVEST) == 0 || work < 0.0F) return 0.0F;
        float t = Mth.clamp(work, 0.0F, 1.0F);
        if ((action & SoulPlotBlockEntity.SOW) != 0) t = t / SoulPlotBlockEntity.HARVEST_SHARE;
        return t > 1.0F ? 0.0F : holding(t);
    }

    /** Leans the body forward by this share of a deep bend, head, arms and legs following as in vanilla's crouch. */
    private void lean(float share) {
        body.xRot += 0.6F * share;
        body.y += 3.2F * share;
        head.y += 4.2F * share;
        head.xRot += 0.4F * share;
        rightArm.y += 3.2F * share;
        leftArm.y += 3.2F * share;
        rightLeg.z += 4.0F * share;
        leftLeg.z += 4.0F * share;
        rightLeg.y += 0.2F * share;
        leftLeg.y += 0.2F * share;
    }
}
