/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArchiveConfiguration {
    // see http://maven.apache.org/shared/maven-archiver/index.html

    private File manifestFile;
    private Map<String, String> manifestEntries = new LinkedHashMap<>();

    public File getManifestFile() {
        return manifestFile;
    }

    public void addManifestEntry(String key, String value) {
        manifestEntries.put(key, value);
    }

    public void addManifestEntries(Map<String, String> map) {
        manifestEntries.putAll(map);
    }

    public Map<String, String> getManifestEntries() {
        return manifestEntries;
    }

    public void setManifestEntries(Map<String, String> manifestEntries) {
        this.manifestEntries = manifestEntries;
    }
}
