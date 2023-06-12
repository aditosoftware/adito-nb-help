package de.adito.aditoweb.nbm.help;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.common.IProjectQuery;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.*;
import lombok.NonNull;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Write small, focused tests: Each test should focus on a specific aspect of the functionality being tested. Tests should be small, self-contained,
 * and should only test a single behavior or function.
 * Use descriptive and meaningful test names: Your test names should be clear and descriptive, so that anyone reading the tests can easily understand what is being tested.
 * Use arrange-act-assert (AAA) pattern: This pattern helps you structure your test methods into three sections:
 * arrange the test data and set up the test environment, act on the data (i.e. call the method you are testing), and finally, assert that the expected results have been produced.
 * Test edge cases: Make sure to test edge cases, such as empty inputs or extreme values, to ensure that your code can handle unexpected scenarios.
 * Use mocking to isolate dependencies: When testing a method that depends on other components,
 * use mocking to isolate the dependencies and ensure that the test only focuses on the behavior of the method being tested.
 * Avoid using hardcoded values: Use variables or constants to define test data, so that it is easy to change the data if needed and avoids duplicating code.
 * Use parameterized tests: Use parameterized tests to test multiple scenarios with different input data, rather than duplicating similar tests.
 * Run tests frequently: Running tests frequently can catch errors early in the development cycle, allowing for quick and efficient bug fixes.
 * Keep tests independent: Each test should be independent of other tests, so that the result of one test does not affect the outcome of another test.
 */
class HelpActionShowDocumentationTest
{
  private HelpActionShowDocumentation helpActionShowDocumentation;
  private INodeJSEnvironment environment;
  private INodeJSExecutor executor;
  private ProgressHandle handle;
  private Node node1;
  private IProjectQuery projectQuery;

  // todo javadoc über JEDER methode

  /**
   * method for parameterized test that should return true
   *
   * @return Stream<Arguments> of boolean and class for each parameterized test
   */
  @NonNull
  static Stream<Arguments> shouldReturnTrue()
  {
    return Stream.of(Arguments.of(true, mock(JSONObject.class)), Arguments.of(false, null));
  }

  /**
   * method for parameterized test that should return true
   *
   * @return Stream<Arguments> of boolean and class for each parameterized test
   */
  @NonNull
  static Stream<Arguments> shouldReturnProjectNodes()
  {
    Node nodeMock1 = mock(Node.class);
    Node nodeMock2 = mock(Node.class);
    doReturn("project1").when(nodeMock1).getDisplayName();
    doReturn("project2").when(nodeMock2).getDisplayName();


    List<Project> projectList = new ArrayList<>();
    List<Project> projectsList = new ArrayList<>();
    List<Project> emptyProjectsList = new ArrayList<>();
    Project project1 = mock(Project.class);
    Project project2 = mock(Project.class);
    projectList.add(project1);
    projectsList.add(project1);
    projectsList.add(project2);


    return Stream.of(
        Arguments.of(true, projectList.stream()),
        Arguments.of(false, emptyProjectsList.stream()),
        Arguments.of(false, projectsList.stream())
    );
  }

  /**
   * method for parameterized test that should return true
   *
   * @return Stream<Arguments> of boolean and string for each parameterized test
   */
  @NonNull
  static Stream<Arguments> shouldReturnString()
  {
    return Stream.of(Arguments.of(true, "2023.0.0"), Arguments.of(false, ""));
  }

  /**
   * method for parameterized test that should return true
   *
   * @return Stream<Arguments> of class and class for each parameterized test
   */
  @NonNull
  static Stream<Arguments> shouldReturnINodeJSExecutor()
  {
    return Stream.of(Arguments.of(mock(INodeJSExecutor.class), mock(Project.class)), Arguments.of(null, null));
  }

