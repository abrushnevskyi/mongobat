package com.github.mongobat.utils;

import com.github.mongobat.changelog.repeatable.RepeatableChangeLog;
import com.github.mongobat.changeset.ChangeEntry;
import com.github.mongobat.exception.MongoBatChangeSetException;
import com.github.mongobat.test.changelogs.AnotherMongoBatTestResource;
import com.github.mongobat.test.changelogs.MongoBatTestResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeServiceTest {

  @Test
  public void shouldFindChangeLogClasses(){
    // given
    String scanPackage = MongoBatTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    // when
    List<Class<?>> foundClasses = service.fetchChangeLogs();
    // then
    assertTrue(foundClasses != null && foundClasses.size() > 0);
  }
  
  @Test
  public void shouldFindChangeSetMethods() throws MongoBatChangeSetException {
    // given
    String scanPackage = MongoBatTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(MongoBatTestResource.class);
    
    // then
    assertTrue(foundMethods != null && foundMethods.size() == 4);
  }

  @Test
  public void shouldFindAnotherChangeSetMethods() throws MongoBatChangeSetException {
    // given
    String scanPackage = MongoBatTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(AnotherMongoBatTestResource.class);

    // then
    assertTrue(foundMethods != null && foundMethods.size() == 4);
  }


  @Test
  public void shouldFindIsRunAlwaysMethod() throws MongoBatChangeSetException {
    // given
    String scanPackage = MongoBatTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(AnotherMongoBatTestResource.class);
    // then
    for (Method foundMethod : foundMethods) {
      if (foundMethod.getName().equals("testChangeSetWithAlways")){
        assertTrue(service.isRunAlwaysChangeSet(foundMethod));
      } else {
        assertFalse(service.isRunAlwaysChangeSet(foundMethod));
      }
    }
  }

  @Test
  public void shouldCreateEntry() throws MongoBatChangeSetException {
    
    // given
    String scanPackage = MongoBatTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    List<Method> foundMethods = service.fetchChangeSets(MongoBatTestResource.class);

    for (Method foundMethod : foundMethods) {
    
      // when
      ChangeEntry entry = service.createChangeEntry(foundMethod);
      
      // then
      Assertions.assertEquals("testuser", entry.getAuthor());
      Assertions.assertEquals(MongoBatTestResource.class.getName(), entry.getChangeLogClass());
      Assertions.assertNotNull(entry.getTimestamp());
      Assertions.assertNotNull(entry.getChangeId());
      Assertions.assertNotNull(entry.getChangeSetMethodName());
    }
  }

  @Test
  public void shouldFailOnDuplicatedChangeSets() {
    String scanPackage = ChangeLogWithDuplicate.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    assertThrows(MongoBatChangeSetException.class, () -> service.fetchChangeSets(ChangeLogWithDuplicate.class));
  }

  @Test
  public void shouldFindAllChangesLogsInNestedPackages() {
    String packageName = RepeatableChangeLog.class.getPackage().getName();
    String parentPackageName = packageName.substring(0, packageName.lastIndexOf("."));

    ChangeService service = new ChangeService(parentPackageName);
    List<Class<?>> changeLogs = service.fetchChangeLogs();

    assertEquals(4, changeLogs.size());
  }

}
