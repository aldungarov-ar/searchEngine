package searchengine;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        String easy = "word";
        char character = 'w';

        Character.UnicodeScript script = Character.UnicodeScript.of(character);
        System.out.println("Character: " + character + ", Script: " + script);
    }
}
