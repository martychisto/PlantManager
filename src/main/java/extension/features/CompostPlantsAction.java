package extension.features;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.util.NotificationUtils;
import extension.util.PlantUtils;
import extension.util.SleepRateLimit;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.concurrent.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompostPlantsAction implements UserActionExecutor, ItemProcessingHandler<HEntity> {
    private static final Logger log = LoggerFactory.getLogger(CompostPlantsAction.class);
    private static final Map<Integer, CompletableFuture<Status>> pending = new ConcurrentHashMap<>();
    private final PlantManagerFeature manager;
    private final BulkItemProcessor processor;
    public CompostPlantsAction(PlantManagerFeature manager, BulkItemProcessor processor) {
        this.manager = manager; this.processor = processor;
    }
    public void execute() {
        if (manager.getCurrentUserId() <= 0) {
            manager.requestInfo();
            NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Cargando usuario. Repite :plants compost en un momento.");
            return;
        }
        NotificationUtils.showSystemNotificationToUser(manager.getExtension(), "Comprobando tus plantas. Solo se compostaran las confirmadas como muertas.");
        processor.startProcessing(this, manager.getPlantsSnapshot());
    }
    public boolean shouldProcess(HEntity plant) {
        if (plant == null || !PlantUtils.isPlant(plant)) return false;
        Object[] data = plant.getStuff();
        return data.length > 1 && data[1] instanceof Number && manager.getCurrentUserId() > 0
            && ((Number)data[1]).intValue() == manager.getCurrentUserId();
    }
    public boolean process(HEntity plant) {
        if (!shouldProcess(plant) || !processor.isProcessing()) return false;
        CompletableFuture<Status> future = new CompletableFuture<>();
        pending.put(plant.getId(), future);
        try {
            SleepRateLimit.sleepRateLimit();
            if (!processor.isProcessing() || !manager.getExtension().sendToServer(new HPacket("GetPetInfo", HMessage.Direction.TOSERVER, plant.getId()))) return false;
            Status status = future.get(3000, TimeUnit.MILLISECONDS);
            if (!status.dead || status.ownerId != manager.getCurrentUserId() || !processor.isProcessing()) return false;
            if (manager.getRoomEntityState().getEntityById(plant.getId()) != plant) return false;
            SleepRateLimit.sleepRateLimit();
            if (!processor.isProcessing() || !manager.getExtension().sendToServer(new HPacket("CompostPlant", HMessage.Direction.TOSERVER, plant.getId()))) return false;
            // A successful socket write is not a server acknowledgement.
            for (int i=0; i<40 && processor.isProcessing(); i++) {
                if (manager.getRoomEntityState().getEntityById(plant.getId()) == null) return true;
                SleepRateLimit.sleepRateLimit(100L);
            }
            log.warn("Compost {}: server removal not confirmed", plant.getId());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Compost {}: could not confirm plant status", plant.getId(), e);
        } finally { pending.remove(plant.getId(), future); }
        return false;
    }
    public static final class Status {
        public final int petId, ownerId;
        public final boolean dead;
        Status(int petId,int ownerId,boolean dead) {this.petId=petId;this.ownerId=ownerId;this.dead=dead;}
    }
    public static Status parseStatus(HPacket p) {
        p.resetReadIndex();
        int petId=p.readInteger(); p.readString();
        for(int i=0;i<9;i++) p.readInteger();
        int ownerId=p.readInteger();
        p.readInteger(); p.readString(); p.readInteger();
        p.readBoolean(); p.readBoolean();
        int skills=p.readInteger();
        if(skills<0 || skills>1000) throw new IllegalArgumentException("Invalid skill count");
        for(int i=0;i<skills;i++) p.readInteger();
        p.readInteger(); p.readBoolean(); p.readBoolean();
        boolean dead=p.readBoolean();
        if(p.isCorrupted()) throw new IllegalArgumentException("Truncated PetInfo");
        return new Status(petId,ownerId,dead);
    }
    public static void handlePetInfo(HMessage message) {
        try {
            Status status=parseStatus(message.getPacket());
            CompletableFuture<Status> f=pending.get(status.petId);
            if(f!=null) f.complete(status);
        } catch(Exception e) { log.warn("Could not parse PetInfo for compost", e); }
    }
    public ACTION_COMMAND_TYPE getActionCommandType() { return ACTION_COMMAND_TYPE.COMPOST; }
    public void showSystemNotification(ExtensionForm extension,int count) {
        NotificationUtils.showSystemNotificationToUser(extension,"Plantas compostadas, confirmadas por el servidor: " + count + ". Si faltan, revisa el registro.");
    }
}
