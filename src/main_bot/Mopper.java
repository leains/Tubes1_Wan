package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper {
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
        if (enemies.length > 0) runCombatHeuristic(rc, enemies);
        else runCleanupHeuristic(rc);
    }

    private static void initHome(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) { mySpawnTower = ally.getLocation(); break; }
        }
    }

    private static void runCombatHeuristic(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo primaryTarget = null;
        int minDistance = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            if (dist < minDistance) { minDistance = dist; primaryTarget = enemy; }
        }

        if (primaryTarget != null) {
            MapLocation targetLoc = primaryTarget.getLocation();
            Direction dirToEnemy = myLoc.directionTo(targetLoc);

            if (rc.isActionReady()) {
                boolean swung = false;
                Direction[] swingAngles = {dirToEnemy, dirToEnemy.rotateLeft(), dirToEnemy.rotateRight()};
                for (Direction swingDir : swingAngles) {
                    if (rc.canMopSwing(swingDir)) { rc.mopSwing(swingDir); swung = true; break; }
                }
                if (!swung && rc.canAttack(targetLoc)) rc.attack(targetLoc);
            }
            if (minDistance > 2) Utils.greedyMove(rc, targetLoc);
        }
    }

    private static void runCleanupHeuristic(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestDirt = null;
        int minDistance = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy() && tile.isPassable()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) { minDistance = dist; bestDirt = tile.getMapLocation(); }
            }
        }

        if (bestDirt != null) {
            if (rc.isActionReady() && rc.canAttack(bestDirt)) rc.attack(bestDirt);
            else Utils.greedyMove(rc, bestDirt);
        } else {
            exploreDir = Utils.explore(rc, exploreDir);
        }
    }


}