/**
 * 
 */
package org.jvnet.hudson.plugins.mavendepsupdate;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
@Extension
public class MavenDependencyUpdateTriggerComputerListener extends ComputerListener
{
    public static final String MAVEN_SHADED_JAR_NAME = "maven-dependency-update-trigger-shaded-maven";

    @Override
    public void preOnline( Computer c, Channel channel, FilePath root, TaskListener listener )
        throws IOException, InterruptedException
    {
        PrintStream logger = listener.getLogger();
        logger.println( "copy jars for MavenDependencyUpdateTrigger" );
        File mavenShadedJar = new File( Hudson.getInstance().getRootDir(),
                                        "plugins/maven-dependency-update-trigger/"+MAVEN_SHADED_JAR_NAME+".jar" );
        if (mavenShadedJar.exists())
        {
            logger.println("mavenShadedJar path " + mavenShadedJar.getPath());
            new FilePath(mavenShadedJar).copyTo(root.child(mavenShadedJar.getName()));
            logger.println("Copied "+mavenShadedJar.getName());
        }
        else
        {
            logger.println("mavenShadedJar not found ");
        }
    }

}
