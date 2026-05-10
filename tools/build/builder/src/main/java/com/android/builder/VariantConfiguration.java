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

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.testing.TestData;
import com.android.ide.common.res2.AssetSet;
import com.android.ide.common.res2.ResourceSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A Variant configuration.
 */
public class VariantConfiguration implements TestData {

    private static final ManifestParser sManifestParser = new DefaultManifestParser();

    private final DefaultProductFlavor mDefaultConfig;
    private final SourceProvider mDefaultSourceProvider;

    private final DefaultBuildType mBuildType;
    /** SourceProvider for the BuildType. Can be null */
    private final SourceProvider mBuildTypeSourceProvider;

    private final List<DefaultProductFlavor> mFlavorConfigs = Lists.newArrayList();
    private final List<SourceProvider> mFlavorSourceProviders = Lists.newArrayList();

    private final Type mType;
    /** Optional tested config in case type is Type#TEST */
    private final VariantConfiguration mTestedConfig;
    private final String mDebugName;
    /** An optional output that is only valid if the type is Type#LIBRARY so that the test
     * for the library can use the library as if it was a normal dependency. */
    private LibraryDependency mOutput;

    private DefaultProductFlavor mMergedFlavor;

    private final Set<JarDependency> mJars = Sets.newHashSet();

    /** List of direct library dependencies. Each object defines its own dependencies. */
    private final List<LibraryDependency> mDirectLibraries = Lists.newArrayList();

    /** list of all library dependencies in a flat list.
     * The order is based on the order needed to call aapt: earlier libraries override resources
     * of latter ones. */
    private final List<LibraryDependency> mFlatLibraries = Lists.newArrayList();

    public static enum Type {
        DEFAULT, LIBRARY, TEST
    }

    /**
     * Parses the manifest file and return the package name.
     * @param manifestFile the manifest file
     * @return the package name found or null
     */
    @Nullable
    public static String getManifestPackage(@NonNull File manifestFile) {
        return sManifestParser.getPackage(manifestFile);
    }

    /**
     * Creates the configuration with the base source sets.
     *
     * This creates a config with a {@link Type#DEFAULT} type.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type. Required.
     * @param debugName an optional debug name
     */
    public VariantConfiguration(
            @NonNull DefaultProductFlavor defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull DefaultBuildType buildType,
            @NonNull SourceProvider buildTypeSourceProvider,
            @Nullable String debugName) {
        this(defaultConfig, defaultSourceProvider,
                buildType, buildTypeSourceProvider,
                Type.DEFAULT, null /*testedConfig*/,
                debugName);
    }

    /**
     * Creates the configuration with the base source sets for a given {@link Type}.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param debugName an optional debug name
     */
    public VariantConfiguration(
            @NonNull DefaultProductFlavor defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull DefaultBuildType buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull Type type,
            @Nullable String debugName) {
        this(defaultConfig, defaultSourceProvider,
                buildType, buildTypeSourceProvider,
                type, null /*testedConfig*/,
                debugName);
    }

