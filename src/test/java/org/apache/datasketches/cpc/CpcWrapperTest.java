/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.cpc;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.PrintStream;

import org.testng.annotations.Test;

import org.apache.datasketches.memory.Memory;
import org.apache.datasketches.Family;

/**
 * @author Lee Rhodes
 */
@SuppressWarnings("javadoc")
public class CpcWrapperTest {
  static PrintStream ps = System.out;

  @SuppressWarnings("unused")
  @Test
  public void check() {
    int lgK = 10;
    CpcSketch sk1 = new CpcSketch(lgK);
    CpcSketch sk2 = new CpcSketch(lgK);
    CpcSketch skD = new CpcSketch(lgK);
    double dEst = skD.getEstimate();
    double dlb = skD.getLowerBound(2);
    double dub = skD.getUpperBound(2);

    int n = 100000;
    for (int i = 0; i < n; i++) {
      sk1.update(i);
      sk2.update(i + n);
      skD.update(i);
      skD.update(i + n);
    }
    byte[] concatArr = skD.toByteArray();

    CpcUnion union = new CpcUnion(lgK);
    CpcSketch result = union.getResult();
    double uEst = result.getEstimate();
    double ulb = result.getLowerBound(2);
    double uub = result.getUpperBound(2);
    union.update(sk1);
    union.update(sk2);
    CpcSketch merged = union.getResult();
    byte[] mergedArr = merged.toByteArray();

    Memory concatMem = Memory.wrap(concatArr);
    CpcWrapper concatSk = new CpcWrapper(concatMem);
    assertEquals(concatSk.getLgK(), lgK);

    printf("              %12s %12s %12s\n", "Lb", "Est", "Ub");
    double ccEst = concatSk.getEstimate();
    double ccLb = concatSk.getLowerBound(2);
    double ccUb = concatSk.getUpperBound(2);
    printf("Concatenated: %12.0f %12.0f %12.0f\n", ccLb, ccEst, ccUb);

    //Memory mergedMem = Memory.wrap(mergedArr);
    CpcWrapper mergedSk = new CpcWrapper(mergedArr);
    double mEst = mergedSk.getEstimate();
    double mLb = mergedSk.getLowerBound(2);
    double mUb = mergedSk.getUpperBound(2);
    printf("Merged:       %12.0f %12.0f %12.0f\n", mLb, mEst, mUb);
    assertEquals(Family.CPC, CpcWrapper.getFamily());
  }

  @SuppressWarnings("unused")
  @Test
  public void checkIsCompressed() {
    CpcSketch sk = new CpcSketch(10);
    byte[] byteArr = sk.toByteArray();
    byteArr[5] &= (byte) -3;
    try {
      CpcWrapper wrapper = new CpcWrapper(Memory.wrap(byteArr));
      fail();
    } catch (AssertionError e) {}
  }

  /**
   * @param format the string to print
   * @param args the arguments
   */
  static void printf(String format, Object... args) {
    //ps.printf(format, args); //disable here
  }

  /**
   * @param s the string to print
   */
  static void println(String s) {
    //ps.println(s);  //disable here
  }

}
