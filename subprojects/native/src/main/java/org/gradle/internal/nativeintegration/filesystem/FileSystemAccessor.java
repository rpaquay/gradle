/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem;

import org.gradle.internal.file.FileMetadataSnapshot;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public interface FileSystemAccessor extends Stat {
    void walkFileTree(File start,
                      Set<FileVisitOption> options,
                      int maxDepth,
                      FileVisitor<? super File> visitor)
        throws IOException;

    enum FileVisitOption {
        FOLLOW_LINKS
    }

    interface FileVisitor<T> {
        FileVisitResult preVisitDirectory(T dir, FileMetadataSnapshot attrs)
            throws IOException;

        FileVisitResult visitFile(T file, FileMetadataSnapshot attrs)
            throws IOException;

        FileVisitResult visitFileFailed(T file, IOException exc)
            throws IOException;

        FileVisitResult postVisitDirectory(T dir, IOException exc)
            throws IOException;
    }

    enum FileVisitResult {
        CONTINUE,
        TERMINATE,
        SKIP_SUBTREE,
//        SKIP_SIBLINGS
    }
}
