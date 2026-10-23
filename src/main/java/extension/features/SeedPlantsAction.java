/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package extension.features;

import extension.entity.ACTION_COMMAND_TYPE;
import extension.features.BulkItemProcessor;
import extension.features.ItemProcessingHandler;
import extension.features.PlantManagerFeature;
import extension.features.UserActionExecutor;
import extension.util.NotificationUtils;
import extension.util.SleepRateLimit;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.List;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedPlantsAction
implements UserActionExecutor,
ItemProcessingHandler<HFloorItem> {
    
    private static final Logger log = LoggerFactory.getLogger(SeedPlantsAction.class);
    private final  PlantManagerFeature manager;
    private final  BulkItemProcessor processor;
    // Habbo.es furnidata: mnstr_seed, mnstr_seed_rare, mnstr_seed_nt.
    public static boolean isSeedType(int id) { return id == 4282 || id == 4304 || id == 14088; }

    @Override
    public void execute() {
        if (this.manager.getCurrentUserId() <= 0) {
            NotificationUtils.showSystemNotificationToUser(this.manager.getExtension(), "Error: User info not loaded yet. Try again in a moment.");
            log.debug("[Plants] Seed command rejected because user info is missing");
            this.manager.getExtension().sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
            return;
        }
        List<HFloorItem> targetSeeds = this.manager.getFloorItemsSnapshot().stream().filter(this::shouldProcess).collect(Collectors.toList());
        NotificationUtils.showSystemNotificationToUser(this.manager.getExtension(), "Seeding plants started... (Found " + targetSeeds.size() + " seeds)");
        log.debug("[Plants] Seed command started. Target seeds: {}", (Object)targetSeeds.size());
        this.processor.startProcessing(this, targetSeeds);
    }

    @Override
    public boolean shouldProcess(HFloorItem item) {
        if (!isSeedType(item.getTypeId())) {
            return false;
        }
        if (item.getOwnerId() <= 0) {
            log.warn("[Plants] Item {} has no owner. Skipping.", (Object)item.getId());
            return false;
        }
        if (this.manager.getCurrentUserId() <= 0) {
            log.warn("[Plants] Current user id has not been parsed. Skipping.");
            return false;
        }
        if (item.getOwnerId() != this.manager.getCurrentUserId()) {
            log.trace("[Plants] Item {} is not owned by current user. Skipping.", (Object)item.getId());
            return false;
        }
        return true;
    }

    @Override
    public boolean process(HFloorItem item) {
        if (!shouldProcess(item) || !this.processor.isProcessing()) return false;
        SleepRateLimit.sleepRateLimit();
        String packetHeader = "UseFurniture";
        boolean sent = this.manager.getExtension().sendToServer(new HPacket(packetHeader, HMessage.Direction.TOSERVER, item.getId(), 0));
        log.debug("[{}] Seed {} {}", packetHeader, item.getId(), sent ? "used" : "failed");
        if (!sent) return false;
        for (int i = 0; i < 40 && this.processor.isProcessing(); i++) {
            if (this.manager.getFloorItemsSnapshot().stream().noneMatch(f -> f.getId() == item.getId())) return true;
            SleepRateLimit.sleepRateLimit(100L);
        }
        log.warn("Seed {}: server removal not confirmed", item.getId());
        return false;
    }

    @Override
    public void showSystemNotification(ExtensionForm extension, int processedCount) {
        NotificationUtils.showSystemNotificationToUser(extension, "Semillas consumidas, confirmadas por el servidor: " + processedCount + ". Si faltan, revisa el registro.");
    }

    @Override
    public ACTION_COMMAND_TYPE getActionCommandType() {
        return ACTION_COMMAND_TYPE.SEED;
    }

    
    public SeedPlantsAction( PlantManagerFeature manager,  BulkItemProcessor processor) {
        if (manager == null) {
            throw new NullPointerException("manager is marked non-null but is null");
        }
        if (processor == null) {
            throw new NullPointerException("processor is marked non-null but is null");
        }
        this.manager = manager;
        this.processor = processor;
    }
}

