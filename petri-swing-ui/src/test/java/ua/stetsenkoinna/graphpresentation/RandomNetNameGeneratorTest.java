package ua.stetsenkoinna.graphpresentation;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class RandomNetNameGeneratorTest {

    @Test
    public void generatesThreeSnakeCaseWords() {
        String name = RandomNetNameGenerator.generate();
        assertTrue("expected snake_case of three lowercase words, got: " + name,
                name.matches("[a-z]+_[a-z]+_[a-z]+"));
    }

    @Test
    public void variesAcrossCalls() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            names.add(RandomNetNameGenerator.generate());
        }
        // Not asserting all 50 are distinct - a collision is possible by chance - just that
        // this is not always returning the same fixed name.
        assertTrue("expected more than one distinct name across 50 calls", names.size() > 1);
    }
}
