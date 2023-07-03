package com.github.mongobee.changeset;

import com.github.mongobee.utils.Environment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Set of changes to be added to the DB. Many changesets are included in one changelog.
 * @author lstolowski
 * @since 27/07/2014
 * @see ChangeLog
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeSet {

  /**
   * Author of the changeset.
   * Obligatory
   * @return author
   */
  String author();

  /**
   * Unique ID of the changeset.
   * Obligatory
   * @return unique id
   */
  String id();

  /**
   * Sequence that provide correct order for changesets. Sorted alphabetically, ascending.
   * Obligatory.
   * @return ordering
   */
  String order();

  /**
   * Changeset description.
   * Obligatory
   * @return description
   */
  String description();

  /**
   * Executes the change set on every mongobee's execution, even if it has been run before.
   * Optional (default is false)
   * @return should run always?
   */
  boolean runAlways() default false;

  /**
   * Changeset group.
   * Optional
   * @return group
   */
  String group() default "";

  /**
   * Changeset will be executed only if environment match.
   * Optional
   * @return environment
   */
  String environment() default Environment.ANY;

  /**
   * Changeset marked as postponed will be skipped during global execution
   * runAlways is ignored for postponed changesets
   * Optional
   * @return postponed
   */
  boolean postponed() default false;

  /**
   * Changeset marked as not repeatable can be executed only once
   * Optional
   * @return repeatable
   */
  boolean repeatable() default true;

}
