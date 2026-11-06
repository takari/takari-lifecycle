/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.plugin;

import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;
import java.util.Comparator;
import java.util.List;

class Sorting {

    public static void sortDescriptors(List<MojoDescriptor> descriptors) {
        descriptors.sort(Comparator.comparing(MojoDescriptor::getImplementation));
    }

    public static void sortParameters(List<MojoParameter> parameters) {
        parameters.sort(Comparator.comparing(MojoParameter::getName));
    }

    public static void sortRequirements(List<MojoRequirement> requirements) {
        requirements.sort(Comparator.comparing(MojoRequirement::getFieldName));
    }
}
