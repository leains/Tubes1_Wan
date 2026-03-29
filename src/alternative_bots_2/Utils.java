package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Utils {

    // =========================================================
    // BUG NAV — Per-robot state stored in fields on the robot's
    // own class (Soldier/Splasher/Mopper each own these fields).
    // Pass them in and return the updated pair via a tiny wrapper.
    // =========================================================

    /**
     * Mutable nav state that each robot class should hold as a field.
     */
    public static class NavState {
        public boolean isTracing = false;
        public Direction tracingDir = null;
        public int startTraceDist = Integer.MAX_VALUE;
    }

    public static void resetNav(NavState nav) {
        nav.isTracing = false;
        nav.tracingDir = null;
        nav.startTraceDist = Integer.MAX_VALUE;
    }

    public static void bugNav(RobotController rc, MapLocation target, NavState nav) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;

        MapLocation myLoc = rc.getLocation();
        if (myLoc.equals(target)) return;

        int currentDist = myLoc.distanceSquaredTo(target);
        Direction directDir = myLoc.directionTo(target);

        if (nav.isTracing) {
            if (currentDist < nav.startTraceDist && rc.canMove(directDir)) {
                resetNav(nav);
            }
        }

        if (!nav.isTracing) {
            if (rc.canMove(directDir)) {
                rc.move(directDir);
            } else {
                nav.isTracing = true;
                nav.startTraceDist = currentDist;
                nav.tracingDir = directDir;
                traceWall(rc, nav);
            }
        } else {
            traceWall(rc, nav);
        }
    }

    private static void traceWall(RobotController rc, NavState nav) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(nav.tracingDir)) {
                rc.move(nav.tracingDir);
                nav.tracingDir = nav.tracingDir.rotateRight().rotateRight();
                return;
            }
            nav.tracingDir = nav.tracingDir.rotateLeft();
        }
    }

    // =========================================================
    // TOWER LOGISTICS
    // =========================================================

    /**
     * Returns the nearest allied tower location seen this turn.
     * Falls back to cachedHome if no tower visible.
     */
    public static MapLocation getNearestTower(RobotController rc, MapLocation cachedHome) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation closest = cachedHome;
        int minDist = (closest == null) ? Integer.MAX_VALUE : myLoc.distanceSquaredTo(closest);

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (ally.getType().isTowerType()) {
                int dist = myLoc.distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    closest = ally.getLocation();
                }
            }
        }
        return closest;
    }

    /**
     * Moves toward and takes paint from the best visible tower.
     * Returns the tower location used (or null if none found).
     */
    public static MapLocation doRefill(RobotController rc, MapLocation cachedTower, NavState nav) throws GameActionException {
        RobotInfo bestTower = null;
        double minScore = Double.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!ally.getType().isTowerType()) continue;
            int dist = myLoc.distanceSquaredTo(ally.getLocation());
            int paintAmt = ally.getPaintAmount();
            double score = dist - (0.3 * paintAmt);
            if (paintAmt < 50) score += 10000.0;
            if (score < minScore) { minScore = score; bestTower = ally; }
        }

        MapLocation towerLoc = (bestTower != null) ? bestTower.getLocation() : cachedTower;
        if (towerLoc == null) return null;

        if (myLoc.distanceSquaredTo(towerLoc) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            int available = (bestTower != null) ? bestTower.getPaintAmount() : 200;
            int amountToTake = Math.min(need, available);
            if (amountToTake > 0 && rc.isActionReady() && rc.canTransferPaint(towerLoc, -amountToTake)) {
                rc.transferPaint(towerLoc, -amountToTake);
            }
        } else {
            bugNav(rc, towerLoc, nav);
        }
        return towerLoc;
    }

    // =========================================================
    // SMART PAINTING
    // =========================================================

    public static void smartPaint(RobotController rc, MapLocation loc) throws GameActionException {
        if (!rc.isActionReady() || !rc.canAttack(loc)) return;
        PaintType required = Helper.resourcePatternType(rc, loc);
        boolean useSecondary = (required == PaintType.ALLY_SECONDARY);
        rc.attack(loc, useSecondary);
    }

    public static void paintBeneath(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation myLoc = rc.getLocation();
        MapInfo currentTile = rc.senseMapInfo(myLoc);
        PaintType required = Helper.resourcePatternType(rc, myLoc);
        if (currentTile.getPaint() != required) {
            smartPaint(rc, myLoc);
        }
    }

    // =========================================================
    // MAP / STRATEGY HELPERS
    // =========================================================

    /**
     * Returns the rotationally symmetric enemy base guess.
     */
    public static MapLocation guessEnemyBase(RobotController rc, MapLocation myBase) {
        if (myBase == null) return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        int enemyX = rc.getMapWidth() - 1 - myBase.x;
        int enemyY = rc.getMapHeight() - 1 - myBase.y;
        return new MapLocation(enemyX, enemyY);
    }

    /**
     * Returns a mid-map waypoint that units should route through when pushing.
     */
    public static MapLocation getMidMapTarget(RobotController rc, MapLocation myBase) {
        MapLocation enemy = guessEnemyBase(rc, myBase);
        if (myBase == null) return enemy;
        int mx = (myBase.x + enemy.x) / 2;
        int my = (myBase.y + enemy.y) / 2;
        return new MapLocation(mx, my);
    }

    /**
     * Simple random explore: picks a passable direction and walks.
     */
    public static Direction explore(RobotController rc, Direction currentDir, NavState nav) throws GameActionException {
        if (!rc.isMovementReady()) return currentDir;

        if (currentDir == null || !rc.canMove(currentDir)) {
            int startIdx = (int)(Math.random() * 8);
            Direction[] dirs = Direction.values();
            for (int i = 0; i < 8; i++) {
                Direction testDir = dirs[(startIdx + i) % 8];
                if (rc.canMove(testDir)) { currentDir = testDir; break; }
            }
        }

        if (currentDir != null && rc.canMove(currentDir)) {
            rc.move(currentDir);
            resetNav(nav);
        }
        return currentDir;
    }
}