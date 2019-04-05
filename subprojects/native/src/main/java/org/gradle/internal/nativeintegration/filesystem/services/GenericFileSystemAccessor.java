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

package org.gradle.internal.nativeintegration.filesystem.services;

import net.rubygrapefruit.platform.file.DirEntry;
import net.rubygrapefruit.platform.file.FileInfo;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileException;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileSystemAccessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

@SuppressWarnings("Since15")
public class GenericFileSystemAccessor implements FileSystemAccessor {
    @SuppressWarnings("FieldCanBeLocal")
    private static boolean debuggingNeeded = false;

    private FileModeAccessor stat;
    private final FileMetadataAccessor metadata;

    public GenericFileSystemAccessor(FileModeAccessor stat, FileMetadataAccessor metadata) {
        this.stat = stat;
        this.metadata = metadata;
    }

    @Override
    public void walkFileTree(File start, Set<FileVisitOption> options, int maxDepth, FileVisitor<? super File> visitor)
        throws IOException {

        if (metadata instanceof NativePlatformBackedFileMetadataAccessor) {
            if (debuggingNeeded) {
                CollectingVisitor visitor1 = new CollectingVisitor();
                CollectingVisitor visitor2 = new CollectingVisitor();
                Files.walkFileTree(start.toPath(), wrap(options), maxDepth, wrapVisitor(visitor1));
                walkFileTreeWorker((NativePlatformBackedFileMetadataAccessor) metadata, start, options, maxDepth, visitor2);
                if (!visitor1.equals(visitor2)) {
                    throw new IllegalArgumentException("Error visiting file tree");
                }
            }

            walkFileTreeWorker((NativePlatformBackedFileMetadataAccessor)metadata, start, options, maxDepth, visitor);
        } else {
            Files.walkFileTree(start.toPath(), wrap(options), maxDepth, wrapVisitor(visitor));
        }
    }

    private void walkFileTreeWorker(NativePlatformBackedFileMetadataAccessor stat, File start, Set<FileVisitOption> options, int maxDepth, FileVisitor<? super File> visitor)
        throws IOException {
        final boolean linkTarget = options.contains(FileVisitOption.FOLLOW_LINKS);
        new RecursiveWalker(stat, start, maxDepth, linkTarget, visitor).invoke();
    }

    private class RecursiveWalker {
        private final NativePlatformBackedFileMetadataAccessor stat;
        private final File start;
        private final int maxDepth;
        private final FileVisitor<? super File> visitor;
        private final boolean linkTarget;

        public RecursiveWalker(NativePlatformBackedFileMetadataAccessor stat, File start, int maxDepth, boolean linkTarget, FileVisitor<? super File> visitor) {
            this.stat = stat;
            this.start = start;
            this.maxDepth = maxDepth;
            this.visitor = visitor;
            this.linkTarget = linkTarget;
        }

        public FileVisitResult invoke() throws IOException {
            return walkFileTreeWorker(new WalkDirEntry(start, stat.statFileInfo(start)), 0);
        }

