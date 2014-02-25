package io.takari.maven.plugins.compiler.api;

/**
 * The MIT License
 *
 * Copyright (c) 2004, The Codehaus
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

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 */
public final class CompilerOutputStyle {
  public final static CompilerOutputStyle ONE_OUTPUT_FILE_PER_INPUT_FILE = new CompilerOutputStyle("one-output-file-per-input-file");

  public final static CompilerOutputStyle ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES = new CompilerOutputStyle("one-output-file");

  // ----------------------------------------------------------------------
  //
  // ----------------------------------------------------------------------

  private String id;

  private CompilerOutputStyle(String id) {
    this.id = id;
  }

  // ----------------------------------------------------------------------
  //
  // ----------------------------------------------------------------------

  public String toString() {
    return id;
  }

  public boolean equals(Object other) {
    if (other == null || !(other instanceof CompilerOutputStyle)) {
      return false;
    }

    return id.equals(((CompilerOutputStyle) other).id);
  }

  public int hashCode() {
    return id.hashCode();
  }
}
