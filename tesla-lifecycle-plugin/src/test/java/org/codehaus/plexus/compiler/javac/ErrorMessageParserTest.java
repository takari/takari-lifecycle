package org.codehaus.plexus.compiler.javac;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import junit.framework.TestCase;

import org.codehaus.plexus.compiler.CompilerMessage;
import org.codehaus.plexus.util.Os;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 */
public class ErrorMessageParserTest extends TestCase {
  private static final String EOL = System.getProperty("line.separator");

  public void testDeprecationMessage() throws Exception {
    String error = "target/compiler-src/testDeprecation/Foo.java:1: warning: Date(java.lang.String) in java.util.Date has been deprecated" + EOL
        + "import java.util.Date;public class Foo{    private Date date = new Date( \"foo\");}" + EOL + "                                                               ^" + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(0, error);

    assertNotNull(compilerError);

    assertFalse(compilerError.isError());

    assertEquals("Date(java.lang.String) in java.util.Date has been deprecated", compilerError.getMessage());

    assertEquals(63, compilerError.getStartColumn());

    assertEquals(66, compilerError.getEndColumn());

    assertEquals(1, compilerError.getStartLine());

    assertEquals(1, compilerError.getEndLine());
  }

  public void testWarningMessage() {
    String error = "target/compiler-src/testWarning/Foo.java:8: warning: finally clause cannot complete normally" + EOL + "        finally { return; }" + EOL + "                          ^" + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(0, error);

    assertNotNull(compilerError);

    assertFalse(compilerError.isError());

    assertEquals("finally clause cannot complete normally", compilerError.getMessage());

    assertEquals(26, compilerError.getStartColumn());

    assertEquals(27, compilerError.getEndColumn());

    assertEquals(8, compilerError.getStartLine());

    assertEquals(8, compilerError.getEndLine());
  }

  public void testErrorMessage() {
    String error = "Foo.java:7: not a statement" + EOL + "         i;" + EOL + "         ^" + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(1, error);

    assertNotNull(compilerError);

    assertTrue(compilerError.isError());

    assertEquals("not a statement", compilerError.getMessage());

    assertEquals(9, compilerError.getStartColumn());

    assertEquals(11, compilerError.getEndColumn());

    assertEquals(7, compilerError.getStartLine());

    assertEquals(7, compilerError.getEndLine());
  }

  public void testUnknownSymbolError() {
    String error = "./org/codehaus/foo/UnknownSymbol.java:7: cannot find symbol" + EOL + "symbol  : method foo()" + EOL + "location: class org.codehaus.foo.UnknownSymbol" + EOL + "        foo();"
        + EOL + "        ^" + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(1, error);

    assertNotNull(compilerError);

    assertTrue(compilerError.isError());

    assertEquals("cannot find symbol" + EOL + "symbol  : method foo()" + EOL + "location: class org.codehaus.foo.UnknownSymbol", compilerError.getMessage());

    assertEquals(8, compilerError.getStartColumn());

    assertEquals(14, compilerError.getEndColumn());

    assertEquals(7, compilerError.getStartLine());

    assertEquals(7, compilerError.getEndLine());
  }

  public void testTwoErrors() throws IOException {
    String errors = "./org/codehaus/foo/ExternalDeps.java:4: package org.apache.commons.lang does not exist" + EOL + "import org.apache.commons.lang.StringUtils;" + EOL
        + "                               ^" + EOL + "./org/codehaus/foo/ExternalDeps.java:12: cannot find symbol" + EOL + "symbol  : variable StringUtils" + EOL
        + "location: class org.codehaus.foo.ExternalDeps" + EOL + "          System.out.println( StringUtils.upperCase( str)  );" + EOL + "                              ^" + EOL + "2 errors" + EOL;

    List<CompilerMessage> messages = JavacCompiler.parseModernStream(1, new BufferedReader(new StringReader(errors)));

    assertEquals(2, messages.size());
  }

