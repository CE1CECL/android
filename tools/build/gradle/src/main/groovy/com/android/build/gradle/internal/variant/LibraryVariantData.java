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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.VariantConfiguration;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Data about a variant that produce a Library bundle (.aar)
 */
public class LibraryVariantData extends BaseVariantData implements TestedVariantData {

    public Zip packageLibTask;

    @Nullable
    private TestVariantData testVariantData = null;

    public LibraryVariantData(@NonNull VariantConfiguration config) {
        super(config);
    }

    @Override
    @NonNull
    protected String computeName() {
        return getVariantConfiguration().hasFlavors() ?
                String.format("%s%s",
                        getFlavoredName(true), getCapitalizedBuildTypeName()) :
                getCapitalizedBuildTypeName();
    }

    @Override
    @NonNull
    public String getDescription() {
        if (getVariantConfiguration().hasFlavors()) {
            return "Test build for the ${getFlavoredName(true)}${config.buildType.name.capitalize()} build";
        } else {
            return "Test for the ${config.buildType.name.capitalize()} build";
        }
    }

    @Override
    @NonNull
    public String getDirName() {
        return getVariantConfiguration().getBuildType().getName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return getVariantConfiguration().getBuildType().getName();
    }

    @Override
    public void setTestVariantData(@Nullable TestVariantData testVariantData) {
        this.testVariantData = testVariantData;
    }

    @Override
    @Nullable
    public TestVariantData getTestVariantData() {
        return testVariantData;
    }
}
