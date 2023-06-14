package de.adito.aditoweb.nbm.help;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.common.IProjectQuery;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.*;
import de.adito.notification.INotificationFacade;
import io.reactivex.rxjava3.annotations.Nullable;
import io.reactivex.rxjava3.core.Observable;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.openide.awt.*;
import org.openide.modules.Places;
import org.openide.nodes.Node;
import org.openide.util.*;
import org.openide.util.actions.NodeAction;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.*;


/**
 * New action in the ADITO Designer that uses the jdito-types to generate a jsdoc.
 *
 * @author F.Adler, 19.01.2023
 */
@NbBundle.Messages("ACTION_showDocumentation_displayName=Show Documentation")
@ActionID(category = "Help", id = "de.adito.aditoweb.nbm.help.HelpActionShowDocumentation")
@ActionRegistration(displayName = "#ACTION_showDocumentation_displayName", iconBase = "de/adito/aditoweb/nbm/help/openBook.png")
@ActionReference(path = "Menu/Help", position = 1450, separatorBefore = 1449)
public class HelpActionShowDocumentation extends NodeAction
{
  //define private static final _LOGGER to use it everywhere in this class
  @VisibleForTesting
  protected static final Logger LOGGER = Logger.getLogger(HelpActionShowDocumentation.class.getName());

  private static final String SUPPORTED_JDITO_VERSION = "2023";
  private static final String JDITO_TYPES = "@aditosoftware/jdito-types";
  private static final String BETTER_DOCS = "better-docs";

  @Override
  protected boolean asynchronous()
  {
    return true;
  }

  @Override
  public String getName()
  {
    return "Show Documentation";
  }

  @Override
  public HelpCtx getHelpCtx()
  {
    return null;
  }

  @Override
  protected String iconResource()
  {
    return "de/adito/aditoweb/nbm/help/openBook.png";
  }

  @Override
  protected boolean enable(Node[] nodes)
  {
    // modularized project will also be seen as different projects as long as you click on entities, best case you click on the project itself
    List<Project> projects = findSelectedProjects(nodes)
        .collect(Collectors.toList());

    if (projects.size() != 1)
    {
      return false;
    }

    Project project = projects.get(0);

    INodeJSExecutor executor = getNodeJSExecutor(project);

    // Enable only if a single project is selected and NodeJS is installed
    return executor != null;
  }

  @Override
  protected void performAction(Node[] nodes)
  {
    final AtomicInteger port = new AtomicInteger(-1);
    try (ServerSocket portSocket = new ServerSocket(0))
    {
      port.set(portSocket.getLocalPort());
    }
    catch (IOException ioException)
    {
      INotificationFacade.INSTANCE.error(ioException);
    }
    if (port.get() == -1)
    {
      INotificationFacade.INSTANCE.notify("No port available", "No port was available at the time of executing the 'Show Documentation' action", false, null);
      return;
    }

    //Set ProgressHandle inside the try-block to automatically close it when finished
    try (ProgressHandle handle = ProgressHandle.createHandle("Rendering JSDoc Documentation"))
    {

      //start ProgressHandle (Loading bar)
      handle.start();
      handle.switchToIndeterminate();

      //Scan over every selected "node" and only get the first project
      Project project = findSelectedProjects(nodes).findFirst().orElseThrow();
      String projectName = project.getProjectDirectory().getName();
      String projectPath = project.getProjectDirectory().getPath();

      INodeJSExecutor executor = getNodeJSExecutor(project);
      INodeJSEnvironment nodeJsEnv = getNodeJSEnvironment(project);

      //set ProgressHandle (loading bar) to have X steps to completion
      handle.switchToDeterminate(16);

      if (checkProjectJDitoTypes(nodeJsEnv, executor))
      {
        //installing all needed modules via npm
        executeInstall(nodeJsEnv, executor, handle, "jsdoc-mermaid", 1);
        executeInstall(nodeJsEnv, executor, handle, BETTER_DOCS, 3);
        executeInstall(nodeJsEnv, executor, handle, "clean-jsdoc-theme", 5);
        executeInstall(nodeJsEnv, executor, handle, "jsdoc@3.6.11", 7);
        executeInstall(nodeJsEnv, executor, handle, "http-server", 9);
        executeInstall(nodeJsEnv, executor, handle, "jsdoc-plugin-typescript", 11);

        handle.progress("Rendering JSDoc Documentation", 13);

        //copy the file included in the .jar to the .aditodesigner/version/help/project folder and change the content to fit each project
        String jsDocPath = moveAndOverwriteJSDocContent(projectPath, port.get(), projectName, nodeJsEnv);

        executeJSDoc(nodeJsEnv, executor, jsDocPath);
        executeHttpServer(handle, nodeJsEnv, executor, jsDocPath, port.get());
        openBrowserWithURI(port.get());

        try
        {
          INotificationFacade.INSTANCE.notify(
              "Local HTTP-Server",
              "localhost:" + port,
              false,
              new OpenBrowser(port.get())
          );
        }
        catch (Exception pE)
        {
          INotificationFacade.INSTANCE.error(pE);
        }
      }
      else
      {
        INotificationFacade.INSTANCE.notify(
            "JDito-Types outdated",
            "Your installed JDito-types are not supported. Update your JDito-types dependency to 2023.0.0 or later.",
            false,
            null
        );
      }
    }
    catch (Exception ex)
    {
      INotificationFacade.INSTANCE.error(ex);
    }
  }

