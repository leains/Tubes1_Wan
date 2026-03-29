package alternative_bots-1;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;   
import java.util.Random;
import java.util.Set;
import java.lang.Integer;

public class RobotPlayer {
    // Inisialisasi
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

    static int soldierCount = 0;
    static int mopperCount = 0;
    static int splasherCount = 0;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("GET READY TO LOSE HAHAHAHAHAHA!!!");
        rc.setIndicatorString("Bot Deployed");

        while (true){
            turnCount += 1;

            try {
                switch(rc.getType()){
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                }
            } catch (GameActionException e){
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e){
                System.out.println("Exception");
                e.printStackTrace();

            } finally{
                Clock.yield();
            }
        }

    }

    public static void runTower(RobotController rc) throws GameActionException{
        // 1. Sense
        RobotInfo[] enemy = rc.senseNearbyRobots(-1,rc.getTeam().opponent());
        // 2. Nerima Message
        Message[] pesan = rc.readMessages(-1);
        // 3. Terusin Message
        for (Message m : pesan){
            if (rc.canBroadcastMessage()){
                rc.broadcastMessage(m.getBytes());
            }
        }
        // 4. Attack Nearby Enemy (STRATEGI: HP TERENDAH)
        if (enemy.length>0){
            RobotInfo target = null;
            boolean first = true;
            int lowest = Integer.MAX_VALUE;
            for (RobotInfo r : enemy){
                if (first){
                    first = false; lowest = r.getHealth(); target = r;
                }else if (rc.canAttack(r.getLocation()) && r.getHealth() < lowest){
                    lowest = r.getHealth();
                    target = r;
                }
            }
            // SERANGGG!!!
            if (target!=null && rc.isActionReady()){
                rc.attack(target.getLocation());
            }
            // AREA ATTACK LESGOO!!
            if (rc.canAttack(null)){
                rc.attack(null);
            }
        }
        // 5. Algoritma Greedy Deploy Robot
        greedySpawn(rc);
    }

    public static void runSoldier(RobotController rc) throws GameActionException{
        // 1. Sense
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] enemy = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] ally = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo curRuin = null;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                // Apakah ruin nya sudah ditempatkan huahuahua?
                if (rc.canSenseRobotAtLocation(tile.getMapLocation())) {
                    RobotInfo atRuin = rc.senseRobotAtLocation(tile.getMapLocation());
                    if (atRuin != null && atRuin.getTeam() == rc.getTeam()) continue;
                }
                curRuin = tile;
                break;
            }
        }
        // 2. Menerima Pesan sekitar jika ada dan PROSES :)
        Message[] pesan = rc.readMessages(-1);
        for (Message m : pesan){
            // Seakan-akan ada pesan. (Algoritma Proses Pesan Tidak Dibuat)
        }

        // 3. Cek Kondisi
        // Kondisi Cat
        if (rc.getPaint()<50) {
        // Refill
            boolean refill = refillPaint(rc);
            if (!refill){
                // Belum bisa refill
                MapLocation nearestTower = findNearestAllyTower(rc,ally);
                if (nearestTower!=null) move(rc,nearestTower);
            }
        }
        // Kondisi HP
        if (rc.getHealth()<30) {
        // Move ke sekutu  (langsung return)
            if (ally.length>0 && rc.isMovementReady()){
                move(rc,ally[0].getLocation());
            }
            return;
        }

        // 4. Strategi Bangun Tower : Ketemu Ruins langsung buat Tower
        if (curRuin!=null){
            boolean done = buildTower(rc,curRuin,nearbyTiles);
            if (!done){
                grindingPaint(rc);
                return;
            }
        }

        // 5. Strategi Greedy Move.
        moveGreedy(rc, nearbyTiles, curRuin, enemy, ally);
        
        // 6. Cat tile sekarang.
        grindingPaint(rc);

        // 7. Attack Tower in range
        for (RobotInfo r : enemy){
            if (r.getType().isTowerType() && rc.isActionReady() && rc.canAttack(r.getLocation())){
                rc.attack(r.getLocation());
                break;
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // 1. Sense
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] enemy = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] ally = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo curRuin = null;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                curRuin = tile;
                break;
            }
        }

        // 2. Menerima Pesan sekitar jika ada dan PROSES :)
        Message[] pesan = rc.readMessages(-1);
        for (Message m : pesan){
            // Seakan-akan ada pesan. (Algoritma Proses Pesan Tidak Dibuat)
        }

        // 3. Cek Kondisi
        // Kondisi Cat
        if (rc.getPaint()<15) {
            //Refill
            boolean refill = refillPaint(rc);
            if (!refill){
                MapLocation nearestTower = findNearestAllyTower(rc,ally);
                if (nearestTower!=null) move (rc,nearestTower);
            }
            return;
        }
        // Kondisi HP
        if (rc.getHealth()<50) {
            // Move ke sekutu (langsung return)
            if (ally.length>0 && rc.isMovementReady()){
                move(rc,ally[0].getLocation());
                return;
            }
        }

        // 4. Support Ally Paint Habis
        if (rc.isActionReady()) {
            for (RobotInfo a : ally) {
                if (!a.getType().isTowerType() && a.getPaintAmount() < 30 && rc.getPaint() > 50) {
                    int toGive = Math.min(rc.getPaint() - 20, 30);
                    if (rc.canTransferPaint(a.getLocation(), toGive)) {
                        rc.transferPaint(a.getLocation(), toGive);
                        break;
                    }
                }
            }
        }

        // 5. Attack nearby enemy !!!
        if (enemy.length > 0 && rc.isActionReady()) {
            RobotInfo target = null;
            int mostPaint = -1;
            for (RobotInfo e : enemy) {
                if (e.getPaintAmount() > mostPaint && rc.canAttack(e.getLocation())) {
                    mostPaint = e.getPaintAmount();
                    target = e;
                }
            }
            if (target != null) {
                // Coba mop swing dulu
                Direction toEnemy = rc.getLocation().directionTo(target.getLocation());
                boolean swung = false;
                for (Direction sd : new Direction[]{toEnemy, toEnemy.rotateLeft(), toEnemy.rotateRight()}) {
                    if (rc.canMopSwing(sd)) {
                        rc.mopSwing(sd);
                        swung = true;
                        break;
                    }
                }
                if (!swung && rc.canAttack(target.getLocation())) {
                    rc.attack(target.getLocation());
                }
            }
        }

        // 6. Strategi Greedy Move
        moveGreedy(rc, nearbyTiles, curRuin, enemy, ally);
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        // 1. Sense
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] enemy = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] ally = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo curRuin = null;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                curRuin = tile;
                break;
            }
        }
        // 2. Menerima Pesan sekitar jika ada dan PROSES :)
        Message[] pesan = rc.readMessages(-1);
        for (Message m : pesan){
            // Seakan-akan ada pesan. (Algoritma Proses Pesan Tidak Dibuat)
        }

        // 3. Cek Kondisi 
        // Cek Paint
        if (rc.getPaint() < 60) {
            //Refill
            boolean refill = refillPaint(rc);
            if (!refill){
                MapLocation nearestTower = findNearestAllyTower(rc,ally);
                if (nearestTower!=null) move (rc,nearestTower);
            }
            return;
        }

        // 4. Cari area splash terbaik OR Strategi Greedy Move
        MapLocation best = bestSplash(rc, nearbyTiles, enemy);
        if (best!=null){
            if (!rc.getLocation().isWithinDistanceSquared(best, 4)) {
                move(rc, best);
            } else {
                if (rc.isActionReady() && rc.canAttack(best)) {
                    rc.attack(best);
                }
                move(rc, best);
            }
        }else{
            moveGreedy(rc, nearbyTiles, curRuin, enemy, ally);
        }
    }

    // HELPER // 
    static void greedySpawn(RobotController rc) throws GameActionException{
        // Cek bisa spawn tidak
        if (!rc.isActionReady()) return;
        int chips = rc.getMoney();
        int paints = rc.getPaint();

        // Rasio : 60% Soldier, 30% Mopper, 10% Splasher
        int total = soldierCount + mopperCount + splasherCount;
        float soldierRatio  = total == 0 ? 0f : (float) soldierCount  / total;
        float mopperRatio   = total == 0 ? 0f : (float) mopperCount   / total;
        float splasherRatio = total == 0 ? 0f : (float) splasherCount / total;

        float soldierDelta  = 0.60f - soldierRatio;
        float mopperDelta   = 0.30f - mopperRatio;
        float splasherDelta = 0.10f - splasherRatio;

        // STRATEGI : SPAWN YANG DELTA-NYA PALING BESAR
        UnitType spawn = null;
        if (soldierDelta>=mopperDelta && soldierDelta>=splasherDelta){
            if (chips >= 200 && paints >= 200) spawn = UnitType.SOLDIER;
        } else if (mopperDelta>=soldierDelta && mopperDelta>=splasherDelta){
            if (chips >= 300 && paints >= 100) spawn = UnitType.MOPPER;
        } else { 
            if (chips >= 400 && paints >= 300) spawn = UnitType.SPLASHER;
        }

        if (spawn==null) return;
        for (Direction d : directions){
            MapLocation lokasi = rc.getLocation().add(d);
            if (rc.canBuildRobot(spawn,lokasi)){
                rc.buildRobot(spawn,lokasi);
                if (spawn == UnitType.SOLDIER) {
                    soldierCount+=1; 
                    break;
                }
                else if (spawn==UnitType.MOPPER) {
                    mopperCount+=1; 
                    break;
                }
                else {
                    splasherCount+=1; 
                    break;
                }
            }
        }
        return;
    }
    static void moveGreedy(RobotController rc, MapInfo[] nearby, MapInfo ruin, RobotInfo[] enemy, RobotInfo[] ally) throws GameActionException{
        if (!rc.isMovementReady()) return;
        // Greedy dengan skor (memilih skor terbaik berdasarkan kondisi yang ada)
        MapLocation cur = rc.getLocation();

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE, score;
        boolean first = true;
        for (Direction dir: directions){
            if (!rc.canMove(dir)) continue;

            MapLocation calon = cur.add(dir);
            score = 0;
            
            // Ruin
            if (ruin!=null){
                int current = cur.distanceSquaredTo(ruin.getMapLocation());
                int next = calon.distanceSquaredTo(ruin.getMapLocation());
                if (next < current) score += 20;
                else score -= 5;
            } 

            // Kondisi Sekitar Direction
            for (MapInfo tile: nearby){
                if (!tile.getMapLocation().isWithinDistanceSquared(calon,4)) continue;
                PaintType paint = tile.getPaint();
                if (paint == PaintType.EMPTY) score += 10;
                else if (paint.isEnemy()) score += 6;
                else if (paint.isAlly()) score -= 3;
                if (tile.hasRuin()) score += 15; 
            }

            // Nabrak dengan Ally (mendekat)
            for (RobotInfo a : ally) {
                if (a.getLocation().isWithinDistanceSquared(calon, 4)) score -= 4;
            }

            if (first || score > bestScore) {
                bestScore = score;
                bestDir   = dir;
                first     = false;
            }
        }

        // Keputusan FINAL
        if (bestDir != null) rc.move(bestDir);
    }

    static boolean buildTower(RobotController rc, MapInfo ruin, MapInfo[] nearby)throws GameActionException{
        MapLocation target = ruin.getMapLocation();
        MapLocation cur = rc.getLocation();

        // STRATEGI : TENTUKAN TOWER TYPE
        UnitType tower;
        int chips = rc.getMoney();
        int towerCount = rc.getNumberTowers();
        if (chips>5000 && towerCount>4) tower = UnitType.LEVEL_ONE_DEFENSE_TOWER; // Syarat : Kaya
        else if (towerCount%2==0) tower = UnitType.LEVEL_ONE_PAINT_TOWER;
        else tower = UnitType.LEVEL_ONE_MONEY_TOWER;

        // Bangun Tower
        if (!cur.isWithinDistanceSquared(target, 8)) {
            move(rc, target);
            return false;
        }
        if (rc.canMarkTowerPattern(tower, target)) {
            rc.markTowerPattern(tower, target);
        }
        for (MapInfo tile : rc.senseNearbyMapInfos(target, 8)) {
            if (!rc.isActionReady()) break;
            PaintType mark  = tile.getMark();
            PaintType paint = tile.getPaint();
            if (mark != PaintType.EMPTY && mark != paint) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation(), useSecondary);
                }
            }
        }

        // Apakah sudah complete?
        if (rc.canCompleteTowerPattern(tower, target)) {
            rc.completeTowerPattern(tower, target);
            rc.setTimelineMarker("MY TOWER HUAHUAHUAHUA!!!", 0, 255, 0);
            return true;
        }
        // Belum Complete
            if (!cur.isWithinDistanceSquared(target, 2)) {
            move(rc, target);
        }

        return false;
    }

    static MapLocation bestSplash(RobotController rc, MapInfo[] nearby, RobotInfo[] enemy) throws GameActionException{
        MapLocation cur      = rc.getLocation();
        MapLocation best     = null;
        int bestScore = 5; // minimum threshold
 
        for (MapInfo centerTile : nearby) {
            if (!centerTile.isPassable()) continue;
            MapLocation center = centerTile.getMapLocation();
            if (!cur.isWithinDistanceSquared(center, 4)) continue;
 
            int score = 0;
            for (MapInfo t : nearby) {
                int dist = center.distanceSquaredTo(t.getMapLocation());
                if (dist > 4) continue;
                PaintType paint = t.getPaint();
                if (dist <= 2) {
                    if (paint.isEnemy()) score += 12;
                    else if (paint == PaintType.EMPTY) score += 8;
                    else if (paint.isAlly()) score -= 5;
                } else {
                    if (paint == PaintType.EMPTY) score += 6;
                    else if (paint.isAlly())  score -= 2;
                }
            }
            for (RobotInfo e : enemy) {
                if (e.getType().isTowerType() && e.getLocation().isWithinDistanceSquared(center, 4)) {
                    score += 15;
                }
            }
            if (score > bestScore) { bestScore = score; best = center; }
        }
        return best;
    }

    static void grindingPaint(RobotController rc) throws GameActionException{
        if (!rc.isActionReady()) return;
        MapLocation lokasi = rc.getLocation();
        if (rc.canAttack(lokasi)){
            MapInfo info = rc.senseMapInfo(lokasi);
            if (!info.getPaint().isAlly()){
                rc.attack(lokasi);
            }
        }
    }

    static boolean refillPaint (RobotController rc) throws GameActionException{
        if (!rc.isActionReady()) return false;
        RobotInfo[] nearby = rc.senseNearbyRobots(2,rc.getTeam());
        for (RobotInfo r: nearby){
            if (r.getType().isTowerType()) {
                int need = rc.getType().paintCapacity - rc.getPaint();
                if (rc.canTransferPaint(r.getLocation(), -need)) {
                    rc.transferPaint(r.getLocation(), -need);
                    return true;
                }
            }
        }
        return false;
    }

    static MapLocation findNearestAllyTower(RobotController rc, RobotInfo[] nearby) throws GameActionException{
        MapLocation best = null;
        boolean first = true;
        int bestDist = Integer.MAX_VALUE;
        MapLocation cur = rc.getLocation();
        for (RobotInfo r: nearby){
            if (r.getType().isTowerType()){
                int delta = cur.distanceSquaredTo(r.getLocation());
                if (first) {
                    best=r.getLocation();
                    bestDist = delta;
                    first = false;
                }else{
                    if (delta<bestDist) {
                        bestDist = delta;
                        best = r.getLocation();
                    }
                }
            }
        }
        return best;
    }

    static void move(RobotController rc, MapLocation target) throws GameActionException{
        if (!rc.isMovementReady()) return;
        MapLocation cur = rc.getLocation();
        if (cur.equals(target)) return;
 
        Direction best = cur.directionTo(target);
        Direction[] attempts = {
            best,
            best.rotateLeft(),
            best.rotateRight(),
            best.rotateLeft().rotateLeft(),
            best.rotateRight().rotateRight()
        };
        for (Direction d : attempts) {
            if (rc.canMove(d)) { rc.move(d); return; }
        }
    }
}