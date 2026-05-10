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
package com.android.build.gradle.internal.tasks

import com.android.builder.internal.incremental.ChangeManager
import com.android.ide.common.res2.FileStatus
import com.android.ide.common.res2.SourceSet
import com.google.common.collect.Lists
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskInputs

public abstract class IncrementalTask extends BaseTask {

    @OutputDirectory @Optional
    File incrementalFolder

    /**
     * Whether this task can support incremental update using the {@link ChangeManager}
     *
     * @return whether this task can support incremental update.
     */
    protected boolean isIncremental() {
        return false
    }

    /**
     * Actual task action. This is called when a full run is needed, which is always the case if
     * {@link #isIncremental()} returns false.
     *
     */
    protected abstract void doFullTaskAction();

    /**
     * Optional incremental task action.
     * Only used if {@link #isIncremental()} returns true.
     *
     * @param changedInputs the changed input files.
     */
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        // do nothing.
    }

    protected Collection<File> getOutputForIncrementalBuild() {
        return Collections.emptyList();
    }

    /**
     * Actual entry point for the action.
     * Calls out to the doTaskAction as needed.
     */
    @TaskAction
    void taskAction() {
        try {
            if (!isIncremental() || incrementalFolder == null) {
                doFullTaskAction()
                return;
            }

            // load known state.
            ChangeManager changeManager = new ChangeManager()
            boolean fullBuild = !changeManager.load(incrementalFolder)

            // update with current files.
            TaskInputs inputs = getInputs()
            FileCollection inputCollection = inputs.getFiles()

            for (File f : inputCollection.files) {
                changeManager.addInput(f)
            }

            for (File f : getOutputForIncrementalBuild()) {
                changeManager.addOutput(f);
            }

            // force full build if output changed somehow.
            Map<File, FileStatus> changedOutputs = changeManager.getChangedOutputs()
            Map<File, FileStatus> changedInputs = changeManager.getChangedInputs()
            if (fullBuild) {
                project.logger.info("No incremental data: full task run")
                doFullTaskAction();
            } else if (!changedOutputs.isEmpty()) {
                project.logger.info("Changed output: full task run")

                doFullTaskAction();
            } else if (changedInputs.isEmpty() && changedOutputs.isEmpty()) {
                // both input and output are empty, this is something we don't control
                // through files, just do a full run
                project.logger.info("Changed non file input/output: full task run")
                doFullTaskAction()
            } else {
                doIncrementalTaskAction(changeManager.getChangedInputs())
            }

            // update the outputs post task-action, to record their state
            // for the next run
            changeManager.updateOutputs(getOutputForIncrementalBuild())

            // write the result down to be used next time the task is run.
            changeManager.write(incrementalFolder)
        } catch (Exception e) {
            // Easiest to do here, is to delete the incremental Data so that
            // next run is full.
            ChangeManager.delete(incrementalFolder)

            throw e
        }
    }

    public static List<File> flattenSourceSets(List<? extends SourceSet> resourceSets) {
        List<File> list = Lists.newArrayList();

        for (SourceSet sourceSet : resourceSets) {
            list.addAll(sourceSet.sourceFiles)
        }

        return list;
    }
}
