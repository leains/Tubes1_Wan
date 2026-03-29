package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher {

    private static MapLocation mySpawnTower = null;
    public static MapLocation currentObjective = null;
    private static Direction exploreDir = null;

    // Per-robot nav state
    private static final Utils.NavState nav = new Utils.NavState();

    public static void run(RobotController rc) throws GameActionException {
        mySpawnTower = Utils.getNearestTower(rc, mySpawnTower);
        listenToTower(rc);
        Utils.paintBeneath(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        reportToTower(rc, enemies);

        // KAMIKAZE: If we can hit an enemy tower, always do it — even at low paint
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType() && rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                return;
            }
        }

        // Low paint refill — but NOT if we're right at an enemy tower
        if (rc.getPaint() < 20) {
            MapLocation tower = Utils.doRefill(rc, mySpawnTower, nav);
            if (tower != null) { mySpawnTower = tower; return; }
        }

        if (enemies.length > 0) {
            runAoECombat(rc, enemies);
        } else {
            runAggressivePush(rc, nearbyTiles);
        }
    }

    // =========================================================
    // INTEL
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

    private static void reportToTower(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (mySpawnTower == null) return;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                MapInfo enemyLocInfo = new MapInfo(
                    enemy.getLocation(), true, false, PaintType.ENEMY_PRIMARY, PaintType.EMPTY, true, false
                );
                Communication.sendMapInformation(rc, enemyLocInfo, mySpawnTower);
                return;
            }
        }
    }

    // =========================================================
    // COMBAT
    // =========================================================

    private static void runAoECombat(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                setObjective(enemy.getLocation());
                if (rc.canAttack(enemy.getLocation())) {
                    rc.attack(enemy.getLocation());
                } else {
                    Utils.bugNav(rc, enemy.getLocation(), nav);
                }
                return;
            }
        }

        MapLocation bestSplashTile = null;
        int maxHits = 0;
        double bestScore = -1;

        for (RobotInfo enemy : enemies) {
            MapLocation enemyLoc = enemy.getLocation();
            if (!rc.canAttack(enemyLoc)) continue;

            // Count how many other enemies are also in splash range of this location
            int hits = 0;
            for (RobotInfo other : enemies) {
                if (enemyLoc.distanceSquaredTo(other.getLocation()) <= 4) hits++;
            }

            double score = hits * 1000.0 + 1000.0 / (myLoc.distanceSquaredTo(enemyLoc) + 1);
            if (score > bestScore) { bestScore = score; bestSplashTile = enemyLoc; maxHits = hits; }
        }

        if (bestSplashTile != null) {
            setObjective(bestSplashTile);
            if (rc.isActionReady() && rc.canAttack(bestSplashTile)) {
                rc.attack(bestSplashTile);
            } else {
                Utils.bugNav(rc, bestSplashTile, nav);
            }
        }
    }

    // =========================================================
    // AGGRESSIVE PUSH — Splashers should be deep in enemy territory
    // =========================================================

    private static void runAggressivePush(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation enemyBase = Utils.guessEnemyBase(rc, mySpawnTower);

        // If we have a commanded objective (enemy tower location), charge it
        if (currentObjective != null && !currentObjective.equals(mySpawnTower)) {
            if (rc.isActionReady() && rc.canAttack(currentObjective)) {
                rc.attack(currentObjective);
            } else {
                Utils.bugNav(rc, currentObjective, nav);
            }
            return;
        }

        // Find the densest cluster of enemy/unpainted tiles toward enemy base
        MapLocation bestArea = null;
        double maxScore = -10000.0;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.isPassable()) continue;
            MapLocation tileLoc = tile.getMapLocation();

            // KEY: Splashers should always push TOWARD enemy base, never cluster near own towers
            int distToEnemy = tileLoc.distanceSquaredTo(enemyBase);
            int distToMe    = myLoc.distanceSquaredTo(tileLoc);
            int distToHome  = (mySpawnTower != null) ? tileLoc.distanceSquaredTo(mySpawnTower) : 0;

            if (!tile.getPaint().isAlly()) {
                double score = 2000.0 / (distToMe + 1)          
                             - distToEnemy * 0.5                  
                             + distToHome  * 0.2;                 

                if (tile.getPaint().isEnemy()) score += 800.0;   

                if (score > maxScore) { maxScore = score; bestArea = tileLoc; }
            }
        }

        if (bestArea != null) {
            setObjective(bestArea);
            if (rc.isActionReady() && rc.canAttack(bestArea)) {
                rc.attack(bestArea);
            } else {
                Utils.bugNav(rc, bestArea, nav);
            }
        } else {
            // No nearby target — deep push toward enemy base
            MapLocation pushTarget = enemyBase;
            setObjective(pushTarget);
            Utils.bugNav(rc, pushTarget, nav);
        }
    }
}