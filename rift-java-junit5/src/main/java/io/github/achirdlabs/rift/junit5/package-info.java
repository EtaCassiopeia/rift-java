/**
 * JUnit 5 support for rift-java: the {@code @RiftTest} extension starts one {@link
 * io.github.achirdlabs.rift.Rift} engine per test class, creates the {@code @RiftImposter}
 * declared imposters, and injects them (and the engine) into {@code @InjectRift}/{@code
 * @InjectImposter} fields and test method parameters.
 */
package io.github.achirdlabs.rift.junit5;
