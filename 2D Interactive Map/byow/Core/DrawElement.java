package byow.Core;

import byow.TileEngine.TETile;
import byow.TileEngine.Tileset;

import java.util.Random;

public class DrawElement {
    public boolean availableRoom(TETile[][] world, Position start, int width, int height) {
        Position[] range = rangeOfRoom(start, width, height);
        return availableRectangle(world, range);
    }

    public boolean availableHallway(TETile[][] world, Position start, int length) {
        Position[] range = rangeOfHallway(start, length);
        return availableRectangle(world, range);
    }

    public boolean availableLShapeHallway(TETile[][] world, Position start,
                                          int length, int turnLength, boolean b) {
        Position turnStart = findTurnStart(start, length, b);
        // convert one L shape hallway to two regular hallways
        Position[] range1 = rangeOfHallway(start, length);
        Position[] range2 = rangeOfHallway(turnStart, turnLength);
        return availableRectangle(world, range1) && availableRectangle(world, range2);
    }


    private Position findTurnStart(Position start, int length, boolean b) {
        Direction type = start.getT();
        Direction turnType = turnStartType(type, b);
        int increment = length - 1;
        return newPosition(start, type, increment, turnType, 1, turnType);
    }

    private Direction turnStartType(Direction startType, boolean b) {
        Direction turnType = null;
        if (b) {
            switch (startType) {
                case RIGHTTOWARDS:
                case LEFTTOWARDS:
                    turnType = Direction.UPTOWARDS;
                    break;
                case UPTOWARDS:
                case DOWNTOWARDS:
                    turnType = Direction.RIGHTTOWARDS;
                    break;
                default:
                    turnType = null;
            }
        } else {
            switch (startType) {
                case RIGHTTOWARDS:
                case LEFTTOWARDS:
                    turnType = Direction.DOWNTOWARDS;
                    break;
                case UPTOWARDS:
                case DOWNTOWARDS:
                    turnType = Direction.LEFTTOWARDS;
                    break;
                default:
                    turnType = null;
            }
        }
        return turnType;
    }


