/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.core.iterators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.KeyExtent;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.thrift.IterInfo;
import org.apache.accumulo.core.iterators.conf.PerColumnIteratorConfig;
import org.apache.accumulo.core.iterators.user.VersioningIterator;
import org.apache.accumulo.start.classloader.AccumuloClassLoader;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.log4j.Logger;

@SuppressWarnings("deprecation")
public class IteratorUtil {
  
  private static final Logger log = Logger.getLogger(IteratorUtil.class);
  
  public static enum IteratorScope {
    majc, minc, scan;
  }
  
  public static class IterInfoComparator implements Comparator<IterInfo> {
    
    @Override
    public int compare(IterInfo o1, IterInfo o2) {
      return (o1.priority < o2.priority ? -1 : (o1.priority == o2.priority ? 0 : 1));
    }
    
  }
  
  /**
   * @deprecated since 1.4
   * @param aggregators
   * @return
   * 
   * @see {@link TableOperations#attachIterators(String, IteratorSetting)}
   */
  public static Map<String,String> generateAggTableProperties(List<? extends PerColumnIteratorConfig> aggregators) {
    
    Map<String,String> props = new TreeMap<String,String>();
    
    for (IteratorScope iterScope : IteratorScope.values()) {
      if (aggregators.size() > 0) {
        props.put(Property.TABLE_ITERATOR_PREFIX + iterScope.name() + ".agg", "10," + AggregatingIterator.class.getName());
      }
    }
    
    for (PerColumnIteratorConfig ac : aggregators) {
      for (IteratorScope iterScope : IteratorScope.values()) {
        props.put(Property.TABLE_ITERATOR_PREFIX + iterScope.name() + ".agg.opt." + ac.encodeColumns(), ac.getClassName());
      }
    }
    
    return props;
  }
  
  public static Map<String,String> generateInitialTableProperties() {
    TreeMap<String,String> props = new TreeMap<String,String>();
    
    for (IteratorScope iterScope : IteratorScope.values()) {
      props.put(Property.TABLE_ITERATOR_PREFIX + iterScope.name() + ".vers", "20," + VersioningIterator.class.getName());
      props.put(Property.TABLE_ITERATOR_PREFIX + iterScope.name() + ".vers.opt.maxVersions", "1");
    }
    return props;
  }
  
  public static int getMaxPriority(IteratorScope scope, AccumuloConfiguration conf) {
    List<IterInfo> iters = new ArrayList<IterInfo>();
    parseIterConf(scope, iters, new HashMap<String,Map<String,String>>(), conf);
    
    int max = 0;
    
    for (IterInfo iterInfo : iters) {
      if (iterInfo.priority > max)
        max = iterInfo.priority;
    }
    
    return max;
  }
  
  private static void parseIterConf(IteratorScope scope, List<IterInfo> iters, Map<String,Map<String,String>> allOptions, AccumuloConfiguration conf) {
    for (Entry<String,String> entry : conf) {
      if (entry.getKey().startsWith(Property.TABLE_ITERATOR_PREFIX.getKey())) {
        
        String suffix = entry.getKey().substring(Property.TABLE_ITERATOR_PREFIX.getKey().length());
        String suffixSplit[] = suffix.split("\\.", 4);
        
        if (!suffixSplit[0].equals(scope.name())) {
          
          // do a sanity check to see if this is a valid scope
          boolean found = false;
          IteratorScope[] scopes = IteratorScope.values();
          for (IteratorScope s : scopes) {
            found = found || suffixSplit[0].equals(s.name());
          }
          
          if (!found) {
            log.warn("Option contains unknown scope: " + entry.getKey());
          }
          
          continue;
        }
        
        if (suffixSplit.length == 2) {
          String sa[] = entry.getValue().split(",");
          int prio = Integer.parseInt(sa[0]);
          String className = sa[1];
          iters.add(new IterInfo(prio, className, suffixSplit[1]));
        } else if (suffixSplit.length == 4 && suffixSplit[2].equals("opt")) {
          String iterName = suffixSplit[1];
          String optName = suffixSplit[3];
          
          Map<String,String> options = allOptions.get(iterName);
          if (options == null) {
            options = new HashMap<String,String>();
            allOptions.put(iterName, options);
          }
          
          options.put(optName, entry.getValue());
          
        } else {
          log.warn("Unrecognizable option: " + entry.getKey());
        }
      }
    }
    
    Collections.sort(iters, new IterInfoComparator());
  }
  
