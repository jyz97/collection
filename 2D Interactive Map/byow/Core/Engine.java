package byow.Core;

import byow.TileEngine.TERenderer;
import byow.TileEngine.TETile;
import byow.TileEngine.Tileset;
import edu.princeton.cs.introcs.StdDraw;

import java.awt.Font;
import java.awt.Color;
import java.io.*;


import java.util.Date;
public class Engine {
    private TERenderer ter = new TERenderer();
    /* Feel free to change the width and height. */
    public static final int WIDTH = 80;
    public static final int HEIGHT = 30;
    private static final int DELAY = 600;

    private static DrawElement de = new DrawElement();
    private int midWidth = WIDTH  /  2;
    private int midHeight = HEIGHT  / 2;
    private int highHeight = (5 * HEIGHT) / 6;

    private boolean exit = false;
    private TETile[][] world = new TETile[WIDTH][HEIGHT];
    private long seed;
    private boolean showMenu = true;
    private boolean haveNoSeed = false;
    private boolean needToQuit = false;
    private DisplayType dt = DisplayType.NOTHING;
    private String message = "";
    private String left = "";
    /**
     * Method used for exploring a fresh world. This method should handle all inputs,
     * including inputs from the main menu.
     */
    public void interactWithKeyboard() {
        char c;
        StringBuilder sb = new StringBuilder();
        Position now = new Position(0, 0);  // location of Avatar, default should never be used
        String piwq = ""; // previousInputWithoutQuitLoad
        Object[] nowPreviInput = new Object[]{now, piwq};

        int mouseX = (int) StdDraw.mouseX();
        int mouseY = (int) StdDraw.mouseY();
        int oldMouseX = mouseX;
        int oldMouseY = mouseY;
        TETile current;

        // initialize the tile rendering engine with a window of size WIDTH x HEIGHT
        ter.initialize(WIDTH, HEIGHT + 4);  // offset for display on top

        // initialize tiles
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                world[x][y] = Tileset.NOTHING;
            }
        }

        // interactive part
        while (true) {
            if (showMenu) {  // show start menu when no key has been pressed
                drawOnCanvas(DisplayType.STARTMENU, "");
                StdDraw.show();
            }

            dt = DisplayType.NOTHING;
            message = "";

            if (StdDraw.hasNextKeyTyped()) {  // keyboard
                showMenu = false;
                c = StdDraw.nextKeyTyped();
                c = Character.toUpperCase(c); //change it to upper case
                sb.append(c);
                nowPreviInput = respondToOneChar(c, nowPreviInput, sb);
                now = (Position) nowPreviInput[0];
                piwq = (String) nowPreviInput[1];
                ter.renderFrameWithoutClearShow(world); // draw world
                // draw upright info(which tile are you looking at) to screen
                // or seed number at the center
                drawOnCanvas(dt, message);
            }

            if (exit) {  // show info after quit
                StdDraw.clear(Color.BLACK);
                drawOnCanvas(dt, message);
                StdDraw.show();
                break;
            }

            //mouse move
            mouseX = (int) StdDraw.mouseX();
            mouseY = (int) StdDraw.mouseY();
            if ((oldMouseX != mouseX) || (oldMouseY != mouseY)) {
                try { // if (x, y) is on the world map
                    current = world[mouseX][mouseY];
                    String cd = current.description();
                    if (!cd.equals("nothing")) {
                        upLeftPaintBlack();
                        String mouseMessage = "This tile is a " + current.description() + ".";
                        drawOnCanvas(DisplayType.MOUSEHOVER, mouseMessage);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    continue;
                }
                oldMouseX = mouseX;  // update mouse place
                oldMouseY = mouseY;
                ter.renderFrameWithoutClearShow(world); // draw world
            }

            upRightPaintBlack();
            drawOnCanvas(dt, message);
            drawOnCanvas(DisplayType.TIME, ""); // keep time updated
            StdDraw.show(); // show everything drew on canvas now
        }
    }




    /**
     * Method used for autograding and testing your code. The input string will be a series
     * of characters (for example, "n123sswwdasdassadwas", "n123sss:q", "lwww". The engine should
     * behave exactly as if the user typed these characters into the engine using
     * interactWithKeyboard.
     *
     * Recall that strings ending in ":q" should cause the game to quite save. For example,
     * if we do interactWithInputString("n123sss:q"), we expect the game to run the first
     * 7 commands (n123sss) and then quit and save. If we then do
     * interactWithInputString("l"), we should be back in the exact same state.
     *
     * In other words, both of these calls:
     *   - interactWithInputString("n123sss:q")
     *   - interactWithInputString("lww")
     *
     * should yield the exact same world state as:
     *   - interactWithInputString("n123sssww")
     *
     * @param input the input string to feed to your program
     * @return the 2D TETile[][] representing the state of the world
     */
    public TETile[][] interactWithInputString(String input) {
        // passed in as an argument, and return a 2D tile representation of the
        // world that would have been drawn if the same inputs had been given
        // to interactWithKeyboard().
        //
        // See proj3.byow.InputDemo for a demo of how you can make a nice clean interface
        // that works for many different input types.
        System.out.println(input);

        // initialize the tile rendering engine with a window of size WIDTH x HEIGHT
        //ter.initialize(WIDTH, HEIGHT);  // commit it out for gradeScope

        // initialize tiles
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                world[x][y] = Tileset.NOTHING;
            }
        }

        // draws the world to the screen
        createWorld(input);
        //ter.renderFrame(world);  //commit it out for gradeScope
        return world;
    }

    /**
     * helper method that slice the input and call them character by character
     */
    private Position createWorld(String input) {
        char c;
        StringBuilder sb = new StringBuilder();
        Position now = new Position(0, 0);  // default should never be used
        String piwq = ""; // previousInputWithoutQuitLoad

        Object[] nowPreviInput = new Object[]{now, piwq};

        for (int i = 0; i < input.length(); i += 1) {
            c = input.charAt(i);
            c = Character.toUpperCase(c); //change it to upper case
            sb.append(c);
            nowPreviInput = respondToOneChar(c, nowPreviInput, sb);
            now = (Position) nowPreviInput[0];
            if (exit) {
                break;
            }
        }
        return now;
    }


    // starting from here
    // helper methods that respond to one character

    /**
     *
     * @return boolean array, haveNoSeed true when N is called, needToQuit true when : is called
     */
    private Object[] respondToOneChar(char c, Object[] nowPreviInput, StringBuilder inputSB) {
        Position now = (Position) nowPreviInput[0];
        String previousInputWithoutQuitLoad = (String) nowPreviInput[1];
        String input = inputSB.toString();

        switch (c) {
            case 'N': // new world
                haveNoSeed = true;
                dt = DisplayType.ENTERPROMP;  // prompt to enter seed
                message = "Please enter your seed:  ";
                break;
            case 'S':
                if (haveNoSeed) { // seed end
                    String seedString = input.substring(1, input.length() - 1);
                    seed = Long.parseLong(seedString);
                    now = newWorld(seed);
                    haveNoSeed = false;

                } else { // move down
                    now = moveOneSpot(c, now);

                }
                break;
            case 'A': // move left
            case 'D': // move right
            case 'W': // move up
                now = moveOneSpot(c, now);
                break;
            case ':': // prepare to quit
                needToQuit = true;
                break;
            case 'Q': // quit
                if (needToQuit) { // call save method and quit
//                String lastChar = inputSB.toString();
//                char last = lastChar.charAt(inputSB.length() - 1);
//                if (last == ':') {
                    save(input, previousInputWithoutQuitLoad);
                    needToQuit = false; // don't need at all, just to complete my logic
                    //System.exit(0);  // comment out for passing gradeScope
                    dt = DisplayType.QUIT;
                    message = "You quit and saved the game";
                    exit = true; // for pass gradeScope
                }
                break;
            case 'R':  // add on menu to choose load and replay
                nowPreviInput = replay();
                now = (Position) nowPreviInput[0];
                previousInputWithoutQuitLoad = (String) nowPreviInput[1];
//                dt = DisplayType.REPLAY;
//                message = "End of Replay. User can start to play again";
                dt = DisplayType.NOTHING;
                message = "";
                break;
            case 'L': // call load method
                nowPreviInput = loadSavedWorld();
                now = (Position) nowPreviInput[0];
                previousInputWithoutQuitLoad = (String) nowPreviInput[1];
                break;
            default:
                if (Character.isDigit(c)) {   // print seed number
                    dt = DisplayType.SEED;
                    message = inputSB.toString().substring(1);
                }
                break;
        }
        return new Object[]{now, previousInputWithoutQuitLoad};
    }


    private Position moveOneSpot(char c, Position now) {
        Direction d;
        switch (c) {
            case 'D':
                d = Direction.RIGHTTOWARDS;
                break;
            case 'A':
                d = Direction.LEFTTOWARDS;
                break;
            case 'W':
                d = Direction.UPTOWARDS;
                break;
            case 'S':
                d = Direction.DOWNTOWARDS;
                break;
            default:
                d = Direction.RIGHTTOWARDS;
                break;
        }
        Position after = de.neighbor(now, d);
        TETile afterItem = world[after.getX()][after.getY()];

        if (!afterItem.equals(Tileset.WALL)) {   // move within the wall
            world[now.getX()][now.getY()] = Tileset.FLOOR;
            world[after.getX()][after.getY()] = Tileset.AVATAR;
            if (afterItem.equals(Tileset.LOCKED_DOOR)) {
                return null;
                // show you escaped the room
            }
        } else {
            after = now; // don't move
        }
        return after;
    }


    private Position newWorld(long sd) {   // create a new file
        String fileName = "./save_data.txt";
        File f = new File(fileName);
        try {
            if (f.exists()) {
                f.delete();
            }

            f.createNewFile();   // may need to add condition when file already exist.
        } catch (IOException e) {
            System.out.println("Error reading file '" + fileName + "'");
            System.exit(0);
        }

        System.out.println(sd); // delete later
        Position now = DrawASpreadingWorld.methodDrawASpreadingWorld(sd, WIDTH, HEIGHT, world);
        return now;
    }

    /**
     * @source www.caveofprogramming.com
     */
    private void save(String input, String previousInputWithoutQuitLoad) {
        // The name of the file to open.
        String fileName = "./save_data.txt";
        String previousPlusNow = previousInputWithoutQuitLoad + input;

        try {
            // Assume default encoding.
            FileWriter fileWriter = new FileWriter(fileName);

            // Always wrap FileWriter in BufferedWriter.
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            // Note that write() does not automatically append a newline character.
            bufferedWriter.write(previousPlusNow);
            // Always close files.
            bufferedWriter.close();
        } catch (IOException ex) {
            System.out.println("Error writing to file '" + fileName + "'");
            // Or we could just do this:
            // ex.printStackTrace();
        }
    }


    /**
     * @source www.caveofprogramming.com
     */
    private Object[] loadSavedWorld() {
        String withoutQuitLoad = loadPreparation();
        Position now = createWorld(withoutQuitLoad);
        return new Object[]{now, withoutQuitLoad};
    }

    private Object[] replay() {
        String withoutQuitLoad = loadPreparation();
        String[] seedPlusSteps = withoutQuitLoad.substring(1).split("S", 2);
        seed = Long.parseLong(seedPlusSteps[0]);
        left = seedPlusSteps[1];
        Position now = newWorld(seed);
        char c;
        // draw replay mode on
        drawOnCanvas(DisplayType.REPLAY, "Replay from last new world created.");
        System.out.println(left.length());
        for (int i = 0; i < left.length(); i += 1) {
            c = left.charAt(i);
            now = moveOneSpot(c, now);
            ter.renderFrameWithoutClearShow(world);
            StdDraw.show();
            StdDraw.pause(DELAY);
        }
        return new Object[]{now, withoutQuitLoad};
    }

    private String loadPreparation() {
        // The name of the file to open.
        String fileName = "./save_data.txt";
//        String previousInput = "";
        String withoutQuitLoad = "";
        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader = new FileReader(fileName);

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String previousInput = bufferedReader.readLine();

            if ((previousInput.length() > 0) && previousInput.contains("L")) {
                for (String s: previousInput.split("L")) {
                    withoutQuitLoad = withoutQuitLoad + s;
                }
            } else {
                withoutQuitLoad = previousInput;
            }
            withoutQuitLoad = withoutQuitLoad.split(":Q", 2)[0];

            System.out.println(withoutQuitLoad); // not necessary
            // Always close files.
            bufferedReader.close();
        } catch (FileNotFoundException ex) {

            System.out.println("Unable to open file '" + fileName + "'");
        } catch (IOException ex) {
            System.out.println("Error reading file '" + fileName + "'");
        }

        return withoutQuitLoad;
    }




    // starting from here
    // helper method for drawing on canvas
    // just render, not show

    /**
     * clear the canvas and draw the message and world according to the display type
     */
    private void drawOnCanvas(DisplayType dT, String s) {
        StdDraw.setPenColor(Color.WHITE);
        switch (dT) {
            case STARTMENU:
                startMenu();
                break;
            case TIME:
                date();
                break;
            case ENTERPROMP:
                enterOrSeedOrQuit(s);  // not my intension, hate style checker
                break;
            case SEED:
                StdDraw.clear(Color.BLACK);
                enterOrSeedOrQuit(s);
                break;
            case QUIT:
                enterOrSeedOrQuit(s);
                break;
            case NOTHING: // clear the left up part by showing empty string
                //upLeftPaintBlack();
                break;
            case REPLAY:
                upperLeft(s);
                break;
            case MOUSEHOVER:
                upperLeft(s);
                break;
            default:
                break;
            // more implementation later if have time
        }
    }

    private void startMenu() {
        Font font = new Font("Arial", Font.BOLD, 30);
        StdDraw.setFont(font);
        StdDraw.text(midWidth, (5 * HEIGHT) / 6, "CS61B: THE GAME");
        StdDraw.text(midWidth, midHeight, "NEW GAME (N)");
        StdDraw.text(midWidth, midHeight - 2, "LOAD GAME (L)");
        StdDraw.text(midWidth, midHeight - 4, "QUIT GAME (:Q)");
        StdDraw.text(midWidth, midHeight - 6, "REPLAY WHILE LOAD (R)");
    }

    public void date() {
        Date date = new Date();
        String da = date.toString();
        Font font = new Font("Arial", Font.ITALIC, 20);
        StdDraw.setFont(font);
        StdDraw.text(WIDTH - 15, HEIGHT + 2, da);
    }

    private void enterOrSeedOrQuit(String s) {
        Font font = new Font("Arial", Font.BOLD, 30);
        StdDraw.setFont(font);
        StdDraw.text(midWidth, midHeight, s);
        System.out.println("draw enter / quit prompt");
    }

    private void upLeftPaintBlack() {
        for (int i = 0; i < midWidth; i += 1) {
            Tileset.NOTHING.draw(i, HEIGHT + 2);
            Tileset.NOTHING.draw(i, HEIGHT + 1);

        }
    }
    private void upRightPaintBlack() {
        for (int i = midWidth; i < WIDTH; i += 1) {
            Tileset.NOTHING.draw(i, HEIGHT + 2);
            Tileset.NOTHING.draw(i, HEIGHT + 1);

        }
    }

    private void upperLeft(String s) {
        Font font = new Font("Arial", Font.BOLD, 20);
        StdDraw.setFont(font);
        StdDraw.text(15, HEIGHT + 2, s);
        System.out.println("draw mouse state");
    }

    private void upperRight(String s) {
        Font font = new Font("Arial", Font.BOLD, 20);
        StdDraw.setFont(font);
        StdDraw.text(15, HEIGHT + 2, s);
        System.out.println("draw mouse state");
    }

}
