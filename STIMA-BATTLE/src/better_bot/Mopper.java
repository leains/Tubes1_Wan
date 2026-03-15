package better_bot;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper {

    private static MapLocation mySpawnTower = null;
    public static MapLocation currentObjective = null;

    private static final Utils.NavState nav = new Utils.NavState();

    public static void run(RobotController rc) throws GameActionException {
        mySpawnTower = Utils.getNearestTower(rc, mySpawnTower);
        listenToTower(rc);

        if (!rc.isMovementReady() && !rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Attack adjacent enemies first
        if (enemies.length > 0) {
            trySwing(rc, enemies);
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        clearEnemyPaintAndMove(rc, nearbyTiles);
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

    // =========================================================
    // COMBAT
    // =========================================================

    private static void trySwing(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo best = null;
        int minHp = Integer.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation()) && enemy.getHealth() < minHp) {
                minHp = enemy.getHealth();
                best = enemy;
            }
        }
        if (best != null) rc.attack(best.getLocation());
    }

    // =========================================================
    // MOVEMENT 
    // =========================================================

    private static void clearEnemyPaintAndMove(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation enemyBase = Utils.guessEnemyBase(rc, mySpawnTower);

        MapLocation pushMagnet = (currentObjective != null && !currentObjective.equals(mySpawnTower))
            ? currentObjective
            : enemyBase;

        MapLocation bestTileToClean = null;
        double maxScore = -10000;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) continue;
            MapLocation tileLoc = tile.getMapLocation();

            // 1. Close to us (actionable)
            // 2. Along the path toward the push magnet (not behind us)
            int distToMe     = myLoc.distanceSquaredTo(tileLoc);
            int distToMagnet = tileLoc.distanceSquaredTo(pushMagnet);

            double score = 5000.0 / (distToMe + 1)       // Closer = more reachable
                         - distToMagnet * 0.3;            // Forward of us is better

            if (score > maxScore) { maxScore = score; bestTileToClean = tileLoc; }
        }

        if (bestTileToClean != null) {
            if (rc.isActionReady() && rc.canAttack(bestTileToClean)) {
                rc.attack(bestTileToClean);
            } else {
                Utils.bugNav(rc, bestTileToClean, nav);
            }
        } else {
            // Nothing to clean nearby — advance toward the push
            Utils.bugNav(rc, pushMagnet, nav);
        }
    }
}