package io.takari.maven.plugins.plugin;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;

// originally copied from org.apache.maven.tools.plugin.generator.GeneratorUtils
class GeneratorUtils {
  private GeneratorUtils() {
    // nop
  }

  /**
   * Returns a literal replacement <code>String</code> for the specified <code>String</code>. This method produces a <code>String</code> that will work as a literal replacement <code>s</code> in the
   * <code>appendReplacement</code> method of the {@link Matcher} class. The <code>String</code> produced will match the sequence of characters in <code>s</code> treated as a literal sequence. Slashes
   * ('\') and dollar signs ('$') will be given no special meaning. TODO: copied from Matcher class of Java 1.5, remove once target platform can be upgraded
   *
   * @see <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/regex/Matcher.html">java.util.regex.Matcher</a>
   * @param s The string to be literalized
   * @return A literal string replacement
   */
  private static String quoteReplacement(String s) {
    if ((s.indexOf('\\') == -1) && (s.indexOf('$') == -1)) {
      return s;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        sb.append('\\');
        sb.append('\\');
      } else if (c == '$') {
        sb.append('\\');
        sb.append('$');
      } else {
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
   * Decodes javadoc inline tags into equivalent HTML tags. For instance, the inline tag "{@code <A&B>}" should be rendered as "<code>&lt;A&amp;B&gt;</code>".
   *
   * @param description The javadoc description to decode, may be <code>null</code>.
   * @return The decoded description, never <code>null</code>.
   */
  static String decodeJavadocTags(String description) {
    if (StringUtils.isEmpty(description)) {
      return "";
    }

    StringBuffer decoded = new StringBuffer(description.length() + 1024);

    Matcher matcher = Pattern.compile("\\{@(\\w+)\\s*([^\\}]*)\\}").matcher(description);
    while (matcher.find()) {
      String tag = matcher.group(1);
      String text = matcher.group(2);
      text = StringUtils.replace(text, "&", "&amp;");
      text = StringUtils.replace(text, "<", "&lt;");
      text = StringUtils.replace(text, ">", "&gt;");
      if ("code".equals(tag)) {
        text = "<code>" + text + "</code>";
      } else if ("link".equals(tag) || "linkplain".equals(tag) || "value".equals(tag)) {
        String pattern = "(([^#\\.\\s]+\\.)*([^#\\.\\s]+))?" + "(#([^\\(\\s]*)(\\([^\\)]*\\))?\\s*(\\S.*)?)?";
        final int label = 7;
        final int clazz = 3;
        final int member = 5;
        final int args = 6;
        Matcher link = Pattern.compile(pattern).matcher(text);
        if (link.matches()) {
          text = link.group(label);
          if (StringUtils.isEmpty(text)) {
            text = link.group(clazz);
            if (StringUtils.isEmpty(text)) {
              text = "";
            }
            if (StringUtils.isNotEmpty(link.group(member))) {
              if (StringUtils.isNotEmpty(text)) {
                text += '.';
              }
              text += link.group(member);
              if (StringUtils.isNotEmpty(link.group(args))) {
                text += "()";
              }
            }
          }
        }
        if (!"linkplain".equals(tag)) {
          text = "<code>" + text + "</code>";
        }
      }
      matcher.appendReplacement(decoded, (text != null) ? quoteReplacement(text) : "");
    }
    matcher.appendTail(decoded);

    return decoded.toString();
  }

}
