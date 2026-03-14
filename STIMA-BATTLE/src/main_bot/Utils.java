package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Utils {

    public static void paintBeneath(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }
    public static void greedyMove(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction dir = rc.getLocation().directionTo(target);
        
        Direction[] attempts = { 
            dir, dir.rotateLeft(), dir.rotateRight(), 
            dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight() 
        };
        
        for (Direction d : attempts) {
            if (rc.canMove(d)) { 
                rc.move(d); 
                return; 
            }
        }
    }
    public static Direction explore(RobotController rc, Direction currentDir) throws GameActionException {
        if (!rc.isMovementReady()) return currentDir;

        if (currentDir == null || !rc.canMove(currentDir)) {
            Direction[] dirs = Direction.values();
            int startIdx = (int)(Math.random() * 8); 
            
            for(int i = 0; i < 8; i++) {
                Direction testDir = dirs[(startIdx + i) % 8];
                if (rc.canMove(testDir)) {
                    currentDir = testDir;
                    break;
                }
            }
        }
        
        if (currentDir != null && rc.canMove(currentDir)) {
            rc.move(currentDir);
        }
        
        return currentDir;
    }
    public static MapLocation doRefill(RobotController rc, MapLocation cachedTower) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo bestTower = null;
        int minDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDist) { 
                    minDist = dist; 
                    bestTower = ally; 
                }
            }
        }

        if (bestTower != null) {
            cachedTower = bestTower.getLocation();
            int need = rc.getType().paintCapacity - rc.getPaint();
            
            int amountToTake = Math.min(need, bestTower.getPaintAmount());
            
            if (rc.getLocation().distanceSquaredTo(cachedTower) <= 2) {
                if (amountToTake > 0 && rc.isActionReady() && rc.canTransferPaint(cachedTower, -amountToTake)) {
                    rc.transferPaint(cachedTower, -amountToTake);
                }
            } else {
                greedyMove(rc, cachedTower); 
            }
            return cachedTower;
        }
        
        if (cachedTower != null) { greedyMove(rc, cachedTower); return cachedTower; }
        return null;
    }
}