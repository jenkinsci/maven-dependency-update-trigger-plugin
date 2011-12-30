package org.jvnet.hudson.plugins.mavendepsupdate.util;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * NOTE : <b>this class is not designed for external use so it can change without any prior notice</b>
 * class coming from ASF sources
 * http://svn.apache.org/repos/asf/maven/maven-3/trunk/maven-core/src/main/java/org/apache/maven/ReactorReader.java
 * FIXME simplify more !!
 *
 * @author Olivier Lamy
 * @since 1.1
 */
public class ReactorReader
    implements WorkspaceReader
{


    private Map<String, MavenProject> projectsByGAV;

    private Map<String, List<MavenProject>> projectsByGA;

    private WorkspaceRepository repository;

    public ReactorReader( Map<String, MavenProject> reactorProjects )
    {
        projectsByGAV = reactorProjects;

        projectsByGA = new HashMap<String, List<MavenProject>>( reactorProjects.size() * 2 );
        for ( MavenProject project : reactorProjects.values() )
        {
            String key = ArtifactUtils.versionlessKey( project.getGroupId(), project.getArtifactId() );

            List<MavenProject> projects = projectsByGA.get( key );

            if ( projects == null )
            {
                projects = new ArrayList<MavenProject>( 1 );
                projectsByGA.put( key, projects );
            }

            projects.add( project );
        }

        repository = new WorkspaceRepository( "reactor", new HashSet<String>( projectsByGAV.keySet() ) );
    }


    public File findArtifact( Artifact artifact )
    {

        // very simple resolution here: is the artifact in reactor projects ?

        String projectKey = ArtifactUtils.key( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );

        MavenProject project = projectsByGAV.get( projectKey );

        if ( project != null )
        {
            return project.getBasedir();
        }

        return null;
    }

    public List<String> findVersions( Artifact artifact )
    {
        String key = ArtifactUtils.versionlessKey( artifact.getGroupId(), artifact.getArtifactId() );

        List<MavenProject> projects = projectsByGA.get( key );
        if ( projects == null || projects.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<String> versions = new ArrayList<String>();

        for ( MavenProject project : projects )
        {
            versions.add( project.getVersion() );
        }

        return Collections.unmodifiableList( versions );
    }

    public WorkspaceRepository getRepository()
    {
        return repository;
    }

}
