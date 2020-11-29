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

package org.apache.datasketches.tuple.arrayofdoubles;

import static java.lang.Math.min;

import org.apache.datasketches.SketchesStateException;
import org.apache.datasketches.memory.WritableMemory;
import org.apache.datasketches.tuple.Util;

/**
 * Computes the intersection of two or more tuple sketches of type ArrayOfDoubles.
 * A new instance represents the Universal Set.
 * Every update() computes an intersection with the internal set
 * and can only reduce the internal set.
 */
public abstract class ArrayOfDoublesIntersection {

  final int numValues_;
  final long seed_;
  final short seedHash_;
  ArrayOfDoublesQuickSelectSketch sketch_;
  boolean isEmpty_;
  long theta_;
  boolean isFirstCall_;

  ArrayOfDoublesIntersection(final int numValues, final long seed) {
    numValues_ = numValues;
    seed_ = seed;
    seedHash_ = Util.computeSeedHash(seed);
    isEmpty_ = false;
    theta_ = Long.MAX_VALUE;
    isFirstCall_ = true;
  }

  /**
   * Updates the internal set by intersecting it with the given sketch.
   * @param sketchIn Input sketch to intersect with the internal set.
   * @param combiner Method of combining two arrays of double values
   */
  public void update(final ArrayOfDoublesSketch sketchIn, final ArrayOfDoublesCombiner combiner) {
    final boolean isFirstCall = isFirstCall_;
    isFirstCall_ = false;
    if (sketchIn == null) {
      isEmpty_ = true;
      sketch_ = null;
      return;
    }
    Util.checkSeedHashes(seedHash_, sketchIn.getSeedHash());
    theta_ = min(theta_, sketchIn.getThetaLong());
    isEmpty_ |= sketchIn.isEmpty();
    if (isEmpty_ || sketchIn.getRetainedEntries() == 0) {
      sketch_ = null;
      return;
    }
    if (isFirstCall) {
      sketch_ = createSketch(sketchIn.getRetainedEntries(), numValues_, seed_);
      final ArrayOfDoublesSketchIterator it = sketchIn.iterator();
      while (it.next()) {
        sketch_.insert(it.getKey(), it.getValues());
      }
    } else { //not the first call
      final int matchSize = min(sketch_.getRetainedEntries(), sketchIn.getRetainedEntries());
      final long[] matchKeys = new long[matchSize];
      final double[][] matchValues = new double[matchSize][];
      int matchCount = 0;
      final ArrayOfDoublesSketchIterator it = sketchIn.iterator();
      while (it.next()) {
        final double[] values = sketch_.find(it.getKey());
        if (values != null) {
          matchKeys[matchCount] = it.getKey();
          matchValues[matchCount] = combiner.combine(values, it.getValues());
          matchCount++;
        }
      }
      sketch_ = null;
      if (matchCount > 0) {
        sketch_ = createSketch(matchCount, numValues_, seed_);
        for (int i = 0; i < matchCount; i++) {
          sketch_.insert(matchKeys[i], matchValues[i]);
        }
      }
      if (sketch_ != null) {
        sketch_.setThetaLong(theta_);
        sketch_.setNotEmpty();
      }
    }
  }

  /**
   * Gets the internal set as an off-heap compact sketch using the given memory.
   * @param dstMem Memory for the compact sketch (can be null).
   * @return Result of the intersections so far as a compact sketch.
   */
  public ArrayOfDoublesCompactSketch getResult(final WritableMemory dstMem) {
    if (isFirstCall_) {
      throw new SketchesStateException(
          "getResult() with no intervening intersections is not a legal result.");
    }
    if (sketch_ == null) {
      return new HeapArrayOfDoublesCompactSketch(
          null, null, Long.MAX_VALUE, true, numValues_, seedHash_);
    }
    return sketch_.compact(dstMem);
  }

  /**
   * Gets the internal set as an on-heap compact sketch.
   * @return Result of the intersections so far as a compact sketch.
   */
  public ArrayOfDoublesCompactSketch getResult() {
    return getResult(null);
  }

  /**
   * Resets the internal set to the initial state, which represents the Universal Set
   */
  public void reset() {
    isEmpty_ = false;
    theta_ = Long.MAX_VALUE;
    sketch_ = null;
    isFirstCall_ = true;
  }

  abstract ArrayOfDoublesQuickSelectSketch createSketch(int size, int numValues, long seed);

}