  protected Stream<Project> findSelectedProjects(Node[] nodes)
  {
    return Arrays.stream(nodes).map(pNode -> IProjectQuery.getInstance().findProjects(pNode, IProjectQuery.ReturnType.MULTIPLE_TO_NULL)).filter(Objects::nonNull).distinct();
  }

  /**
   * private method to write into the log and set progress handle to new workunit. Checks and installs given npm packages
   *
   * @param pNodeJsEnvironment environment for NodeJS
   * @param pExecutor          executor for NodeJS
   * @param pHandle            ProgressHandle for the progress bar
   * @param pPackage           npm package that should be installed
   * @param pPackageNumber     workunit for the ProgressHandle
   */
  @VisibleForTesting
  void executeInstall(@NonNull INodeJSEnvironment pNodeJsEnvironment, @NonNull INodeJSExecutor pExecutor, @Nullable ProgressHandle pHandle, @NonNull String pPackage, int pPackageNumber)
  {
    try
    {
      String stringPackage = pPackage.replaceAll("@.+", "");
      pHandle.progress("verifying " + stringPackage, pPackageNumber);
      if (!verifyPackageInstallation(pNodeJsEnvironment, pExecutor, stringPackage))
      {
        pHandle.progress("installing " + stringPackage, ++pPackageNumber);
        String result = pExecutor.executeSync(pNodeJsEnvironment, INodeJSExecBase.packageManager(), -1, "i", stringPackage, "-g");
        LOGGER.info(result);
      }
    }
    catch (Exception e)
    {
      INotificationFacade.INSTANCE.error(e);
    }
  }

  /**
   * get the absolute path of a installed module from npm
   *
   * @param pNodeEnv   nodejs env
   * @param pModule    module name
   * @param pInnerPath inner path of module
   * @return the absolute path
   */
  protected String getAbsolutePathOfModule(@NonNull INodeJSEnvironment pNodeEnv, @NonNull String pModule, @NonNull String pInnerPath)
  {
    return pNodeEnv.resolveExecBase(INodeJSExecBase.module(pModule, pInnerPath.isEmpty() ? "" : pInnerPath)).getAbsolutePath();
  }