        /**
         * Returns {@link FileVisitResult#TERMINATE} when walking should stop
         */
        private FileVisitResult walkFileTreeWorker(WalkDirEntry walkDirEntry, int curDepth)
            throws IOException {
            // Pre-visit directory
            FileVisitResult preVisitResult = visitor.preVisitDirectory(walkDirEntry.file, walkDirEntry.getMetadataSnapshot());
            if (preVisitResult == FileVisitResult.TERMINATE) {
                return FileVisitResult.TERMINATE;
            }

            // Visit children and subtree
            IOException error = null;
            if (preVisitResult != FileVisitResult.SKIP_SUBTREE) {
                List<? extends DirEntry> entries = null;
                try {
                    entries = stat.listDir(walkDirEntry.file, linkTarget);
                } catch (IOException e) {
                    error = e;
                }
                if (entries != null) {
                    for (DirEntry childEntry : entries) {
                        File childEntryFile = new File(walkDirEntry.file, childEntry.getName());
                        if ((childEntry.getType() == FileInfo.Type.Directory) || (childEntry.getType() == FileInfo.Type.Symlink && linkTarget)) {
                            if (curDepth < maxDepth) {
                                FileVisitResult fileResult = walkFileTreeWorker(new WalkDirEntry(childEntryFile, childEntry), curDepth + 1);
                                if (fileResult == FileVisitResult.TERMINATE) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        } else if (childEntry.getType() == FileInfo.Type.File){
                            FileVisitResult fileResult = visitor.visitFile(childEntryFile, WalkDirEntry.toMetadataSnapshot(childEntry));
                            if (fileResult == FileVisitResult.TERMINATE) {
                                return FileVisitResult.TERMINATE;
                            }
                        }
                    }
                }
            }

            // Post-visit directory
            FileVisitResult postVisitResult = visitor.postVisitDirectory(walkDirEntry.file, error);
            if (postVisitResult == FileVisitResult.TERMINATE) {
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private void walkFileTreeWorker_Old(NativePlatformBackedFileMetadataAccessor stat, File start, Set<FileVisitOption> options, int maxDepth, FileVisitor<? super File> visitor)
        throws IOException {
        final boolean linkTarget = options.contains(FileVisitOption.FOLLOW_LINKS);

        Stack<WalkDirEntry> paths = new Stack<WalkDirEntry>();
        paths.push(new WalkDirEntry(start, stat.statFileInfo(start)));
        while (!paths.empty()) {
            WalkDirEntry dir = paths.pop();

            // Visit directory
            FileVisitResult preVisitResult = visitor.preVisitDirectory(dir.file, dir.getMetadataSnapshot());
            if (preVisitResult == FileVisitResult.TERMINATE) {
                break;
            }

            // Visit subtree
            IOException error = null;
            if (preVisitResult != FileVisitResult.SKIP_SUBTREE) {
                List<? extends DirEntry> entries = null;
                try {
                    entries = stat.listDir(dir.file, linkTarget);
                } catch (IOException e) {
                    error = e;
                }
                if (entries != null) {
                    boolean terminate = false;
                    for (DirEntry entry : entries) {
                        File entryFile = new File(dir.file, entry.getName());
                        if ((entry.getType() == FileInfo.Type.Directory) || (entry.getType() == FileInfo.Type.Symlink && linkTarget)) {
                            if (paths.size() < maxDepth) {
                                paths.push(new WalkDirEntry(entryFile, entry));
                            }
                        } else if (entry.getType() == FileInfo.Type.File){
                            FileVisitResult fileResult = visitor.visitFile(entryFile, WalkDirEntry.toMetadataSnapshot(entry));
                            if (fileResult == FileVisitResult.TERMINATE) {
                                terminate = true;
                                break;
                            }
                        }
                    }
                    if (terminate) {
                        break;
                    }
                }
            }

            // Post-visit directory
            FileVisitResult postVisitResult = visitor.postVisitDirectory(dir.file, error);
            if (postVisitResult == FileVisitResult.TERMINATE) {
                break;
            }
        }
    }

    private static class WalkDirEntry {
        private final File file;
        private final FileInfo fileInfo;

        public WalkDirEntry(File file, FileInfo fileInfo) {
            this.file = file;
            this.fileInfo = fileInfo;
        }

        FileMetadataSnapshot getMetadataSnapshot() {
            return toMetadataSnapshot(this.fileInfo);
        }

        public static FileMetadataSnapshot toMetadataSnapshot(FileInfo stat) {
            return new NativeFileMetadata(stat);
        }

        private static class NativeFileMetadata implements FileMetadataSnapshot {
            private FileInfo fileInfo;

            public NativeFileMetadata(FileInfo fileInfo) {
                this.fileInfo = fileInfo;
            }

            @Override
            public FileType getType() {
                switch(fileInfo.getType()) {
                    case File:
                        return FileType.RegularFile;
                    case Directory:
                        return FileType.Directory;
                    case Symlink:
                    case Other:
                    case Missing:
                    default:
                        return FileType.Missing;
                }
            }

            @Override
            public long getLastModified() {
                return fileInfo.getLastModifiedTime();
            }

            @Override
            public long getLength() {
                if (getType() == FileType.Directory) {
                    return 0;
                }
                return fileInfo.getSize();
            }
        }
    }

    private java.nio.file.FileVisitor<? super Path> wrapVisitor(final FileVisitor<? super File> visitor) {
        return new NioPathFileVisitorAdapter(visitor);
    }

    private Set<java.nio.file.FileVisitOption> wrap(Set<FileVisitOption> options) {
        Set<java.nio.file.FileVisitOption> result = new HashSet<java.nio.file.FileVisitOption>();
        if (options.contains(FileVisitOption.FOLLOW_LINKS)) {
            result.add(java.nio.file.FileVisitOption.FOLLOW_LINKS);
        }
        return result;
    }

    @Override
    public int getUnixMode(File f) throws FileException {
        try {
            return stat.getUnixMode(f);
        } catch (Exception e) {
            throw new FileException(String.format("Could not get file mode for '%s'.", f), e);
        }
    }

    @Override
    public FileMetadataSnapshot stat(File f) throws FileException {
        return metadata.stat(f);
    }

    private static class NioPathFileVisitorAdapter implements java.nio.file.FileVisitor<Path> {
        private final FileVisitor<? super File> visitor;

        NioPathFileVisitorAdapter(FileVisitor<? super File> visitor) {
            this.visitor = visitor;
        }

        @Override
        public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return wrapFileResult(visitor.preVisitDirectory(dir.toFile(), wrapAttrs(attrs)));
        }

        @Override
        public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            return wrapFileResult(visitor.visitFile(file.toFile(), wrapAttrs(attrs)));
        }

        @Override
        public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return wrapFileResult(visitor.visitFileFailed(file.toFile(), wrapException(exc)));
        }

        @Override
        public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return wrapFileResult(visitor.postVisitDirectory(dir.toFile(), wrapException(exc)));
        }

