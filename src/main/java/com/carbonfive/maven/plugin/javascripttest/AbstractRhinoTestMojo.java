package com.carbonfive.maven.plugin.javascripttest;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.AbstractMojo;
import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.*;
import java.util.*;
import static java.lang.String.format;


public abstract class AbstractRhinoTestMojo extends AbstractMojo
{


  /**
   * @parameter
   */
  protected String[] includes;

  /**
   * @parameter
   */
  protected String[] excludes;

  /**
   * @parameter
   */
  protected boolean reimportScripts = false;

  /**
   * @parameter expression="${basedir}
   */
  protected File basedir;

  public void execute() throws MojoExecutionException, MojoFailureException
  {
    try
    {
      String[] testIncludes = includes;
      if (( testIncludes == null ) || ( testIncludes.length == 0 ))
        testIncludes = new String[]{ "src/test/**/suite.html" };

      String[] suites = collectSuites( testIncludes, excludes );
      if (( suites == null ) || ( suites.length == 0 ))
      {
        getLog().info("No tests to run.");
        return;
      }

      for ( String suiteName : suites )
      {
        long startTime = System.currentTimeMillis();
        getLog().info("Running Screw.Unit suite: " + suiteName );
        File suite = new File(getBasedir(), suiteName);

        Global global = new Global();
        Context context  = createAndInitializeContext( global );
        Scriptable scope = context.initStandardObjects( global );


		final ReportManager foo = new ReportManager() {

			@Override
			public void log(Boolean result, String message) {
				getLog().info("TEST [" + result + "]: " + message);
			}

		};

		scope.put("$report", scope, Context.toObject(foo, scope));


        // Establish window scope with dom and all imported and inline scripts executed
        RhinoHelper.execClasspathScript(context, scope, "env.rhino.js");

		runSuite( context, scope, suite );



        // examine and report on results
        SuiteReport report = writeReports(suiteName, suite, context, scope, System.currentTimeMillis() - startTime);

        getLog().info( format("%d test(s), %d failure(s)", report.getTestsRun(), report.getErrors()) );
        if (( report.getErrors() > 0 ))
          throw new MojoFailureException( "Test error: " + report.getFirstError() );
      }

    }
    catch ( MojoFailureException mfe )
    {
      throw mfe;
    }
    catch ( Exception e )
    {
      throw new MojoExecutionException("This plugin has experienced an unexpected error.  Please take some time to report the problem", e);
    }
  }

  // this is the QUnit interface mocked. it's a good interface.
  public class ReportManager {
	public void log(Boolean result, String message) {

	}

	public void moduleStart(String name, Object testEnvironment) {

	}

	public void moduleDone(String name, Long failures, Long total) {

	}

	public void testStart(String name, Object testEnvironment) {
		log(true, "testStart: " + name);
	}

	public void testDone(String name, Long failures, Long total) {

	}

	// before any tests start.
	public void begin() {
		
	}

	// after all tests are completed.
	public void done(Long failures, Long total) {
		
	}
  }
  
  private String[] collectSuites( String[] includes, String[] excludes )
  {
    getLog().info("Basedir: " + basedir );
    if ( ! getBasedir().exists() )
      return new String[0];

    DirectoryScanner scanner = new DirectoryScanner();

    scanner.setBasedir( getBasedir() );

    if ( includes != null )
      scanner.setIncludes( includes );

    if ( excludes != null )
      scanner.setExcludes( excludes );

    scanner.scan();

    return scanner.getIncludedFiles();
  }

  private SuiteReport writeReports(String suiteName, File suite, Context context, Scriptable scope, long executionTime)
    throws IOException
  {
    // Screw.Unit uses the focus event to set a 'focused' class on describe divs before running them,
    // but env.js doesn't support focus events on non-input elements.  Focus them all manually here so our report
    // looks better.
    RhinoHelper.exec("jQuery('body > .describe > .describes > .describe').addClass('focused');","describe focus fix", context, scope );

    SuiteReport report = parseSuiteReport(context, scope);

    generateHumanReadableReport(context, scope, suite, suiteName);

    generateJUnitStyleReport(suiteName, executionTime, report);

    return report;
  }

