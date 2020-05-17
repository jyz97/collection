package byow.Core;

public class Position {
    private int x;
    private int y;
    private Direction type;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Position(int x, int y, Direction t) {
        this.x = x;
        this.y = y;
        this.type = t;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public Direction getT() {
        return this.type;
    }

    public void setDirection(Direction d) {
        this.type = d;
    }

    public void incrementX(int n) {
        this.x += n;
    }

    public void incrementY(int n) {
        this.y += n;
    }


    // return the square of distance between Position a and b
    public static int distance(Position a, Position b) {
        return (int) (Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }


    // set the coordinate to (a, b, t)
    public void set(int a, int b, Direction t) {
        this.x = a;
        this.y = b;
        this.type = t;
    }
}
