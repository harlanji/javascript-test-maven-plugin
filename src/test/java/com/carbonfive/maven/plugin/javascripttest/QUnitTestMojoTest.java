package com.carbonfive.maven.plugin.javascripttest;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.MojoFailureException;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class QUnitTestMojoTest
    extends AbstractMojoTestCase
{
  protected void setUp() throws Exception
  {
      super.setUp();
  }

  public void testPassingTestSuite() throws Exception
  {
    File testPom = new File( getBasedir(), "src/test/resources/test-qunit-project/pom_pass.xml" );

    QUnitTestMojo mojo = (QUnitTestMojo) lookupMojo( "qunit-test", testPom );

    mojo.setBasedir( new File( getBasedir(), "src/test/resources/test-qunit-project" ) );

    assertNotNull( mojo );

    mojo.execute();
  }

}