  @BeforeEach
  void init()
  {
    helpActionShowDocumentation = spy(HelpActionShowDocumentation.class);
    //helpActionShowDocumentation = new HelpActionShowDocumentation();  // todo wenn alles mit spy funktioniert, dann mit echten Konstruktor ausprobieren
    environment = mock(INodeJSEnvironment.class);
    executor = mock(INodeJSExecutor.class);
    handle = mock(ProgressHandle.class);
    node1 = mock(Node.class);
    projectQuery = mock(IProjectQuery.class);
  }

  /**
   * Class for the inheritated asynchronous method
   */
  @Nested
  class Asynchronous
  {
    /**
     * Test if the asynchronous method returns true
     */
    @Test
    void asynchronous()
    {
      assertTrue(helpActionShowDocumentation.asynchronous());
    }
  }


  /**
   *
   */
  @Nested
  class GetName
  {

    @Test
    void getName()
    {
      assertEquals("Show Documentation", helpActionShowDocumentation.getName());
    }
  }


  @Nested
  class GetHelpCtx
  {
    @BeforeEach
    void init()
    {
      doCallRealMethod().when(helpActionShowDocumentation).getHelpCtx();
    }

    @Test
    void getHelpCtx()
    {
      assertNull(helpActionShowDocumentation.getHelpCtx());
    }
  }

  @Nested
  class Enable
  {
    @BeforeEach
    void init()
    {
      doCallRealMethod().when(helpActionShowDocumentation).enable(any());
    }

    @Test
    void shouldReturnFalseWhenNoNodes()
    {
      Node[] nodes = new Node[0];

      boolean result = helpActionShowDocumentation.enable(nodes);

      assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnProjectNodes")
    void shouldReturnFalseWhenMultipleProjectsSelected(Boolean pExpectedResult, Stream<Project> pProjectList)
    {
      try (var mockedStat = mockStatic(IProjectQuery.class))
      {
        doReturn(pProjectList).when(helpActionShowDocumentation).findSelectedProjects(any());
        when(helpActionShowDocumentation.getNodeJSExecutor(any())).thenReturn(spy(INodeJSExecutor.class));

        assertEquals(pExpectedResult, helpActionShowDocumentation.enable(any()));
      }
    }

    /**
     * Tests that the {@link HelpActionShowDocumentation#enable(Node[])} method returns false when the specified project is not found.
     * <p>This test method uses Mockito to mock the {@link IProjectQuery} and the {@link Node} objects,
     * and calls the {@code enable()} method of the {@link HelpActionShowDocumentation} class with an array
     * of nodes that contain the display name of a non-existent project. The {@code enable()} method should
     * return false because the project is not found.
     *
     * @see HelpActionShowDocumentation#enable(Node[])
     * @see IProjectQuery
     * @see Node
     */
    @Test
    void shouldReturnFalseWhenProjectNotFound()
    {
      try (var mockedStat = mockStatic(IProjectQuery.class))
      {
        when(node1.getDisplayName()).thenReturn("project1");
        doReturn(null).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
        when(IProjectQuery.getInstance()).thenReturn(projectQuery);

        assertFalse(helpActionShowDocumentation.enable(new Node[]{node1}));
      }
    }

    /*
    @Test
    void shouldReturnFalseWhenNodeJSExecutorNotFound()
    {
      try (var mockedStat = mockStatic(IProjectQuery.class))
      {
        when(node1.getDisplayName()).thenReturn("project1");
        doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
        when(IProjectQuery.getInstance()).thenReturn(projectQuery);
        when(helpActionShowDocumentation.getNodeJSExecutor(project)).thenReturn(null);

        assertFalse(helpActionShowDocumentation.enable(new Node[]{node1}));
      }
    }

    /
      todo mit mir in ruhe reden?
      Maybe needed in another version because compability fix

      @Test void shouldReturnFalseWhenNodeJSEnvironmentNotFound() {
      try (var mockedStat = mockStatic(IProjectQuery.class)) {
      when(node1.getDisplayName()).thenReturn("project1");
      doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
      when(IProjectQuery.getInstance()).thenReturn(projectQuery);
      <p>
      when(hasd.getNodeJSExecutor(project)).thenReturn(mock(INodeJSExecutor.class));
      when(hasd.getNodeJSEnvironment(project)).thenReturn(null);
      <p>
      assertFalse(hasd.enable(new Node[]{node1}));
      }
      }



    @Test
    void shouldReturnTrueWhenSingleNodeAndNodeJSInstalled()
    {
      try (var mockedStat = mockStatic(IProjectQuery.class))
      {
        when(node1.getDisplayName()).thenReturn("project1");
        doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
        when(IProjectQuery.getInstance()).thenReturn(projectQuery);

        when(helpActionShowDocumentation.getNodeJSExecutor(project)).thenReturn(mock(INodeJSExecutor.class));
        when(helpActionShowDocumentation.getNodeJSEnvironment(project)).thenReturn(mock(INodeJSEnvironment.class));

        assertTrue(helpActionShowDocumentation.enable(new Node[]{node1}));
      }
    }
    */
  }


