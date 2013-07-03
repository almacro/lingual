/*
 * Copyright (c) 2007-2013 Concurrent, Inc. All Rights Reserved.
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

package cascading.lingual.catalog;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class ProviderJarCLITest extends CLIPlatformTestCase
  {
  @Ignore
  @Test
  public void testProviderWithSQLLine() throws IOException
    {
    createProviderJar();

    initCatalog();

    catalog( "--provider", "--add", PROVIDER_JAR_PATH );

    SchemaCatalog schemaCatalog = getSchemaCatalog();
    Format format = Format.getFormat( "tpsv" );
    ProviderDef providerDef = schemaCatalog.findProviderDefFor( null, format );
    assertNotNull( "provider not registered to format", providerDef );

    Protocol protocol = Protocol.getProtocol( getPlatformName().equals( "hadoop" ) ? "hdfs" : "file" );
    schemaCatalog = getSchemaCatalog();
    providerDef = schemaCatalog.findProviderDefFor( null, protocol );
    assertNotNull( "provider not registered to protocol", providerDef );

    catalog( "--schema", "example", "--add" );
    catalog( "--schema", "example", "--table", "products", "--add", SIMPLE_PRODUCTS_TABLE );

    // now confirm that reading this from sqlline
    assertTrue( "unable to run query", shell( "--sql", PROVIDER_SQL_SELECT_FILE, "--platform", getPlatformName() ) );
    }

  }