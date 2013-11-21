package io.tesla.maven.plugins.resources;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;

@Named
@Singleton
public class ResourceFilter {

  private DefaultMustacheFactory mf;

  public ResourceFilter() {
    mf = new NoEncodingMustacheFactory();
  }

  public void filter(Reader reader, Map<Object,Object> properties) throws IOException {
    Mustache maven = mf.compile(reader, "maven", "${", "}");
    StringWriter sw = new StringWriter();
    maven.execute(sw, properties).close();
  }

  private static class NoEncodingMustacheFactory extends DefaultMustacheFactory {
    @Override
    public void encode(String value, Writer writer) {
      //
      // TODO: encoding rules
      //
      try {
        writer.write(value);
      } catch (IOException e) {
        throw new MustacheException(e);
      }
    }
  }
}