  @Nested
  class ExecuteInstall
  {
    @BeforeEach
    void init()
    {
      doCallRealMethod().when(helpActionShowDocumentation).executeInstall(any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldHandleNullHandle()
    {
      assertDoesNotThrow(() -> helpActionShowDocumentation.executeInstall(environment, executor, null, "test", 1));
    }

    @Test
    void shouldLogVerifyIfVerifyPackageInstallationTrue() throws IOException, InterruptedException, TimeoutException
    {
      doReturn(true).when(helpActionShowDocumentation).verifyPackageInstallation(environment, executor, "test");
      helpActionShowDocumentation.executeInstall(environment, executor, handle, "test", 1);
      verify(handle).progress(anyString(), anyInt());
      verify(handle, never()).progress("installing test", 2);
    }

    @Test
    void shouldExecuteInstall() throws IOException, InterruptedException, TimeoutException
    {
      doReturn(false).when(helpActionShowDocumentation).verifyPackageInstallation(environment, executor, "test");
      helpActionShowDocumentation.executeInstall(environment, executor, handle, "test", 1);
      verify(handle, times(2)).progress(anyString(), anyInt());
    }
  }

  @Nested
  class VerifyPackageInstallation
  {
    @BeforeEach
    void init() throws IOException, InterruptedException, TimeoutException
    {
      doCallRealMethod().when(helpActionShowDocumentation).verifyPackageInstallation(any(), any(), anyString());
    }

    @Test
    void shouldReturnFalse() throws IOException, InterruptedException, TimeoutException
    {
      try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
          doReturn(null).when(pJSON).optJSONObject("dependencies")))
      {
        doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
        assertFalse(helpActionShowDocumentation.verifyPackageInstallation(environment, executor, "test"));
      }
    }