    /**
     * Creates the configuration with the base source sets, and an optional tested variant.
     *
     * @param defaultConfig the default configuration. Required.
     * @param defaultSourceProvider the default source provider. Required
     * @param buildType the build type for this variant. Required.
     * @param buildTypeSourceProvider the source provider for the build type.
     * @param type the type of the project.
     * @param testedConfig the reference to the tested project. Required if type is Type.TEST
     * @param debugName an optional debug name
     */
    public VariantConfiguration(
            @NonNull DefaultProductFlavor defaultConfig,
            @NonNull SourceProvider defaultSourceProvider,
            @NonNull DefaultBuildType buildType,
            @Nullable SourceProvider buildTypeSourceProvider,
            @NonNull Type type,
            @Nullable VariantConfiguration testedConfig,
            @Nullable String debugName) {
        mDefaultConfig = checkNotNull(defaultConfig);
        mDefaultSourceProvider = checkNotNull(defaultSourceProvider);
        mBuildType = checkNotNull(buildType);
        mBuildTypeSourceProvider = buildTypeSourceProvider;
        mType = checkNotNull(type);
        mTestedConfig = testedConfig;
        mDebugName = debugName;
        checkState(mType != Type.TEST || mTestedConfig != null);

        mMergedFlavor = mDefaultConfig;

        if (testedConfig != null &&
                testedConfig.mType == Type.LIBRARY &&
                testedConfig.mOutput != null) {
            mDirectLibraries.add(testedConfig.mOutput);
        }

        validate();
    }

    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param sourceProvider the configured product flavor
     * @return the config object
     */
    @NonNull
    public VariantConfiguration addProductFlavor(@NonNull DefaultProductFlavor productFlavor,
                                                 @NonNull SourceProvider sourceProvider) {
        mFlavorConfigs.add(productFlavor);
        mFlavorSourceProviders.add(sourceProvider);
        mMergedFlavor = productFlavor.mergeOver(mMergedFlavor);

        return this;
    }

    /**
     * Sets the dependencies
     *
     * @param container a DependencyContainer.
     * @return the config object
     */
    @NonNull
    public VariantConfiguration setDependencies(@NonNull DependencyContainer container) {

        mDirectLibraries.addAll(container.getAndroidDependencies());
        mJars.addAll(container.getJarDependencies());
        mJars.addAll(container.getLocalDependencies());

        resolveIndirectLibraryDependencies(mDirectLibraries, mFlatLibraries);

        for (LibraryDependency libraryDependency : mFlatLibraries) {
            mJars.addAll(libraryDependency.getLocalDependencies());
        }
        return this;
    }

    /**
     * Returns the list of jar dependencies
     * @return a non null collection of Jar dependencies.
     */
    @NonNull
    public Collection<JarDependency> getJars() {
        return mJars;
    }

    /**
     * Sets the output of this variant. This is required when the variant is a library so that
     * the variant that tests this library can properly include the tested library in its own
     * package.
     *
     * @param output the output of the library as an LibraryDependency that will provides the
     *               location of all the created items.
     * @return the config object
     */
    @NonNull
    public VariantConfiguration setOutput(LibraryDependency output) {
        mOutput = output;
        return this;
    }

    @NonNull
    public DefaultProductFlavor getDefaultConfig() {
        return mDefaultConfig;
    }

    @NonNull
    public SourceProvider getDefaultSourceSet() {
        return mDefaultSourceProvider;
    }

    @NonNull
    public DefaultProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    @NonNull
    public DefaultBuildType getBuildType() {
        return mBuildType;
    }

    /**
     * The SourceProvider for the BuildType. Can be null.
     */
    @Nullable
    public SourceProvider getBuildTypeSourceSet() {
        return mBuildTypeSourceProvider;
    }

    public boolean hasFlavors() {
        return !mFlavorConfigs.isEmpty();
    }

    @NonNull
    public List<DefaultProductFlavor> getFlavorConfigs() {
        return mFlavorConfigs;
    }

    @NonNull
    public Iterable<SourceProvider> getFlavorSourceSets() {
        return mFlavorSourceProviders;
    }

    public boolean hasLibraries() {
        return !mDirectLibraries.isEmpty();
    }

    /**
     * Returns the direct library dependencies
     */
    @NonNull
    public List<LibraryDependency> getDirectLibraries() {
        return mDirectLibraries;
    }

    /**
     * Returns all the library dependencies, direct and transitive.
     */
    @NonNull
    public List<LibraryDependency> getAllLibraries() {
        return mFlatLibraries;
    }

    @NonNull
    public Type getType() {
        return mType;
    }

