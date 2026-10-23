/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package extension.state;

import extension.entity.HEntity_Plant_Stuff_Index_Enum;
import extension.util.PlantSettings;
import extension.util.PlantUtils;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomEntityState {
    
    private static final Logger log = LoggerFactory.getLogger(RoomEntityState.class);
    private final Map<Integer, HEntity> entities = new ConcurrentHashMap<Integer, HEntity>();
    private volatile boolean initialized;

    public RoomEntityState(ExtensionForm extension) {
        extension.intercept(HMessage.Direction.TOCLIENT, "Users", this::handleUsers);
        extension.intercept(HMessage.Direction.TOCLIENT, "UserRemove", this::handleUserRemove);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        extension.intercept(HMessage.Direction.TOSERVER, "RemovePetFromFlat", this::handleRemovePetFromFlat);
        extension.intercept(HMessage.Direction.TOCLIENT, "PetStatusUpdate", this::handlePetStatusUpdate);
    }

    public void addEntity(HEntity entity) {
        if (entity == null) {
            return;
        }
        this.entities.put(entity.getId(), entity);
        PlantSettings.resolveAutoBreedTrustedUserEntity(entity);
    }

    public HEntity removeEntityById(int id) {
        return this.entities.remove(id);
    }

    public HEntity getEntityById(int id) {
        return this.entities.get(id);
    }

    public List<HEntity> findEntitiesByIndex(int index) {
        return this.entities.values().stream().filter(entity -> entity.getIndex() == index).collect(Collectors.toList());
    }

    public List<HEntity> getEntitiesSnapshot() {
        return new ArrayList<HEntity>(this.entities.values());
    }

    public List<HEntity> getPlantsSnapshot() {
        return this.entities.values().stream().filter(PlantUtils::isPlant).collect(Collectors.toList());
    }

    public int getPlantCount() {
        return (int)this.entities.values().stream().filter(PlantUtils::isPlant).count();
    }

    public Set<Integer> getPlantIds() {
        return this.entities.values().stream().filter(PlantUtils::isPlant).map(HEntity::getId).collect(Collectors.toSet());
    }

    public void clearEntities() {
        this.entities.clear();
    }

    public void updateCanReproduce(int petId, boolean canReproduce) {
        HEntity entity = this.entities.get(petId);
        if (entity == null) {
            log.warn("[RoomEntityState] updateCanReproduce: entity {} not in memory", (Object)petId);
            return;
        }
        int index = HEntity_Plant_Stuff_Index_Enum.CAN_REPRODUCE.getIndex();
        boolean updated = PlantUtils.updateIndexPlantEntity(entity, index, canReproduce);
        if (updated) {
            log.debug("[RoomEntityState] Updated canReproduce={} for entity {}", (Object)canReproduce, (Object)petId);
        } else {
            log.error("[RoomEntityState] updateCanReproduce: failed to update entity {}", (Object)petId);
        }
    }

    private void handlePetStatusUpdate(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            packet.readInteger();
            int petId = packet.readInteger();
            packet.readBoolean();
            packet.readString();
            boolean canReproduce = packet.readBoolean();
            this.updateCanReproduce(petId, canReproduce);
        }
        catch (Exception e) {
            log.debug("[RoomEntityState] Failed to parse PetStatusUpdate packet", e);
        }
    }

    private void handleUsers(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HEntity[] parsedEntities = HEntity.parse(packet);
            // Users may contain incremental batches; room transitions clear the cache.
            for (HEntity entity : parsedEntities) {
                this.addEntity(entity);
            }
            this.setInitialized(true);
            log.debug("[Users] Parsed {} entities. Plants in memory: {}", (Object)parsedEntities.length, (Object)this.getPlantCount());
        }
        catch (Exception e) {
            log.debug("[Users] Failed to parse room entities", e);
        }
    }

    private void handleUserRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        try {
            String idStr = packet.readString();
            int entityIndexId = Integer.parseInt(idStr);
            List<HEntity> foundEntities = this.findEntitiesByIndex(entityIndexId);
            if (foundEntities.size() > 1) {
                log.error("More than one index found for the removed entity, not removing it from memory");
                return;
            }
            if (foundEntities.size() == 0) {
                log.trace("[UserRemove] Entity {} removed but was not tracked", (Object)entityIndexId);
                return;
            }
            HEntity uniqueFoundEntity = foundEntities.get(0);
            this.removeEntityById(uniqueFoundEntity.getId());
            log.debug("[UserRemove] Entity {} removed. Plants left: {}", (Object)entityIndexId, (Object)this.getPlantCount());
        }
        catch (Exception e) {
            log.debug("[UserRemove] Failed to parse UserRemove packet", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        packet.readInteger();
        int requestType = packet.readInteger();
        if (requestType == 0) {
            this.clearEntities();
            this.setInitialized(false);
        }
    }

    private void handleQuit(HMessage hMessage) {
        this.clearEntities();
        this.setInitialized(false);
    }

    @Deprecated
    private void handleRemovePetFromFlat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int petId = packet.readInteger();
        if (this.removeEntityById(petId) != null) {
            log.debug("[RemovePetFromFlat] Entity {} removed. Plants left: {}", (Object)petId, (Object)this.getPlantCount());
        } else {
            log.debug("[RemovePetFromFlat] Pet {} removed but was not tracked", (Object)petId);
        }
    }

    
    public boolean isInitialized() {
        return this.initialized;
    }

    
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}

