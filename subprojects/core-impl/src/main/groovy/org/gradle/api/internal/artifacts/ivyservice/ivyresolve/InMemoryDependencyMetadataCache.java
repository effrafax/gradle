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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Stoppable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.Missing;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.ProbablyMissing;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.Resolved;

/**
 * Caches the dependency metadata (descriptors, artifact files) in memory. Uses soft maps to reduce heap pressure.
 */
public class InMemoryDependencyMetadataCache implements Stoppable {

    private final static Logger LOG = Logging.getLogger(InMemoryDependencyMetadataCache.class);

    private Map<String, DataCache> cachePerRepo = new MapMaker().softValues().makeMap();

    private final Stats stats = new Stats();

    public LocalAwareModuleVersionRepository cached(LocalAwareModuleVersionRepository input) {
        DataCache dataCache = cachePerRepo.get(input.getId());
        stats.reposWrapped++;
        if (dataCache == null) {
            LOG.debug("Creating new in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
            dataCache = new DataCache();
            stats.cacheInstances++;
            cachePerRepo.put(input.getId(), dataCache);
        } else {
            LOG.debug("Reusing in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
        }
        return new CachedRepository(dataCache, input);
    }

    public void stop() {
        cachePerRepo.clear();
        LOG.info("In-memory dependency metadata cache closed. {}", stats);
    }

    private class Stats {
        private int cacheInstances;
        private int reposWrapped;
        private int localMetadataCached;
        private int metadataCached;
        private int artifactsCached;
        public String toString() {
            return String.format(
                    "Repos cached: %s, cache instances: %s, modules served from cache: %s (local %s), artifacts: %s",
                    reposWrapped, cacheInstances, metadataCached, localMetadataCached, artifactsCached);
        }
    }

    //TODO SF bust to separate classes and add unit test coverage
    private class DataCache {
        private final Map<ModuleVersionSelector, CachedResult> localDescriptors = new HashMap<ModuleVersionSelector, CachedResult>();
        private final Map<ModuleVersionSelector, CachedResult> descriptors = new HashMap<ModuleVersionSelector, CachedResult>();
        private final Map<ArtifactIdentifier, File> artifacts = new HashMap<ArtifactIdentifier, File>();
    }

    private class CachedResult {
        private final BuildableModuleVersionMetaData.State state;
        private final ModuleDescriptor moduleDescriptor;
        private final boolean isChanging;
        private final ModuleSource moduleSource;
        private final ModuleVersionIdentifier id;

        public CachedResult(BuildableModuleVersionMetaData result) {
            this.state = result.getState();
            if (state == Resolved) {
                this.id = result.getId();
                this.moduleDescriptor = result.getDescriptor();
                this.isChanging = result.isChanging();
                this.moduleSource = result.getModuleSource();
            } else {
                this.id = null;
                this.moduleDescriptor = null;
                this.isChanging = false;
                this.moduleSource = null;
            }
        }

        private boolean isCacheable() {
            return state == Missing || state == ProbablyMissing || state == Resolved;
        }

        public void supply(BuildableModuleVersionMetaData result) {
            assert isCacheable() : "Results are not cacheable, cannot supply the results.";
            if (state == Resolved) {
                result.resolved(id, moduleDescriptor, isChanging, moduleSource);
            } else if (state == Missing) {
                result.missing();
            } else if (state == ProbablyMissing) {
                result.probablyMissing();
            }
        }
    }

    private class CachedRepository implements LocalAwareModuleVersionRepository {
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
            CachedResult fromCache = cache.localDescriptors.get(dependency.getRequested());
            if (fromCache == null) {
                delegate.getLocalDependency(dependency, result);
                CachedResult cachedResult = new CachedResult(result);
                if (cachedResult.isCacheable()) {
                    cache.localDescriptors.put(dependency.getRequested(), cachedResult);
                }
            } else {
                stats.localMetadataCached++;
                fromCache.supply(result);
            }
        }

        public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
            CachedResult fromCache = cache.descriptors.get(dependency.getRequested());
            if (fromCache == null) {
                delegate.getDependency(dependency, result);
                CachedResult cachedResult = new CachedResult(result);
                if (cachedResult.isCacheable()) {
                    cache.descriptors.put(dependency.getRequested(), cachedResult);
                }
            } else {
                stats.metadataCached++;
                fromCache.supply(result);
            }
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
            ArtifactIdentifier id = new DefaultArtifactIdentifier(artifact);
            File fromCache = cache.artifacts.get(id);
            if (fromCache == null) {
                delegate.resolve(artifact, result, moduleSource);
                if (result.getFailure() == null) {
                    cache.artifacts.put(id, result.getFile());
                }
            } else {
                stats.artifactsCached++;
                result.resolved(fromCache);
            }
        }
    }
}