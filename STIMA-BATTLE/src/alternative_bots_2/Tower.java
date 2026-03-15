package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static MapLocation globalEnemyTarget = null;

    private static int soldierCount = 0;
    private static int splasherCount = 0;
    private static int mopperCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        readIntel(rc);

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        runWeightedAttack(rc, enemies);

        broadcastToNearbyAllies(rc);

        runStrategicSpawning(rc, enemies.length > 0);
    }

    // =========================================================
    // INTEL
    // =========================================================

    private static void readIntel(RobotController rc) throws GameActionException {
        for (Message msg : rc.readMessages(rc.getRoundNum() - 1)) {
            int bytes = msg.getBytes();
            if (!Communication.isRobotInfo(bytes)) {
                MapInfo intel = MapInfoCodec.decode(bytes);
                if (intel.getPaint().isEnemy() || intel.hasRuin()) {
                    globalEnemyTarget = intel.getMapLocation();
                }
            }
        }
    }

    /**
     * Every turn, if we know an enemy target, broadcast it to all nearby allies.
     * This keeps the entire army updated, not just newly spawned units.
     */
    private static void broadcastToNearbyAllies(RobotController rc) throws GameActionException {
        if (globalEnemyTarget == null) return;
        MapInfo targetIntel = new MapInfo(
            globalEnemyTarget, true, false, PaintType.ENEMY_PRIMARY, PaintType.EMPTY, false, false
        );
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!ally.getType().isTowerType()) {
                Communication.sendMapInformation(rc, targetIntel, ally.getLocation());
            }
        }
    }

    // =========================================================
    // COMBAT
    // =========================================================

    private static void runWeightedAttack(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (enemies.length == 0 || !rc.isActionReady()) return;

        RobotInfo bestTarget = null;
        double maxScore = -10000.0;

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.getLocation())) continue;
            int hp = enemy.getHealth();
            double score = 1000.0 / (hp + 1); // Focus low-HP targets to finish kills

            // Threat prioritization
            switch (enemy.getType()) {
                case SPLASHER: score += 600.0; break;   // AoE threat — kill first
                case MOPPER:   score += 400.0; break;   // Paint cleaner — kill second
                case SOLDIER:  score += 200.0; break;
                default:       score += 800.0; break;   // Enemy towers — highest priority
            }
            // Is this already near death? Prioritize the kill.
            if ((double)hp / enemy.getType().health < 0.25) score += 1000.0;

            if (score > maxScore) { maxScore = score; bestTarget = enemy; }
        }

        if (bestTarget != null) {
            rc.attack(bestTarget.getLocation());
        } else if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }

    // =========================================================
    // STRATEGIC SPAWNING
    // =========================================================

    /**
     * Spawning logic:
     *
     * Phase 1 (0-100):   Pure soldier rush to grab ruins fast.
     * Phase 2 (100-300): Balance soldiers + splashers for map control.
     * Phase 3 (300+):    Push composition: splashers lead, soldiers support, moppers clean.
     *
     * Target ratios:
     *   Phase 1: 100% soldier
     *   Phase 2: 65% soldier / 25% splasher / 10% mopper
     *   Phase 3: 40% soldier / 45% splasher / 15% mopper
     */
    private static void runStrategicSpawning(RobotController rc, boolean underAttack) throws GameActionException {
        int round = rc.getRoundNum();
        int money = rc.getMoney();
        int paint = rc.getPaint();

        if (underAttack) {
            if (paint > 100 && trySpawn(rc, UnitType.MOPPER)) return;
            if (trySpawn(rc, UnitType.SOLDIER)) return;
        }

        if (round < 100) {
            if (money >= UnitType.SOLDIER.moneyCost && paint > 80) {
                trySpawn(rc, UnitType.SOLDIER);
            }
            return;
        }

        int total = soldierCount + splasherCount + mopperCount;

        double targetSoldierRatio, targetSplasherRatio, targetMopperRatio;
        if (round < 300) {
            targetSoldierRatio  = 0.65;
            targetSplasherRatio = 0.25;
            targetMopperRatio   = 0.10;
        } else {
            targetSoldierRatio  = 0.40;
            targetSplasherRatio = 0.45;
            targetMopperRatio   = 0.15;
        }

        double currentSoldierRatio  = (total == 0) ? 0 : (double) soldierCount  / total;
        double currentSplasherRatio = (total == 0) ? 0 : (double) splasherCount / total;
        double currentMopperRatio   = (total == 0) ? 0 : (double) mopperCount   / total;

        // Can we afford anything?
        if (money < UnitType.MOPPER.moneyCost) return;

        // Spawn whichever unit type is most "underweight" relative to its target ratio
        double soldierDeficit  = targetSoldierRatio  - currentSoldierRatio;
        double splasherDeficit = targetSplasherRatio - currentSplasherRatio;
        double mopperDeficit   = targetMopperRatio   - currentMopperRatio;

        if (soldierDeficit >= splasherDeficit && soldierDeficit >= mopperDeficit) {
            if (money >= UnitType.SOLDIER.moneyCost && paint > 80) trySpawn(rc, UnitType.SOLDIER);
        } else if (splasherDeficit >= mopperDeficit) {
            if (money >= UnitType.SPLASHER.moneyCost && paint > 200) trySpawn(rc, UnitType.SPLASHER);
            else if (money >= UnitType.SOLDIER.moneyCost && paint > 80) trySpawn(rc, UnitType.SOLDIER);
        } else {
            if (money >= UnitType.MOPPER.moneyCost && paint > 60) trySpawn(rc, UnitType.MOPPER);
            else if (money >= UnitType.SOLDIER.moneyCost && paint > 80) trySpawn(rc, UnitType.SOLDIER);
        }
    }

    private static boolean trySpawn(RobotController rc, UnitType type) throws GameActionException {
        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, spawnLoc)) {
                rc.buildRobot(type, spawnLoc);

                // Track composition
                switch (type) {
                    case SOLDIER:  soldierCount++;  break;
                    case SPLASHER: splasherCount++; break;
                    case MOPPER:   mopperCount++;   break;
                    default: break;
                }

                // Immediately send intel to newly born unit
                if (globalEnemyTarget != null) {
                    MapInfo targetIntel = new MapInfo(
                        globalEnemyTarget, true, false, PaintType.ENEMY_PRIMARY, PaintType.EMPTY, false, false
                    );
                    Communication.sendMapInformation(rc, targetIntel, spawnLoc);
                }
                return true;
            }
        }
        return false;
    }
}