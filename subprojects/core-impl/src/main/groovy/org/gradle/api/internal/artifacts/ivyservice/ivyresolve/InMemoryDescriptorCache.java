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

import com.google.common.collect.MapMaker;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.util.Map;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class InMemoryDescriptorCache {

    private final static Logger LOG = Logging.getLogger(InMemoryDescriptorCache.class);

    private Map<ModuleVersionIdentifier, BuildableModuleVersionMetaData> localDescriptorsCache = new MapMaker().softValues().makeMap();
    private Map<ModuleVersionIdentifier, BuildableModuleVersionMetaData> descriptorsCache = new MapMaker().softValues().makeMap();
    private Map<Artifact, File> artifactsCache = new MapMaker().softValues().makeMap();
    private Map<String, CachedRepository> repos = new MapMaker().softValues().makeMap();

    public LocalAwareModuleVersionRepository cached(LocalAwareModuleVersionRepository input) {
        CachedRepository cachedRepository = repos.get(input.getId());
        if (cachedRepository == null) {
            LOG.debug("Creating new in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
            cachedRepository = new CachedRepository(input);
            repos.put(input.getId(), cachedRepository);
        } else {
            LOG.debug("Reusing in-memory cache for repo '{}' [{}] is already cached in memory.", input.getName(), input.getId());
        }
        return cachedRepository;
    }

    private class CachedRepository implements LocalAwareModuleVersionRepository {
        private LocalAwareModuleVersionRepository delegate;

        public CachedRepository(LocalAwareModuleVersionRepository delegate) {
            this.delegate = delegate;
        }

        public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            ModuleVersionIdentifier id = newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
            BuildableModuleVersionMetaData fromCache = localDescriptorsCache.get(id);
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
            BuildableModuleVersionMetaData fromCache = descriptorsCache.get(id);
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
            File fromCache = artifactsCache.get(artifact);
            if (fromCache == null) {
                delegate.resolve(artifact, result, moduleSource);
                if (result.getFailure() != null) {
                    artifactsCache.put(artifact, result.getFile());
                }
            } else {
                result.resolved(fromCache);
            }
        }
    }
}