  private void generateJUnitStyleReport(String suiteName, long executionTime, SuiteReport report)
    throws IOException
  {
    String junitName = suiteName.replace(File.separator, ".");
    File junitFile = new File( getBasedir(), "target/screw-unit/TEST-" + junitName + ".xml" );
    FileWriter junitReportWriter = new FileWriter(junitFile);
    XMLRenderer renderer = new XMLRenderer(junitReportWriter)
      .start("testsuite").attr("failures",report.getErrors()).attr("time",format("%.3f", executionTime/1000.0) )
                         .attr("skipped","0").attr("errors",0).attr("tests",report.getTestsRun()).attr("name",junitName)
        .start("properties").end();

    for ( TestReport test : report.getTests() )
    {
      renderer.start("testcase").attr("time",format("%.3f", executionTime/(report.getTestsRun()*1000.0)))
                                .attr("name",test.test).attr("classname",junitName);
      if ( test.error != null )
        renderer.start("failure").attr("message", test.error).attr("type","org.apache.maven.plugin.MojoFailureException")
                               .text(test.test + ": " + test.error).end();
      renderer.end();
    }
    renderer.end();
    junitReportWriter.close();
  }



  public File getBasedir()
  {
    return basedir;
  }

  public void setBasedir( File basedir)
  {
    this.basedir = basedir;
  }


  private Context createAndInitializeContext(Global global)
  {
    Context context = ContextFactory.getGlobal().enterContext();
    global.init(context);
    context.setOptimizationLevel(-1);
    context.setLanguageVersion(Context.VERSION_1_5);
    return context;
  }





	protected abstract void runSuite(Context context, Scriptable scope, File suite) throws Exception;
	protected abstract SuiteReport parseSuiteReport(Context context, Scriptable scope);
	protected abstract void generateHumanReadableReport(Context context, Scriptable scope, File suite, String suiteName) throws IOException;


  protected class SuiteReport
  {
    private List<TestReport> tests = new ArrayList<TestReport>();
    private int errors;
    private String firstError;

    public void addTest( String test, String error )
    {
      if (( error != null ) && ( error.trim().length() == 0 ))
        error = null;
      tests.add( new TestReport(test, error) );
      if ( error != null )
      {
        if ( firstError == null )
          firstError = error;
        errors++;
      }
    }

    public List<TestReport> getTests()
    {
      return tests;
    }

    public int getTestsRun()
    {
      return tests.size();
    }

    public int getErrors()
    {
      return errors;
    }

    public String getFirstError()
    {
      return firstError;
    }
  }

  protected class TestReport
  {
    String test;
    String error;

    private TestReport(String test, String error)
    {
      this.test = test;
      this.error = error;
    }
  }

  protected class XMLRenderer
  {
    private LinkedList<String> elements = new LinkedList<String>();
    private boolean open;
    private Writer writer;

    private XMLRenderer(Writer writer) throws IOException
    {
      this.writer = writer;
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
    }

    public XMLRenderer start( String name ) throws IOException
    {
      endTag();
      indent();
      writer.write("<" + name);
      elements.addLast( name );
      open = true;
      return this;
    }

    public XMLRenderer attr( String name, int value ) throws IOException
    {
      return attr( name, Integer.toString(value) );
    }

    public XMLRenderer attr( String name, String value ) throws IOException
    {
      if ( ! open )
        throw new IOException("Attempt to write attribute outside of element");
      writer.write(" " + name + "=\"" + value + "\"");
      return this;
    }

    public XMLRenderer end() throws IOException
    {
      String name = elements.removeLast();
      if ( open )
      {
        writer.write( "/>" );
        open = false;
        return this;
      }

      indent();
      writer.write("</" + name + ">");
      return this;
    }

    public XMLRenderer text( String text ) throws IOException
    {
      endTag();
      indent();
      writer.write( text );
      return this;
    }

    private void indent()
      throws IOException
    {
      writer.write("\n");
      for ( String element : elements )
        writer.write("\t");
    }

    private void endTag() throws IOException
    {
      if ( open )
        writer.write(">");
      open = false;
    }
  }

}
