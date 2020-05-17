package byow.Core;

/** This is the main entry point for the program. This class simply parses
 *  the command line inputs, and lets the byow.Core.Engine class take over
 *  in either keyboard or input string mode.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length > 1) {
            System.out.println("Can only have one argument - the input string");
            System.exit(0);
        } else if (args.length == 1) {
            Engine engine = new Engine();
//            engine.interactWithInputString(args[0]);
//            engine.interactWithInputString("N1846195S");
//            engine.interactWithInputString("N1846195S:Q");
//            engine.interactWithInputString("L");
//            engine.interactWithInputString("L:Q");
//            engine.interactWithInputString("N1846195Swwwd");
//            engine.interactWithInputString("N1846195Swwwd:Q");
//            engine.interactWithInputString("Lddd");
//            engine.interactWithInputString("Lddd:Q");
            //
//            engine.interactWithInputString("N999SDDDWWWDDD");
            //
//            engine.interactWithInputString("N999SDDD:Q");
//            engine.interactWithInputString("LWWWDDD");
//            //
//            engine.interactWithInputString("N999SDDD:Q");
//            engine.interactWithInputString("LWWW:Q");
//            engine.interactWithInputString("LDDD:Q");
//            //
//            engine.interactWithInputString("N999SDDD:Q");
//            engine.interactWithInputString("L:Q");
//            engine.interactWithInputString("L:Q");
//            engine.interactWithInputString("LWWWDDD");

//            engine.interactWithInputString("n7193300625454684331saaawasdaawdwsd");
//            engine.interactWithInputString("n7193300625454684331saaawasdaawd");
//            engine.interactWithInputString("n7193300625454684331saaawasdaawd:q");
//            engine.interactWithInputString("lwsd");

            System.out.println(engine.toString());

        } else {
            Engine engine = new Engine();
            engine.interactWithKeyboard();
        }
    }
}
