/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package org.codehaus.foo;

import org.apache.commons.lang.StringUtils;

public class ExternalDeps
{
	public void hello( String str )
	{
        System.out.println( StringUtils.upperCase( str)  );
	}
}
