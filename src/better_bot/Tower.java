package better_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static void run(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        runWeightedAttack(rc, enemies);
        runQuantSpawning(rc, enemies.length > 0);
    }

    private static void runWeightedAttack(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (enemies.length == 0 || !rc.isActionReady()) return;
        
        RobotInfo bestTarget = null;
        double maxThreatScore = -10000.0;

        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                int hp = enemy.getHealth();
                double score = 1000.0 / (hp + 1);
                
                switch (enemy.getType()) {
                    case SPLASHER: score += 500.0; break;
                    case MOPPER: score += 300.0; break;
                    case LEVEL_ONE_PAINT_TOWER:
                    case LEVEL_ONE_MONEY_TOWER: 
                        score += 100.0; break;
                    default: break;
                }

                if (score > maxThreatScore) {
                    maxThreatScore = score;
                    bestTarget = enemy;
                }
            }
        }

        if (bestTarget != null) {
            rc.attack(bestTarget.getLocation());
        } else if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }

    private static void runQuantSpawning(RobotController rc, boolean isUnderAttack) throws GameActionException {
        if (isUnderAttack && trySpawn(rc, UnitType.MOPPER)) return;
        
        if (rc.getRoundNum() < 100) {
            trySpawn(rc, UnitType.SOLDIER);
            return;
        }

        int money = rc.getMoney();
        if (money > 400 && trySpawn(rc, UnitType.SPLASHER)) return;
        if (rc.getRoundNum() % 2 == 0 && trySpawn(rc, UnitType.MOPPER)) return;
        
        trySpawn(rc, UnitType.SOLDIER);
    }

    private static boolean trySpawn(RobotController rc, UnitType type) throws GameActionException {
        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, spawnLoc)) {
                rc.buildRobot(type, spawnLoc);
                System.out.println("TOWER SPAWN: " + type.name());
                return true; 
            }
        }
        return false;
    }
}