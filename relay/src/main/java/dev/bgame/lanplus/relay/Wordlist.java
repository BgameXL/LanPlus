package dev.bgame.lanplus.relay;

import java.util.concurrent.ThreadLocalRandom;

final class Wordlist {

    private Wordlist() {}

    private static final String[] WORDS = {
            "amber", "brisk", "calm", "dawn", "ember", "fern", "glow", "hazel", "iris", "jade",
            "koi", "lush", "moss", "nova", "opal", "pine", "quartz", "reef", "sage", "tide",
            "umber", "vale", "willow", "zephyr", "cedar", "drift", "flint", "grove", "haven", "lark"
    };

    static String randomDomain(String base) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return WORDS[r.nextInt(WORDS.length)] + "-" + WORDS[r.nextInt(WORDS.length)] + "." + base;
    }
}
