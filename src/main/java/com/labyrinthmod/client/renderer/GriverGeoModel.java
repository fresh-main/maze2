package com.labyrinthmod.client.renderer;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib-модель Гривера. Bedrock-формат геометрии и анимаций (он же был у HammerAnimations)
 * лежит в:
 *   assets/labyrinthmod/geo/griver.geo.json
 *   assets/labyrinthmod/animations/griver.animation.json
 *   assets/labyrinthmod/textures/entity/griver.png
 */
public class GriverGeoModel extends GeoModel<GriverEntity> {

    private static final ResourceLocation MODEL_RES =
            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "geo/griver.geo.json");
    private static final ResourceLocation TEXTURE_RES =
            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "textures/entity/griver.png");
    private static final ResourceLocation ANIM_RES =
            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "animations/griver.animation.json");

    @Override
    public ResourceLocation getModelResource(GriverEntity entity) {
        return MODEL_RES;
    }

    @Override
    public ResourceLocation getTextureResource(GriverEntity entity) {
        return TEXTURE_RES;
    }

    @Override
    public ResourceLocation getAnimationResource(GriverEntity entity) {
        return ANIM_RES;
    }
}
