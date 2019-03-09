/*
 * Copyright 2015-16, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.theta;

import static com.yahoo.sketches.QuickSelect.selectExcludingZeros;
import static com.yahoo.sketches.theta.CompactSketch.compactCache;
import static com.yahoo.sketches.theta.PreambleUtil.COMPACT_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.FAMILY_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.FLAGS_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.LG_ARR_LONGS_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.ORDERED_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.PREAMBLE_LONGS_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.READ_ONLY_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.RETAINED_ENTRIES_INT;
import static com.yahoo.sketches.theta.PreambleUtil.SEED_HASH_SHORT;
import static com.yahoo.sketches.theta.PreambleUtil.SER_VER_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.THETA_LONG;
import static com.yahoo.sketches.theta.PreambleUtil.UNION_THETA_LONG;
import static java.lang.Math.min;

import com.yahoo.memory.Memory;
import com.yahoo.memory.MemoryRequestServer;
import com.yahoo.memory.WritableMemory;
import com.yahoo.sketches.Family;
import com.yahoo.sketches.HashOperations;
import com.yahoo.sketches.ResizeFactor;
import com.yahoo.sketches.SketchesArgumentException;
import com.yahoo.sketches.Util;

/**
 * Shared code for the HeapUnion and DirectUnion implementations.
 *
 * @author Lee Rhodes
 * @author Kevin Lang
 */
final class UnionImpl extends Union {
  private final UpdateSketch gadget_;
  private final short seedHash_; //eliminates having to compute the seedHash on every update.
  private long unionThetaLong_; //when on-heap, this is the only copy

  private UnionImpl(final UpdateSketch gadget, final long seed) {
    gadget_ = gadget;
    seedHash_ = computeSeedHash(seed);
  }

  /**
   * Construct a new Union SetOperation on the java heap.
   * Called by SetOperationBuilder.
   *
   * @param lgNomLongs <a href="{@docRoot}/resources/dictionary.html#lgNomLogs">See lgNomLongs</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @param p <a href="{@docRoot}/resources/dictionary.html#p">See Sampling Probability, <i>p</i></a>
   * @param rf <a href="{@docRoot}/resources/dictionary.html#resizeFactor">See Resize Factor</a>
   * @return instance of this sketch
   */
  static UnionImpl initNewHeapInstance(final int lgNomLongs, final long seed, final float p,
      final ResizeFactor rf) {
    final UpdateSketch gadget = new HeapQuickSelectSketch(
        lgNomLongs, seed, p, rf, true); //create with UNION family
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = gadget.getThetaLong();
    return unionImpl;
  }

  /**
   * Construct a new Direct Union in the off-heap destination Memory.
   * Called by SetOperationBuilder.
   *
   * @param lgNomLongs <a href="{@docRoot}/resources/dictionary.html#lgNomLogs">See lgNomLongs</a>.
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @param p <a href="{@docRoot}/resources/dictionary.html#p">See Sampling Probability, <i>p</i></a>
   * @param rf <a href="{@docRoot}/resources/dictionary.html#resizeFactor">See Resize Factor</a>
   * @param memReqSvr a given instance of a MemoryRequestServer
   * @param dstMem the given Memory object destination. It will be cleared prior to use.
   * @return this class
   */
  static UnionImpl initNewDirectInstance(
      final int lgNomLongs,
      final long seed,
      final float p,
      final ResizeFactor rf,
      final MemoryRequestServer memReqSvr,
      final WritableMemory dstMem) {
    final UpdateSketch gadget = new DirectQuickSelectSketch(
        lgNomLongs, seed, p, rf, memReqSvr, dstMem, true); //create with UNION family
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = gadget.getThetaLong();
    dstMem.putLong(UNION_THETA_LONG, gadget.getThetaLong());
    return unionImpl;
  }

  /**
   * Heapify a Union from a Memory object containing data.
   * Called by SetOperation.
   * @param srcMem The source Memory object.
   * <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @return this class
   */
  static UnionImpl heapifyInstance(final Memory srcMem, final long seed) {
    Family.UNION.checkFamilyID(srcMem.getByte(FAMILY_BYTE));
    final UpdateSketch gadget = HeapQuickSelectSketch.heapifyInstance(srcMem, seed);
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = srcMem.getLong(UNION_THETA_LONG);
    return unionImpl;
  }

  /**
   * Fast-wrap a Union object around a Union Memory object containing data.
   * This does NO validity checking of the given Memory.
   * @param srcMem The source Memory object.
   * <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @return this class
   */
  static UnionImpl fastWrap(final Memory srcMem, final long seed) {
    Family.UNION.checkFamilyID(srcMem.getByte(FAMILY_BYTE));
    final UpdateSketch gadget = DirectQuickSelectSketchR.fastReadOnlyWrap(srcMem, seed);
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = srcMem.getLong(UNION_THETA_LONG);
    return unionImpl;
  }

