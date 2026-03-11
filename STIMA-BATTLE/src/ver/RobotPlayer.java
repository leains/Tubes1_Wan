package ver;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    static int turnCount = 0;
    static final Random rng = new Random(6147);

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

    static final int MSG_HELP = 999;
    static final int MSG_ACK = 1;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("Get ready to lose!");
        rc.setIndicatorString("Booting...");

        while (true) {
            turnCount += 1;

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
        Message[] messages = rc.readMessages(-1);

        // Tower attack dulu kalau bisa
        if (rc.isActionReady()) {
            attackAnyEnemyInRange(rc);
        }

        // Spawn random
        if (rc.isActionReady()) {
            UnitType spawnType;
            int roll = rng.nextInt(3);
            if (roll == 0) spawnType = UnitType.SOLDIER;
            else if (roll == 1) spawnType = UnitType.MOPPER;
            else spawnType = UnitType.SPLASHER;

            MapLocation bestSpawn = null;
            int bestScore = Integer.MIN_VALUE;

            for (Direction dir : directions) {
                MapLocation loc = rc.getLocation().add(dir);
                if (!rc.canBuildRobot(spawnType, loc)) continue;

                int score = 0;
                if (rc.canSenseLocation(loc)) {
                    MapInfo info = rc.senseMapInfo(loc);
                    if (info.getPaint() == PaintType.EMPTY) score += 10;
                    else if (info.getPaint().isAlly()) score += 0;
                    else if (info.getPaint().isEnemy()) score -= 3;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestSpawn = loc;
                }
            }

            if (bestSpawn != null && rc.canBuildRobot(spawnType, bestSpawn)) {
                rc.buildRobot(spawnType, bestSpawn);
                rc.setIndicatorString("Spawned " + spawnType);
            }
        }

        // Balas help secara sederhana: kalau tower baca msg help, broadcast ack
        for (Message m : messages) {
            if (m.getBytes() == MSG_HELP && rc.canBroadcastMessage()) {
                rc.broadcastMessage(MSG_ACK);
                break;
            }
        }
    }

    // =========================================================
    // SOLDIER
    // =========================================================
    public static void runSoldier(RobotController rc) throws GameActionException {
        runCommonRobotLogic(rc, true);
    }

    // =========================================================
    // MOPPER
    // =========================================================
    public static void runMopper(RobotController rc) throws GameActionException {
        runCommonRobotLogic(rc, false);
    }

    // =========================================================
    // SPLASHER
    // =========================================================
    public static void runSplasher(RobotController rc) throws GameActionException {
        runCommonRobotLogic(rc, false);
    }

    // =========================================================
    // COMMON ROBOT LOGIC
    // =========================================================
    public static void runCommonRobotLogic(RobotController rc, boolean canBuildTower) throws GameActionException {
        // 1. sensing dulu
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());

        // baca messages
        boolean helpArrived = false;
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int data = m.getBytes();
            if (data == MSG_ACK) {
                helpArrived = true;
            }
        }

        // hitung total power sederhana
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

        // 4. ruins prioritas
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

            UnitType randomTowerType = randomTowerType();

            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dirToRuin == Direction.CENTER ? Direction.NORTH : dirToRuin);
            if (rc.canSenseLocation(shouldBeMarked)
                    && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                    && rc.canMarkTowerPattern(randomTowerType, targetLoc)) {
                rc.markTowerPattern(randomTowerType, targetLoc);
                System.out.println("Trying to build a tower at " + targetLoc);
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

            if (rc.canCompleteTowerPattern(randomTowerType, targetLoc)) {
                rc.completeTowerPattern(randomTowerType, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built a tower at " + targetLoc + "!");
                return;
            }

            // ruins tetap prioritas
            return;
        }

        // 2. gerak dengan heuristic weight
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation nextLoc = rc.getLocation().add(dir);
            int score = 0;

            // kalau mengarah ke musuh
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

                if (nextDist < currentDist) {
                    if (strongerThanEnemy) score += 10;
                    else score -= 10;
                }
            }

            // kalau mengarah ke ruins
            for (MapInfo tile : nearbyTiles) {
                if (tile.hasRuin()) {
                    int currentDistToRuin = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                    int nextDistToRuin = nextLoc.distanceSquaredTo(tile.getMapLocation());
                    if (nextDistToRuin < currentDistToRuin) {
                        score += 20;
                        break;
                    }
                }
            }

            // tile tujuan
            if (rc.canSenseLocation(nextLoc)) {
                MapInfo nextInfo = rc.senseMapInfo(nextLoc);
                PaintType paint = nextInfo.getPaint();

                if (paint == PaintType.EMPTY) score += 10;
                else if (paint.isAlly()) score += 0;
                else if (paint.isEnemy()) score -= 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (rc.isMovementReady() && bestDir != null && rc.canMove(bestDir)) {
            rc.move(bestDir);
        }

        // tambahan: kalau bisa paint, langsung paint
        if (rc.isActionReady()) {
            if (tryPaintNearbyNonAlly(rc)) {
                return;
            }
        }

        // 3. attack
        if (enemyRobots.length > 0) {
            if (strongerThanEnemy) {
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

                if (bestTarget != null && rc.isActionReady()) {
                    if (rc.getType() == UnitType.MOPPER) {
                        // mopper bisa swing kalau memungkinkan
                        Direction d = rc.getLocation().directionTo(bestTarget.getLocation());
                        if (d != Direction.CENTER && rc.canMopSwing(d)) {
                            rc.mopSwing(d);
                            return;
                        }
                    }

                    rc.attack(bestTarget.getLocation());
                    return;
                }
            } else {
                // kirim help
                if (rc.getRoundNum() % 5 == 0) {
                    for (RobotInfo ally : allyRobots) {
                        if (rc.canSendMessage(ally.getLocation(), MSG_HELP)) {
                            rc.sendMessage(ally.getLocation(), MSG_HELP);
                        }
                    }
                }

                // kalau ada ack, stand by
                if (helpArrived) {
                    rc.setIndicatorString("Stand by, waiting allies");
                    return;
                }
            }
        }

        // fallback: cat tile sendiri kalau belum ally
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
    public static UnitType randomTowerType() {
        int roll = rng.nextInt(3);
        if (roll == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (roll == 1) return UnitType.LEVEL_ONE_MONEY_TOWER;
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

            if (paint == PaintType.EMPTY) score += 10;
            else if (paint.isEnemy()) score += 8;
            else if (paint.isAlly()) score -= 100;

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

        // untuk tower bisa AoE jika null valid di engine
        if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }
}