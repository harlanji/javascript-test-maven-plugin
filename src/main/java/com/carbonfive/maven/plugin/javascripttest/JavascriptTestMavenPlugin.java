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

/**
 * @component
 * @goal javascript-test
 * @phase test
 */
public class JavascriptTestMavenPlugin extends AbstractMojo
{
  private static final String LOCATE_SCRIPTS_FUNCTION = "return jtmp_locate_scripts();";
  private static final String LOCATE_CSS_FUNCTION = "return jtmp_locate_css();";
  private static final String TESTS_RUN_FUNCTION = "return jtmp_failure_messages()";

  /**
   * @parameter
   */
  private String[] includes;

  /**
   * @parameter
   */
  private String[] excludes;

  /**
   * @parameter
   */
  private boolean reimportScripts = false;

  /**
   * @parameter expression="${basedir}
   */
  private File basedir;

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

        // Establish window scope with dom and all imported and inline scripts executed
        RhinoHelper.execClasspathScript(context, scope, "env.rhino.js");
        RhinoHelper.execClasspathScript(context, scope, "javascript-test-maven-plugin.js");

        RhinoHelper.exec( "Envjs(\"" + suite + "\");", "suite.html", context, scope );
        importScripts(context, scope, suite);

        // Trigger test execution
        RhinoHelper.exec( "jQuery(window).trigger('load');Envjs.wait();", "start", context, scope );

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

    generateHumanReadableReport(suiteName, suite, context, scope);

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

  private void generateHumanReadableReport(String suiteName, File suite, Context context, Scriptable scope)
    throws IOException
  {
    File reportFile = new File( getBasedir(), "target/screw-unit/" + suiteName.replace(File.separator,"."));
    reportFile.getParentFile().mkdirs();
    FileWriter writer = new FileWriter( reportFile );
    writer.write("<html><head><title>Screw-Unit test report for " + suiteName +
      "</title><style type=\"text/css\">" +
      inlineCss(context, scope, suite) +
      "</style></head><body>"
      + RhinoHelper.execStringFunction("return jQuery('body').html()","jQuery('body').html()", context, scope) +
      "</body></html>");
    writer.close();
  }

  private SuiteReport parseSuiteReport(Context context, Scriptable scope)
  {
    NativeArray tests = RhinoHelper.execNativeArrayFunction(TESTS_RUN_FUNCTION, "tests run", context, scope);
    SuiteReport report = new SuiteReport();
    for ( int i=0; i < tests.getLength(); i++ )
    {
      NativeObject test = (NativeObject) tests.get(i, tests);
      report.addTest( (String) test.get("test",test), (String) test.get("error",test) );
    }
    return report;
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

  private void importScripts(Context context, Scriptable scope, File suite)
    throws IOException
  {
    Set<String> executed = new HashSet<String>();
    Set<String> toExecute = new LinkedHashSet<String>();
    toExecute.addAll(Arrays.asList(RhinoHelper.execStringArrayFunction("return jtmp_locate_scripts()", "locate scripts", context, scope)));
    while ( toExecute.size() > executed.size() )
    {
      for ( String script : toExecute )
      {
        if ( ! executed.contains(script) )
        {
          if ( script.startsWith("file:") )
            RhinoHelper.execScriptFile(context, scope, new File(suite.getParentFile(), script.substring("file:".length())) );
          else
            context.compileString(script, "inline script", 1, null ).exec(context,scope);

          executed.add( script );

          if ( reimportScripts )
          {
            toExecute.clear();
            toExecute.addAll(Arrays.asList(RhinoHelper.execStringArrayFunction(LOCATE_SCRIPTS_FUNCTION, "locate scripts", context, scope)));
            break;
          }
        }
      }
    }
  }

  private String inlineCss(Context context, Scriptable scope, File suite)
    throws IOException
  {
    StringBuilder buf = new StringBuilder();
    char[] cb = new char[4096];
    for ( String source : RhinoHelper.execStringArrayFunction( LOCATE_CSS_FUNCTION, "locate css", context, scope ) )
    {
      Reader in = new FileReader( new File(suite.getParentFile(), source) );
      for ( int c = in.read(cb,0,4096); c >= 0; c = in.read(cb,0,4096) )
        buf.append( cb, 0, c );
      in.close();
      buf.append( "\n" );
    }
    return buf.toString();
  }

  private class SuiteReport
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

  private class TestReport
  {
    String test;
    String error;

    private TestReport(String test, String error)
    {
      this.test = test;
      this.error = error;
    }
  }

  private class XMLRenderer
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
