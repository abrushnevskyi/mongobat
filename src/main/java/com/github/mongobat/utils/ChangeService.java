package com.github.mongobat.utils;

import com.github.mongobat.changeset.ChangeEntry;
import com.github.mongobat.changeset.ChangeLog;
import com.github.mongobat.changeset.ChangeSet;
import com.github.mongobat.exception.MongoBatChangeSetException;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * Utilities to deal with reflections and annotations
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeService {

  private final String changeLogsBasePackage;

  public ChangeService(String changeLogsBasePackage) {
    this.changeLogsBasePackage = changeLogsBasePackage;
  }

  public List<Class<?>> fetchChangeLogs(){
    Reflections reflections = new Reflections(changeLogsBasePackage);
    Set<Class<?>> changeLogs = reflections.getTypesAnnotatedWith(ChangeLog.class);

    return changeLogs.stream()
        .sorted(new ChangeLogComparator())
        .collect(Collectors.toList());
  }

  public List<Method> fetchChangeSets(final Class<?> type) throws MongoBatChangeSetException {
    final List<Method> changeSets = filterChangeSetAnnotation(asList(type.getDeclaredMethods()));

    changeSets.sort(new ChangeSetComparator());

    return changeSets;
  }

  public boolean isRunAlwaysChangeSet(Method changesetMethod){
    if (changesetMethod.isAnnotationPresent(ChangeSet.class)){
      ChangeSet annotation = changesetMethod.getAnnotation(ChangeSet.class);
      return annotation.runAlways();
    } else {
      return false;
    }
  }

  public ChangeEntry createChangeEntry(Method changesetMethod){
    if (changesetMethod.isAnnotationPresent(ChangeSet.class)){
      ChangeSet annotation = changesetMethod.getAnnotation(ChangeSet.class);
  
      return new ChangeEntry(
          annotation.id(),
          annotation.author(),
          new Date(),
          changesetMethod.getDeclaringClass().getName(),
          changesetMethod.getName(),
          annotation.description(),
          annotation.group(),
          annotation.environment(),
          annotation.postponed(),
          annotation.repeatable());
    } else {
      return null;
    }
  }

  private List<Method> filterChangeSetAnnotation(List<Method> allMethods) throws MongoBatChangeSetException {
    final Set<String> changeSetIds = new HashSet<>();
    final List<Method> changesetMethods = new ArrayList<>();
    for (final Method method : allMethods) {
      if (method.isAnnotationPresent(ChangeSet.class)) {
        String id = method.getAnnotation(ChangeSet.class).id();
        if (changeSetIds.contains(id)) {
          throw new MongoBatChangeSetException(String.format("Duplicated changeset id found: '%s'", id));
        }
        changeSetIds.add(id);
        changesetMethods.add(method);
      }
    }
    return changesetMethods;
  }

  public boolean isPostponed(Method method) {
    return Optional.ofNullable(method.getAnnotation(ChangeSet.class))
        .map(ChangeSet::postponed)
        .orElse(false);
  }

  public boolean isRepeatable(Method method) {
    return Optional.ofNullable(method.getAnnotation(ChangeSet.class))
        .map(ChangeSet::repeatable)
        .orElse(false);
  }

}
