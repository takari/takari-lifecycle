/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.configurator;

import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public class MojoConfigurator extends BasicComponentConfigurator {

  @Override
  public void configureComponent(final Object mojoInstance, //
      final PlexusConfiguration pluginConfigurationFromMaven, //
      final ExpressionEvaluator evaluator, //
      final ClassRealm realm, //
      final ConfigurationListener listener) throws ComponentConfigurationException {

    super.configureComponent(mojoInstance, pluginConfigurationFromMaven, evaluator, realm, listener);
  }
}
