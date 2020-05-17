package byow.Core;

//import byow.TileEngine.TERenderer;
import byow.TileEngine.TETile;
import byow.TileEngine.Tileset;

import java.util.Random;

public class DrawASpreadingWorld {
    private static int WIDTH;
    private static int HEIGHT;
    private static Random random;
    private static TETile[][] world;
    private static final int N = 15;
    private static DrawElement de = new DrawElement();

//    public static void main(String[] args) {
//        DrawASpreadingWorld.methodDrawASpreadingWorld(1907, 80, 30);
//    }


    public static Position methodDrawASpreadingWorld(long seed,
                                                     int width, int height, TETile[][] startWorld) {
        WIDTH = width;
        HEIGHT = height;
        random = new Random(seed);
        world = startWorld;
//        // initialize the tile rendering engine with a window of size WIDTH x HEIGHT
//        TERenderer ter = new TERenderer();
//        ter.initialize(WIDTH, HEIGHT);
//
//        // initialize tiles
//        world = new TETile[WIDTH][HEIGHT];
//        for (int x = 0; x < WIDTH; x += 1) {
//            for (int y = 0; y < HEIGHT; y += 1) {
//                world[x][y] = Tileset.NOTHING;
//            }
//        }

        // a start point and draw a room
        int w = RandomUtils.uniform(random, 4, 7);
        int h = RandomUtils.uniform(random, 4, 7);
        Position start = generateStartPosition();
        Position door = de.startNeighbor(start);
        Position[] threeNextStarts = de.room(world, start, w, h, random);
        // spread the world
        recursivelyDraw(N, threeNextStarts);

        // change the wall next to start to a locked door
        world[start.getX()][start.getY()] = Tileset.NOTHING;
        world[door.getX()][door.getY()] = Tileset.LOCKED_DOOR;

        // generate a spot on floor as Avatar
        Position avatar = generateSpotOnFloor();
        world[avatar.getX()][avatar.getY()] = Tileset.AVATAR;

        // draws the world to the screen
//        ter.renderFrame(world);
//        return world;
        return avatar;
    }

    private static Position generateStartPosition() {
        int startX = RandomUtils.uniform(random, WIDTH / 4, WIDTH * 3 / 4);
        int startY = RandomUtils.uniform(random, HEIGHT / 3, HEIGHT * 2 / 3);
        Direction startType = RandomUtils.randomDirection(random);
        return new Position(startX, startY, startType);
    }

    private static void recursivelyDraw(int n, Position[] threeNextStarts) {
        if (n > 0) {
            Position[] tNS;
            int nn = n - 1;
            int w;
            int h;
            int l;
            int tl;
            boolean room = RandomUtils.bernoulli(random,
                    0.7); // true for room, false for hallway 0.6 before
            boolean regular = RandomUtils.bernoulli(random, 0.8); // 0.7 before
            // true for regular hallway, false for L shape hallway
            for (Position s: threeNextStarts) {
                if (room) {
                    w = RandomUtils.uniform(random, 4, 7);
                    h = RandomUtils.uniform(random, 4, 7);
                    if (de.availableRoom(world, s, w, h)) {
                        tNS = de.room(world, s, w, h, random);
                    } else {
                        nn = 0;
                        tNS = null;
                    }
                } else if (regular) {
                    l = RandomUtils.uniform(random, 3, 6);
                    if (de.availableHallway(world, s, l)) {
                        tNS = de.hallway(world, s, l);
                    } else {
                        nn = 0;
                        tNS = null;
                    }
                } else {
                    boolean rightOrUp = RandomUtils.bernoulli(random, 0.5);
                    l = RandomUtils.uniform(random, 3, 6);
                    tl = RandomUtils.uniform(random, 3, 5);
                    if (de.availableLShapeHallway(world, s, l, tl, rightOrUp)) {
                        tNS = de.hallwayLShape(world, s, l, tl, rightOrUp);
                    } else {
                        nn = 0;
                        tNS = null;
                    }
                }
                recursivelyDraw(nn, tNS);
            }
        }
    }


    public static Position generateSpotOnFloor() {
        for (int i = 0; i < WIDTH; i += 1) {
            for (int j = 0; j < HEIGHT; j += 1) {
                if (world[i][j].equals(Tileset.FLOOR)) {
                    return new Position(i, j);
                }
            }
        }
        return new Position(0, 0); // default to solve might be null, should use any time;
    }
}
