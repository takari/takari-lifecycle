package io.takari.maven.plugins.compiler.manager;

/**
 * The MIT License
 *
 * Copyright (c) 2005, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import org.codehaus.plexus.logging.AbstractLogEnabled;

import io.takari.maven.plugins.compiler.api.Compiler;

import java.util.Map;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @plexus.component
 */
public class DefaultCompilerManager extends AbstractLogEnabled implements CompilerManager {
  /**
   * @plexus.requirement role="org.codehaus.plexus.compiler.Compiler"
   */
  private Map<String, Compiler> compilers;

  // ----------------------------------------------------------------------
  // CompilerManager Implementation
  // ----------------------------------------------------------------------

  public Compiler getCompiler(String compilerId) throws NoSuchCompilerException {
    Compiler compiler = compilers.get(compilerId);

    if (compiler == null) {
      throw new NoSuchCompilerException(compilerId);
    }

    return compiler;
  }
}
