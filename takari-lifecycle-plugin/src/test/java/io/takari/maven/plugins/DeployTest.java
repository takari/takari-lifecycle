/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import static org.junit.Assert.assertEquals;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

public class DeployTest {

    @Test
    public void testModernSyntax() throws Exception {
        RemoteRepository repo =
                Deploy.altRepositoryBuilder("myrepo::file:///tmp/repo").build();
        assertEquals("myrepo", repo.getId());
        assertEquals("default", repo.getContentType());
        assertEquals("file:///tmp/repo", repo.getUrl());
    }

    @Test
    public void testLegacySyntax() throws Exception {
        RemoteRepository repo =
                Deploy.altRepositoryBuilder("myrepo::legacy::file:///tmp/repo").build();
        assertEquals("myrepo", repo.getId());
        assertEquals("legacy", repo.getContentType());
        assertEquals("file:///tmp/repo", repo.getUrl());
    }

    @Test(expected = MojoExecutionException.class)
    public void testInvalidSyntax() throws Exception {
        Deploy.altRepositoryBuilder("file:///tmp/repo");
    }
}
