package testbot;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
	
	static int flag = 0;
	static boolean isMessenger = false;
	static MapLocation[] knownTowers = new MapLocation[25];
	static int ktSIdx = 0;
	static int ktLIdx = 0;
	static boolean isSaving = false;
	static int savingTimeout = 0;
	static int buildTimeout = 0;
	static int curBuild = -1;
	
	static boolean isScout = false;
	static int scoutDir = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
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

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");
		
		if (rc.getType() == UnitType.MOPPER &&rc.getID() % 2 == 0) {
			isMessenger = true;
		}
		
		if (rc.getType() == UnitType.SOLDIER &&rc.getID() % 3 == 0) {
			isScout = true;
			scoutDir = rng.nextInt(3);
		}

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
		if (isSaving == true) {
			rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
			rc.setIndicatorString("Saving for " + savingTimeout + " turns");
			savingTimeout++;
			if (savingTimeout > 50) {
				savingTimeout = 0;
				isSaving = false;
			}
		}
		
		if (isSaving == false) {
			if (curBuild == -1) {
				curBuild = rng.nextInt(2);
				buildTimeout = 100;
			} else {
				// Pick a direction to build in.
				Direction dir = directions[rng.nextInt(directions.length)];
				MapLocation nextLoc = rc.getLocation().add(dir);
				// Pick a random robot type to build.
				if (curBuild == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)){
					rc.buildRobot(UnitType.SOLDIER, nextLoc);
					System.out.println("BUILT A SOLDIER");
					buildTimeout = 0;
				}
				else if (curBuild == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)){
					rc.buildRobot(UnitType.MOPPER, nextLoc);
					System.out.println("BUILT A MOPPER");
					buildTimeout = 0;
				}
				else if (curBuild == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)){
					// rc.buildRobot(UnitType.SPLASHER, nextLoc);
					// System.out.println("BUILT A SPLASHER");
					rc.setIndicatorString("SPLASHER NOT IMPLEMENTED YET");
					buildTimeout = 0;
				}
				buildTimeout--;
				if (buildTimeout <= 0) {
					buildTimeout = 0;
					curBuild = -1;
				}
			}
		}

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            //System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
			
			if (m.getBytes() == 0) {
				isSaving = true;
			}
        }

        // TODO: can we attack other bots?
		RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
		MapLocation target = null;
		int maxHealth = 999;
		for (RobotInfo enemy : enemyRobots) {
			if (rc.canAttack(enemy.getLocation()) && enemy.getHealth() < maxHealth) {
				maxHealth = enemy.getHealth();
				target = enemy.getLocation();
			}
		}
		if (target != null) {
			rc.attack(target);
			rc.attack(null);
		}
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
		int minDist = 9999999;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin() && rc.canSenseLocation(tile.getMapLocation()) && rc.senseRobotAtLocation(tile.getMapLocation()) == null){
				int dist = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
				if (dist < minDist) {
					curRuin = tile;
					minDist = dist;
				}
            }
        }
        if (curRuin != null){
			if (curBuild == -1) {
				curBuild = rng.nextInt(2);
			}
            MapLocation targetLoc = curRuin.getMapLocation();
            Direction dir = rc.getLocation().directionTo(targetLoc);
			if (!rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
				if (rc.canMove(dir.rotateLeft())){
					rc.move(dir.rotateLeft());
				} else if (rc.canMove(dir)) {
					rc.move(dir);
				} else if (rc.canMove(dir.rotateLeft().rotateLeft())) {
					rc.move(dir.rotateLeft().rotateLeft());
				} else if (rc.canMove(dir.opposite())) {
					rc.move(dir.opposite());
				}
			}
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
            if (curBuild == 0 && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                System.out.println("Trying to build a paint tower at " + targetLoc);
            }
			if (curBuild == 1 && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)){
                rc.markTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                System.out.println("Trying to build a money tower at " + targetLoc);
            }
            // Fill in any spots in the pattern with the appropriate paint.
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                    boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation()))
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                }
            }
            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built a tower at " + targetLoc + "!");
				curBuild = -1;
            }
			if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built a tower at " + targetLoc + "!");
				curBuild = -1;
            }
        }

        // Move and attack randomly if no objective.
		int maxDist = -1;
		minDist = 9999999;
		MapInfo closestTile = null;
		MapInfo furthestTile = null;
		int paintable_tiles = 0;
		int unpainted_tiles = 0;
		for (MapInfo tile : nearbyTiles) {
			if (tile.isWall()) continue;
			if (tile.getPaint().isEnemy()) continue;
			paintable_tiles++;
			if (tile.getPaint().isAlly()) continue;
			if (rc.canSenseLocation(tile.getMapLocation()) && rc.senseRobotAtLocation(tile.getMapLocation()) != null) continue;
			unpainted_tiles++;
			int dist = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
			if (dist < minDist) {
				closestTile = tile;
				minDist = dist;
			}
			if (dist > maxDist) {
				furthestTile = tile;
				maxDist = dist;
			}
		}
		Direction dir;
		if (furthestTile == null) {
			dir = directions[rng.nextInt(directions.length)];
		} else {
			rc.setIndicatorDot(furthestTile.getMapLocation(), 0, 255, 255);
			dir = rc.getLocation().directionTo(furthestTile.getMapLocation());
		}
		
		rc.setIndicatorString("unpainted: " + unpainted_tiles + " paintable: " + paintable_tiles + " s: " + (3 * unpainted_tiles >= 2 * paintable_tiles));
        if (rc.canMove(dir) && 3 * unpainted_tiles <= paintable_tiles) {
			rc.move(dir);
		} else {
			dir = directions[rng.nextInt(directions.length)];
			if (rc.canMove(dir)) {
				rc.move(dir);
			}
		}
        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
		if (closestTile != null && !closestTile.getPaint().isAlly() && rc.canAttack(closestTile.getMapLocation())){
            rc.attack(closestTile.getMapLocation());
        }
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
		if (isMessenger == true) {
			rc.setIndicatorString("ktlidx: " + ktLIdx + " savingTimeout: " + savingTimeout);
			if (isSaving == true) {
				rc.setIndicatorDot(rc.getLocation(), 255, 155 + savingTimeout, 0);
			} else {
				rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);
			}
		}
		if (isMessenger == true && isSaving == true) {
			savingTimeout++;
			if (savingTimeout > 100) {
				savingTimeout = 0;
				isSaving = false;
			}
			
			if (knownTowers.length > 0) {
				MapLocation dst = knownTowers[ktLIdx];
				Direction dir = rc.getLocation().directionTo(dst);
				if (rc.canMove(dir)) {
					rc.move(dir);
				}
			}
		}
		
        // Move and attack
		MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
		int minDist = 9999999;
		MapInfo curTile = null;
		for (MapInfo tile : nearbyTiles) {
			if (tile.getPaint().isEnemy()) {
				int dist = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
				if (dist < minDist) {
					curTile = tile;
					minDist = dist;
				}
			}
		}
		Direction dir = null;
		if (curTile != null) {
			rc.setIndicatorDot(curTile.getMapLocation(), 255, 0, 255);
			dir = rc.getLocation().directionTo(curTile.getMapLocation());
			if (rc.canMove(dir)){
				rc.move(dir);
			} else if (rc.canMove(dir.rotateLeft().rotateLeft())) {
				rc.move(dir.rotateLeft().rotateLeft());
			}
			MapLocation nextLoc = rc.getLocation().add(dir);
        
			if (rc.canAttack(nextLoc) && nextLoc.equals(curTile.getMapLocation())){
				rc.attack(nextLoc);
			}
		} else {
			dir = directions[rng.nextInt(directions.length)];
			if (rc.canMove(dir)){
				rc.move(dir);
			}
		}
        
        // We can also move our code into different methods or classes to better organize it!
        if (isMessenger == true) {
			updateAllyTowers(rc);
			checkNearbyRuins(rc);
		}
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically 
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }
	
	public static void updateAllyTowers(RobotController rc) throws GameActionException{
		RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
		
		for (RobotInfo ally : allyRobots) {
			if (!ally.getType().isTowerType()) continue;
			
			flag = 0;
			
			for (MapLocation tower : knownTowers) {
				if (tower != null && tower.equals(ally.location)) {
					flag = 1;
					if (rc.canSendMessage(ally.location)) {
						rc.sendMessage(ally.location, 0);
						if (knownTowers[ktLIdx].equals(ally.location)) ktLIdx = (ktLIdx + 1) % ktSIdx;
					}
				}
			}
			if (flag == 1) {
				flag = 0;
				continue;
			}

			knownTowers[ktSIdx] = ally.location;
			if (ktSIdx < knownTowers.length) ktSIdx++;
		}
		
	}
	
	public static void checkNearbyRuins(RobotController rc) throws GameActionException{
		MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
		int minDist = 9999999;
        for (MapInfo tile : nearbyTiles){
			if (!tile.hasRuin() || rc.senseRobotAtLocation(tile.getMapLocation()) != null) continue;
			
			Direction dir = tile.getMapLocation().directionTo(rc.getLocation());
			MapLocation mark = tile.getMapLocation().add(dir);
			if (!rc.senseMapInfo(mark).getMark().isAlly()) continue;
            
			boolean f = false;
			for (MapInfo tile2 : nearbyTiles) {
				if (Math.abs(tile2.getMapLocation().x - tile.getMapLocation().x) <= 2 && Math.abs(tile2.getMapLocation().y - tile.getMapLocation().y) <= 2 && tile2.getPaint().isEnemy()) {
					f = true;
					break;
				}
			}
			if (f == true) continue;
			isSaving = true;
			return;
        }
	}
}
