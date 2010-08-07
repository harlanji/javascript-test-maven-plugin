package com.carbonfive.maven.plugin.javascripttest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;

import java.io.*;

public class RhinoHelper
{
  public static String execStringFunction(String function, String name, Context context, Scriptable scope, Object... args )
  {
    Function fn = context.compileFunction(scope, "function() {" + function + "}", name, 1, null);
    return (String) fn.call( context, scope, scope, args );
  }

  public static String[] execStringArrayFunction(String function, String name, Context context, Scriptable scope, Object... args )
  {
    NativeArray results = execNativeArrayFunction(function, name, context, scope, args);
    if ( results == null )
      return null;

    String[] strArray = new String[ (int) results.getLength() ];
    for ( int i=0; i < strArray.length; i++ )
        strArray[i] = (String) results.get(i,results);
    return strArray;
  }

  public static NativeArray execNativeArrayFunction(String function, String name, Context context, Scriptable scope, Object... args)
  {
    Function fn = context.compileFunction(scope, "function() {" + function + "}", name, 1, null);
    return (NativeArray) fn.call(context, scope, scope, args);
  }

  public static void exec(String script, String name, Context context, Scriptable scope)
  {
    context.compileString(script, name, 1, null).exec(context,scope);
  }

  public static void execClasspathScript(Context rhinoContext, Scriptable scope, String path)
    throws IOException
  {
    Reader in = new InputStreamReader(rhinoContext.getClass().getClassLoader().getResourceAsStream(path));
    compileAndExec(in, "classpath:" + path, rhinoContext, scope);
    in.close();
  }

  public static void execScriptFile(Context rhinoContext, Scriptable scope, File file)
    throws IOException
  {
    Reader in = new FileReader(file);
    compileAndExec(in, file.getName(), rhinoContext, scope);
    in.close();
  }

  public static void compileAndExec(Reader in, String name, Context rhinoContext, Scriptable scope)
    throws IOException
  {
    rhinoContext.compileReader(in, name, 1, null).exec(rhinoContext,scope);
  }

}
