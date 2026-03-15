package better_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier {
    private static final int PHASE_SHIFT_ROUND = 150; 
    private static MapLocation mySpawnTower = null; 
    private static MapLocation currentObjective = null;
    private static int idleTurns = 0;
    private static Direction exploreDir = null;

    public static void run(RobotController rc) throws GameActionException {
        if (mySpawnTower == null) initHome(rc);
        Utils.paintBeneath(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        if (rc.getPaint() < 10 || (currentObjective != null && currentObjective.equals(mySpawnTower) && rc.getPaint() < 50)) {
            currentObjective = mySpawnTower;
            MapLocation activeTower = Utils.doRefill(rc, mySpawnTower);
            if (activeTower != null) {
                mySpawnTower = activeTower; 
                return; 
            }
        }

        if (rc.getRoundNum() < PHASE_SHIFT_ROUND) runWeightedBuilder(rc);
        else runExpansionistHeuristic(rc);
    }

    private static void initHome(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) { mySpawnTower = ally.getLocation(); break; }
        }
    }

    private static void runWeightedBuilder(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestRuin = null;
        double maxHeuristic = -10000.0;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
                MapLocation ruinLoc = tile.getMapLocation();
                int dist = myLoc.distanceSquaredTo(ruinLoc);
                
                double score = 1000.0 / (dist + 1); 
                if (tile.getPaint().isEnemy()) score -= 50; 
                if (ruinLoc.equals(currentObjective)) score += 20; 

                if (score > maxHeuristic) {
                    maxHeuristic = score;
                    bestRuin = ruinLoc;
                }
            }
        }

        if (bestRuin != null) {
            currentObjective = bestRuin;
            if (myLoc.distanceSquaredTo(bestRuin) <= 2) {
                executeBuildPattern(rc, bestRuin);
            } else {
                Utils.bugNav(rc, bestRuin); 
            }
            idleTurns = 0;
        } else {
            idleTurns++;
            if (idleTurns > 3) runExpansionistHeuristic(rc);
        }
    }

    private static void executeBuildPattern(RobotController rc, MapLocation targetLoc) throws GameActionException {
        boolean isEvenTile = ((targetLoc.x + targetLoc.y) % 2 == 0);
        UnitType towerType = isEvenTile ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;

        if (rc.isActionReady() && rc.canMarkTowerPattern(towerType, targetLoc)) {
            rc.markTowerPattern(towerType, targetLoc);
        }
        
        for (MapInfo tile : rc.senseNearbyMapInfos(targetLoc, 8)) {
            if (!rc.isActionReady()) break; 
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();
            
            if (mark != PaintType.EMPTY && mark != paint) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY); 
                if (rc.canAttack(tile.getMapLocation())) rc.attack(tile.getMapLocation(), useSecondary);
            }
        }

        if (rc.isActionReady() && rc.canCompleteTowerPattern(towerType, targetLoc)) {
            rc.completeTowerPattern(towerType, targetLoc);
            currentObjective = null; 
        }
    }

    private static void runExpansionistHeuristic(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestTarget = null;
        int minDistance = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isAlly() && tile.isPassable()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) { 
                    minDistance = dist; 
                    bestTarget = tile.getMapLocation(); 
                }
            }
        }

        if (bestTarget != null) {
            currentObjective = bestTarget;
            if (rc.isActionReady() && rc.canAttack(bestTarget)) rc.attack(bestTarget);
            else Utils.bugNav(rc, bestTarget);
        } else {
            MapLocation pushTarget = Utils.guessEnemyBase(rc, mySpawnTower);
            currentObjective = pushTarget;
            Utils.bugNav(rc, pushTarget);
        }
    }
}