  /**
   * Check if the JSDoc JSON file exists, move it to the cache folder and replace the content with correct paths
   *
   * @param pPath original path of the jsdoc.json
   * @param pPort random port for the webserver
   * @return String of the path where the jsdoc.json is stored
   * @throws IOException if no resources can be found
   */
  @VisibleForTesting
  @NonNull
  protected String moveAndOverwriteJSDocContent(@NonNull String pPath, int pPort, @NonNull String pProjectName, @NonNull INodeJSEnvironment pNodeEnv) throws IOException
  {
    //create cache directory with a subpath with the name of the project
    Path newPath = Places.getCacheSubdirectory("help/" + pProjectName).toPath();

    Gson gson = new Gson();

    // Read the JSON from the input file
    try (InputStream input = this.getClass().getClassLoader().getResourceAsStream("de/adito/aditoweb/nbm/help/jsdoc.json");
         InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(input)))
    {
      JsonObject rootObject = gson.fromJson(reader, JsonObject.class);


      // Change the value of the "source" key
      JsonArray includeArray = rootObject.getAsJsonObject("source").getAsJsonArray("include");
      includeArray.remove(0); //in the given JSON there is already a line that needs to be removed.
      includeArray.add(pPath + "/node_modules/@aditosoftware/jdito-types/dist");
      //includeArray.add(pPath + "/process/"); //comment this in, if you want trouble, and make it double

      // Change the value of the "plugins" key
      JsonArray pluginsArray = rootObject.getAsJsonArray("plugins");
      pluginsArray.set(2, gson.toJsonTree(getAbsolutePathOfModule(pNodeEnv, BETTER_DOCS, "category.js")));
      pluginsArray.set(3, gson.toJsonTree(getAbsolutePathOfModule(pNodeEnv, BETTER_DOCS, "typescript")));

      // Change the value of the "opts" key
      rootObject.getAsJsonObject("opts").addProperty("template", getAbsolutePathOfModule(pNodeEnv, "clean-jsdoc-theme", ""));
      rootObject.getAsJsonObject("opts").addProperty("destination", newPath + "/docs/documentations/");

      // Change the value of the "theme_opts" key
      rootObject.getAsJsonObject("opts").getAsJsonObject("theme_opts").addProperty("base_url", "localhost:" + pPort + "/");

      // Write the updated JSON to a different location
      try (FileWriter fileWriter = new FileWriter(Paths.get(newPath.toString(), "/jsdoc.json").toFile()))
      {
        JsonWriter jsonWriter = new JsonWriter(fileWriter);
        jsonWriter.setIndent("    ");
        gson.toJson(rootObject, jsonWriter);
      }
    }
    //delete cache subdirectory on exit
    FileUtils.forceDeleteOnExit(Places.getCacheSubdirectory("help/" + pProjectName));