  public void testAnotherTwoErrors() throws IOException {
    String errors = "./org/codehaus/foo/ExternalDeps.java:4: package org.apache.commons.lang does not exist" + EOL + "import org.apache.commons.lang.StringUtils;" + EOL
        + "                               ^" + EOL + "./org/codehaus/foo/ExternalDeps.java:12: cannot find symbol" + EOL + "symbol  : variable StringUtils" + EOL
        + "location: class org.codehaus.foo.ExternalDeps" + EOL + "          System.out.println( StringUtils.upperCase( str)  );" + EOL + "                              ^" + EOL + "2 errors" + EOL;

    List<CompilerMessage> messages = JavacCompiler.parseModernStream(1, new BufferedReader(new StringReader(errors)));

    assertEquals(2, messages.size());
  }

  public void testAssertError() throws IOException {
    String errors = "./org/codehaus/foo/ReservedWord.java:5: as of release 1.4, 'assert' is a keyword, and may not be used as an identifier" + EOL
        + "(try -source 1.3 or lower to use 'assert' as an identifier)" + EOL + "        String assert;" + EOL + "               ^" + EOL + "1 error" + EOL;

    List<CompilerMessage> messages = JavacCompiler.parseModernStream(1, new BufferedReader(new StringReader(errors)));

    assertEquals(1, messages.size());
  }

  public void testLocalizedWarningNotTreatedAsError() throws IOException {
    String errors = "./src/main/java/Main.java:9: \u8b66\u544a:[deprecation] java.io.File \u306e toURL() \u306f\u63a8\u5968\u3055\u308c\u307e\u305b\u3093\u3002" + EOL + "    new File( path ).toURL()"
        + EOL + "                    ^" + EOL + "\u8b66\u544a 1 \u500b" + EOL;

    List<CompilerMessage> messages = JavacCompiler.parseModernStream(0, new BufferedReader(new StringReader(errors)));

    assertEquals(1, messages.size());
    assertFalse(((CompilerMessage) messages.get(0)).isError());
  }

  public void testUnixFileNames() {
    String error = "/my/prj/src/main/java/test/prj/App.java:11: not a statement" + EOL + "        System.out.println( \"Hello World!\" );x" + EOL + "                                             ^"
        + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(1, error);

    assertEquals("/my/prj/src/main/java/test/prj/App.java:[11,45] not a statement", String.valueOf(compilerError));
  }

  public void testWindowsDriveLettersMCOMPILER140() {
    String error = "c:\\Documents and Settings\\My Self\\Documents\\prj\\src\\main\\java\\test\\prj\\App.java:11: not a statement" + EOL + "        System.out.println( \"Hello World!\" );x" + EOL
        + "                                             ^" + EOL;

    CompilerMessage compilerError = JavacCompiler.parseModernError(1, error);

    assertEquals("c:\\Documents and Settings\\My Self\\Documents\\prj\\src\\main\\java\\test\\prj\\App.java:[11,45] not a statement", String.valueOf(compilerError));
  }

