package ver2;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    static int turnCount = 0;
    static final Random rng = new Random(9999);

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        runSplasher(rc);
                        break;
                    default:
                        runTower(rc);
                        break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException: " + e.getType());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // =========================================================
    // TOWER
    // =========================================================
    public static void runTower(RobotController rc) throws GameActionException {
        if (rc.isActionReady()) {
            attackAnyEnemyInRange(rc);
        }

        if (!rc.isActionReady()) return;

        // Bot tandingan: lebih sering spawn soldier untuk paint aman
        UnitType spawnType;
        int roll = rng.nextInt(10);
        if (roll < 6) spawnType = UnitType.SOLDIER;
        else if (roll < 8) spawnType = UnitType.MOPPER;
        else spawnType = UnitType.SPLASHER;

        MapLocation bestSpawn = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);
            if (!rc.canBuildRobot(spawnType, loc)) continue;

            int score = 0;
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);

                if (info.getPaint() == PaintType.EMPTY) score += 12;
                else if (info.getPaint().isEnemy()) score += 4;
                else if (info.getPaint().isAlly()) score += 0;
            }

            if (score > bestScore) {
                bestScore = score;
                bestSpawn = loc;
            }
        }

        if (bestSpawn != null && rc.canBuildRobot(spawnType, bestSpawn)) {
            rc.buildRobot(spawnType, bestSpawn);
            rc.setIndicatorString("Safe spawn: " + spawnType);
        }
    }

    // =========================================================
    // SOLDIER
    // =========================================================
    public static void runSoldier(RobotController rc) throws GameActionException {
        runSafePainterLogic(rc, true);
    }

    // =========================================================
    // MOPPER
    // =========================================================
    public static void runMopper(RobotController rc) throws GameActionException {
        runSafePainterLogic(rc, false);
    }

    // =========================================================
    // SPLASHER
    // =========================================================
    public static void runSplasher(RobotController rc) throws GameActionException {
        runSafePainterLogic(rc, false);
    }

    // =========================================================
    // COMMON BOT LOGIC
    // =========================================================
    public static void runSafePainterLogic(RobotController rc, boolean canBuildTower) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());

        int enemyPower = 0;
        for (RobotInfo enemy : enemyRobots) {
            enemyPower += enemy.getHealth();
            enemyPower += enemy.getType().attackStrength;
        }

        int allyPower = 0;
        for (RobotInfo ally : allyRobots) {
            allyPower += ally.getHealth();
            allyPower += ally.getType().attackStrength;
        }

        boolean strongerThanEnemy = allyPower >= enemyPower;

        // PRIORITAS RUIN tetap ada
        MapInfo curRuin = null;
        int bestRuinDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestRuinDist) {
                    bestRuinDist = d;
                    curRuin = tile;
                }
            }
        }

        if (curRuin != null && canBuildTower) {
            MapLocation targetLoc = curRuin.getMapLocation();
            Direction dirToRuin = rc.getLocation().directionTo(targetLoc);

            if (rc.isMovementReady() && dirToRuin != Direction.CENTER && rc.canMove(dirToRuin)) {
                rc.move(dirToRuin);
            }

            UnitType towerType = chooseSafeTowerType();

            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dirToRuin == Direction.CENTER ? Direction.NORTH : dirToRuin);
            if (rc.canSenseLocation(shouldBeMarked)
                    && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                    && rc.canMarkTowerPattern(towerType, targetLoc)) {
                rc.markTowerPattern(towerType, targetLoc);
            }

            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)) {
                if (patternTile.getMark() != PaintType.EMPTY &&
                    patternTile.getMark() != patternTile.getPaint()) {

                    boolean useSecondaryColor =
                            patternTile.getMark() == PaintType.ALLY_SECONDARY;

                    if (rc.isActionReady() && rc.canAttack(patternTile.getMapLocation())) {
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                        return;
                    }
                }
            }

            if (rc.canCompleteTowerPattern(towerType, targetLoc)) {
                rc.completeTowerPattern(towerType, targetLoc);
                return;
            }

            return;
        }

        // Paint dulu kalau ada tile bagus
        if (rc.isActionReady()) {
            if (tryPaintNearbyNonAlly(rc)) {
                return;
            }
        }

        // Fight hanya kalau cukup aman
        if (enemyRobots.length > 0 && strongerThanEnemy && rc.isActionReady()) {
            RobotInfo bestTarget = null;
            int bestTargetHP = Integer.MAX_VALUE;

            for (RobotInfo enemy : enemyRobots) {
                if (rc.canAttack(enemy.getLocation())) {
                    if (enemy.getHealth() < bestTargetHP) {
                        bestTargetHP = enemy.getHealth();
                        bestTarget = enemy;
                    }
                }
            }

            if (bestTarget != null) {
                if (rc.getType() == UnitType.MOPPER) {
                    Direction d = rc.getLocation().directionTo(bestTarget.getLocation());
                    if (d != Direction.CENTER && rc.canMopSwing(d)) {
                        rc.mopSwing(d);
                        return;
                    }
                }

                rc.attack(bestTarget.getLocation());
                return;
            }
        }

        // MOVE: fokus ekspansi aman
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation nextLoc = rc.getLocation().add(dir);
            int score = 0;

            // netral sangat diprioritaskan
            if (rc.canSenseLocation(nextLoc)) {
                MapInfo nextInfo = rc.senseMapInfo(nextLoc);
                PaintType paint = nextInfo.getPaint();

                if (paint == PaintType.EMPTY) score += 20;
                else if (paint.isAlly()) score += 0;
                else if (paint.isEnemy()) {
                    if (strongerThanEnemy) score += 4;
                    else score -= 8;
                }
            }

            // ruin lumayan penting
            for (MapInfo tile : nearbyTiles) {
                if (tile.hasRuin()) {
                    int curDist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                    int nextDist = nextLoc.distanceSquaredTo(tile.getMapLocation());
                    if (nextDist < curDist) {
                        score += 12;
                        break;
                    }
                }
            }

            // respon musuh
            if (enemyRobots.length > 0) {
                RobotInfo nearestEnemy = enemyRobots[0];
                int bestEnemyDist = nextLoc.distanceSquaredTo(nearestEnemy.getLocation());

                for (RobotInfo enemy : enemyRobots) {
                    int d = nextLoc.distanceSquaredTo(enemy.getLocation());
                    if (d < bestEnemyDist) {
                        bestEnemyDist = d;
                        nearestEnemy = enemy;
                    }
                }

                int currentDist = rc.getLocation().distanceSquaredTo(nearestEnemy.getLocation());
                int nextDist = nextLoc.distanceSquaredTo(nearestEnemy.getLocation());

                if (strongerThanEnemy) {
                    if (nextDist < currentDist) score += 3;
                } else {
                    if (nextDist < currentDist) score -= 20;
                    if (nextDist > currentDist) score += 12;
                }
            }

            // hindari kerumunan ally biar nyebar
            for (RobotInfo ally : allyRobots) {
                if (ally.getLocation().distanceSquaredTo(nextLoc) <= 2) {
                    score -= 2;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (rc.isMovementReady() && bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }

        // fallback paint bawah kaki
        if (rc.isActionReady()) {
            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            }
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================
    public static UnitType chooseSafeTowerType() {
        int roll = rng.nextInt(10);
        if (roll < 5) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (roll < 8) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }

    public static boolean tryPaintNearbyNonAlly(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation bestPaintLoc = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            int score = 0;
            PaintType paint = tile.getPaint();

            if (paint == PaintType.EMPTY) score += 12;
            else if (paint.isEnemy()) score += 8;
            else if (paint.isAlly()) score -= 100;

            if (tile.hasRuin()) score += 3;

            if (score > bestScore) {
                bestScore = score;
                bestPaintLoc = loc;
            }
        }

        if (bestPaintLoc != null && bestScore > 0 && rc.canAttack(bestPaintLoc)) {
            rc.attack(bestPaintLoc);
            return true;
        }

        return false;
    }

    public static void attackAnyEnemyInRange(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                return;
            }
        }

        if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }
}