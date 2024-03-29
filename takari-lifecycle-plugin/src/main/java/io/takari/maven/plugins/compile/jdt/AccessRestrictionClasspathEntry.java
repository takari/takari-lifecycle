/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;
import java.util.Collection;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.AccessRule;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

class AccessRestrictionClasspathEntry implements ClasspathEntry {
    private final DependencyClasspathEntry entry;
    private final AccessRestriction accessRestriction;

    private AccessRestrictionClasspathEntry(DependencyClasspathEntry entry, AccessRestriction accessRestriction) {
        this.entry = entry;
        this.accessRestriction = accessRestriction;
    }

    @Override
    public Collection<String> getPackageNames() {
        return entry.getPackageNames();
    }

    @Override
    public NameEnvironmentAnswer findType(String packageName, String typeName) {
        return entry.findType(packageName, typeName, accessRestriction);
    }

    @Override
    public String getEntryDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.getEntryName());
        if (accessRestriction != null) {
            sb.append("[?**/*]");
        }
        return sb.toString();
    }

    public static AccessRestrictionClasspathEntry forbidAll(DependencyClasspathEntry entry) {
        AccessRule accessRule = new AccessRule(
                null /* pattern */, IProblem.ForbiddenReference, true /* keep looking for accessible type */);
        AccessRestriction accessRestriction =
                new AccessRestriction(accessRule, AccessRestriction.COMMAND_LINE, entry.getEntryName());
        return new AccessRestrictionClasspathEntry(entry, accessRestriction);
    }

    public static AccessRestrictionClasspathEntry allowAll(DependencyClasspathEntry entry) {
        return new AccessRestrictionClasspathEntry(entry, null);
    }
}
