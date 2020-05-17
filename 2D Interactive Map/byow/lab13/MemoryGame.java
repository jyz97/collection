package byow.lab13;

import edu.princeton.cs.introcs.StdDraw;

import java.awt.Color;
import java.awt.Font;
import java.util.Random;
import byow.Core.RandomUtils;

public class MemoryGame {
    private int width;
    private int height;
    private int round;
    private static Random rand;
    private boolean gameOver;
    private boolean playerTurn;
    private static final char[] CHARACTERS = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final String[] ENCOURAGEMENT = {"You can do this!", "I believe in you!",
            "You got this!", "You're a star!", "Go Bears!",
            "Too easy for you!", "Wow, so impressive!"};

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please enter a seed");
            return;
        }

        int seed = Integer.parseInt(args[0]);
        rand = new Random(seed);
        MemoryGame game = new MemoryGame(40, 40);
        game.startGame();
    }

    public MemoryGame(int width, int height) {
        /* Sets up StdDraw so that it has a width by height grid of 16 by 16 squares as its canvas
         * Also sets up the scale so the top left is (0,0) and the bottom right is (width, height)
         */
        this.width = width;
        this.height = height;
        StdDraw.setCanvasSize(this.width * 16, this.height * 16);
        Font font = new Font("Monaco", Font.BOLD, 30);
        StdDraw.setFont(font);
        StdDraw.setXscale(0, this.width);
        StdDraw.setYscale(0, this.height);
        StdDraw.clear(Color.BLACK);
        StdDraw.enableDoubleBuffering();
        StdDraw.setPenColor(Color.white);

        //TODO: Initialize random number generator
    }

    public String generateRandomString(int n) {
        //TODO: Generate random string of letters of length n
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < n; i += 1) {
            c = CHARACTERS[RandomUtils.uniform(rand, 26)];
            sb.append(c);
        }
        return sb.toString();
    }

    public void drawFrame(String s) {
        //TODO: Take the string and display it in the center of the screen
        //TODO: If game is not over, display relevant game information at the top of the screen
        StdDraw.clear(Color.black);
        Font font = new Font("Arial", Font.BOLD, 30);
        StdDraw.setFont(font);
        StdDraw.text((double)width / 2, (double)height / 2, s);
        StdDraw.show();
    }

    public void flashSequence(String letters) {
        //TODO: Display each character in letters, making sure to blank the screen between letters
        for (int i = 0; i < letters.length(); i += 1) {
            char c = letters.charAt(i);
            drawFrame(String.valueOf(c));
            int delay = 500; // 0.5 s * 1000000 milisecond / s

//            try {
//                Thread.sleep(delay);
//            } catch (Exception e) {
//                return;
//            }
            StdDraw.pause(delay);
        }
    }

    public String solicitNCharsInput(int n) {
        //TODO: Read n letters of player input
        char c;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            if (StdDraw.hasNextKeyTyped()) {
                c = StdDraw.nextKeyTyped();
                sb.append(c);
                drawFrame(sb.toString());
                n -= 1;
            }
        }
        return sb.toString();
    }

    public void startGame() {
        //TODO: Set any relevant variables before the game starts
        int round = 1;
        boolean correct = true;
        String expect;
        String actual;
        //TODO: Establish Engine loop

        while (correct) {
            drawFrame("round" + round);
            int delay = 500; // 0.5 s * 1000 milisecond / s
            StdDraw.pause(delay);
            expect = generateRandomString(round);
            flashSequence(expect);
            actual = solicitNCharsInput(round);
            if (expect.equals(actual)) {
                round += 1;
            } else {
                correct = false;
            }
        }

    }

}
