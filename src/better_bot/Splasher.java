package better_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher {
    private static MapLocation mySpawnTower = null; 
    private static Direction exploreDir = null;
    private static MapLocation currentObjective = null;

    public static void run(RobotController rc) throws GameActionException {
        if (mySpawnTower == null) initHome(rc);
        Utils.paintBeneath(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        if (rc.getPaint() < 20 || (currentObjective != null && currentObjective.equals(mySpawnTower) && rc.getPaint() < 100)) {
            updateObjective(mySpawnTower);
            MapLocation activeTower = Utils.doRefill(rc, mySpawnTower);
            if (activeTower != null) {
                mySpawnTower = activeTower; 
                return; 
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (enemies.length > 0) runAoECombat(rc, enemies);
        else runDensityMacroPaint(rc, nearbyTiles);
    }

    private static void initHome(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) { mySpawnTower = ally.getLocation(); break; }
        }
    }

    private static void updateObjective(MapLocation newTarget) {
        if (currentObjective == null || !currentObjective.equals(newTarget)) {
            currentObjective = newTarget;
            Utils.resetNav(); 
        }
    }

    private static void runAoECombat(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo bestTarget = null;
        double maxScore = -10000.0;
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            MapLocation enemyLoc = enemy.getLocation();
            int dist = myLoc.distanceSquaredTo(enemyLoc);
            
            double score = 1000.0 / (dist + 1);
            
            if (enemy.getType().isTowerType()) score += 2000.0;
            if (dist <= 4) score += 500.0;

            if (score > maxScore) {
                maxScore = score;
                bestTarget = enemy;
            }
        }

        if (bestTarget != null) {
            MapLocation targetLoc = bestTarget.getLocation();
            updateObjective(targetLoc);
            
            if (rc.isActionReady() && rc.canAttack(targetLoc)) {
                rc.attack(targetLoc);
            } else if (myLoc.distanceSquaredTo(targetLoc) > 0) {
                Utils.bugNav(rc, targetLoc);
            }
        }
    }

    private static void runDensityMacroPaint(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation bestArea = null;
        double maxScore = -10000.0;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isAlly() && tile.isPassable()) {
                MapLocation tileLoc = tile.getMapLocation();
                int dist = myLoc.distanceSquaredTo(tileLoc);
                
                double score = 1000.0 / (dist + 1);
                
                if (tile.getPaint().isEnemy()) score += 300.0;
                
                if (mySpawnTower != null) {
                    int distToTower = tileLoc.distanceSquaredTo(mySpawnTower);
                    if (distToTower > 16 && distToTower < 64) {
                        score += 150.0;
                    }
                }

                if (score > maxScore) {
                    maxScore = score;
                    bestArea = tileLoc;
                }
            }
        }

        if (bestArea != null) {
            updateObjective(bestArea);
            
            if (rc.isActionReady() && rc.canAttack(bestArea)) {
                rc.attack(bestArea);
            } else if (myLoc.distanceSquaredTo(bestArea) > 0) {
                Utils.bugNav(rc, bestArea);
            }
        } else {
            currentObjective = null;
            exploreDir = Utils.explore(rc, exploreDir);
        }
    }
}