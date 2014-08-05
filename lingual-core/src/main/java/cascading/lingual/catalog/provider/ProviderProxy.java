/*
 * Copyright (c) 2007-2014 Concurrent, Inc. All Rights Reserved.
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

package cascading.lingual.catalog.provider;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import cascading.bind.catalog.Resource;
import cascading.bind.catalog.Stereotype;
import cascading.lingual.catalog.Format;
import cascading.lingual.catalog.Protocol;
import cascading.lingual.catalog.ProviderDef;
import cascading.lingual.platform.PlatformBroker;
import cascading.lingual.util.Reflection;
import cascading.scheme.Scheme;
import cascading.tap.SinkMode;
import cascading.tap.Tap;
import cascading.tap.type.FileType;
import com.google.common.base.Throwables;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderProxy
  {
  private static final Logger LOG = LoggerFactory.getLogger( ProviderProxy.class );
  private final PlatformBroker platformBroker;
  private final ProviderDef providerDef;

  private ProviderFactory factoryObject;
  private ClassLoader classLoader = null;

  public ProviderProxy( PlatformBroker platformBroker, ProviderDef providerDef )
    {
    this.platformBroker = platformBroker;
    this.providerDef = providerDef;
    this.factoryObject = instantiateFactory();
    }

  private ProviderFactory instantiateFactory()
    {
    String factoryClassName = providerDef.getFactoryClassName();

    if( factoryClassName == null )
      throw new IllegalStateException( "no factory class found" );

    Class factoryClass = loadClass( factoryClassName, providerDef.getIdentifier() );

    if( factoryClass == null )
      throw new RuntimeException( "unable to load factory class: " + factoryClassName );

    return createProviderFactoryProxy( factoryClass );
    }

  private ProviderFactory createProviderFactoryProxy( Class factoryClass )
    {
    ProxyFactory proxyFactory = new ProxyFactory();

    proxyFactory.setSuperclass( factoryClass );
    proxyFactory.setInterfaces( new Class[]{ProviderFactory.class} );

    try
      {
      return (ProviderFactory) proxyFactory.create( new Class[]{}, new Object[]{}, getProviderFactoryMethodHandler() );
      }
    catch( Exception exception )
      {
      throw new RuntimeException( "failed to create proxy", exception );
      }
    }

  private MethodHandler getProviderFactoryMethodHandler()
    {
    return new ProviderFactoryHandler();
    }

  public Tap createTapProxy( Tap parentTap )
    {
    // Tap and Scheme can come from different providers having different classloaders. E.g. in a format only provider,
    // we load the Scheme from one jar with one classloader, but the tap from a different jar with a different
    // classloader. The DelegatingClassLoader combines both classloaders and tries them in order. This is only relevant for
    // queries that are not running as a flow, but locally like 'select * from table' or 'insert into table values('foo', 42').
    ClassLoader schemeClassLoader = parentTap.getScheme().getClass().getClassLoader();
    if ( !schemeClassLoader.equals( classLoader ) )
      classLoader = new DelegatingClassLoader( schemeClassLoader, classLoader );
    if( parentTap instanceof FileType )
      return createProxy( parentTap, Tap.class, FileType.class );

    return createProxy( parentTap, Tap.class );
    }

  public Scheme createSchemeProxy( Scheme parentScheme )
    {
    return createProxy( parentScheme, Scheme.class );
    }

  private <T> T createProxy( T parentTap, Class<T> type, Class... interfaces )
    {
    ProxyFactory proxyFactory = new ProxyFactory();

    proxyFactory.setSuperclass( type );

    if( interfaces.length != 0 )
      proxyFactory.setInterfaces( interfaces );
    try
      {
      return (T) proxyFactory.create( new Class[]{}, new Object[]{}, getClassLoaderMethodHandler( parentTap ) );
      }
    catch( Exception exception )
      {
      throw new RuntimeException( "failed to create proxy", exception );
      }
    }

  private MethodHandler getClassLoaderMethodHandler( Object parent )
    {
    return new ProxyClassLoaderHandler( classLoader, parent );
    }

  public String getDescription()
    {
    String description = factoryObject.getDescription();

    if( description == null )
      description = providerDef.getDescription();

    return description;
    }

  public Tap createTap( Resource<Protocol, Format, SinkMode> resource, Scheme scheme, Properties properties )
    {
    String identifier = resource.getIdentifier();
    Protocol protocol = resource.getProtocol();
    SinkMode mode = resource.getMode();

    Tap tap = null;
    try
      {
      LOG.info( "using " + getDescription() + " to create tap for {} with properties: {}", resource, properties );
      tap = factoryObject.createTap( protocol.toString(), scheme, identifier, mode, properties );

      if( tap == null )
        tap = factoryObject.createTap( scheme, identifier, mode, properties );

      if( tap == null )
        tap = factoryObject.createTap( scheme, identifier, properties );

      if( tap == null )
        tap = factoryObject.createTap( scheme, identifier, mode );
      }
    catch( Exception exception )
      {
      String errorMessage;
      if( exception.getClass() == InvocationTargetException.class )
        errorMessage = ( (InvocationTargetException) exception ).getTargetException().getMessage();
      else
        errorMessage = exception.getMessage();

      LOG.error( String.format( "factory %s failed to create tap: %s ", factoryObject.getClass().getName(), errorMessage ), exception );
      throw Throwables.propagate( exception );
      }
    return tap;
    }

  public Scheme createScheme( Stereotype<Protocol, Format> stereotype, Protocol protocol, Format format, Properties properties )
    {
    Scheme scheme = null;
    try
      {
      LOG.info( "using " + getDescription() + " to create scheme for stereotype {} with properties: {}", stereotype.getName(), properties );
      scheme = factoryObject.createScheme( protocol.toString(), format.toString(), stereotype.getFields(), properties );

      if( scheme == null )
        scheme = factoryObject.createScheme( format.toString(), stereotype.getFields(), properties );

      if( scheme == null )
        scheme = factoryObject.createScheme( stereotype.getFields(), properties );

      if( scheme == null )
        scheme = factoryObject.createScheme( stereotype.getFields() );
      }
    catch( Exception exception )
      {
      String errorMessage;
      if( exception.getClass() == InvocationTargetException.class )
        errorMessage = ( (InvocationTargetException) exception ).getTargetException().getMessage();
      else
        errorMessage = exception.getMessage();

      LOG.error( String.format( "factory %s failed to create scheme: %s ", factoryObject.getClass().getName(), errorMessage ), exception );
      throw Throwables.propagate( exception );
      }
    return scheme;
    }

  private Class loadClass( String className, String jarPath )
    {
    LOG.debug( "loading: {} from: {}", className, jarPath );

    if( jarPath == null ) // its a default factory
      {
      classLoader = Thread.currentThread().getContextClassLoader();
      return Reflection.loadClass( classLoader, className );
      }

    String qualifiedPath = platformBroker.makePath( platformBroker.getFullProviderPath(), jarPath );

    try
      {
      return platformBroker.loadClass( qualifiedPath, className );
      }
    finally
      {
      try
        {
        classLoader = platformBroker.getUrlClassLoader( qualifiedPath );
        }
      catch( Exception exception )
        {
        // do nothing
        }
      }
    }
  }