    @Nullable
    public VariantConfiguration getTestedConfig() {
        return mTestedConfig;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param directDependencies the libraries to resolve
     * @param outFlatDependencies where to store all the libraries.
     */
    @VisibleForTesting
    void resolveIndirectLibraryDependencies(List<LibraryDependency> directDependencies,
                                            List<LibraryDependency> outFlatDependencies) {
        if (directDependencies == null) {
            return;
        }
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = directDependencies.size() - 1  ; i >= 0 ; i--) {
            LibraryDependency library = directDependencies.get(i);

            // get its libraries
            List<LibraryDependency> dependencies = library.getDependencies();

            // resolve the dependencies for those libraries
            resolveIndirectLibraryDependencies(dependencies, outFlatDependencies);

            // and add the current one (if needed) in front (higher priority)
            if (!outFlatDependencies.contains(library)) {
                outFlatDependencies.add(0, library);
            }
        }
    }

    /**
     * Returns the original package name before any overrides from flavors.
     * If the variant is a test variant, then the package name is the one coming from the
     * configuration of the tested variant, and this call is similar to #getPackageName()
     * @return the package name
     */
    @Nullable
    public String getOriginalPackageName() {
        if (mType == VariantConfiguration.Type.TEST) {
            return getPackageName();
        }

        return getPackageFromManifest();
    }

    /**
     * Returns the package name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors and/or the build Type.
     * @return the package
     */
    @Override
    @NonNull
    public String getPackageName() {
        String packageName;

        if (mType == Type.TEST) {
            assert mTestedConfig != null;

            packageName = mMergedFlavor.getTestPackageName();
            if (packageName == null) {
                String testedPackage = mTestedConfig.getPackageName();
                packageName = testedPackage + ".test";
            }
        } else {
            // first get package override.
            packageName = getPackageOverride();
            // if it's null, this means we just need the default package
            // from the manifest since both flavor and build type do nothing.
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }
        }

        if (packageName == null) {
            throw new RuntimeException("Failed get query package name for " + mDebugName);
        }

