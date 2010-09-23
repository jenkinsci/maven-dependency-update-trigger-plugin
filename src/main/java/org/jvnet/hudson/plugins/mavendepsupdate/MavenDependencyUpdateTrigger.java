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

import static hudson.Util.fixNull;
import hudson.Extension;
import hudson.PluginFirstClassLoader;
import hudson.PluginWrapper;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.scheduler.CronTabList;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
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
import org.apache.tools.ant.AntClassLoader;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.util.repository.ChainedWorkspaceReader;

import antlr.ANTLRException;

/**
 * @author Olivier Lamy
 */
public class MavenDependencyUpdateTrigger
    extends Trigger<BuildableItem>
{

    private static final Logger LOGGER = Logger.getLogger( MavenDependencyUpdateTrigger.class.getName() );

   
    public static final File userMavenConfigurationHome = new File( System.getProperty( "user.home" ), ".m2" );

    public static final File DEFAULT_USER_SETTINGS_FILE = new File( userMavenConfigurationHome, "settings.xml" );

    public static final File DEFAULT_GLOBAL_SETTINGS_FILE =
        new File( System.getProperty( "maven.home", System.getProperty( "user.dir", "" ) ), "conf/settings.xml" );
    
    private final boolean checkPlugins;
    
    @DataBoundConstructor
    public MavenDependencyUpdateTrigger( String cron_value, boolean checkPlugins )
        throws ANTLRException
    {
        super( cron_value );
        this.checkPlugins = checkPlugins;
    }

    @Override
    public void run()
    {
        ProjectBuildingRequest projectBuildingRequest = null;

        ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            PlexusContainer plexusContainer = null;
            PluginWrapper pluginWrapper = Hudson.getInstance().getPluginManager()
                .getPlugin( "maven-dependency-update-trigger" );

            // olamy here hacking for be able to use hpi:run
            // FIXME doesn't work currently some hacking to do in hpi plugin too.
            if ( pluginWrapper.classLoader instanceof PluginFirstClassLoader )
            {
                PluginFirstClassLoader pluginFirstClassLoader = (PluginFirstClassLoader) pluginWrapper.classLoader;

                plexusContainer = getPlexusContainer( pluginFirstClassLoader );
            }
            else
            {
                plexusContainer = getPlexusContainer( (URLClassLoader) pluginWrapper.classLoader );

            }
            Thread.currentThread().setContextClassLoader( plexusContainer.getContainerRealm() );
            LOGGER.info( " in run " + this.job.getRootDir().getAbsolutePath() );

            ProjectBuilder projectBuilder = plexusContainer.lookup( ProjectBuilder.class );

            // FIXME load userProperties from the job
            Properties userProperties = new Properties();

            projectBuildingRequest = getProjectBuildingRequest( userProperties, plexusContainer );

            // check plugins too
            projectBuildingRequest.setProcessPlugins( true );
            // force snapshots update
            projectBuildingRequest.setForceUpdate( true );
            projectBuildingRequest.setResolveDependencies( true );

            List<ProjectBuildingResult> projectBuildingResults = projectBuilder.build( Arrays
                .asList( new File( this.job.getRootDir(), "workspace/trunk/pom.xml" ) ), true, projectBuildingRequest );

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
            WorkspaceReader reactorRepository = new ReactorReader( projectMap, new File( this.job.getRootDir(),
                                                                                         "workspace/trunk" ) );

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

        }
        catch ( PlexusContainerException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( ComponentLookupException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( MavenExecutionRequestPopulationException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( SettingsBuildingException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( ProjectBuildingException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( DependencyResolutionException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( InvalidRepositoryException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( CycleDetectedException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( DuplicateProjectException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( Exception e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( origClassLoader );
        }
        if ( projectBuildingRequest != null )
        {
            SnapshotTransfertListener snapshotTransfertListener = (SnapshotTransfertListener) projectBuildingRequest
                .getRepositorySession().getTransferListener();

            if ( snapshotTransfertListener.snapshotDownloaded )
            {
                LOGGER.info( "snapshotDownloaded so triggering a new build" );
                job.scheduleBuild( 0, new MavenDependencyUpdateTriggerCause() );
            }
        }
    }
    
    /**
     * FIXME move this to a common library : maven3-utils or tools
     * will build a PlexusContainer from {@link PluginFirstClassLoader}
     * @param pluginFirstClassLoader
     * @return
     * @throws PlexusContainerException
     */
    private PlexusContainer getPlexusContainer(PluginFirstClassLoader pluginFirstClassLoader) throws PlexusContainerException {
        DefaultContainerConfiguration conf = new DefaultContainerConfiguration();
        ClassWorld world = new ClassWorld();
        ClassRealm classRealm = new ClassRealm( world, "project-building", pluginFirstClassLoader );
        // olamy yup hackish but it's needed for plexus-shim which a URLClassLoader and PluginFirstClassLoader is not
        for ( URL url : pluginFirstClassLoader.getURLs() )
        {
            classRealm.addURL( url );
            LOGGER.fine(  "add url " + url.toExternalForm() );
        }
        conf.setRealm( classRealm );

        return new DefaultPlexusContainer( conf );
    }
    
    /**
     * simple hack to make hpi:run working 
     * @param urlClassLoader
     * @return
     * @throws PlexusContainerException
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    private PlexusContainer getPlexusContainer(URLClassLoader urlClassLoader) throws PlexusContainerException, MalformedURLException, URISyntaxException {
        // here building a parent first
        AntClassLoader antClassLoader = new AntClassLoader( Thread.currentThread().getContextClassLoader(), false );
        for ( URL url : urlClassLoader.getURLs() )
        {
            File f = new File( url.toURI().toURL().getFile() );
            LOGGER.info( f.getPath() );
            antClassLoader.addPathComponent( f );
        }
        Thread.currentThread().setContextClassLoader( antClassLoader );
        DefaultContainerConfiguration conf = new DefaultContainerConfiguration();
        ClassWorld world = new ClassWorld();
        ClassRealm classRealm = new ClassRealm( world, "project-building", antClassLoader );
        conf.setRealm( classRealm );

        return new DefaultPlexusContainer( conf );
    }    

    
    ProjectBuildingRequest getProjectBuildingRequest( Properties userProperties, PlexusContainer plexusContainer )
        throws ComponentLookupException, SettingsBuildingException, MavenExecutionRequestPopulationException,
        InvalidRepositoryException
    {

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();

        request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_DEBUG );

        SettingsBuilder settingsBuilder = plexusContainer.lookup( SettingsBuilder.class );

        RepositorySystem repositorySystem = plexusContainer.lookup( RepositorySystem.class );
        
        org.sonatype.aether.RepositorySystem repoSystem = plexusContainer.lookup( org.sonatype.aether.RepositorySystem.class );

        SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
        // FIXME find from job configuration
        settingsRequest.setGlobalSettingsFile( DEFAULT_GLOBAL_SETTINGS_FILE );
        settingsRequest.setUserSettingsFile( DEFAULT_USER_SETTINGS_FILE );
        settingsRequest.setSystemProperties( System.getProperties() );
        settingsRequest.setUserProperties( userProperties );

        SettingsBuildingResult settingsBuildingResult = settingsBuilder.build( settingsRequest );

        MavenExecutionRequestPopulator executionRequestPopulator = plexusContainer
            .lookup( MavenExecutionRequestPopulator.class );

        executionRequestPopulator.populateFromSettings( request, settingsBuildingResult.getEffectiveSettings() );

        String localRepoPath = getLocalRepo().getAbsolutePath();
        
        if ( StringUtils.isBlank( localRepoPath ) )
        {
            localRepoPath = settingsBuildingResult.getEffectiveSettings().getLocalRepository();
        }
        if ( StringUtils.isBlank( localRepoPath ) )
        {
            localRepoPath = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
        }

        MavenRepositorySystemSession session = new MavenRepositorySystemSession();

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
    
    

    private File getLocalRepo()
    {
        boolean usePrivateRepo = usePrivateRepo();
        if ( usePrivateRepo )
        {
            return new File( this.job.getRootDir(), "workspace/.repository" );
        }
        return RepositorySystem.defaultUserLocalRepository;
    }
    
    @Extension
    public static class DescriptorImpl
        extends TriggerDescriptor
    {
        public boolean isApplicable( Item item )
        {
            return item instanceof BuildableItem;
        }

        public String getDisplayName()
        {
            return "m2 deps update";
        }

        @Override
        public String getHelpFile()
        {
            return "/help/project-config/timer.html";
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheck( @QueryParameter String value )
        {
            try
            {
                String msg = CronTabList.create( fixNull( value ) ).checkSanity();
                if ( msg != null )
                    return FormValidation.warning( msg );
                return FormValidation.ok();
            }
            catch ( ANTLRException e )
            {
                return FormValidation.error( e.getMessage() );
            }
        }
    }

    public static class MavenDependencyUpdateTriggerCause
        extends Cause
    {
        @Override
        public String getShortDescription()
        {
            return "maven SNAPSHOT dependency update cause";
        }

        @Override
        public boolean equals( Object o )
        {
            return o instanceof MavenDependencyUpdateTriggerCause;
        }

        @Override
        public int hashCode()
        {
            return 5 * 2;
        }
    }
    
    private boolean usePrivateRepo()
    {
        // check if FreeStyleProject
        if (this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for(Builder b : fp.getBuilders())
            {
                if (b instanceof Maven)
                {
                    if (( (Maven) b ).usePrivateRepository)
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        // check if there is a method called usesPrivateRepository
        try
        {
            Method method = this.job.getClass().getMethod( "usesPrivateRepository", null );
            Boolean bool = (Boolean) method.invoke( this.job, null );
            return bool.booleanValue();
        }
        catch ( SecurityException e )
        {
            LOGGER.warning("ignore " + e.getMessage() );
        }
        catch ( NoSuchMethodException e )
        {
            LOGGER.warning("ignore " + e.getMessage() );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.warning("ignore " + e.getMessage() );
        }
        catch ( IllegalAccessException e )
        {
            LOGGER.warning("ignore " + e.getMessage() );
        }
        catch ( InvocationTargetException e )
        {
            LOGGER.warning("ignore " + e.getMessage() );
        }
        
        return false;
    }
    
    private static class SnapshotTransfertListener implements TransferListener
    {
        
        boolean snapshotDownloaded = false;

       
        public void transferCorrupted( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferFailed( TransferEvent arg0 )
        {
            // no op       
        }

        public void transferInitiated( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferProgressed( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferStarted( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferSucceeded( TransferEvent transferEvent )
        {
            if ( transferEvent != null && transferEvent.getResource() != null )
            {
                File file = transferEvent.getResource().getFile();
                if ( file != null && transferEvent.getResource().getResourceName().contains( "SNAPSHOT" ) )
                {
                    // filtering on maven metadata
                    if ( file.getName().endsWith( ".jar" ) || file.getName().endsWith( ".war" ) )
                    {
                        LOGGER.info( "download " + file.getName() );
                        snapshotDownloaded = true;
                    }
                }
            }
        }
        
    }

    // source copied from ASF repo org.apache.maven.ReactorReader
    // FIXME simplify more !!
    static class ReactorReader
        implements WorkspaceReader
    {

        private Map<String, MavenProject> projectsByGAV;

        private Map<String, List<MavenProject>> projectsByGA;

        private WorkspaceRepository repository;
        
        private File workspaceRoot;

        public ReactorReader( Map<String, MavenProject> reactorProjects, File workspaceRoot )
        {
            projectsByGAV = reactorProjects;
            this.workspaceRoot = workspaceRoot;
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

        private File find( MavenProject project, Artifact artifact )
        {
            if ( "pom".equals( artifact.getExtension() ) )
            {
                return project.getFile();
            }

            return findMatchingArtifact( project, artifact ).getFile();
        }

        /**
         * Tries to resolve the specified artifact from the artifacts of the given project.
         * 
         * @param project The project to try to resolve the artifact from, must not be <code>null</code>.
         * @param requestedArtifact The artifact to resolve, must not be <code>null</code>.
         * @return The matching artifact from the project or <code>null</code> if not found.
         */
        private org.apache.maven.artifact.Artifact findMatchingArtifact( MavenProject project,
                                                                         Artifact requestedArtifact )
        {
            String requestedRepositoryConflictId = getConflictId( requestedArtifact );

            org.apache.maven.artifact.Artifact mainArtifact = project.getArtifact();
            if ( requestedRepositoryConflictId.equals( getConflictId( mainArtifact ) ) )
            {
                mainArtifact.setFile( new File( workspaceRoot, project.getArtifactId() ) );
                return mainArtifact;
            }

            Collection<org.apache.maven.artifact.Artifact> attachedArtifacts = project.getAttachedArtifacts();
            if ( attachedArtifacts != null && !attachedArtifacts.isEmpty() )
            {
                for ( org.apache.maven.artifact.Artifact attachedArtifact : attachedArtifacts )
                {
                    if ( requestedRepositoryConflictId.equals( getConflictId( attachedArtifact ) ) )
                    {
                        attachedArtifact.setFile( new File( workspaceRoot, project.getArtifactId() ) );
                        return attachedArtifact;
                    }
                }
            }

            return null;
        }

        /**
         * Gets the repository conflict id of the specified artifact. Unlike the dependency conflict id, the repository
         * conflict id uses the artifact file extension instead of the artifact type. Hence, the repository conflict id more
         * closely reflects the identity of artifacts as perceived by a repository.
         * 
         * @param artifact The artifact, must not be <code>null</code>.
         * @return The repository conflict id, never <code>null</code>.
         */
        private String getConflictId( org.apache.maven.artifact.Artifact artifact )
        {
            StringBuilder buffer = new StringBuilder( 128 );
            buffer.append( artifact.getGroupId() );
            buffer.append( ':' ).append( artifact.getArtifactId() );
            if ( artifact.getArtifactHandler() != null )
            {
                buffer.append( ':' ).append( artifact.getArtifactHandler().getExtension() );
            }
            else
            {
                buffer.append( ':' ).append( artifact.getType() );
            }
            if ( artifact.hasClassifier() )
            {
                buffer.append( ':' ).append( artifact.getClassifier() );
            }
            return buffer.toString();
        }

        private String getConflictId( Artifact artifact )
        {
            StringBuilder buffer = new StringBuilder( 128 );
            buffer.append( artifact.getGroupId() );
            buffer.append( ':' ).append( artifact.getArtifactId() );
            buffer.append( ':' ).append( artifact.getExtension() );
            if ( artifact.getClassifier().length() > 0 )
            {
                buffer.append( ':' ).append( artifact.getClassifier() );
            }
            return buffer.toString();
        }

        public File findArtifact( Artifact artifact )
        {
            String projectKey = artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();

            MavenProject project = projectsByGAV.get( projectKey );

            if ( project != null )
            {
                return find( project, artifact );
            }

            return null;
        }

        public List<String> findVersions( Artifact artifact )
        {
            String key = artifact.getGroupId() + ':' + artifact.getArtifactId();

            List<MavenProject> projects = projectsByGA.get( key );
            if ( projects == null || projects.isEmpty() )
            {
                return Collections.emptyList();
            }

            List<String> versions = new ArrayList<String>();

            for ( MavenProject project : projects )
            {
                if ( find( project, artifact ) != null )
                {
                    versions.add( project.getVersion() );
                }
            }

            return Collections.unmodifiableList( versions );
        }

        public WorkspaceRepository getRepository()
        {
            return repository;
        }
    }
}
