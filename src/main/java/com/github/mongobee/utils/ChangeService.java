package com.github.mongobee.utils;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.changeset.ChangeSet;
import com.github.mongobee.exception.MongobeeChangeSetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
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

  private static final Logger log = LoggerFactory.getLogger(ChangeService.class);

  private static final char PACKAGE_NAME_SEPARATOR = '.';
  private static final char DIRECTORY_NAME_SEPARATOR = '/';
  private static final String CLASS_FILE_EXTENSION = ".class";

  private final String changeLogsBasePackage;

  public ChangeService(String changeLogsBasePackage) {
    this.changeLogsBasePackage = changeLogsBasePackage;
  }

  public List<Class<?>> fetchChangeLogs(){
    String path = formatAsDirectory(changeLogsBasePackage);
    Set<Class<?>> changeLogs = fetchChangeLogs(path);

    return changeLogs.stream()
        .sorted(new ChangeLogComparator())
        .collect(Collectors.toList());
  }

  public List<Method> fetchChangeSets(final Class<?> type) throws MongobeeChangeSetException {
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

  private String formatAsDirectory(String path) {
    return path.replace(PACKAGE_NAME_SEPARATOR, DIRECTORY_NAME_SEPARATOR);
  }

  private Set<Class<?>> fetchChangeLogs(String resourcePath) {
    try {
      URL resource = getResource(resourcePath);
      if (resource == null) {
        log.warn("Resource {} not found.", resourcePath);
        return Set.of();
      }

      File[] files = new File(resource.toURI()).listFiles();
      if (files == null) {
        log.warn("Resource {} is not a directory", resourcePath);
        return Set.of();
      }

      return fetchChangeLogs(files, resourcePath);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private URL getResource(String name) {
    return Thread.currentThread().getContextClassLoader().getResource(name);
  }

  private Set<Class<?>> fetchChangeLogs(File[] files, String resourcePath) throws ClassNotFoundException {
    Set<Class<?>> changeLogs = new HashSet<>();

    for (File file : files) {
      if (file.isFile() && file.getName().endsWith(CLASS_FILE_EXTENSION)) {
        String className = formatAsPackage(resourcePath) + PACKAGE_NAME_SEPARATOR + getNameWithoutExtension(file);
        changeLogs.add(Class.forName(className));
      } else if (file.isDirectory()) {
        String path = resourcePath + DIRECTORY_NAME_SEPARATOR + file.getName();
        changeLogs.addAll(fetchChangeLogs(path));
      }
    }

    return changeLogs;
  }

  private String formatAsPackage(String path) {
    return path.replace(DIRECTORY_NAME_SEPARATOR, PACKAGE_NAME_SEPARATOR);
  }

  private String getNameWithoutExtension(File file) {
    String name = file.getName();
    return name.substring(0, name.length() - CLASS_FILE_EXTENSION.length());
  }

  private List<Method> filterChangeSetAnnotation(List<Method> allMethods) throws MongobeeChangeSetException {
    final Set<String> changeSetIds = new HashSet<>();
    final List<Method> changesetMethods = new ArrayList<>();
    for (final Method method : allMethods) {
      if (method.isAnnotationPresent(ChangeSet.class)) {
        String id = method.getAnnotation(ChangeSet.class).id();
        if (changeSetIds.contains(id)) {
          throw new MongobeeChangeSetException(String.format("Duplicated changeset id found: '%s'", id));
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
