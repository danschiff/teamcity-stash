/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mendhak.teamcity.stash;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.VcsRootInstance;
import mendhak.teamcity.stash.ui.UpdateChangeStatusFeature;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 05.09.12 22:28
 */
public class ChangeStatusListener
{
    private static final Logger LOG = Logger.getInstance(ChangeStatusListener.class.getName());

    @NotNull
    private final ChangeStatusUpdater myUpdater;

    public ChangeStatusListener(@NotNull final EventDispatcher<BuildServerListener> listener,
                                @NotNull final ChangeStatusUpdater updater)
    {
        myUpdater = updater;
        listener.addListener(new BuildServerAdapter()
        {
            @Override
            public void changesLoaded(SRunningBuild build)
            {
                updateBuildStatus(build, true);
            }

            @Override
            public void buildFinished(SRunningBuild build)
            {
                updateBuildStatus(build, false);
            }
        });
    }

    private void updateBuildStatus(@NotNull final SRunningBuild build, boolean isStarting)
    {
        SBuildType bt = build.getBuildType();
        if (bt == null)
        {
            return;
        }

        for (SBuildFeatureDescriptor feature : bt.getBuildFeatures())
        {
            if (!feature.getType().equals(UpdateChangeStatusFeature.FEATURE_TYPE))
            {
                continue;
            }

            final ChangeStatusUpdater.Handler h = myUpdater.getUpdateHandler(feature);

            Map<VcsRootInstance, String> changes = getLatestChangesHash(build);
            if (changes.isEmpty())
            {
                System.err.println("No revisions were found to update Stash status. Please check you have Git VCS roots in the build configuration");
            }

            for (Map.Entry<VcsRootInstance, String> e : changes.entrySet())
            {
                if (isStarting)
                {
                    h.scheduleChangeStarted(e.getValue(), build);
                }
                else
                {
                    h.scheduleChangeCompeted(e.getValue(), build);
                }
            }
        }
    }

    @NotNull
    private Map<VcsRootInstance, String> getLatestChangesHash(@NotNull final SRunningBuild build)
    {
        final Map<VcsRootInstance, String> result = new HashMap<VcsRootInstance, String>();
        for (BuildRevision rev : build.getRevisions())
        {
            System.err.println("Found revision to report status to Stash: " + rev.getRevision() + " from root " + rev.getRoot().getName());
            result.put(rev.getRoot(), rev.getRevision());
        }
        return result;
    }
}