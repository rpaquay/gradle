/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.model.cpp;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.Task;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Represents the compilation details for a binary.
 *
 * @since 4.10
 */
@Incubating
public interface CompilationDetails {
    /**
     * Returns the details of the compilation task for the binary. This is the task that should be run to produce the object files, but may not necessarily be the task that compiles the source files. For example, the task may perform some post processing of the object files.
     */
    Task getCompileTask();

    /**
     * All framework search paths.
     */
    List<File> getFrameworkSearchPaths();

    /**
     * All compilation unit used for this binary.
     */
    List<File> getSources();

    /**
     * All macro define directives.
     */
    DomainObjectSet<? extends MacroDirective> getMacroDefines();

    /**
     * All macro undefine directives.
     */
    Set<String> getMacroUndefines();

    /**
     * All system search paths.
     */
    List<File> getSystemHeaderSearchPaths();

    /**
     * All user search paths.
     */
    List<File> getUserHeaderSearchPaths();

    /**
     * Returns any additional compiler arguments.
     */
    List<String> getAdditionalArgs();
}