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

/**
 * <p>The theta package contains all the sketch classes that are members of the 
 * <a href="{@docRoot}/resources/dictionary.html#thetaSketch">Theta Sketch Framework</a>.  
 * The basic sketching functionality in this package is also 
 * accessible from Hadoop Pig UDFs found in the <i>sketches-pig</i> repository, 
 * and from Hadoop Hive UADFs and UDFs found in the <i>sketches-hive</i> repository.
 * </p>
 * <h3>Simple Java Example</h3>
 * Note: The complete example code can be found in the parallel package under src/test/java and 
 * with the class name "ExamplesTest.java".
<pre>
  public void SimpleCountingSketch() {
    int k = 4096;
    int u = 1000000;
    
    UpdateSketch sketch = UpdateSketch.builder().build(k);
    for (int i = 0; i &lt; u; i++) {
      sketch.update(i);
    }
    
    println(sketch.toString());
  }

### HeapQuickSelectSketch SUMMARY: 
   Nominal Entries (k)     : 4096
   Estimate                : 1002714.745231455
   Upper Bound, 95% conf   : 1027777.3354974985
   Lower Bound, 95% conf   : 978261.4472857157
   p                       : 1.0
   Theta (double)          : 0.00654223948655085
   Theta (long)            : 60341508738660257
   Theta (long, hex        : 00d66048519437a1
   EstMode?                : true
   Empty?                  : false
   Resize Factor           : 8
   Array Size Entries      : 8192
   Retained Entries        : 6560
   Update Seed             : 9001
   Seed Hash               : ffff93cc
### END SKETCH SUMMARY
</pre>
 *
 * @author Lee Rhodes
 */
package org.apache.datasketches.theta;
