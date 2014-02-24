package org.codehaus.plexus.compiler;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * The result returned from a compiling language processor (aka compiler), possibly including
 * some messages.
 * 
 * @author Olivier Lamy
 * @since 2.0
 */
public class CompilerResult {
  private boolean success;

  private List<CompilerMessage> compilerMessages;

  /**
   * Constructs a successful compiler result with no messages.
   */
  public CompilerResult() {
    this.success = true;
  }

  /**
   * Constructs a compiler result.
   * 
   * @param success if the compiler process was successful or not
   * @param compilerMessages a list of messages from the compiler process
   */
  public CompilerResult(boolean success, List<CompilerMessage> compilerMessages) {
    this.success = success;
    this.compilerMessages = compilerMessages;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public CompilerResult success(boolean success) {
    this.setSuccess(success);
    return this;
  }

  public List<CompilerMessage> getCompilerMessages() {
    if (compilerMessages == null) {
      this.compilerMessages = new ArrayList<CompilerMessage>();
    }
    return compilerMessages;
  }

  public void setCompilerMessages(List<CompilerMessage> compilerMessages) {
    this.compilerMessages = compilerMessages;
  }

  public CompilerResult compilerMessages(List<CompilerMessage> compilerMessages) {
    this.setCompilerMessages(compilerMessages);
    return this;
  }
}
