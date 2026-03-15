package alternative_bots_2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier {

    private static MapLocation mySpawnTower = null;
    public static MapLocation currentObjective = null;
    private static int idleTurns = 0;

    private static final Utils.NavState nav = new Utils.NavState();

    public static void run(RobotController rc) throws GameActionException {
        mySpawnTower = Utils.getNearestTower(rc, mySpawnTower);
        listenToTower(rc);
        Utils.paintBeneath(rc);

        if (rc.isActionReady()) {
            try { Helper.tryCompleteResourcePattern(rc); } catch (Exception ignored) {}
        }

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        if (rc.getPaint() < 15) {
            MapLocation tower = Utils.doRefill(rc, mySpawnTower, nav);
            if (tower != null) { mySpawnTower = tower; return; }
        }

        reportToTower(rc);

        if (runWeightedBuilder(rc)) return;

        idleTurns++;
        if (idleTurns > 2) {
            runPushHeuristic(rc);
        }
    }

    // =========================================================
    // INTEL LISTENING
    // =========================================================

    private static void listenToTower(RobotController rc) throws GameActionException {
        for (Message msg : rc.readMessages(rc.getRoundNum() - 1)) {
            int bytes = msg.getBytes();
            if (!Communication.isRobotInfo(bytes)) {
                MapInfo intel = MapInfoCodec.decode(bytes);
                if (intel.getPaint().isEnemy() || intel.hasRuin()) {
                    setObjective(intel.getMapLocation());
                }
            }
        }
    }

    private static void setObjective(MapLocation target) {
        if (currentObjective == null || !currentObjective.equals(target)) {
            currentObjective = target;
            Utils.resetNav(nav);
        }
    }

    // =========================================================
    // REPORTING
    // =========================================================

    private static void reportToTower(RobotController rc) throws GameActionException {
        if (mySpawnTower == null) return;

        // Report enemy towers first (highest value intel)
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemy.getType().isTowerType()) {
                MapInfo enemyLocInfo = new MapInfo(
                    enemy.getLocation(), true, false, PaintType.ENEMY_PRIMARY, PaintType.EMPTY, true, false
                );
                Communication.sendMapInformation(rc, enemyLocInfo, mySpawnTower);
                return;
            }
        }

        // Report unoccupied ruins second
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
                Communication.sendMapInformation(rc, tile, mySpawnTower);
                return;
            }
        }
    }

    // =========================================================
    // TOWER BUILDING 
    // =========================================================

    private static boolean runWeightedBuilder(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation enemyBase = Utils.guessEnemyBase(rc, mySpawnTower);

        MapLocation bestRuin = null;
        double maxScore = -10000.0;

        // If commanded objective is a ruin, prioritize it
        if (currentObjective != null && rc.canSenseLocation(currentObjective)) {
            MapInfo objInfo = rc.senseMapInfo(currentObjective);
            if (objInfo.hasRuin() && rc.senseRobotAtLocation(currentObjective) == null) {
                bestRuin = currentObjective;
                maxScore = 999999.0;
            }
        }

        if (bestRuin == null) {
            for (MapInfo tile : rc.senseNearbyMapInfos()) {
                if (!tile.hasRuin()) continue;
                MapLocation ruinLoc = tile.getMapLocation();
                if (rc.senseRobotAtLocation(ruinLoc) != null) continue; // already built

                int distToMe = myLoc.distanceSquaredTo(ruinLoc);

                // KEY FIX: Reward ruins that are closer to enemy base.
                // Mid-map and forward ruins are BETTER, not penalized.
                int distToEnemy = (enemyBase != null) ? ruinLoc.distanceSquaredTo(enemyBase) : 0;
                int mapArea = rc.getMapWidth() * rc.getMapHeight();

                // Higher score = farther from own base (more aggressive)
                double score = 5000.0 / (distToMe + 1)            // Closer ruins are easier to reach
                             + 3000.0 * (1.0 - (double)distToEnemy / mapArea); // Forward ruins score higher

                if (tile.getPaint().isEnemy()) score -= 200; // Penalty for heavily contested ruins

                if (score > maxScore) { maxScore = score; bestRuin = ruinLoc; }
            }
        }

        if (bestRuin != null) {
            setObjective(bestRuin);
            if (myLoc.distanceSquaredTo(bestRuin) <= 2) {
                executeBuildPattern(rc, bestRuin);
            } else {
                Utils.bugNav(rc, bestRuin, nav);
            }
            idleTurns = 0;
            return true;
        }
        return false;
    }

    private static void executeBuildPattern(RobotController rc, MapLocation targetLoc) throws GameActionException {
        int round = rc.getRoundNum();
        int money  = rc.getMoney();
        MapLocation enemyBase = Utils.guessEnemyBase(rc, mySpawnTower);

        // Strategy: rush money towers early; switch to paint towers mid-game;
        // build forward defense towers near enemy lines in late game.
        UnitType towerType;
        boolean isForwardPosition = (enemyBase != null &&
            targetLoc.distanceSquaredTo(enemyBase) < targetLoc.distanceSquaredTo(mySpawnTower != null ? mySpawnTower : targetLoc));

        if (round < 200 || money < 1500) {
            towerType = UnitType.LEVEL_ONE_MONEY_TOWER;  // Income ASAP
        } else if (isForwardPosition && round > 400) {
            towerType = UnitType.LEVEL_ONE_DEFENSE_TOWER; // Forward tower absorbs damage
        } else {
            towerType = UnitType.LEVEL_ONE_PAINT_TOWER;   // Paint production mid-game
        }

        // Mark the pattern
        if (rc.isActionReady() && rc.canMarkTowerPattern(towerType, targetLoc)) {
            rc.markTowerPattern(towerType, targetLoc);
        }

        // Paint marked tiles
        for (MapInfo tile : rc.senseNearbyMapInfos(targetLoc, 8)) {
            if (!rc.isActionReady()) break;
            PaintType mark  = tile.getMark();
            PaintType paint = tile.getPaint();
            if (mark != PaintType.EMPTY && mark != paint) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation(), useSecondary);
                }
            }
        }

        // Complete pattern if ready
        if (rc.isActionReady() && rc.canCompleteTowerPattern(towerType, targetLoc)) {
            rc.completeTowerPattern(towerType, targetLoc);
            currentObjective = null;
            Utils.resetNav(nav);
        }
    }

    // =========================================================
    // PUSH HEURISTIC 
    // =========================================================


    private static void runPushHeuristic(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation enemyBase = Utils.guessEnemyBase(rc, mySpawnTower);
        MapLocation midMap = Utils.getMidMapTarget(rc, mySpawnTower);

        // If we have a commanded objective (enemy tower), go there
        if (currentObjective != null && !currentObjective.equals(mySpawnTower)) {
            if (rc.isActionReady() && rc.canAttack(currentObjective)) rc.attack(currentObjective);
            Utils.bugNav(rc, currentObjective, nav);
            return;
        }

        // Look for any nearby unpainted tile that is on the path toward enemy
        MapLocation bestTarget = null;
        double maxScore = -10000.0;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.isPassable() || tile.getPaint().isAlly()) continue;
            MapLocation tileLoc = tile.getMapLocation();

            // Score: prefer tiles closer to enemy, farther from our base
            int distToEnemy = tileLoc.distanceSquaredTo(enemyBase);
            int distToMe    = myLoc.distanceSquaredTo(tileLoc);
            double score = 1000.0 / (distToMe + 1) - distToEnemy * 0.1;
            if (tile.getPaint().isEnemy()) score += 500; // Overwrite enemy paint aggressively

            if (score > maxScore) { maxScore = score; bestTarget = tileLoc; }
        }

        if (bestTarget != null) {
            if (rc.isActionReady() && rc.canAttack(bestTarget)) {
                Utils.smartPaint(rc, bestTarget);
            } else {
                Utils.bugNav(rc, bestTarget, nav);
            }
        } else {
            MapLocation pushTarget = (myLoc.distanceSquaredTo(midMap) > 25) ? midMap : enemyBase;
            setObjective(pushTarget);
            Utils.bugNav(rc, pushTarget, nav);
        }
    }
}