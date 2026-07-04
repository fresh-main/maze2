package com.labyrinthmod.client.renderer;

import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GriverRenderer extends GeoEntityRenderer<GriverEntity> {

    public GriverRenderer(EntityRendererProvider.Context context) {
        super(context, new GriverGeoModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    protected float getDeathMaxRotation(GriverEntity entity) {
        return 0.0F;
    }

    @Override
    public boolean shouldShowName(GriverEntity entity) {
        return false;
    }
}