        private java.nio.file.FileVisitResult wrapFileResult(FileVisitResult visitResult) {
            switch(visitResult) {
                case CONTINUE:
                    return java.nio.file.FileVisitResult.CONTINUE;
                case TERMINATE:
                    return java.nio.file.FileVisitResult.TERMINATE;
                case SKIP_SUBTREE:
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                default:
                    throw new IllegalArgumentException();
            }
        }

        private FileMetadataSnapshot wrapAttrs(final BasicFileAttributes attrs) {
            return new NioFileMetadataSnapshot(attrs);
        }

        private IOException wrapException(IOException exc) {
            //return new FileException("File I/O error", exc);
            return exc;
        }

        private static class NioFileMetadataSnapshot implements FileMetadataSnapshot {
            private final BasicFileAttributes attrs;

            public NioFileMetadataSnapshot(BasicFileAttributes attrs) {
                this.attrs = attrs;
            }

            @Override
            public FileType getType() {
                return attrs.isRegularFile() ? FileType.RegularFile :
                    attrs.isDirectory() ? FileType.Directory : FileType.Missing;
            }

            @Override
            public long getLastModified() {
                return attrs.lastModifiedTime().toMillis();
            }

            @Override
            public long getLength() {
                if (getType() == FileType.Directory) {
                    return 0;
                }
                return attrs.size();
            }
        }
    }

    private static class CollectingVisitor implements FileVisitor <File> {
        private final List<Event> events = new ArrayList<Event>();

        private static class Event {
            private final EventKind kind;
            private final File file;
            private final FileMetadataSnapshot metadata;
            private final IOException exc;

            public Event(EventKind kind, File file, FileMetadataSnapshot metadata) {
                this.kind = kind;
                this.file = file;
                this.metadata = metadata;
                this.exc = null;
            }

            public Event(EventKind kind, File file, IOException exc) {
                this.kind = kind;
                this.file = file;
                this.metadata = null;
                this.exc = exc;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("Event{kind=");
                sb.append(kind);
                sb.append(",file=");
                sb.append(file);
                sb.append(",metadata=");
                sb.append(metadata == null ? "null" : String.format("{type=%s,modified=%d,length=%d}", metadata.getType(), metadata.getLastModified(), metadata.getLength()));
                sb.append(",exc=");
                sb.append(exc == null ? "null" : exc.toString());
                sb.append("}");
                return sb.toString();
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof  Event)) {
                    return false;
                }

                Event other = (Event)obj;
                return this.kind == other.kind &&
                    this.file.equals(other.file) &&
                    equalsMetadata(this.metadata, other.metadata) &&
                    equalsException(this.exc, other.exc);
            }

            private boolean equalsMetadata(FileMetadataSnapshot m1, FileMetadataSnapshot m2) {
                if (m1 == null) {
                    return m2 == null;
                } else {
                    if (m2 == null) {
                        return false;
                    } else {
                        return m1.getType() == m2.getType() &&
                            m1.getLength() == m2.getLength() &&
                            Math.abs(m1.getLastModified() - m2.getLastModified()) < 2000; // Within 2 seconds
                    }
                }
            }

            private boolean equalsException(IOException e1, IOException e2) {
                if (e1 == null) {
                    return e2 == null;
                } else {
                    if (e2 == null) {
                        return false;
                    } else {
                        return e1.getClass() == e2.getClass();
                    }
                }
            }
        }

        private enum EventKind {
            preVisitDirectory,
            visitFile,
            visitFileFailed,
            postVisitDirectory,
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CollectingVisitor)) {
                return false;
            }
            CollectingVisitor other = (CollectingVisitor)obj;
            int size = this.events.size();
            if (size != other.events.size()) {
                return false;
            }

            for (int i = 0; i < size; i++) {
                Event event1 = this.events.get(i);
                Event event2 = other.events.get(i);
                if (!event1.equals(event2)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public FileVisitResult preVisitDirectory(File dir, FileMetadataSnapshot attrs) throws IOException {
            events.add(new Event(EventKind.preVisitDirectory, dir, attrs));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(File file, FileMetadataSnapshot attrs) throws IOException {
            events.add(new Event(EventKind.visitFile, file, attrs));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(File file, IOException exc) throws IOException {
            events.add(new Event(EventKind.visitFileFailed, file, exc));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(File dir, IOException exc) throws IOException {
            events.add(new Event(EventKind.postVisitDirectory, dir, exc));
            return FileVisitResult.CONTINUE;
        }
    }
}
