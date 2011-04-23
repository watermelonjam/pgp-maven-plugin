package org.kohsuke.maven.gpg;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Goal which touches a timestamp file.
 *
 * @goal touch
 * 
 * @phase process-sources
 */
public class PgpMojo extends AbstractMojo
{
    /**
     * How to retrieve the secret key?
     *
     * @parameter expression="${pgp.secretkey}
     */
    public String secretkey;

    /**
     * String that indicates where to retrieve the secret key pass phrase from.
     *
     * @parameter expression="${pgp.passphrase}
     */
    public String passphrase;

    /**
     * Skip the PGP signing.
     *
     * @parameter expression="${pgp.skip}" default-value="false"
     */
    private boolean skip;

    /**
     *
     * 
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    public MavenProject project;

    /**
     *
     * @component
     * @required
     * @readonly
     */
    public MavenProjectHelper projectHelper;

    /**
     * @component
     * @required
     * @readonly
     */
    public PlexusContainer container;

    public void execute() throws MojoExecutionException {
        if (skip)   return;


        PGPSecretKey _secretKey = loadSecretKey();
//        String _passphrase = passphrase.load();

        if ( !"pom".equals( project.getPackaging() ) )
            sign(project.getArtifact());

        {// sign POM
            File pomToSign = new File( project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".pom" );

            try {
                FileUtils.copyFile(project.getFile(), pomToSign);
            } catch ( IOException e ) {
                throw new MojoExecutionException( "Error copying POM for signing.", e );
            }

            getLog().debug( "Generating signature for " + pomToSign );

            // fake just enough Artifact for the sign method
            DefaultArtifact a = new DefaultArtifact(null, null, null, null, null, null,
                    new DefaultArtifactHandler("pom"));
            a.setFile(pomToSign);

            sign(a);
        }

        for (Artifact a : (List<Artifact>)project.getAttachedArtifacts())
            sign(a);
    }

    /**
     * From {@link #secretkey}, load the key pair.
     */
    public PGPSecretKey loadSecretKey() throws MojoExecutionException {
        if (secretkey==null)
            secretkey = System.getenv("PGP_SECRETKEY");
        if (secretkey==null)
            throw new MojoExecutionException("No PGP secret key is configured. Either do so in POM, or via -Dpgp.secretkey, or the PGP_SECRETKEY environment variable");

        int head = secretkey.indexOf(':');
        if (head<0)
            throw new MojoExecutionException("Invalid secret key string. It needs to start with a scheme like 'FOO:': "+secretkey);

        String scheme = secretkey.substring(0, head);
        try {
            KeyFileLoader kfl = (KeyFileLoader)container.lookup(KeyFileLoader.class.getName(), scheme);
            return  kfl.load(this, secretkey.substring(head+1));
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException("Invalid secret key scheme '"+scheme+"'. If this is your custom scheme, perhaps you forgot to specify it in <dependency> to this plugin?",e);
         } catch (IOException e) {
            throw new MojoExecutionException("Failed to load key from "+secretkey,e);
        }
    }

    /**
     * Sign and attach the signaature to the build.
     */
    private void sign(Artifact a) {
        File signature = null;


        projectHelper.attachArtifact( project, a.getArtifactHandler().getExtension() + ".asc",
                                      a.getClassifier(), signature );

    }
}