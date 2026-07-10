package io.github.etacassiopeia.rift.spring;

/** When configured imposters are reset to their declared specification during a test class run. */
public enum Reset {

    /** Reset before every test method (restore spec stubs, clear recorded requests and scenarios). */
    PER_TEST,

    /** Reset once before the first test method of the class. */
    PER_CLASS,

    /** Never reset automatically — the test manages imposter state itself. */
    NONE
}
