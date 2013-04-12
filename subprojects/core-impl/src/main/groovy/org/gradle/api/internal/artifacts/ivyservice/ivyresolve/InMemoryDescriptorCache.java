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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class InMemoryDescriptorCache {

    private static Map localDescriptorsCache = new HashMap();
    private static Map descriptorsCache = new HashMap();
    private static Map artifactsCache = new HashMap();

    public LocalAwareModuleVersionRepository cached(LocalAwareModuleVersionRepository input) {
//        return input;
        return new CachedRepository(input);
    }

    private class CachedRepository implements LocalAwareModuleVersionRepository {
        private LocalAwareModuleVersionRepository delegate;

        public CachedRepository(LocalAwareModuleVersionRepository delegate) {
            this.delegate = delegate;
        }

        public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            ModuleVersionIdentifier id = newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
            BuildableModuleVersionMetaData fromCache = (BuildableModuleVersionMetaData) descriptorsCache.get(id);
            if (fromCache == null) {
                delegate.getLocalDependency(dependency, result);
                if (result.getState() == BuildableModuleVersionMetaData.State.Resolved) {
                    localDescriptorsCache.put(result.getId(), result);
                }
            } else {
                result.resolved(id, fromCache.getDescriptor(), fromCache.isChanging(), fromCache.getModuleSource());
            }
        }

        public String getId() {
            return delegate.getId();
        }

        public String getName() {
            return delegate.getName();
        }

        public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            ModuleVersionIdentifier id = newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
            BuildableModuleVersionMetaData fromCache = (BuildableModuleVersionMetaData) descriptorsCache.get(id);
            if (fromCache == null) {
                delegate.getDependency(dependency, result);
                if (result.getState() == BuildableModuleVersionMetaData.State.Resolved) {
                    descriptorsCache.put(result.getId(), result);
                }
            } else {
                result.resolved(id, fromCache.getDescriptor(), fromCache.isChanging(), fromCache.getModuleSource());
            }
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
            BuildableArtifactResolveResult fromCache = (BuildableArtifactResolveResult) artifactsCache.get(artifact);
            if (fromCache == null) {
                delegate.resolve(artifact, result, moduleSource);
                if (result.getFailure() != null) {
                    artifactsCache.put(artifact, result);
                }
            } else {
                result.resolved(fromCache.getFile());
            }
        }
    }
}
