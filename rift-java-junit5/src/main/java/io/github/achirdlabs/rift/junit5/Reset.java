package io.github.achirdlabs.rift.junit5;

/** When configured imposters have their recorded requests, scenario state, and proxy responses cleared during a test class run. */
public enum Reset {

    /** Reset before every test method (clear recorded requests, scenario state, and proxy responses). */
    PER_TEST,

    /** Reset once before the first test method of the class. */
    PER_CLASS,

    /** Never reset automatically — the test manages imposter state itself. */
    NONE
}