        return packageName;
    }

    @Override
    @Nullable
    public String getTestedPackageName() {
        if (mType == Type.TEST) {
            assert mTestedConfig != null;
            if (mTestedConfig.mType == Type.LIBRARY) {
                return getPackageName();
            } else {
                return mTestedConfig.getPackageName();
            }
        }

        return null;
    }

    /**
     * Returns the package override values coming from the Product Flavor and/or the Build Type.
     * If the package is not overridden then this returns null.
     *
     * @return the package override or null
     */
    @Nullable
    public String getPackageOverride() {
        String packageName = mMergedFlavor.getPackageName();
        String packageSuffix = mBuildType.getPackageNameSuffix();

        if (packageSuffix != null && packageSuffix.length() > 0) {
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }

            if (packageSuffix.charAt(0) == '.') {
                packageName = packageName + packageSuffix;
            } else {
                packageName = packageName + '.' + packageSuffix;
            }
        }

        return packageName;
    }

    /**
     * Returns the version name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors, and can have a suffix specified by
     * the build type.
     *
     * @return the version name
     */
    @Nullable
    public String getVersionName() {
        String versionName = mMergedFlavor.getVersionName();
        String versionSuffix = mBuildType.getVersionNameSuffix();

        if (versionSuffix != null && versionSuffix.length() > 0) {
            if (versionName == null) {
                versionName = getVersionNameFromManifest();
            }

            versionName = versionName + versionSuffix;
        }

        return versionName;
    }

    private final static String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     * @return the instrumentation test runner name
     */
    @Override
    @NonNull
    public String getInstrumentationRunner() {
        VariantConfiguration config = this;
        if (mType == Type.TEST) {
            config = getTestedConfig();
        }
        String runner = config.mMergedFlavor.getTestInstrumentationRunner();
        return runner != null ? runner : DEFAULT_TEST_RUNNER;
    }

    /**
     * Reads the package name from the manifest. This is unmodified by the build type.
     */
    @Nullable
    public String getPackageFromManifest() {
        assert mType != Type.TEST;
        File manifestLocation = mDefaultSourceProvider.getManifestFile();
        return sManifestParser.getPackage(manifestLocation);
    }

    /**
     * Reads the version name from the manifest.
     */
    @Nullable
    public String getVersionNameFromManifest() {
        File manifestLocation = mDefaultSourceProvider.getManifestFile();
        return sManifestParser.getVersionName(manifestLocation);
    }

    /**
     * Return the minSdkVersion for this variant.
     *
     * This uses both the value from the manifest (if present), and the override coming
     * from the flavor(s) (if present).
     * @return the minSdkVersion
     */
    @Override
    public int getMinSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getMinSdkVersion();
        }
        int minSdkVersion = mMergedFlavor.getMinSdkVersion();
        if (minSdkVersion == -1) {
            // read it from the main manifest
            File manifestLocation = mDefaultSourceProvider.getManifestFile();
            minSdkVersion = sManifestParser.getMinSdkVersion(manifestLocation);
        }

        return minSdkVersion;
    }

    /**
     * Return the targetSdkVersion for this variant.
     *
     * This uses both the value from the manifest (if present), and the override coming
     * from the flavor(s) (if present).
     * @return the targetSdkVersion
     */
    public int getTargetSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getTargetSdkVersion();
        }
        int targetSdkVersion = mMergedFlavor.getTargetSdkVersion();
        if (targetSdkVersion == -1) {
            // read it from the main manifest
            File manifestLocation = mDefaultSourceProvider.getManifestFile();
            targetSdkVersion = sManifestParser.getTargetSdkVersion(manifestLocation);
        }

        return targetSdkVersion;
    }

    @Nullable
    public File getMainManifest() {
        File defaultManifest = mDefaultSourceProvider.getManifestFile();

        // this could not exist in a test project.
        if (defaultManifest.isFile()) {
            return defaultManifest;
        }

        return null;
    }

    @NonNull
    public List<File> getManifestOverlays() {
        List<File> inputs = Lists.newArrayList();

        if (mBuildTypeSourceProvider != null) {
            File typeLocation = mBuildTypeSourceProvider.getManifestFile();
            if (typeLocation.isFile()) {
                inputs.add(typeLocation);
            }
        }

        for (SourceProvider sourceProvider : mFlavorSourceProviders) {
            File f = sourceProvider.getManifestFile();
            if (f.isFile()) {
                inputs.add(f);
            }
        }

        return inputs;
    }

    /**
     * Returns the dynamic list of {@link ResourceSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.ResourceMerger}.
     *
     * @param generatedResFolder the generated res folder typically the output of the renderscript
     *                           compilation
     * @param includeDependencies whether to include in the result the resources of the dependencies
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<ResourceSet> getResourceSets(@Nullable File generatedResFolder,
                                             boolean includeDependencies) {
        List<ResourceSet> resourceSets = Lists.newArrayList();

        // the list of dependency must be reversed to use the right overlay order.
        if (includeDependencies) {
            for (int n = mFlatLibraries.size() - 1 ; n >= 0 ; n--) {
                LibraryDependency dependency = mFlatLibraries.get(n);
                File resFolder = dependency.getResFolder();
                if (resFolder.isDirectory()) {
                    ResourceSet resourceSet = new ResourceSet(dependency.getFolder().getName());
                    resourceSet.addSource(resFolder);
                    resourceSets.add(resourceSet);
                }
            }
        }

        Set<File> mainResDirs = mDefaultSourceProvider.getResDirectories();

        ResourceSet resourceSet = new ResourceSet(BuilderConstants.MAIN);
        resourceSet.addSources(mainResDirs);
        if (generatedResFolder != null) {
            resourceSet.addSource(generatedResFolder);
        }
        resourceSets.add(resourceSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Set<File> flavorResDirs = sourceProvider.getResDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            resourceSet = new ResourceSet(mFlavorConfigs.get(n).getName());
            resourceSet.addSources(flavorResDirs);
            resourceSets.add(resourceSet);
        }

        if (mBuildTypeSourceProvider != null) {
            Set<File> typeResDirs = mBuildTypeSourceProvider.getResDirectories();
            resourceSet = new ResourceSet(mBuildType.getName());
            resourceSet.addSources(typeResDirs);
            resourceSets.add(resourceSet);
        }

        return resourceSets;
    }

    /**
     * Returns the dynamic list of {@link AssetSet} based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * {@link com.android.ide.common.res2.AssetMerger}.
     *
     * @return a list ResourceSet.
     */
    @NonNull
    public List<AssetSet> getAssetSets(boolean includeDependencies) {
        List<AssetSet> assetSets = Lists.newArrayList();

        if (includeDependencies) {
            // the list of dependency must be reversed to use the right overlay order.
            for (int n = mFlatLibraries.size() - 1 ; n >= 0 ; n--) {
                LibraryDependency dependency = mFlatLibraries.get(n);
                File assetFolder = dependency.getAssetsFolder();
                if (assetFolder.isDirectory()) {
                    AssetSet assetSet = new AssetSet(dependency.getFolder().getName());
                    assetSet.addSource(assetFolder);
                    assetSets.add(assetSet);
                }
            }
        }

        Set<File> mainResDirs = mDefaultSourceProvider.getAssetsDirectories();

        AssetSet assetSet = new AssetSet(BuilderConstants.MAIN);
        assetSet.addSources(mainResDirs);
        assetSets.add(assetSet);

        // the list of flavor must be reversed to use the right overlay order.
        for (int n = mFlavorSourceProviders.size() - 1; n >= 0 ; n--) {
            SourceProvider sourceProvider = mFlavorSourceProviders.get(n);

            Set<File> flavorResDirs = sourceProvider.getAssetsDirectories();
            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            assetSet = new AssetSet(mFlavorConfigs.get(n).getName());
            assetSet.addSources(flavorResDirs);
            assetSets.add(assetSet);
        }

        if (mBuildTypeSourceProvider != null) {
            Set<File> typeResDirs = mBuildTypeSourceProvider.getAssetsDirectories();
            assetSet = new AssetSet(mBuildType.getName());
            assetSet.addSources(typeResDirs);
            assetSets.add(assetSet);
        }

        return assetSets;
    }

    /**
     * Returns all the renderscript import folder that are outside of the current project.
     */
    @NonNull
    public List<File> getRenderscriptImports() {
        List<File> list = Lists.newArrayList();

        for (LibraryDependency lib : mFlatLibraries) {
            File rsLib = lib.getRenderscriptFolder();
            if (rsLib.isDirectory()) {
                list.add(rsLib);
            }
        }

        return list;
    }

    /**
     * Returns all the renderscript source folder from the main config, the flavors and the
     * build type.
     *
     * @return a list of folders.
     */
    @NonNull
    public List<File> getRenderscriptSourceList() {
        List<File> sourceList = Lists.newArrayList();
        sourceList.addAll(mDefaultSourceProvider.getRenderscriptDirectories());
        if (mType != Type.TEST && mBuildTypeSourceProvider != null) {
            sourceList.addAll(mBuildTypeSourceProvider.getRenderscriptDirectories());
        }

        if (hasFlavors()) {
            for (SourceProvider flavorSourceSet : mFlavorSourceProviders) {
                sourceList.addAll(flavorSourceSet.getRenderscriptDirectories());
            }
        }

        return sourceList;
    }

    /**
     * Returns all the aidl import folder that are outside of the current project.
     */
    @NonNull
    public List<File> getAidlImports() {
        List<File> list = Lists.newArrayList();

        for (LibraryDependency lib : mFlatLibraries) {
            File aidlLib = lib.getAidlFolder();
            if (aidlLib.isDirectory()) {
                list.add(aidlLib);
            }
        }

        return list;
    }

    @NonNull
    public List<File> getAidlSourceList() {
        List<File> sourceList = Lists.newArrayList();
        sourceList.addAll(mDefaultSourceProvider.getAidlDirectories());
        if (mType != Type.TEST && mBuildTypeSourceProvider != null) {
            sourceList.addAll(mBuildTypeSourceProvider.getAidlDirectories());
        }

        if (hasFlavors()) {
            for (SourceProvider flavorSourceSet : mFlavorSourceProviders) {
                sourceList.addAll(flavorSourceSet.getAidlDirectories());
            }
        }

        return sourceList;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getCompileClasspath() {
        Set<File> classpath = Sets.newHashSet();

        for (LibraryDependency lib : mFlatLibraries) {
            classpath.add(lib.getJarFile());
            for (File jarFile : lib.getLocalJars()) {
                classpath.add(jarFile);
            }
        }

        for (JarDependency jar : mJars) {
            if (jar.isCompiled()) {
                classpath.add(jar.getJarFile());
            }
        }

        return classpath;
    }

    /**
     * Returns the list of packaged jars for this config. If the config tests a library, this
     * will include the jars of the tested config
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public List<File> getPackagedJars() {
        Set<File> jars = Sets.newHashSetWithExpectedSize(mJars.size() + mFlatLibraries.size());

        for (JarDependency jar : mJars) {
            File jarFile = jar.getJarFile();
            if (jar.isPackaged() && jarFile.exists()) {
                jars.add(jarFile);
            }
        }

        for (LibraryDependency libraryDependency : mFlatLibraries) {
            File libJar = libraryDependency.getJarFile();
            if (libJar.exists()) {
                jars.add(libJar);
            }
            for (File jarFile : libraryDependency.getLocalJars()) {
                if (jarFile.isFile()) {
                    jars.add(jarFile);
                }
            }
        }

        return Lists.newArrayList(jars);
    }

    @NonNull
    public List<String> getBuildConfigLines() {
        List<String> fullList = Lists.newArrayList();

        List<String> list = mDefaultConfig.getBuildConfig();
        if (!list.isEmpty()) {
            fullList.add("// lines from default config.");
            fullList.addAll(list);
        }

        list = mBuildType.getBuildConfig();
        if (!list.isEmpty()) {
            fullList.add("// lines from build type: " + mBuildType.getName());
            fullList.addAll(list);
        }

        for (DefaultProductFlavor flavor : mFlavorConfigs) {
            list = flavor.getBuildConfig();
            if (!list.isEmpty()) {
                fullList.add("// lines from product flavor: " + flavor.getName());
                fullList.addAll(list);
            }
        }

        return fullList;
    }

    @Nullable
    public SigningConfig getSigningConfig() {
        SigningConfig signingConfig = mBuildType.getSigningConfig();
        if (signingConfig != null) {
            return signingConfig;
        }
        return mMergedFlavor.getSigningConfig();
    }

    public boolean isSigningReady() {
        SigningConfig signingConfig = getSigningConfig();
        return signingConfig != null && signingConfig.isSigningReady();
    }

    @NonNull
    public List<Object> getProguardFiles(boolean includeLibraries) {
        List<Object> fullList = Lists.newArrayList();

        // add the config files from the build type, main config and flavors
        fullList.addAll(mDefaultConfig.getProguardFiles());
        fullList.addAll(mBuildType.getProguardFiles());

        for (DefaultProductFlavor flavor : mFlavorConfigs) {
            fullList.addAll(flavor.getProguardFiles());
        }

        // now add the one coming from the library dependencies
        if (includeLibraries) {
            for (LibraryDependency libraryDependency : mFlatLibraries) {
                File proguardRules = libraryDependency.getProguardRules();
                if (proguardRules.exists()) {
                    fullList.add(proguardRules);
                }
            }
        }

        return fullList;
    }

    protected void validate() {
        if (mType != Type.TEST) {
            File manifest = mDefaultSourceProvider.getManifestFile();
            if (!manifest.isFile()) {
                throw new IllegalArgumentException(
                        "Main Manifest missing from " + manifest.getAbsolutePath());
            }
        }
    }


    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        // no ndk support yet, so return null
        return null;
    }
}
