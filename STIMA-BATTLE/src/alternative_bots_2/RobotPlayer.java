package alternative_bots_2;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class RobotPlayer {
    
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                switch (rc.getType()) {
                    case SOLDIER: 
                        Soldier.run(rc); 
                        break;
                    case MOPPER: 
                        Mopper.run(rc); 
                        break;
                    case SPLASHER: 
                        Splasher.run(rc); 
                        break;
                    default: 
                        Tower.run(rc); 
                        break;
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}