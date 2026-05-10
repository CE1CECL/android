/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.model;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactInfo;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.builder.signing.KeystoreHelper;
import com.android.prefs.AndroidLocation;
import com.google.common.collect.Maps;
import junit.framework.TestCase;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.GradleProject;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.KeyStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AndroidProjectTest extends TestCase {

    private final static String MODEL_VERSION = "0.5.0-SNAPSHOT";

    private static final Map<String, ProjectData> sProjectModelMap = Maps.newHashMap();

    private static final class ProjectData {
        AndroidProject model;
        File projectDir;

        static ProjectData create(File projectDir, AndroidProject model) {
            ProjectData projectData = new ProjectData();
            projectData.model = model;
            projectData.projectDir = projectDir;

            return projectData;
        }
    }

    private ProjectData getModelForProject(String projectName) {
        ProjectData projectData = sProjectModelMap.get(projectName);

        if (projectData == null) {
            // Configure the connector and create the connection
            GradleConnector connector = GradleConnector.newConnector();

            File projectDir = new File(getTestDir(), projectName);
            connector.forProjectDirectory(projectDir);

            ProjectConnection connection = connector.connect();
            try {
                // Load the custom model for the project
                AndroidProject model = connection.getModel(AndroidProject.class);
                assertNotNull("Model Object null-check", model);
                assertEquals("Model Name", projectName, model.getName());
                assertEquals("Model version", MODEL_VERSION, model.getModelVersion());

                projectData = ProjectData.create(projectDir, model);

                sProjectModelMap.put(projectName, projectData);

                return projectData;
            } finally {
                connection.close();
            }
        }

        return projectData;
    }

    private Map<String, ProjectData> getModelForMultiProject(String projectName) throws Exception {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(), projectName);
        connector.forProjectDirectory(projectDir);

        Map<String, ProjectData> map = Maps.newHashMap();

        ProjectConnection connection = connector.connect();

        try {
            // Query the default Gradle Model.
            GradleProject model = connection.getModel(GradleProject.class);
            assertNotNull("Model Object null-check", model);

            // Now get the children projects, recursively.
            for (GradleProject child : model.getChildren()) {
                String path = child.getPath();
                String name = path.substring(1);
                File childDir = new File(projectDir, name);

                GradleConnector childConnector = GradleConnector.newConnector();

                childConnector.forProjectDirectory(childDir);

                ProjectConnection childConnection = childConnector.connect();
                try {
                    AndroidProject androidProject = childConnection.getModel(AndroidProject.class);

                    assertNotNull("Model Object null-check for " + path, androidProject);
                    assertEquals("Model Name for " + path, name, androidProject.getName());
                    assertEquals("Model version", MODEL_VERSION, androidProject.getModelVersion());

                    map.put(path, ProjectData.create(childDir, androidProject));

                } catch (UnknownModelException e) {
                    // probably a Java-only project, ignore.
                } finally {
                    childConnection.close();
                }
            }
        } finally {
            connection.close();
        }

        return map;
    }

    public void testBasic() {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("basic");

        AndroidProject model = projectData.model;

        assertFalse("Library Project", model.isLibrary());
        assertEquals("Compile Target", "android-15", model.getCompileTarget());
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty());
    }

    public void testBasicSourceProviders() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("basic");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();
        new SourceProviderTester(model.getName(), projectDir,
                "instrumentTest", defaultConfig.getTestSourceProvider())
                .test();

        Map<String, BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());
    }

    public void testBasicVariantDetails() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("basic");

        AndroidProject model = projectData.model;

        Map<String, Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        // debug variant
        Variant debugVariant = variants.get("Debug");
        assertNotNull("Debug Variant null-check", debugVariant);
        new ProductFlavorTester(debugVariant.getMergedFlavor(), "Debug Merged Flavor")
                .setVersionCode(12)
                .setVersionName("2.0")
                .setMinSdkVersion(16)
                .setTargetSdkVersion(16)
                .test();

        ArtifactInfo debugMainInfo = debugVariant.getMainArtifactInfo();
        assertNotNull("Debug main info null-check", debugMainInfo);
        assertEquals("Debug package name", "com.android.tests.basic.debug",
                debugMainInfo.getPackageName());
        assertTrue("Debug signed check", debugMainInfo.isSigned());
        assertEquals("Debug signingConfig name", "myConfig", debugMainInfo.getSigningConfigName());

        // this variant is tested.
        ArtifactInfo debugTestInfo = debugVariant.getTestArtifactInfo();
        assertNotNull("Test info null-check", debugTestInfo);
        assertEquals("Test package name", "com.android.tests.basic.debug.test",
                debugTestInfo.getPackageName());
        assertNotNull("Test output file null-check", debugTestInfo.getOutputFile());
        assertTrue("Test signed check", debugTestInfo.isSigned());
        assertEquals("Test signingConfig name", "myConfig", debugTestInfo.getSigningConfigName());

        // release variant, not tested.
        Variant releaseVariant = variants.get("Release");
        assertNotNull("Release Variant null-check", releaseVariant);

        ArtifactInfo relMainInfo = releaseVariant.getMainArtifactInfo();
        assertNotNull("Release main info null-check", relMainInfo);
        assertEquals("Release package name", "com.android.tests.basic",
                relMainInfo.getPackageName());
        assertFalse("Release signed check", relMainInfo.isSigned());
        assertNull("Release signingConfig name", relMainInfo.getSigningConfigName());

        ArtifactInfo relTestInfo = releaseVariant.getTestArtifactInfo();
        assertNull("Release test info null-check", relTestInfo);

        // check debug dependencies
        Dependencies dependencies = debugMainInfo.getDependencies();
        assertNotNull(dependencies);
        assertEquals(2, dependencies.getJars().size());
        assertEquals(1, dependencies.getLibraries().size());

        AndroidLibrary lib = dependencies.getLibraries().get(0);
        assertNotNull(lib);
        assertNotNull(lib.getBundle());
        assertNotNull(lib.getFolder());

        assertTrue(dependencies.getProjects().isEmpty());
    }

    public void testBasicSigningConfigs() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("basic");

        AndroidProject model = projectData.model;

        assertEquals("Number of signingConfig", 2, model.getSigningConfigs().size());

        SigningConfig debugSigningConfig = model.getSigningConfigs().get("debug");
        new SigningConfigTester(debugSigningConfig, "debug", true).test();

        SigningConfig mySigningConfig = model.getSigningConfigs().get("myConfig");
        new SigningConfigTester(mySigningConfig, "myConfig", true)
                .setStoreFile(new File(projectData.projectDir, "debug.keystore"))
                .test();
    }

    public void testMigrated() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("migrated");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "migrated", model.getName());
        assertFalse("Library Project", model.isLibrary());

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .setJavaDir("src")
                .setResourcesDir("src")
                .setAidlDir("src")
                .setRenderscriptDir("src")
                .setResDir("res")
                .setAssetsDir("assets")
                .setManifestFile("AndroidManifest.xml")
                .test();

        new SourceProviderTester(model.getName(), projectDir,
                "instrumentTest", defaultConfig.getTestSourceProvider())
                .setJavaDir("tests/java")
                .setResourcesDir("tests/resources")
                .setAidlDir("tests/aidl")
                .setJniDir("tests/jni")
                .setRenderscriptDir("tests/rs")
                .setResDir("tests/res")
                .setAssetsDir("tests/assets")
                .setManifestFile("tests/AndroidManifest.xml")
                .test();
    }

    public void testRenamedApk() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("renamedApk");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "renamedApk", model.getName());

        Map<String, Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        File buildDir = new File(projectDir, "build");

        for (Variant variant : variants.values()) {
            ArtifactInfo mainInfo = variant.getMainArtifactInfo();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo);

            assertEquals("Output file for " + variant.getName(),
                    new File(buildDir, variant.getName() + ".apk"),
                    mainInfo.getOutputFile());
        }
    }

    public void testFlavors() {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject("flavors");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "flavors", model.getName());
        assertFalse("Library Project", model.isLibrary());

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();
        new SourceProviderTester(model.getName(), projectDir,
                "instrumentTest", defaultConfig.getTestSourceProvider())
                .test();

        Map<String, BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());

        Map<String, Variant> variants = model.getVariants();
        assertEquals("Variant Count", 8 , variants.size());

        Variant f1faDebugVariant = variants.get("F1FaDebug");
        assertNotNull("F1faDebug Variant null-check", f1faDebugVariant);
        new ProductFlavorTester(f1faDebugVariant.getMergedFlavor(), "F1faDebug Merged Flavor")
                .test();
        new VariantTester(f1faDebugVariant, projectDir, "flavors-f1fa-debug-unaligned.apk").test();
    }

    public void testTicTacToe() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject("tictactoe");

        ProjectData libModelData = map.get(":lib");
        assertNotNull("lib module model null-check", libModelData);
        assertTrue("lib module library flag", libModelData.model.isLibrary());

        ProjectData appModelData = map.get(":app");
        assertNotNull("app module model null-check", appModelData);

        Dependencies dependencies = appModelData.model.getVariants().get("Debug").getMainArtifactInfo().getDependencies();
        assertNotNull(dependencies);

        List<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());

        AndroidLibrary androidLibrary = libs.get(0);
        assertNotNull(androidLibrary);

        assertEquals("Dependency project path", ":lib", androidLibrary.getProject());

        // TODO: right now we can only test the folder name efficiently
        assertEquals("TictactoeLibUnspecified.aar", androidLibrary.getFolder().getName());
    }

    public void testFlavorLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject("flavorlib");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        assertFalse("Library Project", model.isLibrary());

        Map<String, Variant> variants = model.getVariants();

        ProductFlavorContainer flavor1 = model.getProductFlavors().get("flavor1");
        assertNotNull(flavor1);

        Variant flavor1Debug = variants.get("Flavor1Debug");
        assertNotNull(flavor1Debug);

        Dependencies dependencies = flavor1Debug.getMainArtifactInfo().getDependencies();
        assertNotNull(dependencies);
        List<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        AndroidLibrary androidLibrary = libs.get(0);
        assertNotNull(androidLibrary);
        // TODO: right now we can only test the folder name efficiently
        assertEquals("FlavorlibLib1Unspecified.aar", androidLibrary.getFolder().getName());

        ProductFlavorContainer flavor2 = model.getProductFlavors().get("flavor2");
        assertNotNull(flavor2);

        Variant flavor2Debug = variants.get("Flavor2Debug");
        assertNotNull(flavor2Debug);

        dependencies = flavor2Debug.getMainArtifactInfo().getDependencies();
        assertNotNull(dependencies);
        libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        androidLibrary = libs.get(0);
        assertNotNull(androidLibrary);
        // TODO: right now we can only test the folder name efficiently
        assertEquals("FlavorlibLib2Unspecified.aar", androidLibrary.getFolder().getName());
    }

    public void testMultiproject() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject("multiproject");

        ProjectData baseLibModelData = map.get(":baseLibrary");
        assertNotNull("Module app null-check", baseLibModelData);
        AndroidProject model = baseLibModelData.model;

        Map<String, Variant> variants = model.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = variants.get("Release");
        assertNotNull("Release variant null-check", variant);

        ArtifactInfo mainInfo = variant.getMainArtifactInfo();
        assertNotNull("Main Artifact null-check", mainInfo);

        Dependencies dependencies = mainInfo.getDependencies();
        assertNotNull("Dependencies null-check", dependencies);

        List<String> projects = dependencies.getProjects();
        assertNotNull("project dep list null-check", projects);
        assertEquals("project dep count", 1, projects.size());
        assertEquals("dep on :util check", ":util", projects.get(0));

        List<File> jars = dependencies.getJars();
        assertNotNull("jar dep list null-check", jars);
        // TODO these are jars coming from ':util' They shouldn't be there.
        assertEquals("jar dep count", 2, jars.size());
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return the SDK
     */
    protected File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            }
        }

        throw new IllegalStateException("SDK not defined with ANDROID_HOME");
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f;
                if (System.getenv("IDE_MODE") != null) {
                    f = dir.getParentFile().getParentFile().getParentFile();
                } else {
                    f = dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                    f = new File(f, "tools" + File.separator + "build");
                }
                return f;
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }

    /**
     * Returns the root folder for the tests projects.
     */
    private File getTestDir() {
        File rootDir = getRootDir();
        return new File(rootDir, "tests");
    }

    private static final class ProductFlavorTester {
        @NonNull private final ProductFlavor productFlavor;
        @NonNull private final String name;

        private String packageName = null;
        private int versionCode = -1;
        private String versionName = null;
        private int minSdkVersion = -1;
        private int targetSdkVersion = -1;
        private int renderscriptTargetApi = -1;
        private String testPackageName = null;
        private String testInstrumentationRunner = null;

        ProductFlavorTester(@NonNull ProductFlavor productFlavor, @NonNull String name) {
            this.productFlavor = productFlavor;
            this.name = name;
        }

        ProductFlavorTester setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        ProductFlavorTester setVersionCode(int versionCode) {
            this.versionCode = versionCode;
            return this;
        }

         ProductFlavorTester setVersionName(String versionName) {
            this.versionName = versionName;
            return this;
        }

         ProductFlavorTester setMinSdkVersion(int minSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            return this;
        }

         ProductFlavorTester setTargetSdkVersion(int targetSdkVersion) {
            this.targetSdkVersion = targetSdkVersion;
            return this;
        }

         ProductFlavorTester setRenderscriptTargetApi(int renderscriptTargetApi) {
            this.renderscriptTargetApi = renderscriptTargetApi;
            return this;
        }

         ProductFlavorTester setTestPackageName(String testPackageName) {
            this.testPackageName = testPackageName;
            return this;
        }

         ProductFlavorTester setTestInstrumentationRunner(String testInstrumentationRunner) {
            this.testInstrumentationRunner = testInstrumentationRunner;
            return this;
        }

        void test() {
            assertEquals(name + ":packageName", packageName, productFlavor.getPackageName());
            assertEquals(name + ":VersionCode", versionCode, productFlavor.getVersionCode());
            assertEquals(name + ":VersionName", versionName, productFlavor.getVersionName());
            assertEquals(name + ":minSdkVersion", minSdkVersion, productFlavor.getMinSdkVersion());
            assertEquals(name + ":targetSdkVersion",
                    targetSdkVersion, productFlavor.getTargetSdkVersion());
            assertEquals(name + ":renderscriptTargetApi",
                    renderscriptTargetApi, productFlavor.getRenderscriptTargetApi());
            assertEquals(name + ":testPackageName",
                    testPackageName, productFlavor.getTestPackageName());
            assertEquals(name + ":testInstrumentationRunner",
                    testInstrumentationRunner, productFlavor.getTestInstrumentationRunner());
        }
    }

    private static final class SourceProviderTester {

        @NonNull private final String projectName;
        @NonNull private final String configName;
        @NonNull private final SourceProvider sourceProvider;
        @NonNull private final File projectDir;
        private String javaDir;
        private String resourcesDir;
        private String manifestFile;
        private String resDir;
        private String assetsDir;
        private String aidlDir;
        private String renderscriptDir;
        private String jniDir;

        SourceProviderTester(@NonNull String projectName, @NonNull File projectDir,
                             @NonNull String configName, @NonNull SourceProvider sourceProvider) {
            this.projectName = projectName;
            this.projectDir = projectDir;
            this.configName = configName;
            this.sourceProvider = sourceProvider;
            // configure tester with default relative paths
            setJavaDir("src/" + configName + "/java");
            setResourcesDir("src/" + configName + "/resources");
            setManifestFile("src/" + configName + "/AndroidManifest.xml");
            setResDir("src/" + configName + "/res");
            setAssetsDir("src/" + configName + "/assets");
            setAidlDir("src/" + configName + "/aidl");
            setRenderscriptDir("src/" + configName + "/rs");
            setJniDir("src/" + configName + "/jni");
        }

        SourceProviderTester setJavaDir(String javaDir) {
            this.javaDir = javaDir;
            return this;
        }

        SourceProviderTester setResourcesDir(String resourcesDir) {
            this.resourcesDir = resourcesDir;
            return this;
        }

        SourceProviderTester setManifestFile(String manifestFile) {
            this.manifestFile = manifestFile;
            return this;
        }

        SourceProviderTester setResDir(String resDir) {
            this.resDir = resDir;
            return this;
        }

        SourceProviderTester setAssetsDir(String assetsDir) {
            this.assetsDir = assetsDir;
            return this;
        }

        SourceProviderTester setAidlDir(String aidlDir) {
            this.aidlDir = aidlDir;
            return this;
        }

        SourceProviderTester setRenderscriptDir(String renderscriptDir) {
            this.renderscriptDir = renderscriptDir;
            return this;
        }

        SourceProviderTester setJniDir(String jniDir) {
            this.jniDir = jniDir;
            return this;
        }

        void test() {
            testSinglePathSet("java", javaDir, sourceProvider.getJavaDirectories());
            testSinglePathSet("resources", resourcesDir, sourceProvider.getResourcesDirectories());
            testSinglePathSet("res", resDir, sourceProvider.getResDirectories());
            testSinglePathSet("assets", assetsDir, sourceProvider.getAssetsDirectories());
            testSinglePathSet("aidl", aidlDir, sourceProvider.getAidlDirectories());
            testSinglePathSet("rs", renderscriptDir, sourceProvider.getRenderscriptDirectories());
            testSinglePathSet("jni", jniDir, sourceProvider.getJniDirectories());

            assertEquals("AndroidManifest",
                    new File(projectDir, manifestFile).getAbsolutePath(),
                    sourceProvider.getManifestFile().getAbsolutePath());
        }

        private void testSinglePathSet(String setName, String referencePath, Set<File> pathSet) {
            assertEquals(1, pathSet.size());
            assertEquals(projectName + ": " + configName + "/" + setName,
                    new File(projectDir, referencePath).getAbsolutePath(),
                    pathSet.iterator().next().getAbsolutePath());
        }

    }

    private static final class VariantTester {

        private final Variant variant;
        private final File projectDir;
        private final String outputFileName;

        VariantTester(Variant variant, File projectDir, String outputFileName) {
            this.variant = variant;
            this.projectDir = projectDir;
            this.outputFileName = outputFileName;
        }

        void test() {
            String variantName = variant.getName();
            File build = new File(projectDir,  "build");
            File apk = new File(build, "apk/" + outputFileName);
            assertEquals(variantName + " output", apk, variant.getMainArtifactInfo().getOutputFile());

            List<File> sourceFolders = variant.getMainArtifactInfo().getGeneratedSourceFolders();
            assertEquals("Gen src Folder count", 4, sourceFolders.size());
        }
    }

    private static final class SigningConfigTester {

        public static final String DEFAULT_PASSWORD = "android";
        public static final String DEFAULT_ALIAS = "AndroidDebugKey";

        @NonNull private final SigningConfig signingConfig;
        @NonNull private final String name;
        private File storeFile = null;
        private String storePassword = null;
        private String keyAlias = null;
        private String keyPassword = null;
        private String storeType = KeyStore.getDefaultType();
        private boolean isSigningReady = false;

        SigningConfigTester(@NonNull SigningConfig signingConfig, @NonNull String name,
                            boolean isDebug) throws AndroidLocation.AndroidLocationException {
            assertNotNull(String.format("SigningConfig '%s' null-check", name), signingConfig);
            this.signingConfig = signingConfig;
            this.name = name;

            if (isDebug) {
                storeFile =  new File(KeystoreHelper.defaultDebugKeystoreLocation());
                storePassword = DEFAULT_PASSWORD;
                keyAlias = DEFAULT_ALIAS;
                keyPassword = DEFAULT_PASSWORD;
                isSigningReady = true;
            }
        }

        SigningConfigTester setStoreFile(File storeFile) {
            this.storeFile = storeFile;
            return this;
        }

        SigningConfigTester setStorePassword(String storePassword) {
            this.storePassword = storePassword;
            return this;
        }

        SigningConfigTester setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
            return this;
        }

        SigningConfigTester setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        SigningConfigTester setStoreType(String storeType) {
            this.storeType = storeType;
            return this;
        }

        SigningConfigTester setSigningReady(boolean isSigningReady) {
            this.isSigningReady = isSigningReady;
            return this;
        }

        void test() {
            assertEquals("SigningConfig name", name, signingConfig.getName());

            assertEquals(String.format("SigningConfig '%s' storeFile", name),
                    storeFile, signingConfig.getStoreFile());

            assertEquals(String.format("SigningConfig '%s' storePassword", name),
                    storePassword, signingConfig.getStorePassword());

            String scAlias = signingConfig.getKeyAlias();
            assertEquals(String.format("SigningConfig '%s' keyAlias", name),
                    keyAlias != null ? keyAlias.toLowerCase(Locale.getDefault()) : keyAlias,
                    scAlias != null ? scAlias.toLowerCase(Locale.getDefault()) : scAlias);

            assertEquals(String.format("SigningConfig '%s' keyPassword", name),
                    keyPassword, signingConfig.getKeyPassword());

            assertEquals(String.format("SigningConfig '%s' storeType", name),
                    storeType, signingConfig.getStoreType());

            assertEquals(String.format("SigningConfig '%s' isSigningReady", name),
                    isSigningReady, signingConfig.isSigningReady());
        }
    }
}
