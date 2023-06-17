package com.github.mongobee.utils;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.exception.MongobeeChangeSetException;
import com.github.mongobee.test.changelogs.AnotherMongobeeTestResource;
import com.github.mongobee.test.changelogs.MongobeeTestResource;
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
    String scanPackage = MongobeeTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    // when
    List<Class<?>> foundClasses = service.fetchChangeLogs();
    // then
    assertTrue(foundClasses != null && foundClasses.size() > 0);
  }
  
  @Test
  public void shouldFindChangeSetMethods() throws MongobeeChangeSetException {
    // given
    String scanPackage = MongobeeTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(MongobeeTestResource.class);
    
    // then
    assertTrue(foundMethods != null && foundMethods.size() == 4);
  }

  @Test
  public void shouldFindAnotherChangeSetMethods() throws MongobeeChangeSetException {
    // given
    String scanPackage = MongobeeTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(AnotherMongobeeTestResource.class);

    // then
    assertTrue(foundMethods != null && foundMethods.size() == 4);
  }


  @Test
  public void shouldFindIsRunAlwaysMethod() throws MongobeeChangeSetException {
    // given
    String scanPackage = MongobeeTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);

    // when
    List<Method> foundMethods = service.fetchChangeSets(AnotherMongobeeTestResource.class);
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
  public void shouldCreateEntry() throws MongobeeChangeSetException {
    
    // given
    String scanPackage = MongobeeTestResource.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    List<Method> foundMethods = service.fetchChangeSets(MongobeeTestResource.class);

    for (Method foundMethod : foundMethods) {
    
      // when
      ChangeEntry entry = service.createChangeEntry(foundMethod);
      
      // then
      Assertions.assertEquals("testuser", entry.getAuthor());
      Assertions.assertEquals(MongobeeTestResource.class.getName(), entry.getChangeLogClass());
      Assertions.assertNotNull(entry.getTimestamp());
      Assertions.assertNotNull(entry.getChangeId());
      Assertions.assertNotNull(entry.getChangeSetMethodName());
    }
  }

  @Test
  public void shouldFailOnDuplicatedChangeSets() {
    String scanPackage = ChangeLogWithDuplicate.class.getPackage().getName();
    ChangeService service = new ChangeService(scanPackage);
    assertThrows(MongobeeChangeSetException.class, () -> service.fetchChangeSets(ChangeLogWithDuplicate.class));
  }

}
