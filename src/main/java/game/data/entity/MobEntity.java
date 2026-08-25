package game.data.entity;

import game.data.entity.metadata.MetaData;
import packets.DataTypeProvider;
import config.Config;
import config.Version;
import se.llbit.nbt.CompoundTag;

public class MobEntity extends Entity {
    private float headPitch;

    private MetaData metaData;

    public MobEntity() {
    }

    @Override
    protected void addNbtData(CompoundTag root) {
        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    public static Entity parse(DataTypeProvider provider) {
        PrimitiveEntity primitive = PrimitiveEntity.parse(provider);
        Entity ent = primitive.getEntity(MobEntity::new);

        if (ent == null) { return null; }

        ent.readPosition(provider);

        // As of 26.1 (protocol 774+), velocity (LpVec3) moved before the angles.
        boolean velocityBeforeRotation = Config.versionReporter().isAtLeast(Version.V26_1);
        if (velocityBeforeRotation) {
            parseVelocity(provider);
        }

        ent.pitch = provider.readNext();
        ent.yaw = provider.readNext();
        byte headPitch = provider.readNext();

        if (Config.versionReporter().isAtLeast(Version.V1_19)) {
            provider.readVarInt(); // data — not used by MobEntity but must be consumed
        }

        if (!velocityBeforeRotation) {
            parseVelocity(provider);
        }

        if (ent instanceof MobEntity) {
            ((MobEntity) ent).headPitch = headPitch;
        }

        return ent;
    }

    @Override
    public void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = MetaData.getVersionedMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }
}
