package ua.stetsenkoinna.graphpresentation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a random, human-readable {@code snake_case} name for a document that has not been
 * named yet - every blank canvas used to be called "Untitled" alike, which told two of them
 * apart from each other not at all. Three random English words give each one a name distinct
 * enough to tell apart in the recent-projects list before the user renames it themselves.
 */
public final class RandomNetNameGenerator {

    private static final String[] ADJECTIVES = {
        "brave", "calm", "clever", "curious", "daring", "eager", "fierce", "gentle", "happy",
        "jolly", "keen", "lively", "lucky", "mighty", "noble", "plucky", "proud", "quiet",
        "quick", "rapid", "sharp", "shiny", "silent", "sleepy", "sly", "smooth", "sturdy",
        "swift", "tidy", "vivid", "witty", "zealous"
    };

    private static final String[] COLORS = {
        "amber", "azure", "coral", "crimson", "emerald", "golden", "indigo", "ivory", "jade",
        "lavender", "magenta", "maroon", "navy", "olive", "onyx", "pearl", "plum", "ruby",
        "sapphire", "scarlet", "silver", "teal", "turquoise", "violet"
    };

    private static final String[] NOUNS = {
        "falcon", "tiger", "wolf", "eagle", "panther", "otter", "heron", "fox", "hawk", "lynx",
        "raven", "bison", "cobra", "dolphin", "badger", "beetle", "cricket", "sparrow",
        "phoenix", "dragon", "comet", "glacier", "meteor", "canyon", "river", "forest",
        "mountain", "thunder", "breeze", "ember"
    };

    private RandomNetNameGenerator() {
    }

    /**
     * @return a fresh {@code adjective_color_noun} name, e.g. {@code "brave_azure_falcon"};
     *         a new one on every call, not tied to any document's identity
     */
    public static String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return ADJECTIVES[random.nextInt(ADJECTIVES.length)] + "_"
                + COLORS[random.nextInt(COLORS.length)] + "_"
                + NOUNS[random.nextInt(NOUNS.length)];
    }
}
