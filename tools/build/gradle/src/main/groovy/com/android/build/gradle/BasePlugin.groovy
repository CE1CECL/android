/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.Sdk
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.dependency.DependencyChecker
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.android.build.gradle.internal.dependency.SymbolFileProviderImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestLibraryTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.InstallTask
import com.android.build.gradle.internal.tasks.OutputFileTask
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.LibraryVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.TestedVariantData
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.Dex
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.MergeAssets
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.ProcessAppManifest
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ZipAlign
import com.android.builder.AndroidBuilder
import com.android.builder.DefaultProductFlavor
import com.android.builder.SdkParser
import com.android.builder.VariantConfiguration
import com.android.builder.dependency.JarDependency
import com.android.builder.dependency.LibraryDependency
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GUtil
import proguard.gradle.ProGuardTask

import java.util.jar.Attributes
import java.util.jar.Manifest

import static com.android.builder.BuilderConstants.CONNECTED
import static com.android.builder.BuilderConstants.DEVICE
import static com.android.builder.BuilderConstants.EXT_LIB_ARCHIVE
import static com.android.builder.BuilderConstants.FD_FLAVORS
import static com.android.builder.BuilderConstants.FD_FLAVORS_ALL
import static com.android.builder.BuilderConstants.FD_INSTRUMENT_RESULTS
import static com.android.builder.BuilderConstants.FD_INSTRUMENT_TESTS
import static com.android.builder.BuilderConstants.FD_REPORTS
import static com.android.builder.BuilderConstants.INSTRUMENT_TEST

