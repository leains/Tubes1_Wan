package better_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Mopper {
    private static MapLocation mySpawnTower = null; 
    private static Direction exploreDir = null;
    private static MapLocation currentObjective = null;

    public static void run(RobotController rc) throws GameActionException {
        if (mySpawnTower == null) initHome(rc);
        Utils.paintBeneath(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        if (rc.getPaint() < 10 || (currentObjective != null && currentObjective.equals(mySpawnTower) && rc.getPaint() < 50)) {
            updateObjective(mySpawnTower);
            MapLocation activeTower = Utils.doRefill(rc, mySpawnTower);
            if (activeTower != null) {
                mySpawnTower = activeTower; 
                return; 
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            runWeightedCombat(rc, enemies);
        } else {
            runWeightedCleanup(rc);
        }
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

    private static void runWeightedCombat(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo primaryTarget = null;
        double maxScore = -10000.0;
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            int hp = enemy.getHealth();
            
            double score = (1000.0 / (dist + 1)) - hp;
            
            if (enemy.getType() == UnitType.SPLASHER) score += 200;
            if (enemy.getType().isTowerType()) score += 500;

            if (score > maxScore) {
                maxScore = score;
                primaryTarget = enemy;
            }
        }

        if (primaryTarget != null) {
            MapLocation targetLoc = primaryTarget.getLocation();
            updateObjective(targetLoc);
            Direction dirToEnemy = myLoc.directionTo(targetLoc);

            if (rc.isActionReady()) {
                boolean swung = false;
                Direction[] swingAngles = {dirToEnemy, dirToEnemy.rotateLeft(), dirToEnemy.rotateRight()};
                for (Direction swingDir : swingAngles) {
                    if (rc.canMopSwing(swingDir)) { 
                        rc.mopSwing(swingDir); 
                        swung = true; 
                        break; 
                    }
                }
                
                if (!swung && rc.canAttack(targetLoc)) {
                    rc.attack(targetLoc);
                }
            }
            
            if (myLoc.distanceSquaredTo(targetLoc) > 2) {
                if (!(!rc.isActionReady() && rc.getHealth() < 30)) {
                    Utils.bugNav(rc, targetLoc);
                }
            }
        }
    }

private static void runWeightedCleanup(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestDirt = null;
        double maxScore = -10000.0;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy() && tile.isPassable()) {
                MapLocation dirtLoc = tile.getMapLocation();
                int dist = myLoc.distanceSquaredTo(dirtLoc);
                double score = 1000.0 / (dist + 1);
                
                if (mySpawnTower != null && dirtLoc.distanceSquaredTo(mySpawnTower) <= 25) {
                    score += 500.0; 
                }
                if (score > maxScore) {
                    maxScore = score;
                    bestDirt = dirtLoc;
                }
            }
        }

        if (bestDirt != null) {
            updateObjective(bestDirt);
            if (rc.isActionReady() && rc.canAttack(bestDirt)) rc.attack(bestDirt);
            else if (myLoc.distanceSquaredTo(bestDirt) > 0) Utils.bugNav(rc, bestDirt);
        } else {
            
            MapLocation killTarget = Utils.guessEnemyBase(rc, mySpawnTower);
            currentObjective = killTarget;
            
            
            if (rc.isActionReady()) {
                MapInfo tile = rc.senseMapInfo(myLoc);
                if (tile.getPaint().isEnemy() && rc.canAttack(myLoc)) rc.attack(myLoc);
            }
            
            Utils.bugNav(rc, killTarget);
        }
    }
}