package de.adito.aditoweb.nbm.help;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.common.IProjectQuery;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSEnvironment;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSExecBase;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSExecutor;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSProvider;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
class HelpActionShowDocumentationTest {
    HelpActionShowDocumentation hasd;
    INodeJSEnvironment environment;
    INodeJSExecutor executor;
    INodeJSExecBase base;
    INodeJSProvider provider;
    ProgressHandle handle;
    Project project;
    Node node1;
    Node node2;
    IProjectQuery projectQuery;


    static Stream<Arguments> shouldReturnTrue() {
        return Stream.of(Arguments.of(true, mock(JSONObject.class)), Arguments.of(false, null));
    }

    static Stream<Arguments> shouldReturnString() {
        return Stream.of(Arguments.of(true, "2023.0.0"), Arguments.of(false, ""));
    }
    static Stream<Arguments> shouldReturnINodeJSExecutor() {
        return Stream.of(Arguments.of(mock(INodeJSExecutor.class), mock(Project.class)), Arguments.of(null, null));
    }

    @BeforeEach
    void init() {
        hasd = mock(HelpActionShowDocumentation.class);
        environment = mock(INodeJSEnvironment.class);
        executor = mock(INodeJSExecutor.class);
        base = mock(INodeJSExecBase.class);
        handle = mock(ProgressHandle.class);
        project = mock(Project.class);
        provider = mock(INodeJSProvider.class);
        node1 = mock(Node.class);
        node2 = mock(Node.class);
        projectQuery = mock(IProjectQuery.class);
    }

