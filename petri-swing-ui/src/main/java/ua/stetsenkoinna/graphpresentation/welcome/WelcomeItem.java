package ua.stetsenkoinna.graphpresentation.welcome;

import ua.stetsenkoinna.recentprojects.RecentProjectEntry;

/**
 * One tile in the welcome screen's card grid: either of the two pinned action tiles that always
 * lead the grid, or a card for a project the user has opened before.
 *
 * <p>A sealed interface rather than a shared base class: {@link WelcomeCardRenderer} and {@link
 * WelcomeFrame} switch on which kind of item a cell holds, and the compiler can only check that
 * switch is exhaustive if the set of kinds is closed here.
 */
public sealed interface WelcomeItem permits NewProjectItem, OpenProjectItem, RecentProjectItem {
}

/** The pinned tile that starts a brand-new project. Always first in the grid. */
record NewProjectItem() implements WelcomeItem {
}

/** The pinned tile that opens a project file the user picks. Always second in the grid. */
record OpenProjectItem() implements WelcomeItem {
}

/** A card for one previously opened project. */
record RecentProjectItem(RecentProjectEntry entry) implements WelcomeItem {
}