    private boolean availableRectangle(TETile[][] world, Position[] range) {
        int dimensionX = world.length;
        int dimensionY = world[0].length;
        int wallLeft = range[0].getX();
        int wallRight  = range[1].getX();
        int wallDown = range[0].getY();
        int wallUp = range[1].getY();

        if ((wallLeft < 0) || (wallRight >= dimensionX)
                || (wallDown < 0) || (wallUp >= dimensionY)) {
            // DimensionXY are size of the world[][]
            return false;
        }
        for (int i = wallLeft; i <= wallRight; i++) {
            for (int j = wallDown; j <= wallUp; j++) {
                if (!world[i][j].equals(Tileset.NOTHING)) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * @return an array of two Position, left down and right up
     */
    private Position[] rangeOfRoom(Position start, int width, int height) {
        int incrementX = width - 1;
        int incrementY = height - 1;
        Position rightUp;
        Position leftDown;
        Direction type = start.getT();
        switch (type) {
            case RIGHTTOWARDS:
                leftDown = newPosition(start, Direction.RIGHTTOWARDS, 1, Direction.DOWNTOWARDS, 1);
                rightUp = newPosition(leftDown, Direction.RIGHTTOWARDS,
                        incrementX, Direction.UPTOWARDS, incrementY);
                break;
            case LEFTTOWARDS:
                rightUp = newPosition(start, Direction.LEFTTOWARDS, 1, Direction.UPTOWARDS, 1);
                leftDown = newPosition(rightUp, Direction.LEFTTOWARDS,
                        incrementX, Direction.DOWNTOWARDS, incrementY);
                break;
            case UPTOWARDS:
                leftDown = newPosition(start, Direction.LEFTTOWARDS, 1, Direction.UPTOWARDS, 1);
                rightUp = newPosition(leftDown, Direction.RIGHTTOWARDS,
                        incrementX, Direction.UPTOWARDS, incrementY);
                break;
            case DOWNTOWARDS:
                rightUp = newPosition(start, Direction.RIGHTTOWARDS, 1, Direction.DOWNTOWARDS, 1);
                leftDown = newPosition(rightUp, Direction.LEFTTOWARDS,
                        incrementX, Direction.DOWNTOWARDS, incrementY);
                break;
            default:
                leftDown = newPosition(start, Direction.RIGHTTOWARDS, 1, Direction.DOWNTOWARDS, 1);
                rightUp = newPosition(leftDown, Direction.RIGHTTOWARDS,
                        incrementX, Direction.UPTOWARDS, incrementY);
                break;
        }
        return new Position[]{leftDown, rightUp};
    }

    private Position[] rangeOfHallway(Position start, int length) {
        Direction type = start.getT();
        switch (type) {
            case RIGHTTOWARDS:
            case LEFTTOWARDS:
                return rangeOfRoom(start, length, 3);
            case UPTOWARDS:
            case DOWNTOWARDS:
                return rangeOfRoom(start, 3, length);
            default: // case towards right
                return rangeOfRoom(start, length, 3);
        }
    }


    public Position neighbor(Position p, Direction d) {
        return newPosition(p, d, 1);
    }


    public Position startNeighbor(Position start) {
        return neighbor(start, start.getT());
    }

    private Position newPosition(Position p, Direction d, int n) {
        Position newPosition = new Position(p.getX(), p.getY());
        switch (d) {
            case RIGHTTOWARDS:
                newPosition.incrementX(n);
                break;
            case LEFTTOWARDS:
                newPosition.incrementX(-1 * n);
                break;
            case UPTOWARDS:
                newPosition.incrementY(n);
                break;
            case DOWNTOWARDS:
                newPosition.incrementY(-1 * n);
                break;
            default:
                newPosition.incrementX(n);
                break;
        }
        return newPosition;
    }

    /**
     * @return a position dx x to p, dy y to p
     */
    private Position newPosition(Position p, Direction dx, int x, Direction dy, int y) {
        Position np = newPosition(p, dx, x);
        np = newPosition(np, dy, y);
        return np;
    }

    private Position newPosition(Position p, Direction dx, int x,
                                 Direction dy, int y, Direction type) {
        Position np = newPosition(p, dx, x);
        np = newPosition(np, dy, y);
        np.setDirection(type);
        return np;
    }

    private Position[] roomNextStartHelper(Position[] range, Random r, Direction entrance) {
        int wallLeft = range[0].getX();
        int wallRight  = range[1].getX();
        int wallDown = range[0].getY();
        int wallUp = range[1].getY();
        int nextStartX = RandomUtils.uniform(r, wallLeft + 1, wallRight);
        int nextStartY = RandomUtils.uniform(r, wallDown + 1, wallUp);
        Position[] threeNextStarts;
        Position start0 = new Position(wallRight, nextStartY, Direction.RIGHTTOWARDS);
        Position start1 = new Position(wallLeft, nextStartY, Direction.LEFTTOWARDS);
        Position start2 = new Position(nextStartX, wallUp, Direction.UPTOWARDS);
        Position start3 = new Position(nextStartX, wallDown, Direction.DOWNTOWARDS);
        switch (entrance) {
            case RIGHTTOWARDS:
                threeNextStarts = new Position[]{start0, start2, start3};
                break;
            case LEFTTOWARDS:
                threeNextStarts = new Position[]{start1, start2, start3};
                break;
            case UPTOWARDS:
                threeNextStarts = new Position[]{start0, start1, start2};
                break;
            case DOWNTOWARDS:
                threeNextStarts = new Position[]{start0, start1, start3};
                break;
            default:
                threeNextStarts = new Position[]{start0, start2, start3};
                break;
        }
        return threeNextStarts;
    }

    public Position[] room(TETile[][] world, Position start, int width, int height, Random r) {
        Position[] range = rangeOfRoom(start, width, height);
        // find the range
        int wallLeft = range[0].getX();
        int wallRight = range[1].getX();
        int wallDown = range[0].getY();
        int wallUp = range[1].getY();
        int grassLeft = wallLeft + 1;
        int grassRight = wallRight - 1;
        int grassDown = wallDown + 1;
        int grassUp = wallUp - 1;

        // actually draw the room
        for (int x = grassLeft; x <= grassRight; x += 1) {
            for (int y = grassDown; y <= grassUp; y += 1) {
                world[x][y] = Tileset.FLOOR;
            }
        }
        for (int i = wallLeft; i <= wallRight; i++) {
            world[i][wallDown] = Tileset.WALL;
            world[i][wallUp] = Tileset.WALL;
        }
        for (int j = grassDown; j <= grassUp; j++) {
            world[wallLeft][j] = Tileset.WALL;
            world[wallRight][j] = Tileset.WALL;
        }

        // turn start into a floor (may not necessary
        world[start.getX()][start.getY()] = Tileset.FLOOR;
        // open the wall right next to start
        Position entrance = startNeighbor(start);
        world[entrance.getX()][entrance.getY()] = Tileset.FLOOR;

        // generate the start for next room/hallway
        Position[] threeNextStarts = roomNextStartHelper(range, r, start.getT());

        // not necessary they are wall already
        for (Position p: threeNextStarts) {
            world[p.getX()][p.getY()] = Tileset.WALL;
        }

        return threeNextStarts;
    }


    public Position[] hallway(TETile[][] world, Position start, int length) {
        Direction type = start.getT();

        switch (type) {
            case RIGHTTOWARDS:
                for (int x = start.getX() + 1; x <= start.getX() + length; x++) {
                    world[x][start.getY() + 1] = Tileset.WALL;
                    world[x][start.getY()] = Tileset.FLOOR;
                    world[x][start.getY() - 1] = Tileset.WALL;
                }

                break;
            case LEFTTOWARDS:
                for (int x = start.getX() - 1; x >= start.getX() - length; x--) {
                    world[x][start.getY() + 1] = Tileset.WALL;
                    world[x][start.getY()] = Tileset.FLOOR;
                    world[x][start.getY() - 1] = Tileset.WALL;
                }
                break;
            case UPTOWARDS:
                for (int y = start.getY() + 1; y <= start.getY() + length; y++) {
                    world[start.getX() - 1][y] = Tileset.WALL;
                    world[start.getX()][y] = Tileset.FLOOR;
                    world[start.getX() + 1][y] = Tileset.WALL;
                }
                break;
            case DOWNTOWARDS:
                for (int y = start.getY() - 1; y >= start.getY() - length; y--) {
                    world[start.getX() - 1][y] = Tileset.WALL;
                    world[start.getX()][y] = Tileset.FLOOR;
                    world[start.getX() + 1][y] = Tileset.WALL;
                }
                break;
            default: // case 0
                for (int x = start.getX() + 1; x <= start.getX() + length; x++) {
                    world[x][start.getY() + 1] = Tileset.WALL;
                    world[x][start.getY()] = Tileset.FLOOR;
                    world[x][start.getY() - 1] = Tileset.WALL;
                }
                break;

        }
        Position nextStart = newPosition(start, type, length);
        nextStart.setDirection(type);

        world[start.getX()][start.getY()] = Tileset.FLOOR;
        world[nextStart.getX()][nextStart.getY()] = Tileset.WALL;

        return new Position[]{nextStart};
    }

    public Position[] hallwayLShape(TETile[][] world, Position start,
                                    int length, int turnLength, boolean b) {
        // draw first regular hallway and get the position of wall need to fill
        Position fillWall = hallway(world, start, length)[0];

        // draw second regular hallway
        Position turnStart = findTurnStart(start, length, b);
        Position nextStart = hallway(world, turnStart, turnLength)[0];

        // fill the wall and change turnStart to floor
        world[fillWall.getX()][fillWall.getY()] = Tileset.WALL;
        world[turnStart.getX()][turnStart.getY()] = Tileset.FLOOR;

        world[nextStart.getX()][nextStart.getY()] = Tileset.WALL;

        return new Position[]{nextStart};
    }

}
