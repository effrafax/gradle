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
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class InMemoryDescriptorCache {

    private final static Logger LOG = Logging.getLogger(InMemoryDescriptorCache.class);

    private Map<String, DataCache> cachePerRepo = new MapMaker().softValues().makeMap();

    public LocalAwareModuleVersionRepository cached(LocalAwareModuleVersionRepository input) {
//        return input;
        DataCache dataCache = cachePerRepo.get(input.getId());
        if (dataCache == null) {
            LOG.debug("Creating new in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
            dataCache = new DataCache();
            cachePerRepo.put(input.getId(), dataCache);
        } else {
            LOG.debug("Reusing in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
        }
        return new CachedRepository(dataCache, input);
    }

    private class DataCache {
        //BuildableModuleVersionMetaData is mutable - cannot really be used as value (or make a copy of it)
        private final Map<ModuleVersionIdentifier, BuildableModuleVersionMetaData> localDescriptors = new HashMap<ModuleVersionIdentifier, BuildableModuleVersionMetaData>();
        private final Map<ModuleVersionIdentifier, BuildableModuleVersionMetaData> descriptors = new HashMap<ModuleVersionIdentifier, BuildableModuleVersionMetaData>();
        //Artifact is mutable - cannot really be used as value
        private final Map<Artifact, File> artifacts = new HashMap<Artifact, File>();
    }

    private class CachedRepository implements LocalAwareModuleVersionRepository {
        private final Object lock = new Object();
        private DataCache cache;
        private LocalAwareModuleVersionRepository delegate;

        public CachedRepository(DataCache cache, LocalAwareModuleVersionRepository delegate) {
            this.cache = cache;
            this.delegate = delegate;
        }

        public String getId() {
            return delegate.getId();
        }

        public String getName() {
            return delegate.getName();
        }

        public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            //use selector instead of the id
            ModuleVersionIdentifier id = newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
            BuildableModuleVersionMetaData fromCache = cache.localDescriptors.get(id);
            if (fromCache == null) {
                delegate.getLocalDependency(dependency, result);
                if (result.getState() == BuildableModuleVersionMetaData.State.Resolved) {
                    cache.localDescriptors.put(result.getId(), result);
                }
                //missing(), probablyMissing() - also cache
            } else {
                result.resolved(id, fromCache.getDescriptor(), fromCache.isChanging(), fromCache.getModuleSource());
            }
        }

        public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            ModuleVersionIdentifier id = newId(dependency.getRequested().getGroup(), dependency.getRequested().getName(), dependency.getRequested().getVersion());
            BuildableModuleVersionMetaData fromCache = cache.descriptors.get(id);
            if (fromCache == null) {
                delegate.getDependency(dependency, result);
                if (result.getState() == BuildableModuleVersionMetaData.State.Resolved) {
                    cache.descriptors.put(result.getId(), result);
                }
            } else {
                result.resolved(id, fromCache.getDescriptor(), fromCache.isChanging(), fromCache.getModuleSource());
            }
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
            //use DefaultArtifactIdentifier - (add hashCode(), equals())
            File fromCache = cache.artifacts.get(artifact);
            if (fromCache == null) {
                delegate.resolve(artifact, result, moduleSource);
                if (result.getFailure() == null) {
                    cache.artifacts.put(artifact, result.getFile());
                }
            } else {
                result.resolved(fromCache);
            }
        }
    }
}