    @ParameterizedTest
    @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnTrue")
      // todo eigentlich sollte es ohne string-pfad gehen, ramona fragen?
    void shouldReturnTrue(Boolean pExpected, JSONObject pObject) throws IOException, InterruptedException, TimeoutException
    {
      try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
      {
        doReturn(pJSON).when(pJSON).optJSONObject("dependencies");
        doReturn(pObject).when(pJSON).optJSONObject("test");
      }))
      {
        doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
        assertEquals(pExpected, helpActionShowDocumentation.verifyPackageInstallation(environment, executor, "test"));
      }
    }
  }

  @Nested
  class CheckProjectJDitoTypes
  {
    @BeforeEach
    void init() throws IOException, InterruptedException, TimeoutException
    {
      doCallRealMethod().when(helpActionShowDocumentation).checkProjectJDitoTypes(any(), any());
    }

    @Test
    void shouldReturnFalse() throws IOException, InterruptedException, TimeoutException
    {
      try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
          doReturn(null).when(pJSON).optJSONObject("dependencies")))
      {
        doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
        assertFalse(helpActionShowDocumentation.checkProjectJDitoTypes(environment, executor));
      }
    }

    @ParameterizedTest
    @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnString")
    void shouldReturnTrue(Boolean pExpected, String pObject) throws IOException, InterruptedException, TimeoutException
    {
      try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
      {
        doReturn(pJSON).when(pJSON).optJSONObject("dependencies");
        doReturn(pJSON).when(pJSON).optJSONObject("@aditosoftware/jdito-types");
        doReturn(pObject).when(pJSON).optString("version", "");
      }))
      {
        doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
        assertEquals(pExpected, helpActionShowDocumentation.checkProjectJDitoTypes(environment, executor));
      }
    }
  }

  @Nested
  class ExecuteJSDoc
  {
    @Test
    void shouldExecuteJSDoc() throws IOException, InterruptedException, TimeoutException
    {

      doReturn("ResultTest")
          .when(executor)
          .executeSync(eq(environment), any(), eq(-1L), eq("/node_modules/jsdoc/jsdoc.js"), eq("--configure"), eq("/jsdoc.json"), eq("--verbose"));

      assertTrue(true);
    }
  }

  @Nested
  class GetNodeJSEnvironment
  {
    @BeforeEach
    void init()
    {
      doCallRealMethod().when(helpActionShowDocumentation).getNodeJSEnvironment(any());
    }

    @Test
    void shouldHandleProjectNull()
    {
      assertNull(helpActionShowDocumentation.getNodeJSEnvironment(null));
    }

        /* I ain't dealing with that shit anymore todo
        @ParameterizedTest
        @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnINodeJSEnvironment")
        void shouldHandleProjectNotNull(INodeJSEnvironment pExpected, Project pProject) {
            Optional<INodeJSEnvironment> nodeJSEnvironment = Optional.of(environment);
            Optional<INodeJSProvider> nodeJSProvider = Optional.of(mock(INodeJSProvider.class));
            doReturn(Observable.just(nodeJSEnvironment)).when(nodeJSProvider.get().current());

            doReturn(nodeJSProvider).when(INodeJSProvider.findInstance(pProject));

            INodeJSEnvironment result = hasd.getNodeJSEnvironment(pProject);

            assertEquals(pExpected, result);
        }
         */
  }

  @Nested
  class GetNodeJSExecutor
  {
    @BeforeEach
    void init()
    {
      doCallRealMethod().when(helpActionShowDocumentation).getNodeJSExecutor(any());
    }

    @Test
    void shouldReturnNullIfProjectNull()
    {
      assertNull(helpActionShowDocumentation.getNodeJSExecutor(null));
    }

    @ParameterizedTest
    @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnINodeJSExecutor")
    void shouldHandleProjectNotNull(INodeJSExecutor pExpected, Project pProject) // todo nullable,not null
    {
      try (MockedStatic<INodeJSExecutor> mockedStatic = mockStatic(INodeJSExecutor.class))
      {
        mockedStatic.when(() -> INodeJSExecutor.findInstance(pProject)).thenReturn(Optional.ofNullable(pExpected));

        INodeJSExecutor result = helpActionShowDocumentation.getNodeJSExecutor(pProject);

        assertSame(pExpected, result);
      }
    }


    @Test
    void shouldHandleProjectNotNull()
    {
      // Arrange
      Project projectMock = mock(Project.class);
      INodeJSExecutor nodeJSExecutorMock = mock(INodeJSExecutor.class);

      try (MockedStatic<INodeJSExecutor> mockedStatic = mockStatic(INodeJSExecutor.class))
      {
        mockedStatic.when(() -> INodeJSExecutor.findInstance(projectMock)).thenReturn(Optional.of(nodeJSExecutorMock));

        INodeJSExecutor result = helpActionShowDocumentation.getNodeJSExecutor(projectMock);

        assertSame(result, nodeJSExecutorMock);
      }
    }
  }
}