  public static String findIterator(IteratorScope scope, String className, AccumuloConfiguration conf, Map<String,String> opts) {
    ArrayList<IterInfo> iters = new ArrayList<IterInfo>();
    Map<String,Map<String,String>> allOptions = new HashMap<String,Map<String,String>>();
    
    parseIterConf(scope, iters, allOptions, conf);
    
    for (IterInfo iterInfo : iters)
      if (iterInfo.className.equals(className)) {
        Map<String,String> tmpOpts = allOptions.get(iterInfo.iterName);
        if (tmpOpts != null) {
          opts.putAll(tmpOpts);
        }
        return iterInfo.iterName;
      }
    
    return null;
  }
  
  public static <K extends WritableComparable<?>,V extends Writable> SortedKeyValueIterator<K,V> loadIterators(IteratorScope scope,
      SortedKeyValueIterator<K,V> source, KeyExtent extent, AccumuloConfiguration conf, IteratorEnvironment env) throws IOException {
    List<IterInfo> emptyList = Collections.emptyList();
    Map<String,Map<String,String>> emptyMap = Collections.emptyMap();
    return loadIterators(scope, source, extent, conf, emptyList, emptyMap, env);
  }
  
  public static <K extends WritableComparable<?>,V extends Writable> SortedKeyValueIterator<K,V> loadIterators(IteratorScope scope,
      SortedKeyValueIterator<K,V> source, KeyExtent extent, AccumuloConfiguration conf, List<IterInfo> ssiList, Map<String,Map<String,String>> ssio,
      IteratorEnvironment env) throws IOException {
    List<IterInfo> iters = new ArrayList<IterInfo>(ssiList);
    Map<String,Map<String,String>> allOptions = new HashMap<String,Map<String,String>>();
    
    parseIterConf(scope, iters, allOptions, conf);
    
    for (Entry<String,Map<String,String>> entry : ssio.entrySet()) {
      if (entry.getValue() == null)
        continue;
      Map<String,String> options = allOptions.get(entry.getKey());
      if (options == null) {
        allOptions.put(entry.getKey(), entry.getValue());
      } else {
        options.putAll(entry.getValue());
      }
    }
    
    return loadIterators(source, iters, allOptions, env);
  }
  
  public static <K extends WritableComparable<?>,V extends Writable> SortedKeyValueIterator<K,V> loadIterators(SortedKeyValueIterator<K,V> source,
      Collection<IterInfo> iters, Map<String,Map<String,String>> iterOpts, IteratorEnvironment env) throws IOException {
    SortedKeyValueIterator<K,V> prev = source;
    
    try {
      for (IterInfo iterInfo : iters) {
        @SuppressWarnings("unchecked")
        Class<? extends SortedKeyValueIterator<K,V>> clazz = (Class<? extends SortedKeyValueIterator<K,V>>) AccumuloClassLoader.loadClass(iterInfo.className,
            SortedKeyValueIterator.class);
        SortedKeyValueIterator<K,V> skvi = clazz.newInstance();
        
        Map<String,String> options = iterOpts.get(iterInfo.iterName);
        
        if (options == null)
          options = Collections.emptyMap();
        
        skvi.init(prev, options, env);
        prev = skvi;
      }
    } catch (ClassNotFoundException e) {
      log.error(e.toString());
      throw new IOException(e);
    } catch (InstantiationException e) {
      log.error(e.toString());
      throw new IOException(e);
    } catch (IllegalAccessException e) {
      log.error(e.toString());
      throw new IOException(e);
    }
    return prev;
  }
  
  public static Range maximizeStartKeyTimeStamp(Range range) {
    Range seekRange = range;
    
    if (range.getStartKey() != null && range.getStartKey().getTimestamp() != Long.MAX_VALUE) {
      Key seekKey = new Key(seekRange.getStartKey());
      seekKey.setTimestamp(Long.MAX_VALUE);
      seekRange = new Range(seekKey, true, range.getEndKey(), range.isEndKeyInclusive());
    }
    
    return seekRange;
  }
  
  public static Range minimizeEndKeyTimeStamp(Range range) {
    Range seekRange = range;
    
    if (range.getEndKey() != null && range.getEndKey().getTimestamp() != Long.MIN_VALUE) {
      Key seekKey = new Key(seekRange.getEndKey());
      seekKey.setTimestamp(Long.MIN_VALUE);
      seekRange = new Range(range.getStartKey(), range.isStartKeyInclusive(), seekKey, true);
    }
    
    return seekRange;
  }
}
