package de.adito.aditoweb.nbm.help;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.common.IProjectQuery;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSEnvironment;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSExecBase;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSExecutor;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.javascript.node.INodeJSProvider;
import de.adito.notification.INotificationFacade;
import io.reactivex.rxjava3.annotations.Nullable;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.modules.Places;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;

import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author F.Adler, 19.01.2023
 */
@NbBundle.Messages("ACTION_showDocumentation_displayName=Show Documentation")
@ActionID(category = "Help", id = "de.adito.aditoweb.nbm.help.HelpActionShowDocumentation")
@ActionRegistration(displayName = "#ACTION_showDocumentation_displayName")
@ActionReference(path = "Menu/Help", position = 1700)
public class HelpActionShowDocumentation extends NodeAction {
    //define private static final _LOGGER to use it everywhere in this class
    @VisibleForTesting
    protected static final Logger _LOGGER = Logger.getLogger(HelpActionShowDocumentation.class.getName());

    @Override
    protected boolean asynchronous() {
        return true;
    }

    @Override
    public String getName() {
        return "Show Documentation";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    protected boolean enable(Node[] nodes) {
        List<Project> projects = Arrays.stream(nodes)
                .map(pNode -> IProjectQuery.getInstance().findProjects(pNode, IProjectQuery.ReturnType.MULTIPLE_TO_NULL))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (projects.size() != 1) {
            return false;
        }

        Project project = projects.get(0);

        INodeJSExecutor executor = getNodeJSExecutor(project);
        INodeJSEnvironment provider = getNodeJSEnvironment(project);

        // Enable only if a single project is selected and NodeJS is installed
        return (executor != null && provider != null);
    }

    @Override
    protected void performAction(Node[] nodes) {
        //Set ProgressHandle inside the try-block to automatically close it when finished
        try (ProgressHandle handle = ProgressHandle.createHandle("Rendering JSDoc Documentation"); ServerSocket portSocket = new ServerSocket(0)) {
            int port = portSocket.getLocalPort();
            portSocket.close();

            //start ProgressHandle (Loading bar)
            handle.start();
            handle.switchToIndeterminate();

            //Scan over every selected "node" and only get the first project
            Project project = Arrays.stream(nodes).map(pNode -> IProjectQuery.getInstance().findProjects(pNode, IProjectQuery.ReturnType.MULTIPLE_TO_NULL)).filter(Objects::nonNull).distinct().findFirst().orElseThrow();
            String projectName = project.getProjectDirectory().getName();
            String projectPath = project.getProjectDirectory().getPath();

            INodeJSExecutor executor = getNodeJSExecutor(project);
            INodeJSEnvironment nodeJsEnv = getNodeJSEnvironment(project);

            //set ProgressHandle (loading bar) to have X steps to completion
            handle.switchToDeterminate(16);

            if (checkProjectJDitoTypes(nodeJsEnv, executor)) {
                //installing all needed modules via npm
                executeInstall(nodeJsEnv, executor, handle, "jsdoc-mermaid", 1);
                executeInstall(nodeJsEnv, executor, handle, "better-docs", 3);
                executeInstall(nodeJsEnv, executor, handle, "clean-jsdoc-theme", 5);
                executeInstall(nodeJsEnv, executor, handle, "jsdoc@3.6.11", 7);
                executeInstall(nodeJsEnv, executor, handle, "http-server", 9);
                executeInstall(nodeJsEnv, executor, handle, "jsdoc-plugin-typescript", 11);

                handle.progress("Rendering JSDoc Documentation", 13);

                //copy the file included in the .jar to the .aditodesigner/version/help/project folder and change the content to fit each project
                String jsDocPath = moveAndOverwriteJSDocContent(projectPath, port, projectName, nodeJsEnv);

                executeJSDoc(nodeJsEnv, executor, jsDocPath);
                executeHttpServer(handle, nodeJsEnv, executor, jsDocPath, port);
                Desktop.getDesktop().browse(new URI("http://localhost:" + port));

                INotificationFacade.INSTANCE.notify(
                        "Local HTTP-Server",
                        "localhost:" + port,
                        false,
                        e -> {
                            try {
                                Desktop.getDesktop().browse(new URI("http://localhost:" + port));
                            } catch (Exception ex) {
                                INotificationFacade.INSTANCE.error(ex);
                            }
                        }
                );
            } else {

                INotificationFacade.INSTANCE.notify(
                        "JDito-Types outdated",
                        "Your installed JDito-types are not supported. Update your dependencies.",
                        false,
                        null
                );
            }
        } catch (Exception ex) {
            INotificationFacade.INSTANCE.error(ex);
        }
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
    void executeInstall(@NotNull INodeJSEnvironment pNodeJsEnvironment, @NotNull INodeJSExecutor pExecutor, @Nullable ProgressHandle pHandle, @NotNull String pPackage, int pPackageNumber) {
        try {
            pHandle.progress("verifying " + pPackage, pPackageNumber);
            if (!verifyPackageInstallation(pNodeJsEnvironment, pExecutor, pPackage.replaceAll("/@.+/", ""))) {
                pHandle.progress("installing " + pPackage, ++pPackageNumber);
                _LOGGER.info(pExecutor.executeSync(pNodeJsEnvironment, INodeJSExecBase.packageManager(), -1, "i", pPackage, "-g"));
            }
        } catch (Exception e) {
            INotificationFacade.INSTANCE.error(e);
        }
    }

    /**
     *
     * @param pNodeEnv nodejs env
     * @param pModule module name
     * @param pInnerPath innerpath of module
     * @return path
     */
    protected String getAbsolutePathOfModule(INodeJSEnvironment pNodeEnv, String pModule, String pInnerPath) {
        return pNodeEnv.resolveExecBase(INodeJSExecBase.module(pModule, pInnerPath.isEmpty() ? "": pInnerPath)).getAbsolutePath();
    }

    /**
     * Check if the JSDoc JSON file exists, move it to the cache folder and replace the
     *
     * @param pPath original path of the jsdoc.json
     * @param pPort random port for the webserver
     * @return String of the path where the jsdoc.json is stored
     * @throws IOException yes, it might
     */
    @VisibleForTesting
    protected String moveAndOverwriteJSDocContent(@NotNull String pPath, int pPort, String pProjectName, INodeJSEnvironment pNodeEnv) throws IOException {
        //create cache directory with a subpath with the name of the project
        Path newPath = Places.getCacheSubdirectory("help/" + pProjectName).toPath();

        Gson gson = new Gson();

        // Read the JSON from the input file
        InputStream input = this.getClass().getClassLoader().getResourceAsStream("de/adito/aditoweb/nbm/help/jsdoc.json");
        InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(input));
        JsonObject rootObject = gson.fromJson(reader, JsonObject.class);

        // Change the value of the "source" key
        JsonArray includeArray = rootObject.getAsJsonObject("source").getAsJsonArray("include");
        includeArray.remove(0);
        includeArray.add(pPath + "/node_modules/@aditosoftware/jdito-types/dist");
        //includeArray.add(pPath + "/process/"); //comment this in, if you want trouble, and make it double

        // Change the value of the "plugins" key
        JsonArray pluginsArray = rootObject.getAsJsonArray("plugins");
        pluginsArray.set(2, gson.toJsonTree(getAbsolutePathOfModule(pNodeEnv, "better-docs","category.js")));
        pluginsArray.set(3, gson.toJsonTree(getAbsolutePathOfModule(pNodeEnv,"better-docs", "typescript")));

        // Change the value of the "opts" key
        rootObject.getAsJsonObject("opts").addProperty("template", getAbsolutePathOfModule(pNodeEnv,"clean-jsdoc-theme", ""));
        rootObject.getAsJsonObject("opts").addProperty("destination", newPath + "/docs/documentations/");

        // Change the value of the "theme_opts" key
        rootObject.getAsJsonObject("opts").getAsJsonObject("theme_opts").addProperty("base_url", "localhost:" + pPort + "/");

        // Write the updated JSON to a different location
        try (FileWriter fileWriter = new FileWriter(Paths.get(newPath.toString(), "/jsdoc.json").toFile())) {
            JsonWriter jsonWriter = new JsonWriter(fileWriter);
            jsonWriter.setIndent("    ");
            gson.toJson(rootObject, jsonWriter);
        }
        //delete cache subdirectory on exit
        FileUtils.forceDeleteOnExit(Places.getCacheSubdirectory("help/" + pProjectName));

        //OpenProjects.getDefault().isProjectOpen(OpenProjects.getDefault().getMainProject());//PROPERTY_OPEN_PROJECTS.intern();

        return newPath.toString().replaceAll("\\\\", "/");
    }

    /**
     * Executes the "npm list" command and determines, if all the given packages are installed.
     * It does not handle outdated packages - it just verifies, if it is installed.
     *
     * @param pExecutor          executor
     * @param pNodeJsEnvironment environment
     * @param pPackage           packages that should be checked
     * @return boolean depending if the dependency exists
     */
    @VisibleForTesting
    protected boolean verifyPackageInstallation(@NotNull INodeJSEnvironment pNodeJsEnvironment, @NotNull INodeJSExecutor pExecutor, @NotNull String pPackage) throws IOException, InterruptedException, TimeoutException {
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
            //I hate it
            return false;
    }

    /**
     * checks if the currently installed jdito types are valid for this plugin
     *
     * @param pNodeJsEnvironment NodeJS environment
     * @param pExecutor NodeJS Executor
     * @return boolean if the jdito types are correct
     * @throws IOException a
     * @throws InterruptedException b
     * @throws TimeoutException c
     */
    @VisibleForTesting
    protected boolean checkProjectJDitoTypes(@NotNull INodeJSEnvironment pNodeJsEnvironment, @NotNull INodeJSExecutor pExecutor) throws IOException, InterruptedException, TimeoutException {
        // Execute npm list with --json to validate if the package is installed or not
        String result = pExecutor.executeSync(pNodeJsEnvironment, INodeJSExecBase.packageManager(), -1, false, "list", "@aditosoftware/jdito-types", "--json");

        // Remove first line of "result" because npm list returns the execution path as well
        JSONObject obj = new JSONObject(result.substring(result.indexOf("\n") + 1));
        // Search for the dependencies node and save it again in obj -> nullpointer possible if not
        JSONObject dependencies = obj.optJSONObject("dependencies");
        obj = dependencies != null ? dependencies.optJSONObject("@aditosoftware/jdito-types") : null;

        return obj != null && obj.optString("version", "").startsWith("2023");
    }

    /**
     * executes the JSDoc command
     *
     * @param pNodeJsEnv NodeJS environment
     * @param pNodeJsExecutor NodeJS Executor
     * @param pJSDocPath path to jsdoc
     * @throws IOException a
     * @throws InterruptedException b
     * @throws TimeoutException c
     */
    @VisibleForTesting
    protected void executeJSDoc(INodeJSEnvironment pNodeJsEnv, INodeJSExecutor pNodeJsExecutor, String pJSDocPath) throws IOException, InterruptedException, TimeoutException {
        //executing the jsdoc command to render the html files
        _LOGGER.info(pNodeJsExecutor.executeSync(pNodeJsEnv, INodeJSExecBase.node(), -1, pNodeJsEnv.resolveExecBase(INodeJSExecBase.module("jsdoc", "jsdoc.js")).getAbsolutePath(), "--configure", pJSDocPath + "/jsdoc.json", "--verbose"));
    }

    //ToDO: check if project is open and only one instance and close it if neccessary
    //TODO: SHowDocumentation should open if already running idk how but yes
    //OpenProjects fürs öffnen von Projekten

    /**
     * opens the http-server
     *
     * @param pHandle handle for progress
     * @param pNodeJsEnv environment
     * @param pNodeJsExecutor executor
     * @param pJSDocPath path
     * @param pPort port
     * @throws IOException a
     */
    @VisibleForTesting
    protected void executeHttpServer(ProgressHandle pHandle, INodeJSEnvironment pNodeJsEnv, INodeJSExecutor pNodeJsExecutor, String pJSDocPath, int pPort) throws IOException {
        pHandle.progress("Opening HTTP-Server", 16);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        //opening a local server to view the htmls with a random port
        _LOGGER.info(String.valueOf(pNodeJsExecutor.executeAsync(pNodeJsEnv, INodeJSExecBase.node(), outputStream, null, null, pNodeJsEnv.resolveExecBase(INodeJSExecBase.module("http-server", "bin/http-server")).getAbsolutePath(), pJSDocPath + "/docs/documentations", "-p", "" + pPort)));
    }

    /**
     * Get the NodeJS Environemnt for the given project
     *
     * @param pProject project that should be used
     * @return returns the NodeJS Environment of the project
     */
    @VisibleForTesting
    protected INodeJSEnvironment getNodeJSEnvironment(Project pProject) {
        if (pProject == null) return null;

        Optional<INodeJSProvider> nodeJSProvider = INodeJSProvider.findInstance(pProject);

        if (!nodeJSProvider.isPresent()) return null;

        Optional<INodeJSEnvironment> nodeJSEnvironment = nodeJSProvider.get().current().blockingFirst();

        return nodeJSEnvironment.orElse(null);

    }

    /**
     * Get the NodeJS Executor for the given project
     *
     * @param pProject project that should be used
     * @return returns the NodeJS Executor for the project
     */
    @VisibleForTesting
    protected INodeJSExecutor getNodeJSExecutor(Project pProject) {
        if (pProject == null) return null;

        return INodeJSExecutor.findInstance(pProject).orElse(null);
    }
}