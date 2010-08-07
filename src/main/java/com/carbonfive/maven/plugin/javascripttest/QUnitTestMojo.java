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
import java.io.FileWriter;
import java.io.IOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;


/**
 * @component
 * @goal qunit-test
 * @phase test
 */
public class QUnitTestMojo extends AbstractRhinoTestMojo {

	@Override
	protected void runSuite(Context context, Scriptable scope, File suite) throws Exception {
		RhinoHelper.execClasspathScript(context, scope, "jquery.js");
		RhinoHelper.execClasspathScript(context, scope, "qunit.js");
		
		RhinoHelper.execClasspathScript(context, scope, "qunit-runner.js");

		String code = "window.location = \"" + suite.getAbsolutePath() + "\";";

		RhinoHelper.exec( code, "suite.html", context, scope );

        //importScripts(context, scope, suite);

        // Trigger test execution
        //RhinoHelper.exec( "jQuery(window).trigger('load');Envjs.wait();", "start", context, scope );
	}

	@Override
	protected SuiteReport parseSuiteReport(Context context, Scriptable scope) {
		SuiteReport r = new SuiteReport();

		r.addTest("Some Test", "");

		return r;
	}

	@Override
	protected void generateHumanReadableReport(Context context, Scriptable scope, File suite, String suiteName) throws IOException {
    File reportFile = new File( getBasedir(), "target/screw-unit/" + suiteName.replace(File.separator,"."));
    reportFile.getParentFile().mkdirs();
    FileWriter writer = new FileWriter( reportFile );
    writer.write("");
    writer.close();
	}


}
