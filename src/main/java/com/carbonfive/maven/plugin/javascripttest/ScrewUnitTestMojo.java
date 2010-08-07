/*
 *  Copyright 2010 harlan.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package com.carbonfive.maven.plugin.javascripttest;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

/**
 * @component
 * @goal javascript-test
 * @phase test
 */
public class ScrewUnitTestMojo extends AbstractRhinoTestMojo {

  private static final String LOCATE_SCRIPTS_FUNCTION = "return jtmp_locate_scripts();";
  private static final String LOCATE_CSS_FUNCTION = "return jtmp_locate_css();";
  private static final String TESTS_RUN_FUNCTION = "return jtmp_failure_messages()";

	@Override
	protected void runSuite(Context context, Scriptable scope, File suite) throws Exception {
        RhinoHelper.execClasspathScript(context, scope, "javascript-test-maven-plugin.js");
		RhinoHelper.execClasspathScript(context, scope, "screwunit-runner.js");

		/*
		RhinoHelper.execClasspathScript(context, scope, "screwunit/screw.builder.js");
		RhinoHelper.execClasspathScript(context, scope, "screwunit/screw.matchers.js");
		RhinoHelper.execClasspathScript(context, scope, "screwunit/screw.events.js");
		RhinoHelper.execClasspathScript(context, scope, "screwunit/screw.behaviors.js");
*/

		String code = "window.location = \"" + suite.getAbsolutePath() + "\";";

		RhinoHelper.exec( code, "suite.html", context, scope );

        //importScripts(context, scope, suite);

        // Trigger test execution
        RhinoHelper.exec( "jQuery(window).trigger('load');Envjs.wait();", "start", context, scope );
	}

	
	  private void importScripts(Context context, Scriptable scope, File suite)
    throws IOException
  {
    Set<String> executed = new HashSet<String>();
    Set<String> toExecute = new LinkedHashSet<String>();



    toExecute.addAll(Arrays.asList(RhinoHelper.execStringArrayFunction(LOCATE_SCRIPTS_FUNCTION, "locate scripts", context, scope)));

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

  @Override
  protected void generateHumanReadableReport(Context context, Scriptable scope, File suite, String suiteName)
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

  @Override
  protected SuiteReport parseSuiteReport(Context context, Scriptable scope)
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

}
