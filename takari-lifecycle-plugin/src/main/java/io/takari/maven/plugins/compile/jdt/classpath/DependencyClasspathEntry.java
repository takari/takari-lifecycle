/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import io.takari.maven.plugins.exportpackage.ExportPackageMojo;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.AccessRule;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.osgi.framework.util.CaseInsensitiveDictionaryMap;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

public abstract class DependencyClasspathEntry implements ClasspathEntry {

    protected static final String PATH_EXPORT_PACKAGE = ExportPackageMojo.PATH_EXPORT_PACKAGE;

    protected static final String PATH_MANIFESTMF = "META-INF/MANIFEST.MF";

    protected final Path file;

    protected final Set<String> packageNames;

    protected final Set<String> exportedPackages;

    protected DependencyClasspathEntry(
            Path file, Collection<String> packageNames, Collection<String> exportedPackages) {
        this.file = PathNormalizer.getCanonicalPath(file);
        this.packageNames = Collections.unmodifiableSet(new LinkedHashSet<>(packageNames));
        this.exportedPackages =
                exportedPackages != null ? Collections.unmodifiableSet(new LinkedHashSet<>(exportedPackages)) : null;
    }

    protected AccessRestriction getAccessRestriction(String packageName) {
        if (exportedPackages != null && !exportedPackages.contains(packageName)) {
            AccessRule rule = new AccessRule(
                    null /* pattern */, IProblem.ForbiddenReference, true /* keep looking for accessible type */);
            return new AccessRestriction(rule, AccessRestriction.COMMAND_LINE, getEntryName());
        }
        return null;
    }

    @Override
    public Collection<String> getPackageNames() {
        return packageNames;
    }

    protected static Collection<String> parseExportPackage(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().map(l -> l.replace('.', '/')).collect(Collectors.toList());
        }
    }

    protected static Collection<String> parseBundleManifest(InputStream is) throws IOException, BundleException {
        Map<String, String> headers = parseManifest(is);
        if (!headers.containsKey(Constants.BUNDLE_SYMBOLICNAME)) {
            return null; // not an OSGi bundle
        }
        String exportPackageHeader = headers.get(Constants.EXPORT_PACKAGE);
        if (exportPackageHeader == null) {
            return Collections.emptySet(); // nothing is exported
        }
        Set<String> packages = new HashSet<>();
        for (ManifestElement element : ManifestElement.parseHeader(Constants.EXPORT_PACKAGE, exportPackageHeader)) {
            packages.add(element.getValue().replace('.', '/'));
        }
        return packages;
    }

    private static CaseInsensitiveDictionaryMap<String, String> parseManifest(InputStream is)
            throws IOException, BundleException {
        CaseInsensitiveDictionaryMap<String, String> headers = new CaseInsensitiveDictionaryMap<>();
        ManifestElement.parseBundleManifest(is, headers);
        return headers;
    }

    @Override
    public String getEntryDescription() {
        StringBuilder sb = new StringBuilder(getEntryName());
        if (exportedPackages != null) {
            sb.append("[");
            int idx = 0;
            for (String exportedPackage : exportedPackages) {
                if (idx++ > 0) {
                    sb.append(File.pathSeparatorChar);
                }
                sb.append('+').append(exportedPackage).append("/*");
            }
            if (idx > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append("?**/*");
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public NameEnvironmentAnswer findType(String packageName, String typeName) {
        return findType(packageName, typeName, getAccessRestriction(packageName));
    }

    public abstract NameEnvironmentAnswer findType(
            String packageName, String typeName, AccessRestriction accessRestriction);

    public String getEntryName() {
        return file.toString();
    }
}
