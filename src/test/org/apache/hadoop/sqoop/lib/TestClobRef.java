/**
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.sqoop.lib;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.sqoop.io.LobFile;

/**
 * Test parsing of ClobRef objects.
 */
public class TestClobRef extends TestCase {

  public void testEmptyStr() {
    ClobRef r = ClobRef.parse("");
    assertFalse(r.isExternal());
    assertEquals("", r.toString());
  }

  public void testInline() throws IOException {
    ClobRef r = ClobRef.parse("foo");
    assertFalse(r.isExternal());
    assertEquals("foo", r.toString());

    Reader reader = r.getDataStream(null, null);
    assertNotNull(reader);
    char [] buf = new char[4096];
    int chars = reader.read(buf, 0, 4096);
    reader.close();

    String str = new String(buf, 0, chars);
    assertEquals("foo", str);
  }

  public void testEmptyFile() {
    ClobRef r = ClobRef.parse("externalLob()");
    assertFalse(r.isExternal());
    assertEquals("externalLob()", r.toString());

    r = ClobRef.parse("externalLob(lf,,0,0)");
    assertTrue(r.isExternal());
    assertEquals("externalLob(lf,,0,0)", r.toString());
  }

  public void testInlineNearMatch() {
    ClobRef r = ClobRef.parse("externalLob(foo)bar");
    assertFalse(r.isExternal());
    assertEquals("externalLob(foo)bar", r.toString());

    r = ClobRef.parse("externalLob(foo)");
    assertFalse(r.isExternal());
    assertEquals("externalLob(foo)", r.getData());

    r = ClobRef.parse("externalLob(lf,foo)");
    assertFalse(r.isExternal());
    assertEquals("externalLob(lf,foo)", r.getData());

    r = ClobRef.parse("externalLob(lf,foo,1,2)x");
    assertFalse(r.isExternal());
    assertEquals("externalLob(lf,foo,1,2)x", r.getData());
  }

  public void testExternal() throws IOException {
    final String DATA = "This is the clob data!";
    final String FILENAME = "clobdata";

    doExternalTest(DATA, FILENAME);
  }

  public void testExternalSubdir() throws IOException {
    final String DATA = "This is the clob data!";
    final String FILENAME = "_lob/clobdata";

    try {
      doExternalTest(DATA, FILENAME);
    } finally {
      // remove dir we made.
      Configuration conf = new Configuration();
      FileSystem fs = FileSystem.getLocal(conf);
      String tmpDir = System.getProperty("test.build.data", "/tmp/");
      Path lobDir = new Path(new Path(tmpDir), "_lob");
      fs.delete(lobDir, false);
    }
  }

  private void doExternalTest(final String DATA, final String FILENAME)
      throws IOException {

    Configuration conf = new Configuration();
    conf.set("fs.defaultFS", "file:///");
    FileSystem fs = FileSystem.get(conf);
    String tmpDir = System.getProperty("test.build.data", "/tmp/");

    Path tmpPath = new Path(tmpDir);
    Path clobFile = new Path(tmpPath, FILENAME);

    // make any necessary parent dirs.
    Path clobParent = clobFile.getParent();
    if (!fs.exists(clobParent)) {
      fs.mkdirs(clobParent);
    }

    LobFile.Writer lw = LobFile.create(clobFile, conf, true);
    try {
      long off = lw.tell();
      long len = DATA.length();
      Writer w = lw.writeClobRecord(len);
      w.append(DATA);
      w.close();
      lw.close();

      String refString = "externalLob(lf," + FILENAME 
                + "," + off + "," + len + ")";
      ClobRef clob = ClobRef.parse(refString);
      assertTrue(clob.isExternal());
      assertEquals(refString, clob.toString());
      Reader r = clob.getDataStream(conf, tmpPath);
      assertNotNull(r);

      char [] buf = new char[4096];
      int chars = r.read(buf, 0, 4096);
      r.close();

      String str = new String(buf, 0, chars);
      assertEquals(DATA, str);
    } finally {
      fs.delete(clobFile, false);
    }
  }
}

