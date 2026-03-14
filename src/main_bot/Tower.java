package main_bot;

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
        if (!rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        attackLowestHpEnemy(rc, enemies);
        adaptiveSpawn(rc, enemies.length > 0);
    }

    private static void attackLowestHpEnemy(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (enemies.length == 0 || !rc.isActionReady()) return;
        RobotInfo target = null;
        int lowestHp = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation()) && enemy.getHealth() < lowestHp) {
                lowestHp = enemy.getHealth();
                target = enemy;
            }
        }

        if (target != null) rc.attack(target.getLocation());
        else if (rc.canAttack(null)) rc.attack(null);
    }

    private static void adaptiveSpawn(RobotController rc, boolean isUnderAttack) throws GameActionException {
        if (!rc.isActionReady()) return;
        UnitType unitToSpawn = UnitType.SOLDIER; 

        if (isUnderAttack && rc.getMoney() >= 300 && rc.getPaint() >= 100) {
            unitToSpawn = UnitType.MOPPER;
        } else if (rc.getRoundNum() > 150) {
            if (rc.getRoundNum() % 3 == 0 && rc.getMoney() >= 300) unitToSpawn = UnitType.MOPPER;
            else if (rc.getRoundNum() % 5 == 0 && rc.getMoney() >= 400) unitToSpawn = UnitType.SPLASHER;
        }

        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(unitToSpawn, spawnLoc)) {
                rc.buildRobot(unitToSpawn, spawnLoc);
                break; 
            }
        }
    }
}