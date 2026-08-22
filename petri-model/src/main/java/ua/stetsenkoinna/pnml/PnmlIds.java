package ua.stetsenkoinna.pnml;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one rule this family of tools uses for an element id: what counts as valid, and how an
 * id that does not qualify is turned into one that does.
 *
 * <p>ISO/IEC 15909-2 types every id as {@code xs:ID}, an XML {@code NCName}. This is an ASCII
 * under-approximation of that: it accepts less than the full Unicode {@code NCName} grammar,
 * but it is the same under-approximation the web application shares this PNML dialect with
 * already enforces, so an id either tool accepts, the other accepts too.
 *
 * <p>The writer runs {@link #sanitize} on a net's own display name to derive its
 * {@code <net id>} ({@link PnmlGenerator}, {@link PnmlModelGenerator}) and, through {@link
 * XmlHelper#sanitizeIds}, on whatever id a hand-built {@code PetriP}/{@code PetriT} carries
 * before writing it out. The reader runs the same {@link XmlHelper#sanitizeIds} over a whole
 * document before reading a single id out of it, so that nothing downstream ever has to tell a
 * valid id from a sanitized one.
 */
final class PnmlIds {

    private static final Pattern NCNAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.\\-]*$");

    private PnmlIds() {
        // Utility class
    }

    /** @return whether {@code id} is already a valid NCName by this project's rule */
    static boolean isValid(String id) {
        return id != null && NCNAME.matcher(id).matches();
    }

    /**
     * Turns any string into a valid NCName: every character outside {@code [A-Za-z0-9_.-]}
     * becomes a hyphen, a result that still could not start an NCName gets an {@code "n-"}
     * prefix, runs of hyphens collapse to one and the ends are trimmed. A string with nothing
     * usable in it becomes {@code "n"}.
     *
     * <p>Idempotent on a string that is already valid: every character is already in the
     * allowed set and already starts correctly, so nothing here changes it. That is what lets
     * a writer call this unconditionally instead of checking {@link #isValid} first.
     *
     * @param raw the string to sanitize, {@code null} treated as empty
     * @return a valid NCName, never null or empty
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "n";
        }
        StringBuilder replaced = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            replaced.append(isBodyChar(c) ? c : '-');
        }
        String prefixed = isNameStartChar(replaced.charAt(0)) ? replaced.toString() : "n-" + replaced;
        String collapsed = prefixed.replaceAll("-+", "-").replaceAll("^-+|-+$", "");
        return collapsed.isEmpty() ? "n" : collapsed;
    }

    /**
     * @return {@code candidate} if it is not already in {@code used}, otherwise {@code
     *         candidate} with the first {@code "-2"}, {@code "-3"}, … suffix that is not.
     *         Either way, the returned id is added to {@code used} as a side effect, so two
     *         calls in a row never hand out the same id.
     */
    static String makeUnique(String candidate, Set<String> used) {
        if (used.add(candidate)) {
            return candidate;
        }
        for (int suffix = 2; ; suffix++) {
            String attempt = candidate + "-" + suffix;
            if (used.add(attempt)) {
                return attempt;
            }
        }
    }

    private static boolean isNameStartChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isBodyChar(char c) {
        return isNameStartChar(c) || (c >= '0' && c <= '9') || c == '.' || c == '-';
    }
}