/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin {
    public static final String GRADLE_MIN_VERSION = "1.6"
    public static final String[] GRADLE_SUPPORTED_VERSIONS = [ GRADLE_MIN_VERSION ]

    public static final String INSTALL_GROUP = "Install"

    public static File TEST_SDK_DIR;

    protected Instantiator instantiator
    private ToolingModelBuilderRegistry registry

    private final Map<Object, AndroidBuilder> builders = Maps.newIdentityHashMap()

    final List<BaseVariantData> variantDataList = []
    final Map<LibraryDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]
    final Map<SigningConfig, ValidateSigningTask> validateSigningTaskMap = [:]

    protected Project project
    private LoggerWrapper loggerWrapper
    private Sdk sdk
    private String creator

    private boolean hasCreatedTasks = false

    private ProductFlavorData<DefaultProductFlavor> defaultConfigData
    protected DefaultAndroidSourceSet mainSourceSet
    protected DefaultAndroidSourceSet testSourceSet

    protected Task uninstallAll
    protected Task assembleTest
    protected Task deviceCheck
    protected Task connectedCheck

    protected BasePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
        this.registry = registry
        String pluginVersion = getLocalVersion()
        if (pluginVersion != null) {
            creator = "Android Gradle " + pluginVersion
        } else  {
            creator = "Android Gradle"
        }
    }

    protected abstract BaseExtension getExtension()
    protected abstract void doCreateAndroidTasks()

    protected void apply(Project project) {
        this.project = project

        checkGradleVersion()
        sdk = new Sdk(project, logger)

        project.apply plugin: JavaBasePlugin

        // Register a builder for the custom tooling model
        registry.register(new ModelBuilder());

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

        uninstallAll = project.tasks.create("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = INSTALL_GROUP

        deviceCheck = project.tasks.create("deviceCheck")
        deviceCheck.description = "Runs all device checks using Device Providers and Test Servers."
        deviceCheck.group = JavaBasePlugin.VERIFICATION_GROUP

        connectedCheck = project.tasks.create("connectedCheck")
        connectedCheck.description = "Runs all device checks on currently connected devices."
        connectedCheck.group = JavaBasePlugin.VERIFICATION_GROUP

        project.afterEvaluate {
            createAndroidTasks(false)
        }
    }

    protected void setBaseExtension(@NonNull BaseExtension extension) {
        sdk.setExtension(extension)
        mainSourceSet = (DefaultAndroidSourceSet) extension.sourceSets.create(extension.defaultConfig.name)
        testSourceSet = (DefaultAndroidSourceSet) extension.sourceSets.create(INSTRUMENT_TEST)

        defaultConfigData = new ProductFlavorData<DefaultProductFlavor>(
                extension.defaultConfig, mainSourceSet,
                testSourceSet, project)
    }

    private void checkGradleVersion() {
        boolean foundMatch = false
        for (String version : GRADLE_SUPPORTED_VERSIONS) {
            if (project.getGradle().gradleVersion.startsWith(version)) {
                foundMatch = true
                break
            }
        }

        if (!foundMatch) {
            throw new BuildException(
                    String.format(
                            "Gradle version %s is required. Current version is %s",
                            GRADLE_MIN_VERSION, project.getGradle().gradleVersion), null);

        }
    }

    final void createAndroidTasks(boolean force) {
        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.")
        }

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force && (!project.state.executed || project.state.failure != null) && TEST_SDK_DIR == null) {
            return
        }

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        doCreateAndroidTasks()
        createReportTasks()
    }

    void checkTasksAlreadyCreated() {
        if (hasCreatedTasks) {
            throw new GradleException(
                    "Android tasks have already been created.\n" +
                    "This happens when calling android.applicationVariants,\n" +
                    "android.libraryVariants or android.testVariants.\n" +
                    "Once these methods are called, it is not possible to\n" +
                    "continue configuring the model.")
        }
    }


    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    SdkParser getSdkParser() {
        return sdk.parser
    }

    SdkParser getLoadedSdkParser() {
        return sdk.loadParser()
    }

    File getSdkDirectory() {
        return sdk.directory
    }

    ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(BaseVariantData variantData) {
        AndroidBuilder androidBuilder = builders.get(variantData)

        if (androidBuilder == null) {
            SdkParser parser = getLoadedSdkParser()
            androidBuilder = new AndroidBuilder(parser, creator, logger, verbose)

            builders.put(variantData, androidBuilder)
        }

        return androidBuilder
    }


    protected String getRuntimeJars() {
        return runtimeJarList.join(File.pathSeparator)
    }

    public List<String> getRuntimeJarList() {
        SdkParser sdkParser = getLoadedSdkParser()
        return AndroidBuilder.getBootClasspath(sdkParser);
    }

    protected void createProcessManifestTask(BaseVariantData variantData,
                                             String manifestOurDir) {
        def processManifestTask = project.tasks.create("process${variantData.name}Manifest",
                ProcessAppManifest)
        variantData.processManifestTask = processManifestTask
        processManifestTask.dependsOn variantData.prepareDependenciesTask

        processManifestTask.plugin = this
        processManifestTask.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration
        ProductFlavor mergedFlavor = config.mergedFlavor

        processManifestTask.conventionMapping.mainManifest = {
            config.mainManifest
        }
        processManifestTask.conventionMapping.manifestOverlays = {
            config.manifestOverlays
        }
        processManifestTask.conventionMapping.packageNameOverride = {
            config.packageOverride
        }
        processManifestTask.conventionMapping.versionName = {
            config.versionName
        }
        processManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processManifestTask.conventionMapping.versionCode = {
            mergedFlavor.versionCode
        }
        processManifestTask.conventionMapping.minSdkVersion = {
            mergedFlavor.minSdkVersion
        }
        processManifestTask.conventionMapping.targetSdkVersion = {
            mergedFlavor.targetSdkVersion
        }
        processManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variantData.dirName/AndroidManifest.xml")
        }
    }

    protected void createProcessTestManifestTask(BaseVariantData variantData,
                                                 String manifestOurDir) {
        def processTestManifestTask = project.tasks.create("process${variantData.name}TestManifest",
                ProcessTestManifest)
        variantData.processManifestTask = processTestManifestTask
        processTestManifestTask.dependsOn variantData.prepareDependenciesTask

        processTestManifestTask.plugin = this
        processTestManifestTask.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration

        processTestManifestTask.conventionMapping.testPackageName = {
            config.packageName
        }
        processTestManifestTask.conventionMapping.minSdkVersion = {
            config.minSdkVersion
        }
        processTestManifestTask.conventionMapping.targetSdkVersion = {
            config.targetSdkVersion
        }
        processTestManifestTask.conventionMapping.testedPackageName = {
            config.testedPackageName
        }
        processTestManifestTask.conventionMapping.instrumentationRunner = {
            config.instrumentationRunner
        }
        processTestManifestTask.conventionMapping.libraries = {
            getManifestDependencies(config.directLibraries)
        }
        processTestManifestTask.conventionMapping.manifestOutputFile = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variantData.dirName/AndroidManifest.xml")
        }
    }

    protected void createRenderscriptTask(BaseVariantData variantData) {
        VariantConfiguration config = variantData.variantConfiguration

        def renderscriptTask = project.tasks.create("compile${variantData.name}Renderscript",
                RenderscriptCompile)
        variantData.renderscriptCompileTask = renderscriptTask

        renderscriptTask.dependsOn variantData.prepareDependenciesTask
        renderscriptTask.plugin = this
        renderscriptTask.variant = variantData

        renderscriptTask.targetApi = config.mergedFlavor.renderscriptTargetApi
        renderscriptTask.debugBuild = config.buildType.renderscriptDebugBuild
        renderscriptTask.optimLevel = config.buildType.renderscriptOptimLevel

        renderscriptTask.conventionMapping.sourceDirs = { config.renderscriptSourceList }
        renderscriptTask.conventionMapping.importDirs = { config.renderscriptImports }

        renderscriptTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/rs/$variantData.dirName")
        }
        renderscriptTask.conventionMapping.resOutputDir = {
            project.file("$project.buildDir/res/rs/$variantData.dirName")
        }
    }

    protected void createMergeResourcesTask(@NonNull BaseVariantData variantData,
                                            final boolean process9Patch) {
        MergeResources mergeResourcesTask = basicCreateMergeResourcesTask(
                variantData,
                "merge",
                "$project.buildDir/res/all/$variantData.dirName",
                true /*includeDependencies*/,
                process9Patch)
        variantData.mergeResourcesTask = mergeResourcesTask
    }

    protected MergeResources basicCreateMergeResourcesTask(
            @NonNull BaseVariantData variantData,
            @NonNull String taskNamePrefix,
            @NonNull String outputLocation,
            final boolean includeDependencies,
            final boolean process9Patch) {
        MergeResources mergeResourcesTask = project.tasks.create(
                "$taskNamePrefix${variantData.name}Resources", MergeResources)

        mergeResourcesTask.dependsOn variantData.prepareDependenciesTask, variantData.renderscriptCompileTask
        mergeResourcesTask.plugin = this
        mergeResourcesTask.variant = variantData
        mergeResourcesTask.incrementalFolder = project.file(
                "$project.buildDir/incremental/${taskNamePrefix}Resources/$variantData.dirName")

        mergeResourcesTask.process9Patch = process9Patch

        mergeResourcesTask.conventionMapping.inputResourceSets = {
            variantData.variantConfiguration.getResourceSets(
                    variantData.renderscriptCompileTask.getResOutputDir(),
                    includeDependencies)
        }

        mergeResourcesTask.conventionMapping.outputDir = { project.file(outputLocation) }

        return mergeResourcesTask
    }

    protected void createMergeAssetsTask(@NonNull BaseVariantData variantData,
                                         @Nullable String outputLocation,
                                         final boolean includeDependencies) {
        if (outputLocation == null) {
            outputLocation = "$project.buildDir/assets/$variantData.dirName"
        }

        def mergeAssetsTask = project.tasks.create("merge${variantData.name}Assets", MergeAssets)
        variantData.mergeAssetsTask = mergeAssetsTask

        mergeAssetsTask.dependsOn variantData.prepareDependenciesTask
        mergeAssetsTask.plugin = this
        mergeAssetsTask.variant = variantData
        mergeAssetsTask.incrementalFolder =
            project.file("$project.buildDir/incremental/mergeAssets/$variantData.dirName")

        mergeAssetsTask.conventionMapping.inputAssetSets = {
            variantData.variantConfiguration.getAssetSets(includeDependencies)
        }
        mergeAssetsTask.conventionMapping.outputDir = { project.file(outputLocation) }
    }

    protected void createBuildConfigTask(BaseVariantData variantData) {
        def generateBuildConfigTask = project.tasks.create(
                "generate${variantData.name}BuildConfig", GenerateBuildConfig)
        variantData.generateBuildConfigTask = generateBuildConfigTask

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        if (variantConfiguration.type == VariantConfiguration.Type.TEST) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.
            generateBuildConfigTask.dependsOn variantData.processManifestTask
        }

        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variantData

        generateBuildConfigTask.conventionMapping.packageName = {
            variantConfiguration.originalPackageName
        }

        generateBuildConfigTask.conventionMapping.debuggable = {
            variantConfiguration.buildType.isDebuggable()
        }

        generateBuildConfigTask.conventionMapping.javaLines = {
            variantConfiguration.buildConfigLines
        }

        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/buildConfig/${variantData.dirName}")
        }
    }

    protected void createProcessResTask(BaseVariantData variantData) {
        createProcessResTask(variantData, "$project.buildDir/symbols/$variantData.dirName")
    }

    protected void createProcessResTask(BaseVariantData variantData, final String symbolLocation) {
        def processResources = project.tasks.create("process${variantData.name}Resources",
                ProcessAndroidResources)
        variantData.processResourcesTask = processResources
        processResources.dependsOn variantData.processManifestTask, variantData.mergeResourcesTask, variantData.mergeAssetsTask

        processResources.plugin = this
        processResources.variant = variantData

        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        processResources.conventionMapping.manifestFile = {
            variantData.processManifestTask.manifestOutputFile
        }

        processResources.conventionMapping.resDir = {
            variantData.mergeResourcesTask.outputDir
        }

        processResources.conventionMapping.assetsDir =  {
            variantData.mergeAssetsTask.outputDir
        }

        processResources.conventionMapping.libraries = {
            getTextSymbolDependencies(variantConfiguration.allLibraries)
        }
        processResources.conventionMapping.packageForR = {
            variantConfiguration.originalPackageName
        }

        // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/r/$variantData.dirName")
        }
        processResources.conventionMapping.textSymbolOutputDir = {
            project.file(symbolLocation)
        }
        processResources.conventionMapping.packageOutputFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variantData.baseName}.ap_")
        }
        if (variantData.runProguard) {
            processResources.conventionMapping.proguardOutputFile = {
                project.file("$project.buildDir/proguard/${variantData.dirName}/aapt_rules.txt")
            }
        }

        processResources.conventionMapping.type = { variantConfiguration.type }
        processResources.conventionMapping.debuggable = { variantConfiguration.buildType.debuggable }
        processResources.conventionMapping.aaptOptions = { extension.aaptOptions }
    }

    protected void createProcessJavaResTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        Copy processResources = project.tasks.create("process${variantData.name}JavaRes",
                ProcessResources);
        variantData.processJavaResources = processResources

        // set the input
        processResources.from(((AndroidSourceSet) variantConfiguration.defaultSourceSet).resources)

        if (variantConfiguration.type != VariantConfiguration.Type.TEST) {
            processResources.from(
                    ((AndroidSourceSet) variantConfiguration.buildTypeSourceSet).resources)
        }
        if (variantConfiguration.hasFlavors()) {
            for (SourceProvider flavorSourceSet : variantConfiguration.flavorSourceSets) {
                processResources.from(((AndroidSourceSet) flavorSourceSet).resources)
            }
        }

        processResources.conventionMapping.destinationDir = {
            project.file("$project.buildDir/javaResources/$variantData.dirName")
        }
    }

    protected void createAidlTask(BaseVariantData variantData) {
        VariantConfiguration variantConfiguration = variantData.variantConfiguration

        def compileTask = project.tasks.create("compile${variantData.name}Aidl", AidlCompile)
        variantData.aidlCompileTask = compileTask
        variantData.aidlCompileTask.dependsOn variantData.prepareDependenciesTask

        compileTask.plugin = this
        compileTask.variant = variantData
        compileTask.incrementalFolder =
            project.file("$project.buildDir/incremental/aidl/$variantData.dirName")


        compileTask.conventionMapping.sourceDirs = { variantConfiguration.aidlSourceList }
        compileTask.conventionMapping.importDirs = { variantConfiguration.aidlImports }

        compileTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/aidl/$variantData.dirName")
        }
    }

    protected void createCompileTask(BaseVariantData variantData,
                                     BaseVariantData testedVariantData) {
        def compileTask = project.tasks.create("compile${variantData.name}", JavaCompile)
        variantData.javaCompileTask = compileTask
        compileTask.dependsOn variantData.processResourcesTask, variantData.generateBuildConfigTask, variantData.aidlCompileTask

        VariantConfiguration config = variantData.variantConfiguration

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).java)
        sourceList.add({ variantData.processResourcesTask.sourceOutputDir })
        sourceList.add({ variantData.generateBuildConfigTask.sourceOutputDir })
        sourceList.add({ variantData.aidlCompileTask.sourceOutputDir })
        sourceList.add({ variantData.renderscriptCompileTask.sourceOutputDir })

        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).java)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(((AndroidSourceSet) flavorSourceSet).java)
            }
        }
        compileTask.source = sourceList.toArray()

        // if the tested variant is an app, add its classpath. For the libraries,
        // it's done automatically since the classpath includes the library output as a normal
        // dependency.
        if (testedVariantData instanceof ApplicationVariantData) {
            compileTask.conventionMapping.classpath =  {
                project.files(config.compileClasspath) + testedVariantData.javaCompileTask.classpath + testedVariantData.javaCompileTask.outputs.files
            }
        } else {
            compileTask.conventionMapping.classpath =  {
                project.files(config.compileClasspath)
            }
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn project.configurations.compile.buildDependencies

        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/$variantData.dirName")
        }
        compileTask.conventionMapping.dependencyCacheDir = {
            project.file("$project.buildDir/dependency-cache/$variantData.dirName")
        }

        // set source/target compatibility
        compileTask.conventionMapping.sourceCompatibility = {
            extension.compileOptions.sourceCompatibility.toString()
        }
        compileTask.conventionMapping.targetCompatibility = {
            extension.compileOptions.targetCompatibility.toString()
        }

        // setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed.
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars()
        }
    }

    /**
     * Creates the tasks to build the test apk.
     *
     * @param variant the test variant
     * @param testedVariant the tested variant
     * @param configDependencies the list of config dependencies
     */
    protected void createTestApkTasks(@NonNull TestVariantData variantData,
                                      @NonNull BaseVariantData testedVariantData) {
        // The test app is signed with the same info as the tested app so there's no need
        // to test both.
        if (!variantData.isSigned()) {
            throw new GradleException(
                    "Tested Variant '${testedVariantData.name}' is not configured to create a signed APK.")
        }

        createPrepareDependenciesTask(variantData)

        // Add a task to process the manifest
        createProcessTestManifestTask(variantData, "manifests")

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantData)

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantData, true /*process9Patch*/)

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/)

        if (testedVariantData.variantConfiguration.type == VariantConfiguration.Type.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantData.assembleTask != null) {
                variantData.processManifestTask.dependsOn testedVariantData.assembleTask
                variantData.mergeResourcesTask.dependsOn testedVariantData.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantData)

        // Add a task to generate resource source files
        createProcessResTask(variantData)

        // process java resources
        createProcessJavaResTask(variantData)

        createAidlTask(variantData)

        // Add a task to compile the test application
        createCompileTask(variantData, testedVariantData)

        addPackageTasks(variantData, null)

        if (assembleTest != null) {
            assembleTest.dependsOn variantData.assembleTask
        }
    }

    protected void createCheckTasks(boolean hasFlavors, boolean isLibraryTest) {
        List<AndroidReportTask> reportTasks = Lists.newArrayListWithExpectedSize(2)

        List<DeviceProvider> providers = extension.deviceProviders
        List<TestServer> servers = extension.testServers

        Task mainConnectedTask = connectedCheck
        String connectedRootName = "${CONNECTED}${INSTRUMENT_TEST.capitalize()}"
        // if more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.
        if (hasFlavors) {
            mainConnectedTask = project.tasks.create(connectedRootName, AndroidReportTask)
            mainConnectedTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainConnectedTask.description = "Installs and runs instrumentation tests for all flavors on connected devices."
            mainConnectedTask.reportType = ReportType.MULTI_FLAVOR
            connectedCheck.dependsOn mainConnectedTask

            mainConnectedTask.conventionMapping.resultsDir = {
                String rootLocation = extension.testOptions.resultsDir != null ?
                    extension.testOptions.resultsDir : "$project.buildDir/$FD_INSTRUMENT_RESULTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }
            mainConnectedTask.conventionMapping.reportsDir = {
                String rootLocation = extension.testOptions.reportDir != null ?
                    extension.testOptions.reportDir :
                    "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

                project.file("$rootLocation/connected/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainConnectedTask)
        }

        Task mainProviderTask = deviceCheck
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            mainProviderTask = project.tasks.create("${DEVICE}${INSTRUMENT_TEST.capitalize()}",
                    AndroidReportTask)
            mainProviderTask.group = JavaBasePlugin.VERIFICATION_GROUP
            mainProviderTask.description = "Installs and runs instrumentation tests using all Device Providers."
            mainProviderTask.reportType = ReportType.MULTI_FLAVOR
            deviceCheck.dependsOn mainProviderTask

            mainProviderTask.conventionMapping.resultsDir = {
                String rootLocation = extension.testOptions.resultsDir != null ?
                    extension.testOptions.resultsDir : "$project.buildDir/$FD_INSTRUMENT_RESULTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }
            mainProviderTask.conventionMapping.reportsDir = {
                String rootLocation = extension.testOptions.reportDir != null ?
                    extension.testOptions.reportDir :
                    "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

                project.file("$rootLocation/devices/$FD_FLAVORS_ALL")
            }

            reportTasks.add(mainProviderTask)
        }

        // now look for the testedvariant and create the check tasks for them.
        // don't use an auto loop as we can't reuse baseVariantData or the closure lower
        // gets broken.
        int count = variantDataList.size();
        for (int i = 0 ; i < count ; i++) {
            final BaseVariantData baseVariantData = variantDataList.get(i);
            if (baseVariantData instanceof TestedVariantData) {
                final TestVariantData testVariantData = ((TestedVariantData) baseVariantData).testVariantData
                if (testVariantData == null) {
                    continue
                }

                // create the check tasks for this test

                // first the connected one.
                def connectedTask = createDeviceProviderInstrumentTestTask(
                        hasFlavors ?
                            "${connectedRootName}${baseVariantData.name}" : connectedRootName,
                        "Installs and runs the tests for Build '${baseVariantData.name}' on connected devices.",
                        isLibraryTest ?
                            DeviceProviderInstrumentTestLibraryTask :
                            DeviceProviderInstrumentTestTask,
                        testVariantData,
                        baseVariantData,
                        new ConnectedDeviceProvider(getSdkParser()),
                        CONNECTED
                )

                mainConnectedTask.dependsOn connectedTask
                testVariantData.connectedTestTask = connectedTask

                // now the providers.
                for (DeviceProvider deviceProvider : providers) {
                    DefaultTask providerTask = createDeviceProviderInstrumentTestTask(
                            hasFlavors ?
                                "${deviceProvider.name}${INSTRUMENT_TEST.capitalize()}${baseVariantData.name}" :
                                "${deviceProvider.name}${INSTRUMENT_TEST.capitalize()}",
                            "Installs and runs the tests for Build '${baseVariantData.name}' using Provider '${deviceProvider.name.capitalize()}'.",
                            isLibraryTest ?
                                DeviceProviderInstrumentTestLibraryTask :
                                DeviceProviderInstrumentTestTask,
                            testVariantData,
                            baseVariantData,
                            deviceProvider,
                            "$DEVICE/$deviceProvider.name"
                    )

                    mainProviderTask.dependsOn providerTask
                    testVariantData.providerTestTaskList.add(providerTask)

                    if (!deviceProvider.isConfigured()) {
                        providerTask.enabled = false;
                    }
                }

                // now the test servers
                // don't use an auto loop as it'll break the closure inside.
                for (TestServer testServer : servers) {
                    DefaultTask serverTask = project.tasks.create(
                            hasFlavors ?
                                "${testServer.name}${"upload".capitalize()}${baseVariantData.name}" :
                                "${testServer.name}${"upload".capitalize()}",
                            TestServerTask)
                    serverTask.description = "Uploads APKs for Build '${baseVariantData.name}' to Test Server '${testServer.name.capitalize()}'."
                    serverTask.group = JavaBasePlugin.VERIFICATION_GROUP
                    serverTask.dependsOn testVariantData.assembleTask, baseVariantData.assembleTask

                    serverTask.testServer = testServer

                    serverTask.conventionMapping.testApk = { testVariantData.outputFile }
                    if (!(baseVariantData instanceof LibraryVariantData)) {
                        serverTask.conventionMapping.testedApk = { baseVariantData.outputFile }
                    }

                    serverTask.conventionMapping.variantName = { baseVariantData.name }

                    deviceCheck.dependsOn serverTask

                    if (!testServer.isConfigured()) {
                        serverTask.enabled = false;
                    }
                }
            }
        }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        if (!reportTasks.isEmpty() && project.gradle.startParameter.continueOnFailure) {
            project.gradle.taskGraph.whenReady { taskGraph ->
                for (AndroidReportTask reportTask : reportTasks) {
                    if (taskGraph.hasTask(reportTask)) {
                        reportTask.setWillRun()
                    }
                }
            }
        }
    }

    private DeviceProviderInstrumentTestTask createDeviceProviderInstrumentTestTask(
            @NonNull String taskName,
            @NonNull String description,
            @NonNull Class<? extends DeviceProviderInstrumentTestTask> taskClass,
            @NonNull TestVariantData variantData,
            @NonNull BaseVariantData testedVariantData,
            @NonNull DeviceProvider deviceProvider,
            @NonNull String subFolder) {

        def testTask = project.tasks.create(taskName, taskClass)
        testTask.description = description
        testTask.group = JavaBasePlugin.VERIFICATION_GROUP
        testTask.dependsOn testedVariantData.assembleTask, variantData.assembleTask

        testTask.plugin = this
        testTask.variant = variantData
        testTask.flavorName = variantData.flavorName
        testTask.deviceProvider = deviceProvider

        testTask.conventionMapping.testApp = { variantData.outputFile }
        if (testedVariantData.variantConfiguration.type != VariantConfiguration.Type.LIBRARY) {
            testTask.conventionMapping.testedApp = { testedVariantData.outputFile }
        }

        testTask.conventionMapping.resultsDir = {
            String rootLocation = extension.testOptions.resultsDir != null ?
                extension.testOptions.resultsDir :
                "$project.buildDir/$FD_INSTRUMENT_RESULTS"

            String flavorFolder = variantData.flavorDirName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }
        testTask.conventionMapping.reportsDir = {
            String rootLocation = extension.testOptions.reportDir != null ?
                extension.testOptions.reportDir :
                "$project.buildDir/$FD_REPORTS/$FD_INSTRUMENT_TESTS"

            String flavorFolder = variantData.flavorDirName
            if (!flavorFolder.isEmpty()) {
                flavorFolder = "$FD_FLAVORS/" + flavorFolder
            }

            project.file("$rootLocation/$subFolder/$flavorFolder")
        }

        return testTask
    }

    /**
     * Creates the packaging tasks for the given Variant.
     * @param variantData the variant data.
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     */
    protected void addPackageTasks(@NonNull ApkVariantData variantData,
                                   @Nullable Task assembleTask) {

        VariantConfiguration variantConfig = variantData.variantConfiguration

        Closure libraryClosure = { project.files(variantConfig.packagedJars) }
        Closure sourceClosure = { variantData.javaCompileTask.outputs.files }
        Closure proguardFileClosure = { }

        if (!(variantData instanceof TestVariantData) && variantConfig.buildType.runProguard) {

            def proguardTask = project.tasks.create("proguard${variantData.name}", ProGuardTask);
            proguardTask.dependsOn variantData.javaCompileTask
            variantData.proguardTask = proguardTask

            File outFile = project.file(
                    "${project.buildDir}/classes-proguard/${variantData.dirName}/classes.jar")

            libraryClosure = { Collections.emptyList() }
            sourceClosure = { Collections.emptyList() }
            proguardFileClosure = { outFile }

            // because the Proguard task acts on all the config right away and not when the
            // task actually runs, let's configure it in its doFirst

            proguardTask.doFirst {

                // all the config files coming from build type, product flavors.
                List<Object> proguardFiles = variantConfig.getProguardFiles(true /*includeLibs*/);
                for (Object proguardFile : proguardFiles) {
                    proguardTask.configuration(proguardFile)
                }

                // also the config file output by aapt
                proguardTask.configuration(variantData.processResourcesTask.proguardOutputFile)

                // injar: the compilation output
                proguardTask.injars(variantData.javaCompileTask.destinationDir)

                // injar: the dependencies
                for (File inJar : variantConfig.packagedJars) {
                    proguardTask.injars(inJar, filter: '!META-INF/MANIFEST.MF')
                }

                // libraryJars: the runtime jars
                for (String runtimeJar : getRuntimeJarList()) {
                    proguardTask.libraryjars(runtimeJar)
                }

                proguardTask.outjars(outFile)

                proguardTask.dump("${project.buildDir}/proguard/${variantData.dirName}/dump.txt")
                proguardTask.printseeds(
                        "${project.buildDir}/proguard/${variantData.dirName}/seeds.txt")
                proguardTask.printusage(
                        "${project.buildDir}/proguard/${variantData.dirName}/usage.txt")
                proguardTask.printmapping(
                        "${project.buildDir}/proguard/${variantData.dirName}/mapping.txt")
            }
        }

        // Add a dex task
        def dexTaskName = "dex${variantData.name}"
        def dexTask = project.tasks.create(dexTaskName, Dex)
        variantData.dexTask = dexTask
        if (variantData.proguardTask != null) {
            dexTask.dependsOn variantData.proguardTask
        } else {
            dexTask.dependsOn variantData.javaCompileTask
        }

        dexTask.plugin = this
        dexTask.variant = variantData
        dexTask.incrementalFolder =
                project.file("$project.buildDir/incremental/dex/$variantData.dirName")

        dexTask.conventionMapping.libraries = libraryClosure
        dexTask.conventionMapping.sourceFiles = sourceClosure
        dexTask.conventionMapping.proguardedJar = proguardFileClosure
        dexTask.conventionMapping.outputFile = {
            project.file(
                    "${project.buildDir}/libs/${project.archivesBaseName}-${variantData.baseName}.dex")
        }
        dexTask.dexOptions = extension.dexOptions

        // Add a task to generate application package
        def packageApp = project.tasks.create("package${variantData.name}", PackageApplication)
        variantData.packageApplicationTask = packageApp
        packageApp.dependsOn variantData.processResourcesTask, dexTask, variantData.processJavaResources

        packageApp.plugin = this
        packageApp.variant = variantData

        VariantConfiguration config = variantData.variantConfiguration

        packageApp.conventionMapping.resourceFile = {
            variantData.processResourcesTask.packageOutputFile
        }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }

        packageApp.conventionMapping.packagedJars = { config.packagedJars }

        packageApp.conventionMapping.javaResourceDir = {
            getOptionalDir(variantData.processJavaResources.destinationDir)
        }

        packageApp.conventionMapping.jniDebugBuild = { config.buildType.jniDebugBuild }

        SigningConfigDsl sc = (SigningConfigDsl) config.signingConfig
        packageApp.conventionMapping.signingConfig = { sc }
        if (sc != null) {
            ValidateSigningTask validateSigningTask = validateSigningTaskMap.get(sc)
            if (validateSigningTask == null) {
                validateSigningTask = project.tasks.create("validate${sc.name.capitalize()}Signing",
                    ValidateSigningTask)
                validateSigningTask.plugin = this
                validateSigningTask.signingConfig = sc

                validateSigningTaskMap.put(sc, validateSigningTask)
            }

            packageApp.dependsOn validateSigningTask
        }

        def signedApk = variantData.isSigned()
        def apkName = signedApk ?
            "${project.archivesBaseName}-${variantData.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variantData.baseName}-unsigned.apk"

        packageApp.conventionMapping.outputFile = {
            project.file("$project.buildDir/apk/${apkName}")
        }

        Task appTask = packageApp
        OutputFileTask outputFileTask = packageApp

        if (signedApk) {
            if (variantData.zipAlign) {
                // Add a task to zip align application package
                def zipAlignTask = project.tasks.create("zipalign${variantData.name}", ZipAlign)
                variantData.zipAlignTask = zipAlignTask

                zipAlignTask.dependsOn packageApp
                zipAlignTask.conventionMapping.inputFile = { packageApp.outputFile }
                zipAlignTask.conventionMapping.outputFile = {
                    project.file(
                            "$project.buildDir/apk/${project.archivesBaseName}-${variantData.baseName}.apk")
                }
                zipAlignTask.conventionMapping.zipAlignExe = { getSdkParser().zipAlign }

                appTask = zipAlignTask
                outputFileTask = zipAlignTask
                variantData.outputFile = project.file(
                        "$project.buildDir/apk/${project.archivesBaseName}-${variantData.baseName}.apk")
            }

            // Add a task to install the application package
            def installTask = project.tasks.create("install${variantData.name}", InstallTask)
            installTask.description = "Installs the " + variantData.description
            installTask.group = INSTALL_GROUP
            installTask.dependsOn appTask
            installTask.conventionMapping.packageFile = { outputFileTask.outputFile }
            installTask.conventionMapping.adbExe = { getSdkParser().adb }

            variantData.installTask = installTask
        }

        // Add an assemble task
        if (assembleTask == null) {
            assembleTask = project.tasks.create("assemble${variantData.name}")
            assembleTask.description = "Assembles the " + variantData.description
            assembleTask.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        }
        assembleTask.dependsOn appTask
        variantData.assembleTask = assembleTask

        variantData.outputFile = { outputFileTask.outputFile }

        // add an uninstall task
        def uninstallTask = project.tasks.create("uninstall${variantData.name}", UninstallTask)
        uninstallTask.description = "Uninstalls the " + variantData.description
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variantData
        uninstallTask.conventionMapping.adbExe = { getSdkParser().adb }

        variantData.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
    }

    private void createReportTasks() {
        def dependencyReportTask = project.tasks.create("androidDependencies", DependencyReportTask)
        dependencyReportTask.setDescription("Displays the Android dependencies of the project")
        dependencyReportTask.setVariants(variantDataList)
        dependencyReportTask.setGroup("Android")

        def signingReportTask = project.tasks.create("signingReport", SigningReportTask)
        signingReportTask.setDescription("Displays the signing info for each variant")
        signingReportTask.setVariants(variantDataList)
        signingReportTask.setGroup("Android")
    }


    //----------------------------------------------------------------------------------------------
    //------------------------------ START DEPENDENCY STUFF ----------------------------------------
    //----------------------------------------------------------------------------------------------


    protected void createPrepareDependenciesTask(@NonNull BaseVariantData variantData) {
        def prepareDependenciesTask = project.tasks.create("prepare${variantData.name}Dependencies",
                PrepareDependenciesTask)
        variantData.prepareDependenciesTask = prepareDependenciesTask

        prepareDependenciesTask.plugin = this
        prepareDependenciesTask.variant = variantData

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        VariantDependencies configurationDependencies = variantData.variantDependency
        prepareDependenciesTask.addChecker(configurationDependencies.checker)

        for (LibraryDependencyImpl lib : configurationDependencies.libraries) {
            addDependencyToPrepareTask(prepareDependenciesTask, lib)
        }
    }

    def addDependencyToPrepareTask(PrepareDependenciesTask prepareDependenciesTask,
                                   LibraryDependencyImpl lib) {
        def prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
        }

        for (LibraryDependencyImpl childLib : lib.dependencies) {
            addDependencyToPrepareTask(prepareDependenciesTask, childLib)
        }
    }

    def resolveDependencies(VariantDependencies variantDeps) {
        Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules = [:]
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = [:]
        Multimap<LibraryDependency, VariantDependencies> reverseMap = ArrayListMultimap.create()

        resolveDependencyForConfig(variantDeps, modules, artifacts, reverseMap)

        modules.values().each { List list ->

            if (!list.isEmpty()) {
                // get the first item only
                LibraryDependencyImpl androidDependency = (LibraryDependencyImpl) list.get(0)

                String bundleName = GUtil.toCamelCase(androidDependency.name.replaceAll("\\:", " "))

                def prepareLibraryTask = prepareTaskMap.get(androidDependency)
                if (prepareLibraryTask == null) {
                    prepareLibraryTask = project.tasks.create("prepare${bundleName}Library",
                            PrepareLibraryTask)
                    prepareLibraryTask.description = "Prepare ${androidDependency.name}"
                    prepareLibraryTask.bundle = androidDependency.bundle
                    prepareLibraryTask.explodedDir = androidDependency.bundleFolder

                    prepareTaskMap.put(androidDependency, prepareLibraryTask)
                }

                // Use the reverse map to find all the configurations that included this android
                // library so that we can make sure they are built.
                List<VariantDependencies> configDepList = reverseMap.get(androidDependency)
                if (configDepList != null && !configDepList.isEmpty()) {
                    for (VariantDependencies configDependencies: configDepList) {
                        prepareLibraryTask.dependsOn configDependencies.compileConfiguration.buildDependencies
                    }
                }
            }
        }
    }

    def resolveDependencyForConfig(
            VariantDependencies variantDeps,
            Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<LibraryDependency, VariantDependencies> reverseMap) {

        Configuration compileClasspath = variantDeps.compileConfiguration

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)

        variantDeps.checker = new DependencyChecker(variantDeps, logger)

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        List<LibraryDependencyImpl> bundles = []
        List<JarDependency> jars = []
        List<JarDependency> localJars = []
        collectArtifacts(compileClasspath, artifacts)
        compileClasspath.incoming.resolutionResult.root.dependencies.each { ResolvedDependencyResult dep ->
            addDependency(dep.selected, variantDeps, bundles, jars, modules,
                    artifacts, reverseMap)
        }

        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result. This only includes the local jar files for this project.
        compileClasspath.allDependencies.each { dep ->
            if (dep instanceof SelfResolvingDependency &&
                    !(dep instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dep).resolve()
                for (File f : files) {
                    // TODO: support compile only dependencies.
                    localJars << new JarDependency(f)
                }
            }
        }

        // handle package dependencies. We'll refuse aar libs only in package but not
        // in compile and remove all dependencies already in compile to get package-only jar
        // files.
        Configuration packageClasspath = variantDeps.packageConfiguration
        Set<File> compileFiles = compileClasspath.files
        Set<File> packageFiles = packageClasspath.files

        for (File f : packageFiles) {
            if (compileFiles.contains(f)) {
                continue
            }

            if (f.getName().toLowerCase().endsWith(".jar")) {
                jars.add(new JarDependency(f, false /*compiled*/, true /*packaged*/))
            } else {
                throw new RuntimeException("Package-only dependency '" +
                        f.absolutePath +
                        "' is not supported")
            }
        }

        variantDeps.addLibraries(bundles)
        variantDeps.addJars(jars)
        variantDeps.addLocalJars(localJars)

        // TODO - filter bundles out of source set classpath

        configureBuild(variantDeps)
    }

    def ensureConfigured(Configuration config) {
        config.allDependencies.withType(ProjectDependency).each { dep ->
            project.evaluationDependsOn(dep.dependencyProject.path)
            ensureConfigured(dep.projectConfiguration)
        }
    }

    static def collectArtifacts(Configuration configuration, Map<ModuleVersionIdentifier,
                         List<ResolvedArtifact>> artifacts) {
        configuration.resolvedConfiguration.resolvedArtifacts.each { ResolvedArtifact artifact ->
            def id = artifact.moduleVersion.id
            List<ResolvedArtifact> moduleArtifacts = artifacts[id]
            if (moduleArtifacts == null) {
                moduleArtifacts = []
                artifacts[id] = moduleArtifacts
            }
            moduleArtifacts << artifact
        }
    }

    def addDependency(ResolvedModuleVersionResult moduleVersion,
                      VariantDependencies configDependencies,
                      Collection<LibraryDependencyImpl> bundles,
                      List<JarDependency> jars,
                      Map<ModuleVersionIdentifier, List<LibraryDependencyImpl>> modules,
                      Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
                      Multimap<LibraryDependency, VariantDependencies> reverseMap) {
        def id = moduleVersion.id
        if (configDependencies.checker.excluded(id)) {
            return
        }

        List<LibraryDependencyImpl> bundlesForThisModule = modules[id]
        if (bundlesForThisModule == null) {
            bundlesForThisModule = []
            modules[id] = bundlesForThisModule

            def nestedBundles = []
            moduleVersion.dependencies.each { ResolvedDependencyResult dep ->
                addDependency(dep.selected, configDependencies, nestedBundles,
                        jars, modules, artifacts, reverseMap)
            }

            def moduleArtifacts = artifacts[id]
            moduleArtifacts?.each { artifact ->
                if (artifact.type == EXT_LIB_ARCHIVE) {
                    String bundleName = GUtil.toCamelCase(id.group + " " + id.name + " " + id.version)
                    def explodedDir = project.file(
                            "$project.buildDir/exploded-bundles/${bundleName}.aar")
                    LibraryDependencyImpl adep = new LibraryDependencyImpl(
                            artifact.file, explodedDir, nestedBundles,
                            id.group + ":" + id.name + ":" + id.version)
                    bundlesForThisModule << adep
                    reverseMap.put(adep, configDependencies)
                } else {
                    // TODO: support compile only dependencies.
                    jars << new JarDependency(artifact.file)
                }
            }

            if (bundlesForThisModule.empty && !nestedBundles.empty) {
                throw new GradleException("Module version $id depends on libraries but is not a library itself")
            }
        } else {
            for (LibraryDependency adep : bundlesForThisModule) {
                reverseMap.put(adep, configDependencies)
            }
        }

        bundles.addAll(bundlesForThisModule)
    }

    private void configureBuild(VariantDependencies configurationDependencies) {
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
                                                 String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------- END DEPENDENCY STUFF -----------------------------------------
    //----------------------------------------------------------------------------------------------

    protected static File getOptionalDir(File dir) {
        if (dir.isDirectory()) {
            return dir
        }

        return null
    }

    @NonNull
    protected List<ManifestDependencyImpl> getManifestDependencies(
            List<LibraryDependency> libraries) {

        List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            // get the dependencies
            List<ManifestDependencyImpl> children = getManifestDependencies(lib.dependencies)
            list.add(new ManifestDependencyImpl(lib.manifest, children))
        }

        return list
    }

    @NonNull
    protected static List<SymbolFileProviderImpl> getTextSymbolDependencies(
            List<LibraryDependency> libraries) {

        List<SymbolFileProviderImpl> list = Lists.newArrayListWithCapacity(libraries.size())

        for (LibraryDependency lib : libraries) {
            list.add(new SymbolFileProviderImpl(lib.manifest, lib.symbolFile))
        }

        return list
    }

    private static String getLocalVersion() {
        Class clazz = BasePlugin.class
        String className = clazz.getSimpleName() + ".class"
        String classPath = clazz.getResource(className).toString()
        if (!classPath.startsWith("jar")) {
            // Class not from JAR, unlikely
            return null
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/META-INF/MANIFEST.MF";
        Manifest manifest = new Manifest(new URL(manifestPath).openStream());
        Attributes attr = manifest.getMainAttributes();
        return attr.getValue("Plugin-Version");
    }
}