  /**
   * Test that CRLF is parsed correctly wrt. the filename in warnings.
   *
   * @throws Exception
   */
  public void testCRLF_windows() throws Exception {
    // This test is only relevant on windows (test hardcodes EOL)
    if (!Os.isFamily("windows")) {
      return;
    }

    String CRLF = new String(new byte[] {
        (byte) 0x0D, (byte) 0x0A
    });
    String errors = "warning: [options] bootstrap class path not set in conjunction with -source 1.6"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpServerImpl.java]]"
        + CRLF
        + "[parsing completed 19ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpServer.java]]"
        + CRLF
        + "[parsing completed 1ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpServerAware.java]]"
        + CRLF
        + "[parsing completed 1ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java]]"
        + CRLF
        + "[parsing completed 3ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpThreadPool.java]]"
        + CRLF
        + "[parsing completed 3ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpQueueAware.java]]"
        + CRLF
        + "[parsing completed 0ms]"
        + CRLF
        + "[parsing started RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpThreadPoolAware.java]]"
        + CRLF
        + "[parsing completed 1ms]"
        + CRLF
        + "[search path for source files: C:\\commander\\pre\\ec\\ec-http\\src\\main\\java]"
        + CRLF
        + "[search path for class files: C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\resources.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\rt.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\sunrsasign.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\jsse.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\jce.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\charsets.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\jfr.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\classes,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\dnsns.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\localedata.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\sunec.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\sunjce_provider.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\sunmscapi.jar,C:\\Program Files\\Java\\jdk1.7.0_04\\jre\\lib\\ext\\zipfs.jar,C:\\commander\\pre\\ec\\ec-http\\target\\classes,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-lock\\1.0.0-SNAPSHOT\\ec-lock-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-timer\\1.0.0-SNAPSHOT\\ec-timer-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\org\\apache\\commons\\commons-math\\2.2\\commons-math-2.2.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-validation\\1.0.0-SNAPSHOT\\ec-validation-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-xml\\1.0.0-SNAPSHOT\\ec-xml-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\commons-beanutils\\commons-beanutils\\1.8.3-PATCH1\\commons-beanutils-1.8.3-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\commons-collections\\commons-collections\\3.2.1\\commons-collections-3.2.1.jar,C:\\Users\\anders\\.m2\\repository\\dom4j\\dom4j\\1.6.1-PATCH1\\dom4j-1.6.1-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\javax\\validation\\validation-api\\1.0.0.GA\\validation-api-1.0.0.GA.jar,C:\\Users\\anders\\.m2\\repository\\org\\codehaus\\jackson\\jackson-core-asl\\1.9.7\\jackson-core-asl-1.9.7.jar,C:\\Users\\anders\\.m2\\repository\\org\\codehaus\\jackson\\jackson-mapper-asl\\1.9.7\\jackson-mapper-asl-1.9.7.jar,C:\\Users\\anders\\.m2\\repository\\org\\hibernate\\hibernate-core\\3.6.7-PATCH14\\hibernate-core-3.6.7-PATCH14.jar,C:\\Users\\anders\\.m2\\repository\\antlr\\antlr\\2.7.6\\antlr-2.7.6.jar,C:\\Users\\anders\\.m2\\repository\\org\\hibernate\\hibernate-commons-annotations\\3.2.0.Final\\hibernate-commons-annotations-3.2.0.Final.jar,C:\\Users\\anders\\.m2\\repository\\javax\\transaction\\jta\\1.1\\jta-1.1.jar,C:\\Users\\anders\\.m2\\repository\\org\\hibernate\\javax\\persistence\\hibernate-jpa-2.0-api\\1.0.1.Final\\hibernate-jpa-2.0-api-1.0.1.Final.jar,C:\\Users\\anders\\.m2\\repository\\org\\hyperic\\sigar\\1.6.5.132\\sigar-1.6.5.132.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-context\\3.1.1.RELEASE-PATCH1\\spring-context-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-expression\\3.1.1.RELEASE-PATCH1\\spring-expression-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-core\\3.1.1.RELEASE-PATCH1\\spring-core-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\tanukisoft\\wrapper\\3.5.14\\wrapper-3.5.14.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-log\\1.0.0-SNAPSHOT\\ec-log-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\ch\\qos\\logback\\logback-classic\\1.0.3-PATCH4\\logback-classic-1.0.3-PATCH4.jar,C:\\Users\\anders\\.m2\\repository\\ch\\qos\\logback\\logback-core\\1.0.3-PATCH4\\logback-core-1.0.3-PATCH4.jar,C:\\Users\\anders\\.m2\\repository\\org\\slf4j\\slf4j-api\\1.6.4\\slf4j-api-1.6.4.jar,C:\\Users\\anders\\.m2\\repository\\org\\slf4j\\jul-to-slf4j\\1.6.4\\jul-to-slf4j-1.6.4.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-queue\\1.0.0-SNAPSHOT\\ec-queue-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-security\\1.0.0-SNAPSHOT\\ec-security-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-acl\\1.0.0-SNAPSHOT\\ec-acl-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-transaction\\1.0.0-SNAPSHOT\\ec-transaction-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\org\\aspectj\\aspectjrt\\1.7.0.M1-PATCH1\\aspectjrt-1.7.0.M1-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-crypto\\1.0.0-SNAPSHOT\\ec-crypto-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\org\\bouncycastle\\bcprov-jdk16\\1.46\\bcprov-jdk16-1.46.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-property\\1.0.0-SNAPSHOT\\ec-property-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\org\\apache\\commons\\commons-lang3\\3.1\\commons-lang3-3.1.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-tx\\3.1.1.RELEASE-PATCH1\\spring-tx-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\org\\aopalliance\\com.springsource.org.aopalliance\\1.0.0\\com.springsource.org.aopalliance-1.0.0.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\ldap\\spring-ldap-core\\1.3.1.RELEASE\\spring-ldap-core-1.3.1.RELEASE.jar,C:\\Users\\anders\\.m2\\repository\\commons-lang\\commons-lang\\2.5\\commons-lang-2.5.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\security\\spring-security-core\\2.0.6.PATCH1\\spring-security-core-2.0.6.PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar,C:\\Users\\anders\\.m2\\repository\\cglib\\cglib-nodep\\2.2.2\\cglib-nodep-2.2.2.jar,C:\\Users\\anders\\.m2\\repository\\org\\apache\\commons\\commons-digester3\\3.2-PATCH5\\commons-digester3-3.2-PATCH5.jar,C:\\Users\\anders\\.m2\\repository\\cglib\\cglib\\2.2.2\\cglib-2.2.2.jar,C:\\Users\\anders\\.m2\\repository\\asm\\asm\\3.3.1\\asm-3.3.1.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-aop\\3.1.1.RELEASE-PATCH1\\spring-aop-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\com\\google\\guava\\guava\\12.0\\guava-12.0.jar,C:\\Users\\anders\\.m2\\repository\\com\\google\\code\\findbugs\\jsr305\\2.0.0\\jsr305-2.0.0.jar,C:\\Users\\anders\\.m2\\repository\\com\\intellij\\annotations\\116.108\\annotations-116.108.jar,C:\\Users\\anders\\.m2\\repository\\commons-io\\commons-io\\2.3\\commons-io-2.3.jar,C:\\Users\\anders\\.m2\\repository\\net\\jcip\\jcip-annotations\\1.0\\jcip-annotations-1.0.jar,C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar,C:\\Users\\anders\\.m2\\repository\\commons-codec\\commons-codec\\1.6\\commons-codec-1.6.jar,C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\orbit\\javax.servlet\\3.0.0.v201112011016\\javax.servlet-3.0.0.v201112011016.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-continuation\\8.1.4.v20120524\\jetty-continuation-8.1.4.v20120524.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-http\\8.1.4.v20120524\\jetty-http-8.1.4.v20120524.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-io\\8.1.4.v20120524\\jetty-io-8.1.4.v20120524.jar,C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar,C:\\Users\\anders\\.m2\\repository\\org\\mortbay\\jetty\\servlet-api\\3.0.20100224\\servlet-api-3.0.20100224.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-beans\\3.1.1.RELEASE-PATCH1\\spring-beans-3.1.1.RELEASE-PATCH1.jar,C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-asm\\3.1.1.RELEASE-PATCH1\\spring-asm-3.1.1.RELEASE-PATCH1.jar,.]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/net/BindException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/ArrayList.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/Collection.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/Collections.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/HashSet.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/concurrent/TimeUnit.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/Handler.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/Server.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/nio/SelectChannelConnector.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/ssl/SslSelectChannelConnector.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/ssl/SslContextFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\intellij\\annotations\\116.108\\annotations-116.108.jar(org/jetbrains/annotations/NonNls.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\intellij\\annotations\\116.108\\annotations-116.108.jar(org/jetbrains/annotations/NotNull.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\intellij\\annotations\\116.108\\annotations-116.108.jar(org/jetbrains/annotations/TestOnly.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-beans\\3.1.1.RELEASE-PATCH1\\spring-beans-3.1.1.RELEASE-PATCH1.jar(org/springframework/beans/factory/BeanNameAware.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-beans\\3.1.1.RELEASE-PATCH1\\spring-beans-3.1.1.RELEASE-PATCH1.jar(org/springframework/beans/factory/annotation/Autowired.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\google\\guava\\guava\\12.0\\guava-12.0.jar(com/google/common/collect/Iterables.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-log\\1.0.0-SNAPSHOT\\ec-log-1.0.0-SNAPSHOT.jar(com/electriccloud/log/Log.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-log\\1.0.0-SNAPSHOT\\ec-log-1.0.0-SNAPSHOT.jar(com/electriccloud/log/LogFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar(com/electriccloud/service/ServiceManager.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar(com/electriccloud/service/ServiceState.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/ExceptionUtil.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/SystemUtil.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/ToString.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/ToStringSupport.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/String.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Object.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/io/Serializable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Comparable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/CharSequence.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Enum.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/ToStringAware.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\springframework\\spring-beans\\3.1.1.RELEASE-PATCH1\\spring-beans-3.1.1.RELEASE-PATCH1.jar(org/springframework/beans/factory/Aware.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar(com/electriccloud/service/Service.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Integer.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/concurrent/RejectedExecutionException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/component/AbstractLifeCycle.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/thread/ThreadPool.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-queue\\1.0.0-SNAPSHOT\\ec-queue-1.0.0-SNAPSHOT.jar(com/electriccloud/queue/ExecuteQueue.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar(com/electriccloud/service/ServiceManagerAware.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-util\\1.0.0-SNAPSHOT\\ec-util-1.0.0-SNAPSHOT.jar(com/electriccloud/util/ToStringImpl.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/component/LifeCycle.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/InterruptedException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Runnable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Exception.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/io/IOException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/KeyManagementException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/NoSuchAlgorithmException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/SecureRandom.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/SSLContext.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/TrustManager.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/HttpResponse.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/HttpClient.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/methods/HttpGet.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/methods/HttpPost.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/methods/HttpUriRequest.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/Scheme.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/ssl/SSLSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/entity/StringEntity.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/impl/client/DefaultConnectionKeepAliveStrategy.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/impl/client/DefaultHttpClient.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/impl/client/DefaultHttpRequestRetryHandler.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/impl/conn/tsccm/ThreadSafeClientConnManager.class)]]"
        + CRLF
        + "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java:31: warning: [deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated"
        + CRLF
        + "import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;"
        + CRLF
        + "                                      ^"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/params/HttpParams.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/protocol/HttpContext.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/util/EntityUtils.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-security\\1.0.0-SNAPSHOT\\ec-security-1.0.0-SNAPSHOT.jar(com/electriccloud/security/DummyX509TrustManager.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/SchemeLayeredSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/SchemeSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/LayeredSchemeSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/LayeredSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/SocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/params/CoreConnectionPNames.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/SuppressWarnings.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/annotation/Retention.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/annotation/RetentionPolicy.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/annotation/Target.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/annotation/ElementType.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\google\\guava\\guava\\12.0\\guava-12.0.jar(com/google/common/annotations/GwtCompatible.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\google\\guava\\guava\\12.0\\guava-12.0.jar(com/google/common/annotations/GwtIncompatible.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\com\\electriccloud\\ec-core\\1.0.0-SNAPSHOT\\ec-core-1.0.0-SNAPSHOT.jar(com/electriccloud/infoset/InfosetType.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/annotation/Annotation.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Override.class)]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpServerImpl]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Error.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Throwable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/RuntimeException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/AutoCloseable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Class.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Number.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/AbstractList.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/AbstractCollection.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Iterable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Byte.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Character.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Short.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/nio/AbstractNIOConnector.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/AbstractConnector.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/component/AggregateLifeCycle.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/Connector.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/component/Destroyable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-util\\8.1.4.v20120524\\jetty-util-8.1.4.v20120524.jar(org/eclipse/jetty/util/component/Dumpable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-http\\8.1.4.v20120524\\jetty-http-8.1.4.v20120524.jar(org/eclipse/jetty/http/HttpBuffers.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/handler/HandlerWrapper.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/handler/AbstractHandlerContainer.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\eclipse\\jetty\\jetty-server\\8.1.4.v20120524\\jetty-server-8.1.4.v20120524.jar(org/eclipse/jetty/server/handler/AbstractHandler.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/net/SocketException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Thread.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/IllegalStateException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/AbstractSet.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/Iterator.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/IllegalArgumentException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/Locale.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Long.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Float.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Double.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Boolean.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/Void.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/AssertionError.class)]]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpServerImpl.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpServer]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpServer.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpThreadPoolAware]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpThreadPoolAware.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpThreadPool]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/concurrent/Future.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/concurrent/Callable.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/util/Date.class)]]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpThreadPool.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpQueueAware]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpQueueAware.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpServerAware]"
        + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpServerAware.class]]"
        + CRLF
        + "[checking com.electriccloud.http.HttpUtil]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/net/URI.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/methods/HttpRequestBase.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/message/AbstractHttpMessage.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/HttpMessage.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/impl/client/AbstractHttpClient.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/annotation/GuardedBy.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/ResponseHandler.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/ClientProtocolException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/HttpEntity.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/methods/HttpEntityEnclosingRequestBase.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/entity/AbstractHttpEntity.class)]]"
        + CRLF
        + "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java:151: warning: [deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated"
        + CRLF
        + "        ThreadSafeClientConnManager connectionManager ="
        + CRLF
        + "        ^"
        + CRLF
        + "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java:152: warning: [deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated"
        + CRLF
        + "            new ThreadSafeClientConnManager();"
        + CRLF
        + "                ^"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/GeneralSecurityException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/X509TrustManager.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/KeyException.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/ssl/X509HostnameVerifier.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/SSLSocketFactory.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/HostNameResolver.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/HostnameVerifier.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/ssl/TrustStrategy.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/security/KeyStore.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/scheme/SchemeRegistry.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/ClientConnectionManager.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/client/HttpRequestRetryHandler.class)]]"
        + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpclient\\4.2\\httpclient-4.2.jar(org/apache/http/conn/ConnectionKeepAliveStrategy.class)]]"
        + CRLF + "[loading ZipFileIndexFileObject[C:\\Users\\anders\\.m2\\repository\\org\\apache\\httpcomponents\\httpcore\\4.2\\httpcore-4.2.jar(org/apache/http/ParseException.class)]]" + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/io/UnsupportedEncodingException.class)]]" + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpUtil$1.class]]" + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/StringBuilder.class)]]" + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/AbstractStringBuilder.class)]]" + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/java/lang/StringBuffer.class)]]" + CRLF
        + "[loading ZipFileIndexFileObject[C:\\Program Files\\Java\\jdk1.7.0_04\\lib\\ct.sym(META-INF/sym/rt.jar/javax/net/ssl/KeyManager.class)]]" + CRLF
        + "[wrote RegularFileObject[C:\\commander\\pre\\ec\\ec-http\\target\\classes\\com\\electriccloud\\http\\HttpUtil.class]]" + CRLF + "[total 654ms]" + CRLF + "4 warnings" + CRLF;
    List<CompilerMessage> compilerErrors = JavacCompiler.parseModernStream(0, new BufferedReader(new StringReader(errors)));
    assertEquals("count", 3, compilerErrors.size());
    CompilerMessage error1 = compilerErrors.get(0);
    assertEquals("file", "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java", error1.getFile());
    assertEquals("message", "[deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated", error1.getMessage());
    assertEquals("line", 31, error1.getStartLine());
    assertEquals("column", 38, error1.getStartColumn());
    CompilerMessage error2 = compilerErrors.get(1);
    assertEquals("file", "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java", error2.getFile());
    assertEquals("message", "[deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated", error2.getMessage());
    assertEquals("line", 151, error2.getStartLine());
    assertEquals("column", 8, error2.getStartColumn());
    CompilerMessage error3 = compilerErrors.get(2);
    assertEquals("file", "C:\\commander\\pre\\ec\\ec-http\\src\\main\\java\\com\\electriccloud\\http\\HttpUtil.java", error3.getFile());
    assertEquals("message", "[deprecation] ThreadSafeClientConnManager in org.apache.http.impl.conn.tsccm has been deprecated", error3.getMessage());
    assertEquals("line", 152, error3.getStartLine());
    assertEquals("column", 16, error3.getStartColumn());
  }

}
