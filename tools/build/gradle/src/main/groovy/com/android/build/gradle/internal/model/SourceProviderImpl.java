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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;

import java.io.File;
import java.io.Serializable;
import java.util.Set;

/**
 * Implementation of SourceProvider that is serializable. Objects used in the DSL cannot be
 * serialized.
 */
class SourceProviderImpl implements SourceProvider, Serializable {
    private static final long serialVersionUID = 1L;

    private File manifestFile;
    private Set<File> javaDirs;
    private Set<File> resourcesDirs;
    private Set<File> aidlDirs;
    private Set<File> rsDirs;
    private Set<File> jniDirs;
    private Set<File> resDirs;
    private Set<File> assetsDirs;

    @NonNull
    static SourceProviderImpl cloneProvider(SourceProvider sourceProvider) {
        SourceProviderImpl sourceProviderClone = new SourceProviderImpl();

        sourceProviderClone.manifestFile = sourceProvider.getManifestFile();
        sourceProviderClone.javaDirs = sourceProvider.getJavaDirectories();
        sourceProviderClone.resourcesDirs = sourceProvider.getResourcesDirectories();
        sourceProviderClone.aidlDirs = sourceProvider.getAidlDirectories();
        sourceProviderClone.rsDirs = sourceProvider.getRenderscriptDirectories();
        sourceProviderClone.jniDirs = sourceProvider.getJniDirectories();
        sourceProviderClone.resDirs = sourceProvider.getResDirectories();
        sourceProviderClone.assetsDirs = sourceProvider.getAssetsDirectories();

        return sourceProviderClone;
    }

    private SourceProviderImpl() {
    }

    @NonNull
    @Override
    public File getManifestFile() {
        return manifestFile;
    }

    @NonNull
    @Override
    public Set<File> getJavaDirectories() {
        return javaDirs;
    }

    @NonNull
    @Override
    public Set<File> getResourcesDirectories() {
        return resourcesDirs;
    }

    @NonNull
    @Override
    public Set<File> getAidlDirectories() {
        return aidlDirs;
    }

    @NonNull
    @Override
    public Set<File> getRenderscriptDirectories() {
        return rsDirs;
    }

    @NonNull
    @Override
    public Set<File> getJniDirectories() {
        return jniDirs;
    }

    @NonNull
    @Override
    public Set<File> getResDirectories() {
        return resDirs;
    }

    @NonNull
    @Override
    public Set<File> getAssetsDirectories() {
        return assetsDirs;
    }

    @Override
    public String toString() {
        return "SourceProviderImpl{" +
                "manifestFile=" + manifestFile +
                ", javaDirs=" + javaDirs +
                ", resourcesDirs=" + resourcesDirs +
                ", aidlDirs=" + aidlDirs +
                ", rsDirs=" + rsDirs +
                ", jniDirs=" + jniDirs +
                ", resDirs=" + resDirs +
                ", assetsDirs=" + assetsDirs +
                '}';
    }
}
