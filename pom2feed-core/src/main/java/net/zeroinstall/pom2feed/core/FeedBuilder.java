package net.zeroinstall.pom2feed.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import net.zeroinstall.model.*;
import org.apache.maven.model.*;
import static net.zeroinstall.pom2feed.core.FeedUtils.*;
import static net.zeroinstall.pom2feed.core.MavenUtils.*;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Iteratively builds Zero Install feeds using data from Maven projects.
 */
public class FeedBuilder {

    /**
     * The base URL of the Maven repository used to provide binaries.
     */
    private final URL mavenRepository;
    /**
     * The base URL of the pom2feed service used to provide dependencies.
     */
    private final URL pom2feedService;
    private final InterfaceDocument document;
    private final Feed feed;

    /**
     * Creates feed builder for a new feed.
     *
     * @param mavenRepository The base URL of the Maven repository used to
     * provide binaries.
     * @param pom2feedService The base URL of the pom2feed service used to
     * provide dependencies.
     */
    public FeedBuilder(URL mavenRepository, URL pom2feedService) {
        this.mavenRepository = checkNotNull(mavenRepository);
        this.pom2feedService = checkNotNull(pom2feedService);
        this.document = InterfaceDocument.Factory.newInstance();
        this.feed = document.addNewInterface();
    }

    /**
     * Creates a feed builder an existing feed.
     *
     * @param pom2feedService The base URL of the pom2feed service used to
     * provide dependencies.
     * @param document The existing feed document.
     */
    public FeedBuilder(URL mavenRepository, URL pom2feedService, InterfaceDocument document) {
        this.mavenRepository = checkNotNull(mavenRepository);
        this.pom2feedService = checkNotNull(pom2feedService);
        this.document = checkNotNull(document);
        this.feed = (document.getInterface() != null) ? document.getInterface() : document.addNewInterface();
    }

    /**
     * Returns the generated feed/interface.
     *
     * @return An XML representation of the feed/interface.
     */
    public InterfaceDocument getDocument() {
        return document;
    }

    /**
     * Fills the feed with project-wide metadata from a Maven model.
     *
     * @param model The Maven model to extract the metadata from. Should be from
     * the latest version of the project.
     * @return The {@link FeedBuilder} instance for calling further methods in a
     * fluent fashion.
     */
    public FeedBuilder addMetadata(Model model) {
        checkNotNull(model);

        feed.addName(model.getName());
        feed.addNewSummary().setStringValue("Auto-generated feed for " + model.getGroupId() + "." + model.getArtifactId());
        if (!isNullOrEmpty(model.getDescription())) {
            feed.addNewDescription().setStringValue(model.getDescription());
        }
        if (!isNullOrEmpty(model.getUrl())) {
            feed.addHomepage(model.getUrl());
        }
        return this;
    }

    /**
     * Adds a local-path implementation to the feed using version and dependency
     * information from a Maven model.
     *
     * @param model The Maven model to extract the version and dependency
     * information from.
     * @return The {@link FeedBuilder} instance for calling further methods in a
     * fluent fashion.
     */
    public FeedBuilder addLocalImplementation(Model model) {
        checkNotNull(model);

        Implementation implementation = addNewImplementation(model);
        addDependencies(implementation, model);

        Command command = addNewCommand(implementation);
        command.setPath(getArtifactLocalFileName(model));

        implementation.setId(".");
        implementation.setLocalPath(".");
        return this;
    }

    /**
     * Adds a "download single file" implementation to the feed using version
     * and dependency information from a Maven model.
     *
     * @param model The Maven model to extract the version and dependency
     * information from.
     * @return The {@link FeedBuilder} instance for calling further methods in a
     * fluent fashion.
     */
    public FeedBuilder addRemoteImplementation(Model model) throws IOException {
        checkNotNull(model);

        Implementation implementation = addNewImplementation(model);
        addDependencies(implementation, model);

        Command command = addNewCommand(implementation);
        command.setPath(getArtifactFileName(model.getArtifactId(), model.getVersion(), model.getPackaging()));

        addFile(implementation, model);
        return this;
    }

    /**
     * Adds an implementation to the feed using version and dependency
     * information from a Maven model.
     *
     * @param model The Maven model to extract the version and dependency
     * information from.
     * @return The implementation that was created and added to the feed.
     */
    private Implementation addNewImplementation(Model model) {
        Implementation implementation = feed.addNewImplementation();
        implementation.setVersion(pom2feedVersion(model.getVersion()));
        if (!model.getLicenses().isEmpty()) {
            implementation.setLicense(model.getLicenses().get(0).getName());
        }
        return implementation;
    }

    /**
     * Adds a Java run command for an implementation.
     *
     * @param implementation The implementation to add the command to.
     */
    private Command addNewCommand(Implementation implementation) {
        Command command = implementation.addNewCommand();
        command.setName("run");

        Runner runner = command.addNewRunner();
        runner.setInterface("http://repo.roscidus.com/java/openjdk-jre");
        runner.addArg("-jar");

        return command;
    }

    /**
     * Converts Maven dependencies to Zero Install dependencies.
     *
     * @param implementation The implementation to add the dependencies to.
     * @param model The Maven model to extract the dependencies from.
     */
    private void addDependencies(Implementation implementation, Model model) {
        if (model.getBuild() != null && model.getBuild().getPluginsAsMap() != null) {
            Plugin compilerPlugin = model.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-compiler-plugin");
            if (compilerPlugin != null) {
                Xpp3Dom config = (Xpp3Dom) compilerPlugin.getConfiguration();
                String javaVersion = config.getChild("target").getValue();
                if (!isNullOrEmpty(javaVersion)) {
                    net.zeroinstall.model.Dependency javaDep = implementation.addNewRequires();
                    javaDep.setInterface("http://repo.roscidus.com/java/openjdk-jre");
                    Constraint constraint = javaDep.addNewVersion2();
                    constraint.setNotBefore(javaVersion);
                }
            }
        }

        for (org.apache.maven.model.Dependency mavenDep : model.getDependencies()) {
            net.zeroinstall.model.Dependency ziDep = implementation.addNewRequires();
            ziDep.setInterface(MavenUtils.getServiceUrl(pom2feedService, mavenDep.getGroupId(), mavenDep.getArtifactId()));
            ziDep.setVersion(FeedUtils.pom2feedVersion(mavenDep.getVersion()));

            Environment environment = ziDep.addNewEnvironment();
            environment.setName("CLASSPATH");
            environment.setInsert(".");
        }
    }

    /**
     * Creates a file download entry for a JAR hosted in a Maven repository.
     *
     * @param implementation The implementation to add the download entry to.
     * @param model The Maven model describing the artifact.
     * @throws IOException
     */
    private void addFile(Implementation implementation, Model model) throws IOException {
        String fileUrl = getArtifactFileUrl(mavenRepository, model.getGroupId(), model.getArtifactId(), model.getVersion(), model.getPackaging());

        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("HEAD");
        long size = connection.getContentLength();

        InputStream stream = new URL(fileUrl.toString() + ".sha1").openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String hash = reader.readLine();

        String fileName = getArtifactFileName(model.getArtifactId(), model.getVersion(), model.getPackaging());

        File file = implementation.addNewFile();
        file.setHref(fileUrl);
        file.setSize(size);
        file.setDest(fileName);

        ManifestDigest digest = implementation.addNewManifestDigest();
        digest.setSha1New(getSha1ManifestDigest(hash, size, fileName));
        implementation.setId("sha1new=" + digest.getSha1New());
    }
}
