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

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;

/**
 * A {@link File} and its associated data needed to figure out if a file changed or not.
 */
class FileEntity {

    private static final byte[] sBuffer = new byte[4096];

    private final File mFile;
    private final long mLastModified;
    private long mLength;
    private String mSha1;

    /**
     * Exception to indicate a failure to check a jar file's content.
     */
    private static final class Sha1Exception extends Exception {
        private static final long serialVersionUID = 1L;
        private final File file;

        public Sha1Exception(File jarFile, Throwable cause) {
            super(cause);
            file = jarFile;
        }

        public File getJarFile() {
            return file;
        }
    }

    /**
     * Creates an entity from cached data.
     *
     * @param file the file
     * @param lastModified when it was last modified
     * @param length its length
     * @param sha1 its sha1
     */
    FileEntity(File file, long lastModified, long length, String sha1) {
        mFile = file;
        mLastModified = lastModified;
        mLength = length;
        mSha1 = sha1;
    }

    /**
     * Creates an entity from a {@link File}.
     *
     * The sha1 is not computed yet, it'll be done on demand when {@link #getSha1()} is called.
     *
     * @param file the file.
     */
    FileEntity(File file) {
        mFile = file;
        mLastModified = file.lastModified();
        mLength = file.length();
    }

    /**
     * Returns the file's last modified info.
     * @return the file's last modified info.
     */
    long getLastModified() {
        return mLastModified;
    }

    /**
     * Return the file length.
     * @return the file length.
     */
    long getLength() {
        return mLength;
    }

    /**
     * Returns the file this entity represents.
     * @return the file.
     */
    File getFile() {
        return mFile;
    }

    /**
     * Returns the file's sha1, computing it if necessary.
     *
     * @return the fha1 or null if it couldn't be computed.
     */
    String getSha1() {
        try {
            return computeAndReturnSha1();
        } catch (Sha1Exception e) {
            return null;
        }
    }

    /**
     * Checks whether the {@link File#lastModified()} matches the cached value. If not, length
     * is updated and the sha1 is reset (but not recomputed, this is done on demand).
     *
     * @return return whether the file was changed.
     */
    private boolean checkValidity() {
        if (mLastModified != mFile.lastModified()) {
            mLength = mFile.length();
            mSha1 = null;
            return true;
        }

        return false;
    }

    /**
     * Returns whether the two entity are different files.
     *
     * This will compute the files' sha1 if they are not yet computed.
     *
     * @param fileEntity the file to compare to.
     * @return true if the files are the same, false otherwise.
     */
    public boolean isDifferentThan(FileEntity fileEntity) {
        assert fileEntity.mFile.equals(mFile);

        // same date, same files.
        if (mLastModified == fileEntity.mLastModified) {
            return false;
        }

        try {
            // different date doesn't necessarily mean different file.
            // start with size, less computing intensive than sha1.
            return mLength != fileEntity.mLength ||
                    !computeAndReturnSha1().equals(fileEntity.computeAndReturnSha1());
        } catch (Sha1Exception e) {
            // if we can't compute the sha1, we consider the files different.
            return true;
        }
    }

    /**
     * Returns the file's sha1, computing it if necessary.
     *
     * @return the sha1
     * @throws Sha1Exception
     */
    private String computeAndReturnSha1() throws Sha1Exception {
        if (mSha1 == null) {
            mSha1 = getSha1(mFile);
        }
        return mSha1;
    }

    /**
     * Computes the sha1 of a file and returns it.
     *
     * @param f the file to compute the sha1 for.
     * @return the sha1 value
     * @throws Sha1Exception if the sha1 value cannot be computed.
     */
    static String getSha1(File f) throws Sha1Exception {
        synchronized (sBuffer) {

            try {
                HashCode value = ByteStreams.hash(Files.newInputStreamSupplier(f), Hashing.sha1());
                return value.toString();
            } catch (Exception e) {
                throw new Sha1Exception(f, e);
            }
        }
    }

    @Override
    public String toString() {
        return "FileEntity{" +
                "mFile=" + mFile +
                ", mLastModified=" + mLastModified +
                ", mLength=" + mLength +
                ", mSha1='" + mSha1 + '\'' +
                '}';
    }
}
