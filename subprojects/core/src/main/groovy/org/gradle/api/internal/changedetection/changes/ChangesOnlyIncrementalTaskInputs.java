/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChanges;
import org.gradle.api.tasks.incremental.InputFile;

import java.util.ArrayList;
import java.util.List;

public class ChangesOnlyIncrementalTaskInputs extends StatefulIncrementalTaskInputs {
    private final TaskStateChanges inputFilesState;
    private List<InputFile> removedFiles = new ArrayList<InputFile>();

    public ChangesOnlyIncrementalTaskInputs(TaskStateChanges inputFilesState) {
        this.inputFilesState = inputFilesState;
    }

    public boolean isIncremental() {
        return true;
    }

    @Override
    protected void doOutOfDate(final Action<? super InputFile> outOfDateAction) {
        for (TaskStateChange change : inputFilesState) {
            InputFile fileChange = (InputFile) change;
            if (fileChange.isRemoved()) {
                removedFiles.add(fileChange);
            } else {
                outOfDateAction.execute(fileChange);
            }
        }
    }

    @Override
    protected void doRemoved(Action<? super InputFile> removedAction) {
        for (InputFile removedFile : removedFiles) {
            removedAction.execute(removedFile);
        }
    }
}
