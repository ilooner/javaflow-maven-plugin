/*
 * JavaflowEnhanceMojo.java
 * 
 * Copyright 2013 Stephen J. Scheck
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package meme.singularsyntax.mojo;

import com.google.common.collect.Lists;
import com.topekalabs.java.utils.ClassUtils;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.utils.RewritingUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Maven goal that enhances Java class files with Javaflow instrumentation.
 *
 * http://commons.apache.org/sandbox/javaflow/
 *
 * Usage: Create the files
 *
 * ${project}/src/main/javaflow/classes ${project}/src/test/javaflow/classes
 *
 * that list, one per line, the fully-qualified Java package path to the main
 * and test class files that should be bytecode-enhanced for Javaflow, for
 * example:
 *
 * meme/singularsyntax/ojos/MojoMan.class meme/singularsyntax/ojos/PojoPan.class
 * meme/singularsyntax/ojos/RojoRon.class
 * meme/singularsyntax/ojos/RojoRon$SomeInner.class
 * meme/singularsyntax/ojos/RojoRon$AnotherInner.class
 *
 * Note that inner classes compile to separate class files and must be included
 * individually. It is only necessary to include those inner classes which call
 * Javaflow API methods or are in the call graph between other classes which do.
 *
 * When the goal executes, the indicated class files are enhanced with Javaflow
 * bytecode. Backups of the original classes are made in the
 *
 * ${project.build.directory}/javaflow/orig-classes
 * ${project.build.directory}/javaflow/orig-test-classes
 *
 * directories: The goal can be executed with the following command:
 *
 * mvn javaflow:enhance
 *
 * To bind the goal to a project's build, add the following to pom.xml:
 *
 * <build>
 * <plugins>
 * <plugin>
 * <groupId>meme.singularsyntax.java</groupId>
 * <artifactId>javaflow-maven-plugin</artifactId>
 * <version>1.0-SNAPSHOT</version>
 * <executions>
 * <execution>
 * <phase>process-classes</phase>
 * <goals>
 * <goal>enhance</goal>
 * </goals>
 * </execution>
 * </executions>
 * </plugin>
 * </plugins>
 * </build>
 *
 * @goal instrument
 *
 * @phase process-classes
 *
 * @requiresDependencyResolution compile
 *
 */
public class JavaflowEnhanceMojo extends AbstractMojo
{

    private static final String CLASSFILE_REWRITE_TEMPLATE = "%s.JAVAFLOW_INSTRUMENTED";

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * mainBackupDir
     *
     * @parameter default-value="${project.build.directory}/javaflow/main"
     */
    private File mainBackupDirectory;
    
    /**
     * testBackupDir
     * 
     * @parameter default-value="${project.build.directory}/javaflow/test"
     */
    private File testBackupDirectory;
    
    /**
     * mainClassNames
     * 
     * @parameter
     */
    private List<String> mainClassNames;

    
    /**
     * testClassNames
     * 
     * @parameter
     */
    private List<String> testClassNames;
    
    @Override
    public void execute() throws MojoExecutionException
    {
        prepareClasspath();
        
        File mainOutputDirectory = new File(project.getBuild().getOutputDirectory());
        File testOutputDirectory = new File(project.getBuild().getTestOutputDirectory());

        executeHelper(mainOutputDirectory,
                      mainBackupDirectory,
                      mainClassNames);
        
        executeHelper(testOutputDirectory,
                      testBackupDirectory,
                      testClassNames);
    }
    
    private void executeHelper(File baseDirectory,
                               File backupDirectory,
                               List<String> classNames) throws
                               MojoExecutionException
    {
        if(classNames.isEmpty())
        {
            return;
        }
        
        List<File> classFiles = Lists.newArrayList();
        
        for(String className: classNames)
        {
            if(ClassUtils.isClassName(className))
            {
                Collection<File> tempClassFiles = ClassUtils.getClassAndInnerClassFiles(baseDirectory,
                                                                                        className);
                classFiles.addAll(tempClassFiles);
            }
            else if(ClassUtils.isFQClassName(className))
            {
                Collection<File> tempClassFiles = ClassUtils.getFQClassAndInnerClassFiles(baseDirectory,
                                                                                          className);
                classFiles.addAll(tempClassFiles);
            }
            else
            {
                throw new MojoExecutionException("This given class name " +
                                                 className +
                                                 " is not valid");
            }
        }
        
        instrumentClassFiles(baseDirectory,
                             backupDirectory,
                             classFiles);
    }

    private void instrumentClassFiles(File baseDirectory,
                                      File backupDirectory,
                                      List<File> classFiles) throws MojoExecutionException
    {
        Log log = getLog();
        ResourceTransformer transformer = new AsmClassTransformer();

        for(File classFile: classFiles)
        {
            try
            {
                File instrumentedClassFile = new File(String.format(CLASSFILE_REWRITE_TEMPLATE,
                                                                    classFile));
                File backupClassFile = com.topekalabs.file.utils.FileUtils.rebase(baseDirectory,
                                                                                  backupDirectory,
                                                                                  classFile);

                if(backupClassFile.exists() && (classFile.lastModified() <= backupClassFile.lastModified()))
                {
                    log.info(classFile + " is up to date");
                    continue;
                }

                log.info(String.format("Enhancing class file bytecode for Javaflow: %s",
                                       classFile));
                RewritingUtils.rewriteClassFile(classFile,
                                                transformer,
                                                instrumentedClassFile);

                if(backupClassFile.exists())
                {
                    log.debug(String.format("Backup for original class file %s already exists - removing it",
                                            backupClassFile));
                    backupClassFile.delete();
                }

                log.debug(String.format("Renaming original class file from %s to %s",
                                        classFile,
                                        backupClassFile));
                FileUtils.moveFile(classFile,
                                   backupClassFile);

                log.debug(String.format("Renaming rewritten class file from %s to %s",
                                        instrumentedClassFile,
                                        classFile));
                
                FileUtils.moveFile(instrumentedClassFile, classFile);
                backupClassFile.setLastModified(classFile.lastModified());

            }
            catch(IOException e)
            {
                throw new MojoExecutionException(e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void prepareClasspath() throws MojoExecutionException
    {
        List<String> runtimeClasspathElements = null;
        URLClassLoader classLoader = null;

        try
        {
            runtimeClasspathElements = project.getCompileClasspathElements();
            URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];

            for(int ii = 0; ii < runtimeClasspathElements.size(); ii++)
            {
                String element = runtimeClasspathElements.get(ii);
                File elementFile = new File(element);
                runtimeUrls[ii] = elementFile.toURI().toURL();
            }

            classLoader = new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(classLoader);

        }
        catch(DependencyResolutionRequiredException e)
        {
            throw new MojoExecutionException(e.getMessage());
        }
        catch(MalformedURLException e)
        {
            throw new MojoExecutionException(e.getMessage());
        }
    }
}
