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

public class JavascriptTestMavenPluginTest
    extends AbstractMojoTestCase
{
  protected void setUp() throws Exception
  {
      super.setUp();
  }

  public void testPassingTestSuite() throws Exception
  {
    File testPom = new File( getBasedir(), "src/test/resources/test-project-1/pom_pass.xml" );

    ScrewUnitTestMojo mojo = (ScrewUnitTestMojo) lookupMojo( "javascript-test", testPom );

    mojo.setBasedir( new File( getBasedir(), "src/test/resources/test-project-1" ) );

    assertNotNull( mojo );

    mojo.execute();

    assertReportsExists("src.test.javascript.suite.html", "src.test.javascript.suite2.html",
                        "TEST-src.test.javascript.suite.html.xml", "TEST-src.test.javascript.suite2.html.xml");

    JUnitReportHandler report = parseJUnitReport("TEST-src.test.javascript.suite.html.xml");

    assertEquals( 5, report.getTestCount() );
    assertEquals( 0, report.getErrorCount() );
    assertEquals( 0, report.getFailureCount() );

    String[] expectedTests = new String[] { "decrements the man's luck by 5",
                                            "does not change the man's luck",
                                            "decrements the luck field by the given amount",
                                            "decrements the luck field to zero",
                                            "removes the man's hair" };

    assertEquals( expectedTests.length, report.getTests().size() );
    assertEquals( 0, report.getErrors().size() );
    for ( int i=0; i < expectedTests.length; i++ )
      assertEquals( expectedTests[i], report.getTests().get(i) );
  }

  public void testFailingTestSuite() throws Exception
  {
    File testPom = new File( getBasedir(), "src/test/resources/test-project-1/pom_fail.xml" );

    ScrewUnitTestMojo mojo = (ScrewUnitTestMojo) lookupMojo( "javascript-test", testPom );

    mojo.setBasedir( new File( getBasedir(), "src/test/resources/test-project-1" ) );
    assertNotNull( mojo );

    try
    {
      mojo.execute();
      fail("Expected test failure exception");
    }
    catch ( MojoFailureException mfe )
    {
      // should still get report after test failure.
      assertReportsExists("src.test.javascript.fail_suite.html", "TEST-src.test.javascript.fail_suite.html.xml" );

      JUnitReportHandler report = parseJUnitReport("TEST-src.test.javascript.fail_suite.html.xml");

      assertEquals( 5, report.getTestCount() );
      assertEquals( 0, report.getErrorCount() );
      assertEquals( 1, report.getFailureCount() );

      String[] expectedTests = new String[] { "decrements the man's luck by 5",
                                              "does not change the man's luck",
                                              "decrements the luck field by the given amount",
                                              "decrements the luck field to zero",
                                              "removes the man's hair" };

      assertEquals( expectedTests.length, report.getTests().size() );
      assertEquals( 1, report.getErrors().size() );
      for ( int i=0; i < expectedTests.length; i++ )
      {
        assertEquals( expectedTests[i], report.getTests().get(i) );
        if ( "decrements the luck field by the given amount".equals( expectedTests[i] ) )
          assertEquals( "expected 2 to equal 1", report.getErrors().get(expectedTests[i] ) );
      }
    }
  }

  private JUnitReportHandler parseJUnitReport(String reportFile)
    throws ParserConfigurationException, SAXException, IOException
  {
    File expectedJunitReport = new File( getBasedir(), "src/test/resources/test-project-1/target/screw-unit/" + reportFile);
    SAXParserFactory factory = SAXParserFactory.newInstance();
    SAXParser saxParser = factory.newSAXParser();
    JUnitReportHandler report = new JUnitReportHandler();
    saxParser.parse( expectedJunitReport, report );
    return report;
  }

  private void assertReportsExists(String... expectedReports)
  {
    for ( String report : expectedReports )
    {
      File expectedReport = new File( getBasedir(), "src/test/resources/test-project-1/target/screw-unit/" + report );
      assertTrue( expectedReport.exists() );
    }
  }

  /*  TODO: Create a blue-ridge rails project and test it here.
  public void testExistingTestSuite() throws Exception
  {
    File testPom = new File( getBasedir(), "src/test/resources/test-project-2/pom.xml" );

    JavascriptTestMavenPlugin mojo = (JavascriptTestMavenPlugin) lookupMojo( "javascript-test", testPom );

    mojo.setBasedir( new File( getBasedir(), "src/test/resources/test-project-2" ) );

    assertNotNull( mojo );

    mojo.execute();

    File expectedReport = new File( getBasedir(), "src/test/resources/test-project-2/target/screw-unit/spec_javascripts_fixtures_method_dimensions.html" );
    assertTrue( expectedReport.exists() );
  }
  */


  private class JUnitReportHandler extends DefaultHandler
  {
    private List<String> tests = new ArrayList<String>();
    private Map<String,String> errors = new HashMap<String,String>();
    private String currentTest = null;
    private int testCount = 0;
    private int errorCount = 0;
    private int failureCount = 0;

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException
    {
      if ( "testsuite".equals( qName ) )
      {
        testCount = Integer.parseInt( atts.getValue("tests") );
        errorCount = Integer.parseInt( atts.getValue("errors") );
        failureCount = Integer.parseInt( atts.getValue("failures") );
      }
      else if ( "testcase".equals( qName ) )
      {
        String name = atts.getValue("name");
        tests.add(name);
        currentTest = name;
      }
      else if ( "failure".equals( qName ) )
      {
        errors.put( currentTest, atts.getValue("message") );
      }
    }

    @Override
    public void error(SAXParseException e) throws SAXException
    {
      throw new SAXException( e );
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException
    {
      throw new SAXException( e );
    }

    public List<String> getTests()
    {
      return tests;
    }

    public Map<String, String> getErrors()
    {
      return errors;
    }

    public int getTestCount()
    {
      return testCount;
    }

    public int getErrorCount()
    {
      return errorCount;
    }

    public int getFailureCount()
    {
      return failureCount;
    }
  }
}