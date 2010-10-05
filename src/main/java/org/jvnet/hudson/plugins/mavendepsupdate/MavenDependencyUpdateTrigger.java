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
import hudson.FilePath;
import hudson.PluginFirstClassLoader;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.scheduler.CronTabList;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.cli.CLIManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import antlr.ANTLRException;

/**
 * @author Olivier Lamy
 */
public class MavenDependencyUpdateTrigger
    extends Trigger<BuildableItem>
{

    private static final Logger LOGGER = Logger.getLogger( MavenDependencyUpdateTrigger.class.getName() );

    private final boolean checkPlugins;
    
    public static boolean debug = Boolean.getBoolean( "MavenDependencyUpdateTrigger.debug" );
    
    private static final CLIManager mavenCliManager = new CLIManager();  

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

        Node node = super.job.getLastBuiltOn();

        if ( node == null )
        {
            // FIXME schedule the first buid ??
            //job.scheduleBuild( arg0, arg1 )
            LOGGER.info( "no previous build found so skip maven update trigger" );
            return;
        }

        ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            PluginWrapper pluginWrapper = Hudson.getInstance().getPluginManager()
                .getPlugin( "maven-dependency-update-trigger" );

            FilePath mavenShadedJar = node.getRootPath()
                .child( MavenDependencyUpdateTriggerComputerListener.MAVEN_SHADED_JAR_NAME + ".jar" );

            boolean isMaster = node == Hudson.getInstance();

            if ( isMaster )
            {
                mavenShadedJar = node.getRootPath().child( "plugins" ).child( "maven-dependency-update-trigger" )
                    .child( MavenDependencyUpdateTriggerComputerListener.MAVEN_SHADED_JAR_NAME + ".jar" );
            }

            AbstractProject<?, ?> abstractProject = (AbstractProject<?, ?>) super.job;

            FilePath workspace = node.getWorkspaceFor( (TopLevelItem) super.job );

            FilePath moduleRoot = abstractProject.getScm().getModuleRoot( workspace );


            
            String rootPomPath = moduleRoot.getRemote() + "/" + getRootPomPath();

            String localRepoPath = getLocalRepo( workspace ).toString();

            String projectWorkspace = moduleRoot.getRemote();

            MavenUpdateChecker checker = new MavenUpdateChecker( mavenShadedJar, rootPomPath, localRepoPath,
                                                                 this.checkPlugins, projectWorkspace, isMaster );
            if ( isMaster )
            {
                checker.setClassLoaderParent( (PluginFirstClassLoader) pluginWrapper.classLoader );
            }
            
            VirtualChannel virtualChannel = node.getChannel();
            FilePath alternateSettings = getAlternateSettings(virtualChannel);
            checker.setAlternateSettings( alternateSettings );
            
            FilePath globalSettings = getGlobalSettings(virtualChannel);
            checker.setGlobalSettings( globalSettings );

            checker.setUserProperties( getUserProperties() );
            
            checker.setActiveProfiles( getActiveProfiles() );
            
            LOGGER.info( "run MavenUpdateChecker for project " + job.getName() + " on node " + node.getDisplayName() );

            MavenUpdateCheckerResult mavenUpdateCheckerResult = virtualChannel.call( checker );

            if ( debug )
            {
                StringBuilder debugLines = new StringBuilder( "MavenUpdateChecker for project " + job.getName()
                    + " on node " + node.getDisplayName() ).append( SystemUtils.LINE_SEPARATOR );
                for ( String line : mavenUpdateCheckerResult.getDebugLines() )
                {
                    debugLines.append( line ).append( SystemUtils.LINE_SEPARATOR );
                }
                LOGGER.info( debugLines.toString() );
            }

            if ( mavenUpdateCheckerResult.getFileUpdatedNames().size() > 0 )
            {
                StringBuilder stringBuilder = new StringBuilder( "MavenUpdateChecker for project " + job.getName()
                    + " on node " + node.getDisplayName() );
                stringBuilder.append( " , snapshotDownloaded so triggering a new build : " )
                    .append( SystemUtils.LINE_SEPARATOR );
                for ( String fileName : mavenUpdateCheckerResult.getFileUpdatedNames() )
                {
                    stringBuilder.append( " * " + fileName ).append( SystemUtils.LINE_SEPARATOR );
                }
                job.scheduleBuild( 0,
                                   new MavenDependencyUpdateTriggerCause( mavenUpdateCheckerResult
                                       .getFileUpdatedNames() ) );
                LOGGER.info( stringBuilder.toString() );
            }

        }
        catch ( Exception e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( origClassLoader );
        }
    }

    private File getLocalRepo( FilePath workspace )
    {
        boolean usePrivateRepo = usePrivateRepo();
        if ( usePrivateRepo )
        {
            return new File( workspace.getRemote(), ".repository" );
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
            return Messages.plugin_title();
        }

        @Override
        public String getHelpFile()
        {
            return "/plugin/maven-dependency-update-trigger/help.html";
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
        private List<String> snapshotsDownloaded;

        MavenDependencyUpdateTriggerCause( List<String> snapshotsDownloaded )
        {
            this.snapshotsDownloaded = snapshotsDownloaded;
        }

        @Override
        public String getShortDescription()
        {
            StringBuilder sb = new StringBuilder( "maven SNAPSHOT dependency update cause : " );
            if ( snapshotsDownloaded != null && snapshotsDownloaded.size() > 0 )
            {
                sb.append( " " );
                for ( String snapshot : snapshotsDownloaded )
                {
                    sb.append( snapshot );
                }
                sb.append( " " );
            }

            return sb.toString();
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
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    if ( ( (Maven) b ).usePrivateRepository )
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
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( NoSuchMethodException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalAccessException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( InvocationTargetException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }

        return false;
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
    
    private FilePath getAlternateSettings(VirtualChannel virtualChannel)
    {
        //-s,--settings or from configuration for maven native project
        // check if FreeStyleProject
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    String targets = ( (Maven) b ).getTargets();
                    String[] args = Util.tokenize( targets );
                    if ( args == null )
                    {
                        return null;
                    }
                    CommandLine cli = getCommandLine( args );
                    if ( cli != null && cli.hasOption( CLIManager.ALTERNATE_USER_SETTINGS ) )
                    {
                        return new FilePath( virtualChannel, cli.getOptionValue( CLIManager.ALTERNATE_POM_FILE ) );
                    }
                }
            }
            return null;
        }
        
        // check if there is a method called getAlternateSettings
        try
        {
            Method method = this.job.getClass().getMethod( "getAlternateSettings", null );
            String alternateSettings = (String) method.invoke( this.job, null );
            return alternateSettings != null ? new FilePath( virtualChannel, alternateSettings ) : null;
        }
        catch ( SecurityException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( NoSuchMethodException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalAccessException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( InvocationTargetException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        } 
        
        
        
        return null;
    }
    
    private FilePath getGlobalSettings(VirtualChannel virtualChannel)
    {
        //-gs,--global-settings
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    String targets = ( (Maven) b ).getTargets();
                    String[] args = Util.tokenize( targets );
                    if ( args == null )
                    {
                        return null;
                    }
                    CommandLine cli = getCommandLine( args );
                    if ( cli != null && cli.hasOption( CLIManager.ALTERNATE_GLOBAL_SETTINGS ) )
                    {
                        return new FilePath( virtualChannel, cli.getOptionValue( CLIManager.ALTERNATE_GLOBAL_SETTINGS ) );
                    }
                }
            }
            return null;
        }
        return null;
    }
    
    private Properties getUserProperties() throws IOException
    {
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    String properties = ( (Maven) b ).properties;
                    return load( properties );
                }
            }
        }
        return new Properties();
    }
    
    private Properties load(String properties) throws IOException {
        Properties p = new Properties();
        p.load(new ByteArrayInputStream(properties.getBytes()));
        return p;
    }
    
    private String getRootPomPath()
    {
        
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    String targets = ( (Maven) b ).getTargets();
                    String[] args = Util.tokenize( targets );
                    
                    if ( args == null  )
                    {
                        return null;
                    }
                    CommandLine cli = getCommandLine( args );
                    if (cli != null && cli.hasOption( CLIManager.ALTERNATE_POM_FILE ))
                    {
                        return cli.getOptionValue( CLIManager.ALTERNATE_POM_FILE );
                    }
                }
            }
            return null;
        }        
        
        // check if there is a method called getRootPOM
        try
        {
            Method method = this.job.getClass().getMethod( "getRootPOM", null );
            String rootPom = (String) method.invoke( this.job, null );
            return rootPom;
        }
        catch ( SecurityException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( NoSuchMethodException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalAccessException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( InvocationTargetException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }        
        
        return "pom.xml";
    }
    
    private List<String>  getActiveProfiles()
    {
        if ( this.job instanceof FreeStyleProject )
        {
            FreeStyleProject fp = (FreeStyleProject) this.job;
            for ( Builder b : fp.getBuilders() )
            {
                if ( b instanceof Maven )
                {
                    String targets = ( (Maven) b ).getTargets();
                    String[] args = Util.tokenize( targets );
                    
                    if ( args == null  )
                    {
                        return null;
                    }
                    CommandLine cli = getCommandLine( args );
                    if (cli != null && cli.hasOption( CLIManager.ACTIVATE_PROFILES ))
                    {
                        return Arrays.asList( cli.getOptionValues( CLIManager.ACTIVATE_PROFILES ) );
                    }
                }
            }
            return Collections.emptyList();
        }
        // check if there is a method called getGoals
        try
        {
            Method method = this.job.getClass().getMethod( "getGoals", null );
            String goals = (String) method.invoke( this.job, null );
            String[] args = Util.tokenize( goals );
            if ( args == null  )
            {
                return null;
            }
            CommandLine cli = getCommandLine( args );
            if (cli != null && cli.hasOption( CLIManager.ACTIVATE_PROFILES ))
            {
                return Arrays.asList( cli.getOptionValues( CLIManager.ACTIVATE_PROFILES ) );
            }            
        }
        catch ( SecurityException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( NoSuchMethodException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( IllegalAccessException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        catch ( InvocationTargetException e )
        {
            LOGGER.warning( "ignore " + e.getMessage() );
        }
        return Collections.emptyList();
    }
    
    private CommandLine getCommandLine(String[] args)
    {
        try
        {
            return mavenCliManager.parse( args );
        }
        catch ( ParseException e )
        {
            LOGGER.info( "ignore error parsing maven args " + e.getMessage());
            return null;
        }
    }

}
