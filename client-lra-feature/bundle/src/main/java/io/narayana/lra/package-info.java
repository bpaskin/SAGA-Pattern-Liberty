/**
 * Marks all classes in this package as vetoed from CDI bean discovery.
 *
 * The {@code io.narayana.lra.*} packages contain Narayana's internal
 * LRA client implementation classes. They are embedded inside the
 * {@code usr:clientLRA-2.0} Liberty feature bundle and must not be
 * scanned by Weld/CDI during application deployment. Without this
 * annotation Liberty's injection engine attempts to process
 * {@code NarayanaLRAClient} and fails with
 * {@code NoClassDefFoundError: LRAStatus} because the LRA annotation
 * classes are not visible through the application classloader.
 */
@jakarta.enterprise.inject.Vetoed
package io.narayana.lra;
