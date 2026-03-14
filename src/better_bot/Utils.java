package better_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Utils {

    private static boolean isTracing = false;
    private static Direction tracingDir = null;
    private static int startTraceDist = Integer.MAX_VALUE;

    public static void resetNav() {
        isTracing = false;
        tracingDir = null;
        startTraceDist = Integer.MAX_VALUE;
    }

    public static void bugNav(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        int currentDist = rc.getLocation().distanceSquaredTo(target);
        Direction directDir = rc.getLocation().directionTo(target);

        if (isTracing) {
            if (currentDist < startTraceDist && rc.canMove(directDir)) {
                resetNav();
            }
        }

        if (!isTracing) {
            if (rc.canMove(directDir)) {
                rc.move(directDir);
            } else {
                isTracing = true;
                startTraceDist = currentDist;
                tracingDir = directDir;
                traceWall(rc);
            }
        } else {
            traceWall(rc);
        }
    }

    private static void traceWall(RobotController rc) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(tracingDir)) {
                rc.move(tracingDir);
                tracingDir = tracingDir.rotateRight().rotateRight();
                break;
            }
            tracingDir = tracingDir.rotateLeft();
        }
    }

    public static MapLocation doRefill(RobotController rc, MapLocation cachedTower) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo bestTower = null;
        double minScore = Double.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                int paintAmt = ally.getPaintAmount();

                double score = dist - (0.5 * paintAmt);

                if (paintAmt < 50) score += 10000.0;

                if (score < minScore) {
                    minScore = score;
                    bestTower = ally;
                }
            }
        }

        if (bestTower != null) {
            cachedTower = bestTower.getLocation();
            int need = rc.getType().paintCapacity - rc.getPaint();
            int amountToTake = Math.min(need, bestTower.getPaintAmount());
            
            if (rc.getLocation().distanceSquaredTo(cachedTower) <= 2) {
                if (amountToTake > 0 && rc.isActionReady() && rc.canTransferPaint(cachedTower, -amountToTake)) {
                    rc.transferPaint(cachedTower, -amountToTake);
                }
            } else {
                bugNav(rc, cachedTower);
            }
            return cachedTower;
        }
        
        if (cachedTower != null) { bugNav(rc, cachedTower); return cachedTower; }
        return null;
    }

    public static Direction explore(RobotController rc, Direction currentDir) throws GameActionException {
        if (!rc.isMovementReady()) return currentDir;

        if (currentDir == null || !rc.canMove(currentDir)) {
            Direction[] dirs = Direction.values();
            int startIdx = (int)(Math.random() * 8); 
            
            for(int i = 0; i < 8; i++) {
                Direction testDir = dirs[(startIdx + i) % 8];
                if (rc.canMove(testDir)) {
                    currentDir = testDir;
                    break;
                }
            }
        }
        
        if (currentDir != null && rc.canMove(currentDir)) {
            rc.move(currentDir);
            resetNav();
        }
        
        return currentDir;
    }

    public static void paintBeneath(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }
    public static MapLocation guessEnemyBase(RobotController rc, MapLocation myBase) {
        if (myBase == null) return rc.getLocation(); 
        
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        
        int enemyX = width - 1 - myBase.x;
        int enemyY = height - 1 - myBase.y;
        
        return new MapLocation(enemyX, enemyY);
    }
}