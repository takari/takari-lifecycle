package io.takari.resources.filtering;

/**
 * Enumerates the actions that can be taken when {@link FilterResourcesProcessor} hits a reference to a missing property.
 *
 * @since 1.13.4
 */
public enum MissingPropertyAction {
  /**
   * Filtering will result in empty string (this was the default behaviour of Takari Lifecycle).
   */
  empty,

  /**
   * Filtering will leave the expression untouched (mimics maven-resources-plugin).
   */
  leave,

  /**
   * Error will be reported failing the build.
   */
  fail;

  /**
   * The default action, to not have it scattered and different across multiple Mojos.
   */
  public static final MissingPropertyAction DEFAULT = MissingPropertyAction.empty;
}