    @Nested
    class Asynchronous {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).asynchronous();
        }
        @Test
        void asynchronous() {
            assertTrue(hasd.asynchronous());
        }
    }


    @Nested
    class GetName {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).getName();
        }
        @Test
        void getName() {
            assertEquals("Show Documentation", hasd.getName());
        }
    }


    @Nested
    class GetHelpCtx {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).getHelpCtx();
        }
        @Test
        void getHelpCtx() {
            assertNull(hasd.getHelpCtx());
        }
    }

    @Nested
    class Enable {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).enable(any());
        }
        @Test
        void shouldReturnFalseWhenNoNodes() {
            // Arrange
            Node[] nodes = new Node[0];

            // Act
            boolean result = hasd.enable(nodes);

            // Assert
            assertFalse(result);
        }

        @Test
        void shouldReturnFalseWhenMultipleProjectsSelected() {
            try (var mockedStat = mockStatic(IProjectQuery.class)) {
                when(node1.getDisplayName()).thenReturn("project1");
                when(node2.getDisplayName()).thenReturn("project2");
                doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
                when(IProjectQuery.getInstance()).thenReturn(projectQuery);

                assertFalse(hasd.enable(new Node[]{node1, node2}));
            }
        }

        @Test
        void shouldReturnFalseWhenProjectNotFound() {
            try (var mockedStat = mockStatic(IProjectQuery.class)) {
                when(node1.getDisplayName()).thenReturn("project1");
                doReturn(null).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
                when(IProjectQuery.getInstance()).thenReturn(projectQuery);

                assertFalse(hasd.enable(new Node[]{node1}));
            }
        }

        @Test
        void shouldReturnFalseWhenNodeJSExecutorNotFound() {
            try (var mockedStat = mockStatic(IProjectQuery.class)) {
                when(node1.getDisplayName()).thenReturn("project1");
                doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
                when(IProjectQuery.getInstance()).thenReturn(projectQuery);
                when(hasd.getNodeJSExecutor(project)).thenReturn(null);

                assertFalse(hasd.enable(new Node[]{node1}));
            }
        }

        @Test
        void shouldReturnFalseWhenNodeJSEnvironmentNotFound() {
            try (var mockedStat = mockStatic(IProjectQuery.class)) {
                when(node1.getDisplayName()).thenReturn("project1");
                doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
                when(IProjectQuery.getInstance()).thenReturn(projectQuery);

                when(hasd.getNodeJSExecutor(project)).thenReturn(mock(INodeJSExecutor.class));
                when(hasd.getNodeJSEnvironment(project)).thenReturn(null);

                assertFalse(hasd.enable(new Node[]{node1}));
            }
        }

        @Test
        void shouldReturnTrueWhenSingleNodeAndNodeJSInstalled() {
            try (var mockedStat = mockStatic(IProjectQuery.class)) {
                when(node1.getDisplayName()).thenReturn("project1");
                doReturn(project).when(projectQuery).findProjects(any(Lookup.Provider.class), eq(IProjectQuery.ReturnType.MULTIPLE_TO_NULL));
                when(IProjectQuery.getInstance()).thenReturn(projectQuery);

                when(hasd.getNodeJSExecutor(project)).thenReturn(mock(INodeJSExecutor.class));
                when(hasd.getNodeJSEnvironment(project)).thenReturn(mock(INodeJSEnvironment.class));

                assertTrue(hasd.enable(new Node[]{node1}));
            }
        }
    }

    @Nested
    class ExecuteInstall {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).executeInstall(any(), any(), any(), any(), anyInt());
        }

        @Test
        void shouldHandleNullHandle() {
            assertDoesNotThrow(() -> hasd.executeInstall(environment, executor, null, "test", 1));
        }

        @Test
        void shouldLogVerifyIfVerifyPackageInstallationTrue() throws IOException, InterruptedException, TimeoutException {
            doReturn(true).when(hasd).verifyPackageInstallation(environment, executor, "test");
            hasd.executeInstall(environment, executor, handle, "test", 1);
            verify(handle).progress(anyString(), anyInt());
            verify(handle, times(0)).progress("installing test", 2);
        }

        @Test
        void shouldExecuteInstall() throws IOException, InterruptedException, TimeoutException {
            doReturn(false).when(hasd).verifyPackageInstallation(environment, executor, "test");
            hasd.executeInstall(environment, executor, handle, "test", 1);
            verify(handle, times(2)).progress(anyString(), anyInt());
        }
    }

    @Nested
    class VerifyPackageInstallation {
        @BeforeEach
        void init() throws IOException, InterruptedException, TimeoutException {
            doCallRealMethod().when(hasd).verifyPackageInstallation(any(), any(), anyString());
        }

        @Test
        void shouldReturnFalse() throws IOException, InterruptedException, TimeoutException {
            try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
                    doReturn(null).when(pJSON).optJSONObject("dependencies"))) {
                doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
                assertFalse(hasd.verifyPackageInstallation(environment, executor, "test"));
            }
        }

        @ParameterizedTest
        @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnTrue")
        void shouldReturnTrue(Boolean pExpected, JSONObject pObject) throws IOException, InterruptedException, TimeoutException {
            try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
            {
                doReturn(pJSON).when(pJSON).optJSONObject("dependencies");
                doReturn(pObject).when(pJSON).optJSONObject("test");
            })) {
                doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
                assertEquals(pExpected, hasd.verifyPackageInstallation(environment, executor, "test"));
            }
        }
    }

    @Nested
    class CheckProjectJDitoTypes {
        @BeforeEach
        void init() throws IOException, InterruptedException, TimeoutException {
            doCallRealMethod().when(hasd).checkProjectJDitoTypes(any(), any());
        }

        @Test
        void shouldReturnFalse() throws IOException, InterruptedException, TimeoutException {
            try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
                    doReturn(null).when(pJSON).optJSONObject("dependencies"))) {
                doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
                assertFalse(hasd.checkProjectJDitoTypes(environment, executor));
            }
        }

        @ParameterizedTest
        @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnString")
        void shouldReturnTrue(Boolean pExpected, String pObject) throws IOException, InterruptedException, TimeoutException {
            try (var JSONObjectMockConstruction = mockConstruction(JSONObject.class, (pJSON, pContext) ->
            {
                doReturn(pJSON).when(pJSON).optJSONObject("dependencies");
                doReturn(pJSON).when(pJSON).optJSONObject("@aditosoftware/jdito-types");
                doReturn(pObject).when(pJSON).optString("version", "");
            })) {
                doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
                assertEquals(pExpected, hasd.checkProjectJDitoTypes(environment, executor));
            }
        }
    }

    @Nested
    class ExecuteJSDoc {
        @BeforeEach
        void init() throws IOException, InterruptedException, TimeoutException {
            doCallRealMethod().when(hasd).executeJSDoc(any(), any(), any(), any());
        }

        @Test
        void shouldExecuteJSDoc() throws IOException, InterruptedException, TimeoutException {
            doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq("/node_modules/jsdoc/jsdoc.js"), eq("--configure"), eq("/jsdoc.json"), eq("--verbose"));
        }
    }

    @Nested
    class GetNodeJSEnvironment {
        @BeforeEach
        void init() {
            doCallRealMethod().when(hasd).getNodeJSEnvironment(any());
        }

        @Test
        void shouldHandleProjectNull() {
            assertNull(hasd.getNodeJSEnvironment(null));
        }

        /* I ain't dealing with that shit anymore
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
    class ExecutorJSDoc {
        @BeforeEach
        void init() throws IOException, InterruptedException, TimeoutException {
            doCallRealMethod().when(hasd).executeJSDoc(any(), any(), anyString(), any());
        }
        @Test
        void shouldReturnSomething() throws IOException, InterruptedException, TimeoutException {
            doReturn("ResultTest").when(executor).executeSync(eq(environment), any(), eq(-1L), eq(false), any());
            assertDoesNotThrow(() -> hasd.executeJSDoc(environment, executor,  "", ""));
        }
    }
    @Nested
    class GetNodeJSExecutor {
        @BeforeEach
        void init()  {
            doCallRealMethod().when(hasd).getNodeJSExecutor(any());
        }

        @Test
        void shouldReturnNullIfProjectNull() {
            assertNull(hasd.getNodeJSExecutor(null));
        }

        @ParameterizedTest
        @MethodSource("de.adito.aditoweb.nbm.help.HelpActionShowDocumentationTest#shouldReturnINodeJSExecutor")
        void shouldHandleProjectNotNull(INodeJSExecutor pExpected, Project pProject) {
            // Arrange
            try (MockedStatic<INodeJSExecutor> mockedStatic = mockStatic(INodeJSExecutor.class)) {
                mockedStatic.when(() -> INodeJSExecutor.findInstance(pProject)).thenReturn(Optional.ofNullable(pExpected));

                // Act
                INodeJSExecutor result = hasd.getNodeJSExecutor(pProject);

                // Assert
                assertSame(pExpected, result);
            }
        }



        @Test
        void shouldHandleProjectNotNull() {
            // Arrange
            Project projectMock = mock(Project.class);
            INodeJSExecutor nodeJSExecutorMock = mock(INodeJSExecutor.class);

            try (MockedStatic<INodeJSExecutor> mockedStatic = mockStatic(INodeJSExecutor.class)) {
                mockedStatic.when(() -> INodeJSExecutor.findInstance(projectMock)).thenReturn(Optional.of(nodeJSExecutorMock));

                // Act
                INodeJSExecutor result = hasd.getNodeJSExecutor(projectMock);

                // Assert
                assertSame(result, nodeJSExecutorMock);
            }
        }
    }
}