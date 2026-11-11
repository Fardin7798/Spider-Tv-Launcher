package com.spidertv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Saved order and hidden-list helpers behind Manage apps. */
public class SpiderAppOrderTest {

    private static List<String> list(String... s) {
        return new ArrayList<>(Arrays.asList(s));
    }

    @Test
    public void splitTrimsDropsEmptiesAndRepeats() {
        assertEquals(list("a.b", "c.d"), SpiderAppOrder.split(" a.b ,, c.d,a.b, "));
        assertTrue(SpiderAppOrder.split("").isEmpty());
        assertTrue(SpiderAppOrder.split(null).isEmpty());
    }

    @Test
    public void joinRoundTrips() {
        final List<String> pkgs = list("x.one", "x.two", "x.three");
        assertEquals("x.one,x.two,x.three", SpiderAppOrder.join(pkgs));
        assertEquals(pkgs, SpiderAppOrder.split(SpiderAppOrder.join(pkgs)));
        assertEquals("", SpiderAppOrder.join(Collections.<String>emptyList()));
    }

    @Test
    public void arrangePutsSavedFirstAndKeepsTheRestStable() {
        final List<String> row = list("a", "b", "c", "d", "e");
        SpiderAppOrder.arrange(row, list("d", "gone", "b"), s -> s);
        assertEquals(list("d", "b", "a", "c", "e"), row);
    }

    @Test
    public void arrangeWithNoSavedOrderChangesNothing() {
        final List<String> row = list("c", "a", "b");
        SpiderAppOrder.arrange(row, Collections.<String>emptyList(), s -> s);
        assertEquals(list("c", "a", "b"), row);
    }

    @Test
    public void moveStepsAndClampsAtTheEnds() {
        final List<String> row = list("a", "b", "c");
        assertEquals(list("b", "a", "c"), SpiderAppOrder.move(row, "a", 1));
        assertEquals(list("a", "c", "b"), SpiderAppOrder.move(row, "c", -1));
        assertEquals(list("a", "b", "c"), SpiderAppOrder.move(row, "a", -1));
        assertEquals(list("a", "b", "c"), SpiderAppOrder.move(row, "c", 5));
        assertEquals(list("a", "b", "c"), SpiderAppOrder.move(row, "zz", 1));
        assertEquals(list("a", "b", "c"), row);
    }

    @Test
    public void keepRestAppendsOffScreenSavedEntries() {
        assertEquals(list("b", "a", "phone.app"),
                SpiderAppOrder.keepRest(list("b", "a"), list("a", "phone.app", "b")));
    }

    @Test
    public void setHiddenAddsOnceAndRemoves() {
        final List<String> one = SpiderAppOrder.setHidden(list("a"), "b", true);
        assertEquals(list("a", "b"), one);
        assertEquals(list("a", "b"), SpiderAppOrder.setHidden(one, "b", true));
        assertEquals(list("a"), SpiderAppOrder.setHidden(one, "b", false));
        assertEquals(list("a"), SpiderAppOrder.setHidden(list("a"), "zz", false));
    }
}
