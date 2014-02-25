package io.takari.maven.plugins.compiler.api;

/**
 *
 * Copyright 2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * This class encapsulates a message from a compiler.  This class is deprecated
 * and only exists for backwards compatibility. Clients should be using
 * {@link CompilerMessage} instead.
 *
 * @author <a href="mailto:andrew@eisenberg.as">Andrew Eisenberg</a>
 */
@Deprecated
public class CompilerError extends CompilerMessage {

  public CompilerError(String message, boolean error) {
    super(message, error);
  }

}
