/*
 * Copyright (c) 2007-2012 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cascading.lingual.common;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import com.google.common.base.Throwables;

/**
 *
 */
public abstract class Main<O extends Options>
  {
  protected final PrintStream outPrintStream;
  protected final PrintStream errPrintStream;
  protected final Properties properties;

  private String[] args;

  private O options;

  protected Main()
    {
    this.outPrintStream = System.out;
    this.errPrintStream = System.err;
    this.properties = new Properties();
    }

  public Main( Properties properties )
    {
    this.outPrintStream = System.out;
    this.errPrintStream = System.err;
    this.properties = properties;
    }

  protected Main( PrintStream outPrintStream, PrintStream errPrintStream )
    {
    this.outPrintStream = outPrintStream;
    this.errPrintStream = errPrintStream;
    this.properties = new Properties();
    }

  protected Main( PrintStream outPrintStream, PrintStream errPrintStream, Properties properties )
    {
    this.outPrintStream = outPrintStream;
    this.errPrintStream = errPrintStream;
    this.properties = properties;
    }

  public O getOptions()
    {
    return options;
    }

  public Printer getPrinter()
    {
    return new Printer( getOutPrintStream() );
    }

  protected boolean printUsage()
    {
    if( !options.isHelp() )
      return false;

    options.printUsage( getOutPrintStream() );

    return true;
    }

  protected boolean printVersion()
    {
    if( !options.isVersion() )
      return false;

    options.printVersion( getOutPrintStream() );

    return true;
    }

  protected abstract boolean handle() throws IOException;

  public boolean parse( String... args ) throws IOException
    {
    this.args = args;

    // reset instance with new options
    this.options = createOptions();

    return this.options.parse( getErrPrintStream(), args );
    }

  protected abstract O createOptions();

  public PrintStream getOutPrintStream()
    {
    return outPrintStream;
    }

  public PrintStream getErrPrintStream()
    {
    return errPrintStream;
    }

  protected void setVerbose()
    {
    if( getOptions().isVerbose() )
      setLogLevel( getOptions().getVerbose() );
    else
      setLogLevel( "off" );
    }

  public static void setLogLevel( String level )
    {
    org.apache.log4j.Logger.getLogger( "cascading" ).setLevel( org.apache.log4j.Level.toLevel( level ) );
    }

  protected void printFailure( PrintStream errPrintStream, Throwable throwable )
    {
    errPrintStream.println( "command failed with: " + throwable.getMessage() );

    Throwable cause = Throwables.getRootCause( throwable );

    if( cause != null )
      {
      errPrintStream.println( "with cause: " + cause.getClass() );

      if( cause.getMessage() != null )
        errPrintStream.println( "          : " + cause.getMessage() );

      errPrintStream.println( Throwables.getStackTraceAsString( cause ) );
      }
    }
  }
