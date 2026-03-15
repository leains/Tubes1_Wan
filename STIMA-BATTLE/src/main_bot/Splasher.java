package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher {
    private static MapLocation mySpawnTower = null; 
    private static Direction exploreDir = null;

    public static void run(RobotController rc) throws GameActionException {
        if (mySpawnTower == null) initHome(rc);
        Utils.paintBeneath(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        if (rc.getPaint() < 30) {
            MapLocation activeTower = Utils.doRefill(rc, mySpawnTower);
            if (activeTower != null) {
                mySpawnTower = activeTower; 
                exploreDir = null;          
                return; 
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (enemies.length > 0) runCombatSplash(rc, enemies);
        else runMacroPaint(rc, nearbyTiles);
    }

    private static void initHome(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) { mySpawnTower = ally.getLocation(); break; }
        }
    }

    private static void runCombatSplash(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo bestTarget = null;
        int maxPriority = -1000;
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            int priority = enemy.getType().isTowerType() ? 100 : 10;
            priority -= myLoc.distanceSquaredTo(enemy.getLocation());
            if (priority > maxPriority) { maxPriority = priority; bestTarget = enemy; }
        }

        if (bestTarget != null) {
            MapLocation targetLoc = bestTarget.getLocation();
            if (rc.isActionReady() && rc.canAttack(targetLoc)) rc.attack(targetLoc);
            else Utils.greedyMove(rc, targetLoc);
        }
    }

    private static void runMacroPaint(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation bestArea = null;
        int minDistance = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isAlly() && tile.isPassable()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) { minDistance = dist; bestArea = tile.getMapLocation(); }
            }
        }

        if (bestArea != null) {
            if (rc.isActionReady() && rc.canAttack(bestArea)) rc.attack(bestArea);
            else Utils.greedyMove(rc, bestArea);
        } else {
            exploreDir = Utils.explore(rc, exploreDir);
        }
    }


}