    return newPath.toString().replaceAll("\\\\", "/");
  }

  /**
   * Executes the "npm list" command and determines, if all the given packages are installed.
   * It does not handle outdated packages - it just verifies, if it is installed.
   *
   * @param pExecutor          NodeJS Executor
   * @param pNodeJsEnvironment NodeJS environment
   * @param pPackage           packages that should be checked
   * @return boolean depending if the dependency exists
   */
  @VisibleForTesting
  protected boolean verifyPackageInstallation(@NonNull INodeJSEnvironment pNodeJsEnvironment, @NonNull INodeJSExecutor pExecutor, @NonNull String pPackage) throws IOException, InterruptedException, TimeoutException
  {
    //execute npm list with --json to validate if the package is installed or not
    String result = pExecutor.executeSync(pNodeJsEnvironment, INodeJSExecBase.packageManager(), -1, false, "list", pPackage, "-g", "--json");

    //remove first line of "result" because npm list returns the execution path as well
    JSONObject obj = new JSONObject(result.substring(result.indexOf("\n") + 1));
    //search for the dependencies node and save it again in obj -> nullpointer possible if not
    obj = obj.optJSONObject("dependencies");

    if (obj != null)
      //it could be that there are other npm installations with a dependency on this exact package.
      //so we only check for the globally installed package
      //maybe check for the wanted version as well but not neccessary now
      return obj.optJSONObject(pPackage) != null;
    else
      //if no installation is found, return false
      return false;
  }

  /**
   * checks if the currently installed jdito types are valid for this plugin
   *
   * @param pNodeJsEnvironment NodeJS environment
   * @param pExecutor          NodeJS Executor
   * @return boolean if the jdito types are correct
   * @throws IOException          if an error occurred
   * @throws InterruptedException if the timeout killed the process
   * @throws TimeoutException     if the timeout killed the process
   */
  @VisibleForTesting
  protected boolean checkProjectJDitoTypes(@NonNull INodeJSEnvironment pNodeJsEnvironment, @NonNull INodeJSExecutor pExecutor) throws IOException, InterruptedException, TimeoutException
  {
    // Execute npm list with --json to validate if the package is installed or not
    String result = pExecutor.executeSync(pNodeJsEnvironment, INodeJSExecBase.packageManager(), -1, false, "list", JDITO_TYPES, "--json");

    // Remove first line of "result" because npm list returns the execution path as well
    JSONObject obj = new JSONObject(result.substring(result.indexOf("\n") + 1));
    // Search for the dependencies node and save it again in obj -> nullpointer possible if not
    JSONObject dependencies = obj.optJSONObject("dependencies");
    JSONObject jditoTypes = dependencies != null ? dependencies.optJSONObject(JDITO_TYPES) : null;
    return jditoTypes != null && jditoTypes.optString("version", "").startsWith(SUPPORTED_JDITO_VERSION);
  }

  /**
   * Opens the standard browser of the user and directs them to the local http-server with the given port
   *
   * @param pPort available port that has been selected by the serversocket
   */
  private void openBrowserWithURI(int pPort) throws URISyntaxException, IOException
  {
    Desktop.getDesktop().browse(new URI("http://localhost:" + pPort));
  }

  /**
   * executes the JSDoc command
   *
   * @param pNodeJsEnv      NodeJS environment
   * @param pNodeJsExecutor NodeJS Executor
   * @param pJSDocPath      path to jsdoc
   * @throws IOException          if an error occurred
   * @throws InterruptedException if the timeout killed the process
   * @throws TimeoutException     if the timeout killed the process
   */
  @VisibleForTesting
  protected void executeJSDoc(@NonNull INodeJSEnvironment pNodeJsEnv, @NonNull INodeJSExecutor pNodeJsExecutor, @NonNull String pJSDocPath) throws IOException, InterruptedException, TimeoutException
  {
    //executing the jsdoc command to render the html files
    String result = pNodeJsExecutor.executeSync(pNodeJsEnv, INodeJSExecBase.node(), -1,
                                                pNodeJsEnv.resolveExecBase(INodeJSExecBase.module("jsdoc", "jsdoc.js")).getAbsolutePath(), "--configure", pJSDocPath + "/jsdoc.json", "--verbose");
    LOGGER.info(result);
  }

  /**
   * opens the http-server
   *
   * @param pHandle         handle for progress
   * @param pNodeJsEnv      environment
   * @param pNodeJsExecutor executor
   * @param pJSDocPath      path
   * @param pPort           port
   * @throws IOException if an error occurred
   */
  @VisibleForTesting
  protected void executeHttpServer(@NonNull ProgressHandle pHandle, @NonNull INodeJSEnvironment pNodeJsEnv, @NonNull INodeJSExecutor pNodeJsExecutor, @NonNull String pJSDocPath, int pPort) throws IOException
  {
    pHandle.progress("Opening HTTP-Server", 16);
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
    {
      //opening a local server to view the htmls with a random port
      String result = String.valueOf(pNodeJsExecutor.executeAsync(pNodeJsEnv, INodeJSExecBase.node(), outputStream, null, null,
                                                                  pNodeJsEnv.resolveExecBase(INodeJSExecBase.module("http-server", "bin/http-server")).getAbsolutePath(), pJSDocPath + "/docs/documentations", "-p", "" + pPort));
      LOGGER.info(result);
    }
  }

  /**
   * Get the NodeJS Environemnt for the given project
   *
   * @param pProject project that should be used
   * @return returns the NodeJS Environment of the project
   */
  @VisibleForTesting
  @Nullable
  protected INodeJSEnvironment getNodeJSEnvironment(@Nullable Project pProject)
  {
    if (pProject == null)
      return null;

    Optional<INodeJSProvider> nodeJSProvider = INodeJSProvider.findInstance(pProject);

    if (!nodeJSProvider.isPresent())
      return null;

    Observable<Optional<INodeJSEnvironment>> nodeJSEnvironment = nodeJSProvider.get().current();

    if (Boolean.TRUE.equals(nodeJSEnvironment.isEmpty().blockingGet()))
      return null;
    else
      return nodeJSEnvironment.blockingFirst().orElse(null);
  }

  /**
   * Get the NodeJS Executor for the given project
   *
   * @param pProject project that should be used
   * @return returns the NodeJS Executor for the project
   */
  @VisibleForTesting
  @Nullable
  protected INodeJSExecutor getNodeJSExecutor(@Nullable Project pProject)
  {
    if (pProject == null)
      return null;

    return INodeJSExecutor.findInstance(pProject).orElse(null);
  }

  private class OpenBrowser implements ActionListener
  {
    private final int port;

    private OpenBrowser(int pPort)
    {
      port = pPort;
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
      try
      {
        openBrowserWithURI(port);
      }
      catch (Exception pException)
      {
        INotificationFacade.INSTANCE.error(pException);
      }
    }
  }
}