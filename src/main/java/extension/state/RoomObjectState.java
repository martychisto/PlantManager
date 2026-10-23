/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package extension.state;

import gearth.extensions.ExtensionForm;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomObjectState {
    
    private static final Logger log = LoggerFactory.getLogger(RoomObjectState.class);
    private final Map<Integer, HFloorItem> floorItems = new ConcurrentHashMap<Integer, HFloorItem>();

    public RoomObjectState(ExtensionForm extension) {
        extension.intercept(HMessage.Direction.TOCLIENT, "Objects", this::handleObjects);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::handleObjectAdd);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectRemove", this::handleObjectRemove);
        extension.intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", this::handleGetGuestRoom);
        extension.intercept(HMessage.Direction.TOSERVER, "Quit", this::handleQuit);
    }

    public List<HFloorItem> getFloorItemsSnapshot() {
        return new ArrayList<HFloorItem>(this.floorItems.values());
    }

    public void clearFloorItems() {
        this.floorItems.clear();
    }

    private void handleObjects(HMessage hMessage) {
        try {
            HFloorItem[] items;
            hMessage.getPacket().resetReadIndex();
            for (HFloorItem item : items = HFloorItem.parse(hMessage.getPacket())) {
                this.floorItems.put(item.getId(), item);
            }
            log.debug("[Objects] Parsed {} items", (Object)items.length);
        }
        catch (Exception e) {
            log.debug("[Objects] Failed to parse room objects", e);
        }
    }

    private void handleObjectAdd(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            HFloorItem item = new HFloorItem(packet);
            item.setOwnerName(packet.readString());
            this.floorItems.put(item.getId(), item);
        }
        catch (Exception e) {
            log.debug("[ObjectAdd] Failed to parse added object", e);
        }
    }

    private void handleObjectRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        try {
            packet.resetReadIndex();
            int id = Integer.parseInt(packet.readString());
            this.floorItems.remove(id);
        }
        catch (Exception e) {
            log.debug("[ObjectRemove] Failed to parse removed object", e);
        }
    }

    private void handleGetGuestRoom(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        packet.resetReadIndex();
        packet.readInteger();
        int requestType = packet.readInteger();
        if (requestType == 0) {
            this.clearFloorItems();
        }
    }

    private void handleQuit(HMessage hMessage) {
        this.clearFloorItems();
    }
}

