package main_bot;

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

        if (rc.getRoundNum() < PHASE_SHIFT_ROUND) runBuilderHeuristic(rc);
        else runExpansionistHeuristic(rc);
    }

    private static void initHome(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) { mySpawnTower = ally.getLocation(); break; }
        }
    }

    private static void runBuilderHeuristic(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapInfo closestRuin = null;
        int minDistance = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) { minDistance = dist; closestRuin = tile; }
            }
        }

        if (closestRuin != null) {
            MapLocation targetLoc = closestRuin.getMapLocation();
        if (minDistance <= 2) {
                UnitType towerType = (rc.getNumberTowers() % 2 == 0) ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
                if (rc.isActionReady() && rc.canMarkTowerPattern(towerType, targetLoc)) {
                    rc.markTowerPattern(towerType, targetLoc);
                }
                
                for (MapInfo tile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                    if (!rc.isActionReady()) break; 
                    
                    PaintType mark = tile.getMark();
                    PaintType paint = tile.getPaint();
                    
                    if (mark != PaintType.EMPTY && mark != paint) {
                        boolean useSecondary = (mark == PaintType.ALLY_SECONDARY); 
                        if (rc.canAttack(tile.getMapLocation())) {
                            rc.attack(tile.getMapLocation(), useSecondary);
                        }
                    }
                }
                if (rc.isActionReady()) {
                    if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
                        rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                        System.out.println("Paint tower");
                    } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)) {
                        rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                        System.out.println("money tower");
                    }
                }
            } else {
                Utils.greedyMove(rc, targetLoc);
            }
        } else {
            runExpansionistHeuristic(rc);
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
                if (dist < minDistance) { minDistance = dist; bestTarget = tile.getMapLocation(); }
            }
        }

        if (bestTarget != null) {
            if (rc.isActionReady() && rc.canAttack(bestTarget)) rc.attack(bestTarget);
            else Utils.greedyMove(rc, bestTarget);
        } else {
            exploreDir = Utils.explore(rc, exploreDir);
        }
    }


}