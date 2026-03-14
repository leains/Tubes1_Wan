package main_bot;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString("Bot Online - Augmented Greedy");

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
                System.out.println("GameActionException RobotPlayer");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("General Exception RobotPlayer");
                e.printStackTrace();
            } finally {
                Clock.yield(); 
            }
        }
    }
}