  /**
   * Fast-wrap a Union object around a Union Memory object containing data.
   * This does NO validity checking of the given Memory.
   * @param srcMem The source Memory object.
   * <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @return this class
   */
  static UnionImpl fastWrap(final WritableMemory srcMem, final long seed) {
    Family.UNION.checkFamilyID(srcMem.getByte(FAMILY_BYTE));
    final UpdateSketch gadget = DirectQuickSelectSketch.fastWritableWrap(srcMem, seed);
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = srcMem.getLong(UNION_THETA_LONG);
    return unionImpl;
  }

  /**
   * Wrap a Union object around a Union Memory object containing data.
   * Called by SetOperation.
   * @param srcMem The source Memory object.
   * <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @return this class
   */
  static UnionImpl wrapInstance(final Memory srcMem, final long seed) {
    Family.UNION.checkFamilyID(srcMem.getByte(FAMILY_BYTE));
    final UpdateSketch gadget = DirectQuickSelectSketchR.readOnlyWrap(srcMem, seed);
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = srcMem.getLong(UNION_THETA_LONG);
    return unionImpl;
  }

  /**
   * Wrap a Union object around a Union Memory object containing data.
   * Called by SetOperation.
   * @param srcMem The source Memory object.
   * <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See seed</a>
   * @return this class
   */
  static UnionImpl wrapInstance(final WritableMemory srcMem, final long seed) {
    Family.UNION.checkFamilyID(srcMem.getByte(FAMILY_BYTE));
    final UpdateSketch gadget = DirectQuickSelectSketch.writableWrap(srcMem, seed);
    final UnionImpl unionImpl = new UnionImpl(gadget, seed);
    unionImpl.unionThetaLong_ = srcMem.getLong(UNION_THETA_LONG);
    return unionImpl;
  }

  @Override
  public CompactSketch getResult(final boolean dstOrdered, final WritableMemory dstMem) {
    final int gadgetCurCount = gadget_.getRetainedEntries(true);
    final int k = 1 << gadget_.getLgNomLongs();
    final long[] gadgetCacheCopy =
        (gadget_.isDirect()) ? gadget_.getCache() : gadget_.getCache().clone();

    //Pull back to k
    final long curGadgetThetaLong = gadget_.getThetaLong();
    final long adjGadgetThetaLong = (gadgetCurCount > k)
        ? selectExcludingZeros(gadgetCacheCopy, gadgetCurCount, k + 1) : curGadgetThetaLong;

    //Finalize Theta and curCount
    final long unionThetaLong = (gadget_.isDirect())
        ? gadget_.getMemory().getLong(UNION_THETA_LONG) : unionThetaLong_;

    final long minThetaLong = min(min(curGadgetThetaLong, adjGadgetThetaLong), unionThetaLong);
    final int curCountOut = (minThetaLong < curGadgetThetaLong)
        ? HashOperations.count(gadgetCacheCopy, minThetaLong)
        : gadgetCurCount;

    //Compact the cache
    final long[] compactCacheOut =
        compactCache(gadgetCacheCopy, curCountOut, minThetaLong, dstOrdered);
    final boolean empty = gadget_.isEmpty();
    return createCompactSketch(
        compactCacheOut, empty, seedHash_, curCountOut, minThetaLong, dstOrdered, dstMem);
  }

  @Override
  public CompactSketch getResult() {
    return getResult(true, null);
  }

  @Override
  public void reset() {
    gadget_.reset();
    unionThetaLong_ = gadget_.getThetaLong();
  }

  @Override
  public byte[] toByteArray() {
    final byte[] gadgetByteArr = gadget_.toByteArray();
    final WritableMemory mem = WritableMemory.wrap(gadgetByteArr);
    mem.putLong(UNION_THETA_LONG, unionThetaLong_); // union theta
    return gadgetByteArr;
  }

  @Override
  public boolean isSameResource(final Memory that) {
    return (gadget_ instanceof DirectQuickSelectSketchR)
        ? gadget_.getMemory().isSameResource(that) : false;
  }

