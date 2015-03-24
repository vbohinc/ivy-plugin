/**
 * Copyright 2010-2011 Timothy Bingaman, Jesse Bexten
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.ivy;

import java.util.List;
import java.util.Set;

import jenkins.model.Jenkins;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;

/**
 * Invoke downstream projects with applicable parameters using Hudson's
 * DependencyGraph.Dependency interface.
 * 
 * @author tbingaman
 */
public class IvyThresholdDependency extends IvyDependency {
    private Result threshold;
    private boolean useUpstreamParameters;

    public IvyThresholdDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, Result threshold, boolean useUpstreamParameters) {
        super(upstream, downstream);
        this.threshold = threshold;
        this.useUpstreamParameters = useUpstreamParameters;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener, List<Action> actions) {
        if (!build.getResult().isBetterOrEqualTo(threshold))
            return false;
        
        AbstractProject<?,?> down = getDownstreamProject();
        if (AbstractIvyBuild.debug)
            listener.getLogger().println("Considering whether to trigger " + down + " or not");

        if (inDownstreamProjects(down)) {
            if (AbstractIvyBuild.debug)
                listener.getLogger().println(" -> No, because downstream has dependencies in the downstream projects list");
            return false;
        }
        
        if (useUpstreamParameters) {
            List<ParametersAction> paramActions = build.getActions(ParametersAction.class);

            for (ParametersAction parametersAction : paramActions) {
                actions.add(parametersAction);
            }
        }
        return true;
    }

    /**
     * Determines whether any of the upstream project are either
     * building or in the queue.
     *
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     *
     * @param downstreamProject
     *      The AbstractProject we want to build.
     * @param excludeProject
     *      An AbstractProject to exclude - if we see this in the transitive
     *      dependencies, we're not going to bother checking to see if it's
     *      building. For example, pass the current parent project to be sure
     *      that it will be ignored when looking for building dependencies.
     * @return
     *      True if any upstream projects are building or in queue, false otherwise.
     */
    @SuppressWarnings("rawtypes")
    private boolean areUpstreamsBuilding(AbstractProject<?,?> downstreamProject,
            AbstractProject<?,?> excludeProject) {
        DependencyGraph graph = Jenkins.getInstance().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);
        for (AbstractProject tup : tups) {
            if(tup!=excludeProject && (tup.isBuilding() || tup.isInQueue()))
                return true;
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private boolean inDownstreamProjects(AbstractProject<?,?> downstreamProject) {
        DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);
        
        List<AbstractProject<?,?>> downstreamProjects = getUpstreamProject().getDownstreamProjects();
        for (AbstractProject<?,?> tup : tups) {
            for (AbstractProject<?,?> dp : downstreamProjects) {
                if(dp!=getUpstreamProject() && dp!=downstreamProject && !isIvyModuleOfDownstreamProject(dp, downstreamProject) && dp==tup) 
                    return true;
            }
        }
        return false;
    }

    private boolean isIvyModuleOfDownstreamProject(AbstractProject<?, ?> dp, AbstractProject<?, ?> downstreamProject) {
        return dp instanceof IvyModule && ((IvyModule) dp).getParent() == downstreamProject;
    }
}
