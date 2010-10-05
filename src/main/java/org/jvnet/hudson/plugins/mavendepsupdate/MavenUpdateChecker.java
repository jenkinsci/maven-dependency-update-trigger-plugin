/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc. Olivier Lamy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.mavendepsupdate;


import hudson.FilePath;
import hudson.PluginFirstClassLoader;
import hudson.remoting.Callable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.repository.DelegatingLocalArtifactRepository;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jvnet.hudson.plugins.mavendepsupdate.util.Maven3Utils;
import org.jvnet.hudson.plugins.mavendepsupdate.util.ReactorReader;
import org.jvnet.hudson.plugins.mavendepsupdate.util.SnapshotTransfertListener;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.util.repository.ChainedWorkspaceReader;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
public class MavenUpdateChecker
    implements Callable<MavenUpdateCheckerResult, IOException>
{

    private static final Logger LOGGER = Logger.getLogger( MavenUpdateChecker.class.getName() );

    private final FilePath mavenShadedJarPath;

    private final String rootPomPath;

    private final String localRepoPath;

    private final boolean checkPlugins;

    private final String projectWorkspace;

    private final boolean masterRun;
    
    
    //---------------------------------------
    // optionnal parameters
    //---------------------------------------
    
    // use for master run
    private PluginFirstClassLoader classLoaderParent;

    private FilePath alternateSettings;
    
    private FilePath globalSettings;
    
    private Properties userProperties;
    
    
    private MavenUpdateCheckerResult mavenUpdateCheckerResult = new MavenUpdateCheckerResult();

    public MavenUpdateChecker( FilePath mavenShadedJarPath, String rootPomPath, String localRepoPath,
                               boolean checkPlugins, String projectWorkspace, boolean masterRun )
    {
        this.mavenShadedJarPath = mavenShadedJarPath;
        this.rootPomPath = rootPomPath;
        this.localRepoPath = localRepoPath;
        this.checkPlugins = checkPlugins;
        this.projectWorkspace = projectWorkspace;
        this.masterRun = masterRun;
    }

    public MavenUpdateCheckerResult call()
        throws IOException
    {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try
        {

            PluginFirstClassLoader pluginFirstClassLoader = getPluginFirstClassLoader();
            Thread.currentThread().setContextClassLoader( pluginFirstClassLoader );
            String classLoaderName = getClass().getClassLoader().toString();
            System.out.println( "class loader " + classLoaderName );
            mavenUpdateCheckerResult.addDebugLine( classLoaderName );
            PlexusContainer plexusContainer = getPlexusContainer( pluginFirstClassLoader );

            Thread.currentThread().setContextClassLoader( plexusContainer.getContainerRealm() );
            mavenUpdateCheckerResult.addDebugLine( "ok for new DefaultPlexusContainer( conf ) " );
            mavenUpdateCheckerResult.addDebugLine( "Thread.currentThread().getContextClassLoader() "
                + Thread.currentThread().getContextClassLoader() );
            mavenUpdateCheckerResult.addDebugLine( "Thread.currentThread().getContextClassLoader().parent "
                + ( Thread.currentThread().getContextClassLoader().getParent() == null ? "null" : Thread
                    .currentThread().getContextClassLoader().getParent().toString() ) );
            mavenUpdateCheckerResult.addDebugLine( "classLoader  urls "
                + Arrays.asList( plexusContainer.getContainerRealm().getURLs() ) );
            ProjectBuilder projectBuilder = plexusContainer.lookup( ProjectBuilder.class );

            // FIXME load userProperties from the job
            Properties userProperties = this.userProperties == null ? new Properties() : this.userProperties;

            ProjectBuildingRequest projectBuildingRequest = getProjectBuildingRequest( userProperties, plexusContainer );

            // check plugins too
            projectBuildingRequest.setProcessPlugins( true );
            // force snapshots update

            projectBuildingRequest.setResolveDependencies( true );

            List<ProjectBuildingResult> projectBuildingResults = projectBuilder.build( Arrays
                .asList( new File( rootPomPath ) ), true, projectBuildingRequest );

            ProjectDependenciesResolver projectDependenciesResolver = plexusContainer
                .lookup( ProjectDependenciesResolver.class );

            List<MavenProject> mavenProjects = new ArrayList<MavenProject>( projectBuildingResults.size() );

            for ( ProjectBuildingResult projectBuildingResult : projectBuildingResults )
            {
                mavenProjects.add( projectBuildingResult.getProject() );
            }

            ProjectSorter projectSorter = new ProjectSorter( mavenProjects );

            // use the projects reactor model as a workspaceReader
            // if reactors are not available remotely dependencies resolve will failed
            // due to artifact not found

            final Map<String, MavenProject> projectMap = getProjectMap( mavenProjects );
            WorkspaceReader reactorRepository = new ReactorReader( projectMap, new File( projectWorkspace ) );

            MavenRepositorySystemSession mavenRepositorySystemSession = (MavenRepositorySystemSession) projectBuildingRequest
                .getRepositorySession();

            mavenRepositorySystemSession.setUpdatePolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );
            mavenRepositorySystemSession.setWorkspaceReader( ChainedWorkspaceReader
                .newInstance( reactorRepository, projectBuildingRequest.getRepositorySession().getWorkspaceReader() ) );

            MavenPluginManager mavenPluginManager = plexusContainer.lookup( MavenPluginManager.class );

            for ( MavenProject mavenProject : projectSorter.getSortedProjects() )
            {
                LOGGER.info( "resolve dependencies for project " + mavenProject.getId() );
                DefaultDependencyResolutionRequest dependencyResolutionRequest = new DefaultDependencyResolutionRequest(
                                                                                                                         mavenProject,
                                                                                                                         mavenRepositorySystemSession );

                DependencyResolutionResult dependencyResolutionResult = projectDependenciesResolver
                    .resolve( dependencyResolutionRequest );

                if ( checkPlugins )
                {
                    for ( Plugin plugin : mavenProject.getBuildPlugins() )
                    {
                        // only for SNAPSHOT
                        if ( StringUtils.endsWith( plugin.getVersion(), "SNAPSHOT" ) )
                        {
                            mavenPluginManager.getPluginDescriptor( plugin, mavenProject.getRemotePluginRepositories(),
                                                                    mavenRepositorySystemSession );
                        }
                    }
                }

            }
            SnapshotTransfertListener snapshotTransfertListener = (SnapshotTransfertListener) projectBuildingRequest
                .getRepositorySession().getTransferListener();

            if ( snapshotTransfertListener.isSnapshotDownloaded() )
            {
                mavenUpdateCheckerResult.addFilesUpdatedNames( snapshotTransfertListener.getSnapshots() );
            }

        }
        catch ( Exception e )
        {
            //throw new IOException2( e.getMessage(), e );

            mavenUpdateCheckerResult.addDebugLine( e.getMessage() );
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter( sw );
            e.printStackTrace( pw );
            mavenUpdateCheckerResult.addDebugLine( sw.toString() );
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
        return mavenUpdateCheckerResult;
    }

    private PlexusContainer getPlexusContainer( PluginFirstClassLoader pluginFirstClassLoader )
        throws MalformedURLException, IOException, InterruptedException, PlexusContainerException
    {
        if ( this.masterRun )
        {
            return Maven3Utils.getPlexusContainer( this.classLoaderParent );
        }
        DefaultContainerConfiguration conf = new DefaultContainerConfiguration();
        ClassWorld world = new ClassWorld();
        ClassRealm classRealm = new ClassRealm( world, "project-building", pluginFirstClassLoader );
        // olamy yup hackish but it's needed for plexus-shim
        for ( URL url : pluginFirstClassLoader.getURLs() )
        {
            classRealm.addURL( url );
            mavenUpdateCheckerResult.addDebugLine( "add url " + url.toExternalForm() );
        }
        conf.setRealm( classRealm );
        mavenUpdateCheckerResult.addDebugLine( "before  return new DefaultPlexusContainer( conf ) " );
        return new DefaultPlexusContainer( conf );
    }

    PluginFirstClassLoader getPluginFirstClassLoader()
        throws MalformedURLException, IOException, InterruptedException
    {
        PluginFirstClassLoader pluginFirstClassLoader = new PluginFirstClassLoader();
        pluginFirstClassLoader.setParent( this.masterRun ? this.classLoaderParent : Thread.currentThread()
            .getContextClassLoader() );
        // parent first as hudson classes must be loaded first in a remote env
        pluginFirstClassLoader.setParentFirst( !this.masterRun );
        String mavenShadedJarPathStr = mavenShadedJarPath.getRemote();//.toURI().toURL().getFile();
        mavenUpdateCheckerResult.addDebugLine( "add mavenShadedJarPathStr " + mavenShadedJarPathStr );
        List<File> jarFiles = new ArrayList<File>( 1 );
        jarFiles.add( new File( mavenShadedJarPathStr ) );
        pluginFirstClassLoader.addPathFiles( jarFiles );

        mavenUpdateCheckerResult.addDebugLine( "pluginFirstClassLoader end" );
        return pluginFirstClassLoader;
    }

    ProjectBuildingRequest getProjectBuildingRequest( Properties userProperties, PlexusContainer plexusContainer )
        throws ComponentLookupException, SettingsBuildingException, MavenExecutionRequestPopulationException,
        InvalidRepositoryException
    {

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();

        request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_DEBUG );

        SettingsBuilder settingsBuilder = plexusContainer.lookup( SettingsBuilder.class );

        RepositorySystem repositorySystem = plexusContainer.lookup( RepositorySystem.class );

        org.sonatype.aether.RepositorySystem repoSystem = plexusContainer
            .lookup( org.sonatype.aether.RepositorySystem.class );

        SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();

        if (globalSettings != null)
        {
            mavenUpdateCheckerResult.addDebugLine( "globalSettings " + globalSettings.getRemote() );
            settingsRequest.setGlobalSettingsFile( new File( globalSettings.getRemote() ) );
        }
        else
        {
            settingsRequest.setGlobalSettingsFile( new File( System.getProperty( "maven.home",
                                                                             System.getProperty( "user.dir", "" ) ),
                                                         "conf/settings.xml" ) );
        }
        if (alternateSettings != null)
        {
            mavenUpdateCheckerResult.addDebugLine( "alternateSettings " + alternateSettings.getRemote() );
            settingsRequest.setUserSettingsFile( new File(alternateSettings.getRemote()) );
        }
        else
        {
            settingsRequest.setUserSettingsFile( new File( new File( System.getProperty( "user.home" ), ".m2" ),
            "settings.xml" ) );
        }
        settingsRequest.setSystemProperties( System.getProperties() );
        settingsRequest.setUserProperties( userProperties );

        SettingsBuildingResult settingsBuildingResult = settingsBuilder.build( settingsRequest );

        MavenExecutionRequestPopulator executionRequestPopulator = plexusContainer
            .lookup( MavenExecutionRequestPopulator.class );

        executionRequestPopulator.populateFromSettings( request, settingsBuildingResult.getEffectiveSettings() );

        MavenRepositorySystemSession session = new MavenRepositorySystemSession();

        session.setUpdatePolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );

        SnapshotTransfertListener snapshotTransfertListener = new SnapshotTransfertListener();
        session.setTransferListener( snapshotTransfertListener );

        LocalRepository localRepo = new LocalRepository( localRepoPath );
        session.setLocalRepositoryManager( repoSystem.newLocalRepositoryManager( localRepo ) );

        ArtifactRepository localArtifactRepository = repositorySystem.createLocalRepository( new File( localRepoPath ) );

        request.setLocalRepository( new DelegatingLocalArtifactRepository( localArtifactRepository ) );

        request.getProjectBuildingRequest().setRepositorySession( session );

        return request.getProjectBuildingRequest();
    }

    private Map<String, MavenProject> getProjectMap( List<MavenProject> projects )
    {
        Map<String, MavenProject> index = new LinkedHashMap<String, MavenProject>();

        for ( MavenProject project : projects )
        {
            String projectId = ArtifactUtils.key( project.getGroupId(), project.getArtifactId(), project.getVersion() );
            index.put( projectId, project );
        }

        return index;
    }

    public void setClassLoaderParent( PluginFirstClassLoader classLoaderParent )
    {
        this.classLoaderParent = classLoaderParent;
    }

    public void setAlternateSettings( FilePath alternateSettings )
    {
        this.alternateSettings = alternateSettings;
    }

    public void setGlobalSettings( FilePath globalSettings )
    {
        this.globalSettings = globalSettings;
    }

    public void setUserProperties( Properties userProperties )
    {
        this.userProperties = userProperties;
    }
}
