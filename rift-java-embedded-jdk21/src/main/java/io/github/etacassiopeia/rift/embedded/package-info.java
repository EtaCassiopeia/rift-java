/**
 * In-process Rift engine on JDK 21 using the preview Foreign Function &amp; Memory API. Built from
 * the same sources as {@code rift-java-embedded} with preview enabled; the renamed FFM methods are
 * resolved reflectively at class load. The binding and preview flags arrive with the embedded
 * issues; this module currently establishes the artifact and its JDK 21 build.
 */
package io.github.etacassiopeia.rift.embedded;