  @Override
  public void update(final Sketch sketchIn) { //Only valid for theta Sketches using SerVer = 3
    //UNION Empty Rule: AND the empty states. This does not require separate treatment.

    if (sketchIn == null) {
      //null is interpreted as (Theta = 1.0, count = 0, empty = T).  Nothing changes
      return;
    }
    Util.checkSeedHashes(seedHash_, sketchIn.getSeedHash());
    Sketch.checkSketchAndMemoryFlags(sketchIn);


    final long thetaLongIn = sketchIn.getThetaLong();
    unionThetaLong_ = min(unionThetaLong_, thetaLongIn); //Theta rule with incoming
    final int curCountIn = sketchIn.getRetainedEntries(true);

    if (sketchIn.isOrdered()) { //Only true if Compact. Use early stop
      //Ordered, thus compact
      if (sketchIn.isDirect()) {
        final Memory skMem = ((CompactSketch) sketchIn).getMemory();
        final int preambleLongs = skMem.getByte(PREAMBLE_LONGS_BYTE) & 0X3F;
        for (int i = 0; i < curCountIn; i++ ) {
          final int offsetBytes = (preambleLongs + i) << 3;
          final long hashIn = skMem.getLong(offsetBytes);
          if (hashIn >= unionThetaLong_) { break; } // "early stop"
          gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
        }
      }
      else { //sketchIn is on the Java Heap or has array
        final long[] cacheIn = sketchIn.getCache(); //not a copy!
        for (int i = 0; i < curCountIn; i++ ) {
          final long hashIn = cacheIn[i];
          if (hashIn >= unionThetaLong_) { break; } // "early stop"
          gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
        }
      }
    } //End ordered, compact
    else { //either not-ordered compact or Hash Table form. A HT may have dirty values.
      final long[] cacheIn = sketchIn.getCache(); //if off-heap this will be a copy
      final int arrLongs = cacheIn.length;
      for (int i = 0, c = 0; (i < arrLongs) && (c < curCountIn); i++ ) {
        final long hashIn = cacheIn[i];
        if ((hashIn <= 0L) || (hashIn >= unionThetaLong_)) { continue; } //rejects dirty values
        gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
        c++; //insures against invalid state inside the incoming sketch
      }
    }
    unionThetaLong_ = min(unionThetaLong_, gadget_.getThetaLong()); //Theta rule with gadget
    if (gadget_.isDirect()) {
      ((WritableMemory)gadget_.getMemory()).putLong(UNION_THETA_LONG, unionThetaLong_);
    }
  }

  @Override
  public void update(final Memory skMem) {
    //UNION Empty Rule: AND the empty states
    if (skMem == null) { return; }
    final int cap = (int)skMem.getCapacity();
    final int fam = skMem.getByte(FAMILY_BYTE);
    final int serVer = skMem.getByte(SER_VER_BYTE);
    if (serVer == 1) { //older SetSketch, which is compact and ordered
      if (fam != 3) { //the original SetSketch
        throw new SketchesArgumentException(
            "Family must be old SET_SKETCH: " + Family.idToFamily(fam));
      }
      if (cap <= 24) { return; } //empty
      processVer1(skMem);
    }
    else if (serVer == 2) { //older SetSketch, which is compact and ordered
      if (fam != 3) { //the original SetSketch
        throw new SketchesArgumentException(
            "Family must be old SET_SKETCH: " + Family.idToFamily(fam));
      }
      if (cap <= 8) { return; } //empty
      processVer2(skMem);
    }
    else if (serVer == 3) { //The OpenSource sketches
      if ((fam < 1) || (fam > 3)) {
        throw new SketchesArgumentException(
            "Family must be Alpha, QuickSelect, or Compact: " + Family.idToFamily(fam));
      }
      if (cap <= 8) { return; } //empty and Theta = 1.0
      processVer3(skMem);
    }
    else {
      throw new SketchesArgumentException("SerVer is unknown: " + serVer);
    }
  }

  @Override
  public void update(final long datum) {
    gadget_.update(datum);
  }

  @Override
  public void update(final double datum) {
    gadget_.update(datum);
  }

  @Override
  public void update(final String datum) {
    gadget_.update(datum);
  }

  @Override
  public void update(final byte[] data) {
    gadget_.update(data);
  }

  @Override
  public void update(final char[] data) {
    gadget_.update(data);
  }

  @Override
  public void update(final int[] data) {
    gadget_.update(data);
  }

  @Override
  public void update(final long[] data) {
    gadget_.update(data);
  }

  //Restricted

  @Override
  long[] getCache() {
    return gadget_.getCache();
  }

  @Override
  int getRetainedEntries(final boolean valid) {
    return gadget_.getRetainedEntries(valid);
  }

  @Override
  short getSeedHash() {
    return gadget_.getSeedHash();
  }

  @Override
  long getThetaLong() {
    return min(unionThetaLong_, gadget_.getThetaLong());
  }

  @Override
  boolean isEmpty() {
    return gadget_.isEmpty();
  }

