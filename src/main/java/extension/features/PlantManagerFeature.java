/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package extension.features;

import extension.features.AbortPlantsAction;
import extension.features.AutoBreedFeature;
import extension.features.BulkItemProcessor;
import extension.features.CanReproducePlantsAction;
import extension.features.CompostPlantsAction;
import extension.features.SeedPlantsAction;
import extension.features.TreatPlantsAction;
import extension.features.UserActionExecutor;
import extension.state.RoomEntityState;
import extension.state.RoomObjectState;
import extension.state.SessionState;
import extension.util.NotificationUtils;
import extension.util.PlantSettings;
import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlantManagerFeature {
    
    private static final Logger log = LoggerFactory.getLogger(PlantManagerFeature.class);
    public static final String TREAT_COMMAND = ":plants";
    public static final String COMPOST_COMMAND = ":plants compost";
    public static final String SEED_COMMAND = ":plants seed";
    public static final String ABORT_COMMAND = ":plants abort";
    public static final String CAN_REPRODUCE_ON_COMMAND = ":plants canreproduce on";
    public static final String CAN_REPRODUCE_OFF_COMMAND = ":plants canreproduce off";
    public static final String AUTO_BREED_ON_COMMAND = ":plants autobreed on";
    public static final String AUTO_BREED_OFF_COMMAND = ":plants autobreed off";
    public static final String AUTO_BREED_ADD_USER_COMMAND = ":plants autobreed adduser";
    public static final String AUTO_BREED_REMOVE_USER_COMMAND = ":plants autobreed removeuser";
    public static final int PROCESS_DELAY_MS = 600;
    private final ExtensionForm extension;
    private RoomEntityState roomEntityState;
    private RoomObjectState roomObjectState;
    private SessionState sessionState;
    private BulkItemProcessor processor;
    private AutoBreedFeature autoBreedFeature;

    public void install() {
        this.roomEntityState = new RoomEntityState(this.extension);
        this.roomObjectState = new RoomObjectState(this.extension);
        this.sessionState = new SessionState(this.extension);
        this.processor = new BulkItemProcessor(this, this.extension);
        this.autoBreedFeature = new AutoBreedFeature(this);
        this.autoBreedFeature.install();
        this.extension.intercept(HMessage.Direction.TOCLIENT, "PetInfo", TreatPlantsAction::handlePetInfo);
        this.extension.intercept(HMessage.Direction.TOCLIENT, "PetInfo", CompostPlantsAction::handlePetInfo);
        this.extension.intercept(HMessage.Direction.TOCLIENT, "PetStatusUpdate", CanReproducePlantsAction::handlePetStatusUpdate);
        this.extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::handleChat);
        this.extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        this.extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
        log.info("[Plants] Extension installed");
    }

    public void requestInfo() {
        boolean sent = this.extension.sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        log.debug("[InfoRetrieve] Info requested {}", (Object)(sent ? "sent" : "failed"));
    }

    public void reset() {
        if (this.roomEntityState != null) {
            this.roomEntityState.clearEntities();
        }
        if (this.roomObjectState != null) {
            this.roomObjectState.clearFloorItems();
        }
        this.abortProcessingSafe();
        if (this.roomEntityState != null) {
            this.roomEntityState.setInitialized(false);
        }
        if (this.sessionState != null) {
            this.sessionState.setCurrentRoomId(-1);
        }
        if (this.autoBreedFeature != null) {
            this.autoBreedFeature.reset();
        }
        log.info("[Plants] Extension reset, clearing everything..");
    }

    public HEntity removePlantById(int id) {
        return this.roomEntityState != null ? this.roomEntityState.removeEntityById(id) : null;
    }

    public List<HEntity> getPlantsSnapshot() {
        return this.roomEntityState != null ? this.roomEntityState.getPlantsSnapshot() : Collections.emptyList();
    }

    public Set<Integer> getPlantIds() {
        return this.roomEntityState != null ? this.roomEntityState.getPlantIds() : Collections.emptySet();
    }

    public int getPlantCount() {
        return this.roomEntityState != null ? this.roomEntityState.getPlantCount() : 0;
    }

    public List<HFloorItem> getFloorItemsSnapshot() {
        return this.roomObjectState != null ? this.roomObjectState.getFloorItemsSnapshot() : Collections.emptyList();
    }

    public boolean isInitialized() {
        return this.roomEntityState != null && this.roomEntityState.isInitialized();
    }

    public int getCurrentUserId() {
        return this.sessionState != null ? this.sessionState.getCurrentUserId() : -1;
    }

    public String getCurrentUsername() {
        return this.sessionState != null ? this.sessionState.getCurrentUsername() : null;
    }

    public int getCurrentRoomId() {
        return this.sessionState != null ? this.sessionState.getCurrentRoomId() : -1;
    }

    public void abortProcessingSafe() {
        if (this.processor != null) {
            this.processor.abortProcessing();
        }
    }

    public void handleChat(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        String text = packet.readString();
        if (!this.isCommand(text)) {
            return;
        }
        hMessage.setBlocked(true);
        log.debug("[Chat] Command received: {}", (Object)text);
        if (this.isAutoBreedCommand(text)) {
            this.handleAutoBreedCommand(text);
            return;
        }
        if (ABORT_COMMAND.equals(text) && this.autoBreedFeature != null) {
            this.autoBreedFeature.stopClickUser(true);
        }
        if (!this.isInitialized()) {
            NotificationUtils.showSystemNotificationToUser(this.getExtension(), "Error: Room not initialized yet! Please wait for the room to load or reload it.");
            log.debug("[Chat] Command rejected because the room is not initialized");
            return;
        }
        UserActionExecutor action = null;
        if (TREAT_COMMAND.equals(text)) {
            action = new TreatPlantsAction(this, this.processor);
        } else if (COMPOST_COMMAND.equals(text)) {
            action = new CompostPlantsAction(this, this.processor);
        } else if (SEED_COMMAND.equals(text)) {
            action = new SeedPlantsAction(this, this.processor);
        } else if (ABORT_COMMAND.equals(text)) {
            action = new AbortPlantsAction(this, this.processor);
        } else if (CAN_REPRODUCE_ON_COMMAND.equals(text)) {
            action = new CanReproducePlantsAction(this, this.processor, true);
        } else if (CAN_REPRODUCE_OFF_COMMAND.equals(text)) {
            action = new CanReproducePlantsAction(this, this.processor, false);
        } else {
            log.error("[Chat] Unrecognized command: {}", (Object)text);
        }
        if (action != null) {
            action.execute();
        }
    }

    private boolean isCommand(String text) {
        return TREAT_COMMAND.equals(text) || COMPOST_COMMAND.equals(text) || SEED_COMMAND.equals(text) || ABORT_COMMAND.equals(text) || CAN_REPRODUCE_ON_COMMAND.equals(text) || CAN_REPRODUCE_OFF_COMMAND.equals(text) || this.isAutoBreedCommand(text);
    }

    private boolean isAutoBreedCommand(String text) {
        return AUTO_BREED_ON_COMMAND.equals(text) || AUTO_BREED_OFF_COMMAND.equals(text) || AUTO_BREED_ADD_USER_COMMAND.equals(text) || AUTO_BREED_REMOVE_USER_COMMAND.equals(text) || text.startsWith(":plants autobreed adduser ") || text.startsWith(":plants autobreed removeuser ");
    }

    private void handleAutoBreedCommand(String text) {
        if (AUTO_BREED_ON_COMMAND.equals(text) || AUTO_BREED_OFF_COMMAND.equals(text)) {
            boolean enabled = AUTO_BREED_ON_COMMAND.equals(text);
            PlantSettings.setAutoBreedEnabled(enabled);
            int trustedUserCount = PlantSettings.getAutoBreedTrustedUsernames().size();
            NotificationUtils.showSystemNotificationToUser(this.getExtension(), "Auto-accept breeding has been turned " + (enabled ? "on" : "off") + " (" + trustedUserCount + " trusted users).");
            log.info("[AutoBreed] Auto-accept breeding turned {} with {} trusted users", (Object)(enabled ? "on" : "off"), (Object)trustedUserCount);
            return;
        }
        if (AUTO_BREED_ADD_USER_COMMAND.equals(text)) {
            this.autoBreedFeature.beginClickAddUser();
            return;
        }
        if (AUTO_BREED_REMOVE_USER_COMMAND.equals(text)) {
            this.autoBreedFeature.beginClickRemoveUser();
            return;
        }
        if (text.startsWith(":plants autobreed adduser ")) {
            String username = text.substring(":plants autobreed adduser ".length()).trim();
            if (username.isEmpty()) {
                this.autoBreedFeature.beginClickAddUser();
            } else {
                this.autoBreedFeature.addTrustedUsername(username);
            }
            return;
        }
        if (text.startsWith(":plants autobreed removeuser ")) {
            String username = text.substring(":plants autobreed removeuser ".length()).trim();
            if (username.isEmpty()) {
                this.autoBreedFeature.beginClickRemoveUser();
            } else {
                this.autoBreedFeature.removeTrustedUsername(username);
            }
        }
    }

    public void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        int roomId = packet.readInteger();
        int requestType = packet.readInteger();
        if (requestType == 0) {
            log.trace("[GetGuestRoom] Room {} requested. Aborting processing.", (Object)roomId);
            this.abortProcessingSafe();
        }
    }

    public void handleQuit(HMessage hMessage) {
        log.trace("[Quit] Room quit. Aborting processing.");
        this.abortProcessingSafe();
    }

    
    public PlantManagerFeature(ExtensionForm extension) {
        this.extension = extension;
    }

    
    public ExtensionForm getExtension() {
        return this.extension;
    }

    
    public RoomEntityState getRoomEntityState() {
        return this.roomEntityState;
    }

    
    public RoomObjectState getRoomObjectState() {
        return this.roomObjectState;
    }

    
    public SessionState getSessionState() {
        return this.sessionState;
    }
}

