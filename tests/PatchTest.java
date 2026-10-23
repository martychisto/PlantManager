import extension.features.CompostPlantsAction;
import extension.features.SeedPlantsAction;
import gearth.protocol.HPacket;
import java.util.*;
import extension.features.*;
import extension.state.*;
import gearth.extensions.*;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatchTest {
    static int checks;
    static void check(boolean value) {checks++; if(!value) throw new AssertionError("check " + checks);}
    static HPacket info(boolean dead, int skills) {
        List<Object> fields=new ArrayList<>();
        fields.add(123); fields.add("Plant");
        for(int i=0;i<9;i++) fields.add(i);
        fields.add(456); fields.add(10); fields.add("Owner"); fields.add(3);
        fields.add(false); fields.add(false); fields.add(skills);
        for(int i=0;i<skills;i++) fields.add(100+i);
        fields.add(0); fields.add(true); fields.add(true); fields.add(dead);
        fields.add(3); fields.add(10000); fields.add(dead?0:100); fields.add(0); fields.add(false);
        return new HPacket(1,fields.toArray());
    }
    static void set(Object target,String name,Object value) throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static class Fake extends ExtensionForm {
        int sent; boolean dead, acknowledge=true;
        RoomEntityState state;
        public void intercept(HMessage.Direction d,String name,ExtensionBase.MessageListener listener) {}
        public boolean sendToServer(HPacket packet) {
            sent++;
            if(sent==1) CompostPlantsAction.handlePetInfo(new HMessage(info(dead,0),HMessage.Direction.TOCLIENT,0));
            else if(acknowledge) state.removeEntityById(123);
            return true;
        }
    }
    static void integration() throws Exception {
        for(boolean dead:new boolean[]{false,true}) {
            Fake ext=new Fake();ext.dead=dead;
            PlantManagerFeature manager=new PlantManagerFeature(ext);
            SessionState session=new SessionState(ext);set(session,"currentUserId",456);set(manager,"sessionState",session);
            RoomEntityState state=new RoomEntityState(ext);ext.state=state;set(manager,"roomEntityState",state);
            HEntity plant=new HEntity(new HPacket(1,123,"Plant","","16",4,1,1,"0",0,2,16,456,"Owner",3,false,false,false,false,false,false,1,""));
            state.addEntity(plant);
            BulkItemProcessor processor=new BulkItemProcessor(manager,ext);set(processor,"processing",new AtomicBoolean(true));
            CompostPlantsAction action=new CompostPlantsAction(manager,processor);
            check(action.shouldProcess(plant));
            check(action.process(plant)==dead);
            check(ext.sent==(dead?2:1));
            check((state.getEntityById(123)==null)==dead);
            plant.getStuff()[1]=999;
            check(!action.shouldProcess(plant));
            processor.shutdown();
        }
    }
    public static void main(String[] args) throws Exception {
        for(boolean dead:new boolean[]{false,true}) for(int skills:new int[]{0,1,4}) {
            HPacket p=info(dead,skills);
            p.readInteger(); // Interceptors may leave the read cursor advanced.
            CompostPlantsAction.Status s=CompostPlantsAction.parseStatus(p);
            check(s.petId==123); check(s.ownerId==456); check(s.dead==dead);
        }
        for(int id:new int[]{4282,4304,14088}) check(SeedPlantsAction.isSeedType(id));
        for(int id:new int[]{4284,3116,9145,0,-1}) check(!SeedPlantsAction.isSeedType(id));
        boolean rejected=false;
        try {CompostPlantsAction.parseStatus(new HPacket(1,123,"Plant"));} catch(Exception e){rejected=true;}
        check(rejected);
        integration();
        System.out.println("PASS: " + checks + " checks (live/dead PetInfo, owner, variable skills, cursor reset, seed types, truncated packet)");
    }
}
