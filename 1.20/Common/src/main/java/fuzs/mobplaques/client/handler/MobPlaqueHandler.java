package fuzs.mobplaques.client.handler;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import fuzs.mobplaques.MobPlaques;
import fuzs.mobplaques.client.gui.plaque.*;
import fuzs.mobplaques.config.ClientConfig;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import fuzs.puzzleslib.api.event.v1.data.DefaultedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MobPlaqueHandler {
    private static final int PLAQUE_HORIZONTAL_DISTANCE = 2;
    private static final int PLAQUE_VERTICAL_DISTANCE = 2;
    public static final Map<ResourceLocation, MobPlaqueRenderer> PLAQUE_RENDERERS = new LinkedHashMap<String, MobPlaqueRenderer>() {{
        this.put("health", new HealthPlaqueRenderer());
        this.put("air", new AirPlaqueRenderer());
        this.put("armor", new ArmorPlaqueRenderer());
        this.put("toughness", new ToughnessPlaqueRenderer());
    }}.entrySet().stream().collect(Collectors.toMap(e -> new ResourceLocation(MobPlaques.MOD_ID, e.getKey()), Map.Entry::getValue, (o1, o2) -> o1, LinkedHashMap::new));

    @SuppressWarnings("ConstantValue")
    public static EventResult onRenderNameTag(Entity entity, DefaultedValue<Component> content, EntityRenderer<?> entityRenderer, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, float partialTick) {
        if (!MobPlaques.CONFIG.get(ClientConfig.class).allowRendering.get()) return EventResult.PASS;
        if (entity instanceof LivingEntity livingEntity && canMobRenderPlaques(livingEntity)) {
            Minecraft minecraft = Minecraft.getInstance();
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            // other mods might be rendering this mob without a level in some menu, so camera is null then
            if (dispatcher.camera != null && dispatcher.camera.getEntity() != null && shouldShowName(livingEntity, dispatcher)) {
                poseStack.pushPose();
                int offsetY = "deadmau5".equals(content.get().getString()) ? -13 : -3;
                poseStack.translate(0.0, livingEntity.getBbHeight() + 0.5, 0.0);
                poseStack.mulPose(dispatcher.cameraOrientation());
                float plaqueScale = (float) MobPlaques.CONFIG.get(ClientConfig.class).plaqueScale;
                if (MobPlaques.CONFIG.get(ClientConfig.class).scaleWithDistance) {
                    double distanceSqr = dispatcher.distanceToSqr(livingEntity);
                    float pickRange = minecraft.gameMode.getPickRange();
                    double scaleRatio = Mth.clamp((distanceSqr - Math.pow(pickRange / 2.0, 2.0)) / (Math.pow(pickRange * 2.0, 2.0) / 2.0), 0.0, 2.0);
                    plaqueScale *= 1.0 + scaleRatio;
                }
                float scale = 0.025F * plaqueScale;
                poseStack.scale(-scale, -scale, scale);
                offsetY -= MobPlaques.CONFIG.get(ClientConfig.class).heightOffset * (0.5F / plaqueScale);
                if (MobPlaques.CONFIG.get(ClientConfig.class).renderBelowNameTag) {
                    offsetY += 23 * (0.5F / plaqueScale);
                } else {
                    offsetY -= (getPlaquesHeight(minecraft.font, livingEntity) + PLAQUE_VERTICAL_DISTANCE) * (0.5F / plaqueScale);
                }
                boolean plaqueBackground = MobPlaques.CONFIG.get(ClientConfig.class).plaqueBackground;
                Iterator<MobPlaqueRenderer> iterator = PLAQUE_RENDERERS.values().iterator();
                List<MutableInt> widths = getPlaquesWidths(minecraft.font, livingEntity);
                for (MutableInt width : widths) {
                    int rowStart = -width.intValue() / 2;
                    int maxRowHeight = 0;
                    while (iterator.hasNext()) {
                        MobPlaqueRenderer plaqueRenderer = iterator.next();
                        if (!plaqueRenderer.wantsToRender(livingEntity)) continue;
                        int plaqueWidth = plaqueRenderer.getWidth(minecraft.font, livingEntity);
                        // light value stolen from Neat mod by Vazkii, probably shows up elsewhere in vanilla though
                        // also kinda there to hide the fact that icons always render with full brightness, not sure what to do about that otherwise lol
                        plaqueRenderer.render(poseStack, multiBufferSource, 0xF000F0, rowStart + plaqueWidth / 2, offsetY, plaqueBackground, minecraft.font, livingEntity);
                        maxRowHeight = Math.max(plaqueRenderer.getHeight(), maxRowHeight);
                        plaqueWidth += PLAQUE_HORIZONTAL_DISTANCE;
                        rowStart += plaqueWidth;
                        if (width.addAndGet(-plaqueWidth) <= 0) break;
                    }
                    offsetY += maxRowHeight + PLAQUE_VERTICAL_DISTANCE;
                }
                poseStack.popPose();
            }
        }
        return EventResult.PASS;
    }

    private static List<MutableInt> getPlaquesWidths(Font font, LivingEntity entity) {
        int maxWidth = MobPlaques.CONFIG.get(ClientConfig.class).maxPlaqueRowWidth;
        List<MutableInt> widths = Lists.newArrayList();
        int index = -1;
        for (MobPlaqueRenderer plaqueRenderer : PLAQUE_RENDERERS.values()) {
            if (plaqueRenderer.wantsToRender(entity)) {
                int plaqueWidth = plaqueRenderer.getWidth(font, entity);
                if (widths.isEmpty() || maxWidth < widths.get(index).intValue() + PLAQUE_HORIZONTAL_DISTANCE + plaqueWidth) {
                    widths.add(new MutableInt(plaqueWidth));
                    index++;
                } else {
                    widths.get(index).add(PLAQUE_HORIZONTAL_DISTANCE + plaqueWidth);
                }
            }
        }
        return widths;
    }

    private static int getPlaquesHeight(Font font, LivingEntity entity) {
        int maxWidth = MobPlaques.CONFIG.get(ClientConfig.class).maxPlaqueRowWidth;
        int currentWidth = -1;
        int currentMaxHeight = 0;
        int totalHeight = -1;
        for (MobPlaqueRenderer plaqueRenderer : PLAQUE_RENDERERS.values()) {
            if (plaqueRenderer.wantsToRender(entity)) {
                int plaqueWidth = plaqueRenderer.getWidth(font, entity);
                if (currentWidth == -1 || maxWidth < currentWidth + PLAQUE_HORIZONTAL_DISTANCE + plaqueWidth) {
                    currentWidth = plaqueWidth;
                    currentMaxHeight = plaqueRenderer.getHeight();
                    totalHeight += plaqueRenderer.getHeight() + (totalHeight == -1 ? 0 : PLAQUE_VERTICAL_DISTANCE);
                } else {
                    currentWidth += PLAQUE_HORIZONTAL_DISTANCE + plaqueWidth;
                    if (plaqueRenderer.getHeight() > currentMaxHeight) {
                        totalHeight += plaqueRenderer.getHeight() - currentMaxHeight;
                        currentMaxHeight = plaqueRenderer.getHeight();
                    }
                }
            }
        }
        return totalHeight;
    }

    private static boolean canMobRenderPlaques(LivingEntity entity) {
        if (!MobPlaques.CONFIG.get(ClientConfig.class).mobBlacklist.contains(entity.getType())) {
            if (MobPlaques.CONFIG.get(ClientConfig.class).disallowedMobSelectors.stream().noneMatch(selector -> selector.canMobRenderPlaque(entity))) {
                return MobPlaques.CONFIG.get(ClientConfig.class).allowedMobSelectors.stream().anyMatch(selector -> selector.canMobRenderPlaque(entity));
            }
        }
        return false;
    }

    private static boolean shouldShowName(LivingEntity entity, EntityRenderDispatcher entityRenderDispatcher) {
        if (MobPlaques.CONFIG.get(ClientConfig.class).pickedEntity && entity != entityRenderDispatcher.crosshairPickEntity) {
            return false;
        }
        double d0 = entityRenderDispatcher.distanceToSqr(entity);
        int maxRenderDistance = MobPlaques.CONFIG.get(ClientConfig.class).maxRenderDistance;
        float f = entity.isDiscrete() ? maxRenderDistance / 2.0F : maxRenderDistance;
        if (d0 >= (double) (f * f)) {
            return false;
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            boolean invisibleToPlayer = !entity.isInvisibleTo(player);
            if (entity != player) {
                Team team = entity.getTeam();
                Team team1 = player.getTeam();
                if (team != null) {
                    Team.Visibility team$visibility = team.getNameTagVisibility();
                    switch (team$visibility) {
                        case ALWAYS:
                            return invisibleToPlayer;
                        case NEVER:
                            return false;
                        case HIDE_FOR_OTHER_TEAMS:
                            return team1 == null ? invisibleToPlayer : team.isAlliedTo(team1) && (team.canSeeFriendlyInvisibles() || invisibleToPlayer);
                        case HIDE_FOR_OWN_TEAM:
                            return team1 == null ? invisibleToPlayer : !team.isAlliedTo(team1) && invisibleToPlayer;
                        default:
                            return true;
                    }
                }
            }
            return Minecraft.renderNames() && entity != minecraft.getCameraEntity() && invisibleToPlayer && !entity.isVehicle();
        }
    }
}