  //no seedHash, assumes given seed is correct. No p, no empty flag, no concept of direct
  // can only be compact, ordered, size > 24
  private void processVer1(final Memory skMem) {
    final long thetaLongIn = skMem.getLong(THETA_LONG);
    unionThetaLong_ = min(unionThetaLong_, thetaLongIn); //Theta rule
    final int curCount = skMem.getInt(RETAINED_ENTRIES_INT);
    final int preLongs = 3;
    for (int i = 0; i < curCount; i++ ) {
      final int offsetBytes = (preLongs + i) << 3;
      final long hashIn = skMem.getLong(offsetBytes);
      if (hashIn >= unionThetaLong_) { break; } // "early stop"
      gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
    }
    unionThetaLong_ = min(unionThetaLong_, gadget_.getThetaLong());
    if (gadget_.isDirect()) {
      ((WritableMemory)gadget_.getMemory()).putLong(UNION_THETA_LONG, unionThetaLong_);
    }
  }

  //has seedHash and p, could have 0 entries & theta,
  // can only be compact, ordered, size >= 8
  private void processVer2(final Memory skMem) {
    Util.checkSeedHashes(seedHash_, skMem.getShort(SEED_HASH_SHORT));
    final int preLongs = skMem.getByte(PREAMBLE_LONGS_BYTE) & 0X3F;
    final int curCount = skMem.getInt(RETAINED_ENTRIES_INT);
    final long thetaLongIn;
    if (preLongs == 1) {
      return;
    }
    if (preLongs == 2) {
      assert curCount > 0;
      thetaLongIn = Long.MAX_VALUE;
    } else { //prelongs == 3, curCount may be 0 (e.g., from intersection)
      thetaLongIn = skMem.getLong(THETA_LONG);
    }
    unionThetaLong_ = min(unionThetaLong_, thetaLongIn); //Theta rule
    for (int i = 0; i < curCount; i++ ) {
      final int offsetBytes = (preLongs + i) << 3;
      final long hashIn = skMem.getLong(offsetBytes);
      if (hashIn >= unionThetaLong_) { break; } // "early stop"
      gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
    }
    unionThetaLong_ = min(unionThetaLong_, gadget_.getThetaLong());
    if (gadget_.isDirect()) {
      ((WritableMemory)gadget_.getMemory()).putLong(UNION_THETA_LONG, unionThetaLong_);
    }
  }

  //has seedHash, p, could have 0 entries & theta,
  // could be unordered, ordered, compact, or not, size >= 8
  private void processVer3(final Memory skMem) {
    Util.checkSeedHashes(seedHash_, skMem.getShort(SEED_HASH_SHORT));
    final int preLongs = skMem.getByte(PREAMBLE_LONGS_BYTE) & 0X3F;
    final int curCount;
    final long thetaLongIn;
    if (preLongs == 1) { //SingleItemSketch if not empty, Read-Only, Compact and Ordered
      final int flags = skMem.getByte(FLAGS_BYTE);
      if (flags == (READ_ONLY_FLAG_MASK | COMPACT_FLAG_MASK | ORDERED_FLAG_MASK)) {
        curCount = 1;
        thetaLongIn = Long.MAX_VALUE;
      } else {
        return; //otherwise an empty sketch
      }
    }
    else if (preLongs == 2) { //curCount has to be > 0 and exact mode. Cannot be from intersection.
      curCount = skMem.getInt(RETAINED_ENTRIES_INT);
      assert curCount > 0;
      thetaLongIn = Long.MAX_VALUE;
    }
    else { //prelongs == 3, curCount may be 0 (e.g., from intersection).
      curCount = skMem.getInt(RETAINED_ENTRIES_INT);
      assert curCount > 0;
      thetaLongIn = skMem.getLong(THETA_LONG);
    }
    unionThetaLong_ = min(unionThetaLong_, thetaLongIn); //Theta rule
    final boolean ordered = (skMem.getByte(FLAGS_BYTE) & ORDERED_FLAG_MASK) != 0;
    if (ordered) { //must be compact
      for (int i = 0; i < curCount; i++ ) {
        final int offsetBytes = (preLongs + i) << 3;
        final long hashIn = skMem.getLong(offsetBytes);
        if (hashIn >= unionThetaLong_) { break; } // "early stop"
        gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
      }
    }
    else { //not-ordered, could be compact or hash-table form
      final boolean compact = (skMem.getByte(FLAGS_BYTE) & COMPACT_FLAG_MASK) != 0;
      final int size = (compact) ? curCount : 1 << skMem.getByte(LG_ARR_LONGS_BYTE);
      for (int i = 0; i < size; i++ ) {
        final int offsetBytes = (preLongs + i) << 3;
        final long hashIn = skMem.getLong(offsetBytes);
        if ((hashIn <= 0L) || (hashIn >= unionThetaLong_)) { continue; }
        gadget_.hashUpdate(hashIn); //backdoor update, hash function is bypassed
      }
    }
    unionThetaLong_ = min(unionThetaLong_, gadget_.getThetaLong()); //sync thetaLongs
    if (gadget_.isDirect()) {
      ((WritableMemory)gadget_.getMemory()).putLong(UNION_THETA_LONG, unionThetaLong_);
    }
  }

}
