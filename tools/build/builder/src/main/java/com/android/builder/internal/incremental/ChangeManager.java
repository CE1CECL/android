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

package com.android.builder.internal.incremental;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.FileStatus;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Class handling changes in input and output.
 *
 * The class stores the state of inputs/outputs after a task is run, and on subsequent runs can
 * compare this to the current state of detect exact file changes in the inputs or outputs.
 *
 * Gradle already does this to figure out if a task needs to be run, but does not offer this
 * information to the task.
 * This should become available in the Gradle plugin in the future, but in the meantime this
 * provides the same information, allowing us to build truly incremental tasks.
 *
 */
public class ChangeManager {

    private static final String FN_INPUTS_DATA = "inputs.data";
    private static final String FN_OUTPUTS_DATA = "outputs.data";

    private FileManager mInputs = new FileManager();
    private FileManager mOutputs = new FileManager();

    public ChangeManager() {
    }

    /**
     * Loads the known state.
     *
     * @param incrementalFolder the folder in which to store the incremental data
     * @return false if the loading failed.
     */
    public boolean load(File incrementalFolder) {
        File inputs = new File(incrementalFolder, FN_INPUTS_DATA);
        File outputs = new File(incrementalFolder, FN_OUTPUTS_DATA);
        return inputs.exists() && outputs.exists() &&
                mInputs.load(inputs) && mOutputs.load(outputs);
    }

    /**
     * Writes the incremental data to a given folder.
     * @param incrementalFolder the name of the folder to write to.
     *
     * @throws IOException
     */
    public void write(File incrementalFolder) throws IOException {
        if (!incrementalFolder.isDirectory() && !incrementalFolder.mkdirs()) {
            throw new IOException("Failed to create directory " + incrementalFolder);
        }

        mInputs.write(new File(incrementalFolder, FN_INPUTS_DATA));
        mOutputs.write(new File(incrementalFolder, FN_OUTPUTS_DATA));
    }

    /**
     * Delete the incremental data from the given folder.
     * @param incrementalFolder the folder to delete the incremental data from.
     */
    public static void delete(File incrementalFolder) {
        File file = new File(incrementalFolder, FN_INPUTS_DATA);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        file = new File(incrementalFolder, FN_OUTPUTS_DATA);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * Add an input file or folder.
     * @param file the file.
     */
    public void addInput(File file) {
        mInputs.addFile(file);
    }

    /**
     * Adds a new output file or folder
     * @param file the file.
     */
    public void addOutput(File file) {
        mOutputs.addFile(file);
    }

    /**
     * Get the list of changed inputs. Empty list means no input changes.
     *
     * @return a map of (File, FileStatus) for all changed input.
     */
    @NonNull
    public Map<File, FileStatus> getChangedInputs() {
        return mInputs.getChangedFiles();
    }

    /**
     * Returns a list of changed output. Empty list means no output changes.
     *
     * @return a map of (file, status) for all changed output files.
     */
    @NonNull
    public Map<File, FileStatus> getChangedOutputs() {
        return mOutputs.getChangedFiles();
    }

    /**
     * Update the outputs before writing the file states
     */
    public void updateOutputs(Collection<File> outputs) {
        mOutputs.update(outputs);
    }
}
