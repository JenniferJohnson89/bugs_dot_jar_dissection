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
package org.apache.accumulo.server.master.tableOps;

import java.math.BigInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.impl.Tables;
import org.apache.accumulo.core.client.impl.thrift.TableOperation;
import org.apache.accumulo.core.client.impl.thrift.TableOperationExceptionType;
import org.apache.accumulo.core.client.impl.thrift.ThriftTableOperationException;
import org.apache.accumulo.core.zookeeper.ZooUtil;
import org.apache.accumulo.server.client.HdfsZooInstance;
import org.apache.accumulo.server.zookeeper.DistributedReadWriteLock;
import org.apache.accumulo.server.zookeeper.IZooReaderWriter;
import org.apache.accumulo.server.zookeeper.ZooQueueLock;
import org.apache.accumulo.server.zookeeper.ZooReaderWriter;
import org.apache.accumulo.server.zookeeper.ZooReservation;
import org.apache.accumulo.server.zookeeper.ZooReaderWriter.Mutator;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;

public class Utils {
  
  static void checkTableDoesNotExist(Instance instance, String tableName, String tableId, TableOperation operation) throws ThriftTableOperationException {
    
    String id = Tables.getNameToIdMap(instance).get(tableName);
    
    if (id != null && !id.equals(tableId))
      throw new ThriftTableOperationException(null, tableName, operation, TableOperationExceptionType.EXISTS, null);
  }
  
  static String getNextTableId(String tableName, Instance instance) throws ThriftTableOperationException {
    
    String tableId = null;
    try {
      IZooReaderWriter zoo = ZooReaderWriter.getRetryingInstance();
      final String ntp = ZooUtil.getRoot(instance) + Constants.ZTABLES;
      byte[] nid = zoo.mutate(ntp, "0".getBytes(), ZooUtil.PUBLIC, new Mutator() {
        @Override
        public byte[] mutate(byte[] currentValue) throws Exception {
          BigInteger nextId = new BigInteger(new String(currentValue), Character.MAX_RADIX);
          nextId = nextId.add(BigInteger.ONE);
          return nextId.toString(Character.MAX_RADIX).getBytes();
        }
      });
      return new String(nid);
    } catch (Exception e1) {
      Logger.getLogger(CreateTable.class).error("Failed to assign tableId to " + tableName, e1);
      throw new ThriftTableOperationException(tableId, tableName, TableOperation.CREATE, TableOperationExceptionType.OTHER, e1.getMessage());
    }
  }
  
  static final Lock tableNameLock = new ReentrantLock();
  static final Lock idLock = new ReentrantLock();
  private static final Logger log = Logger.getLogger(Utils.class);
  
  public static long reserveTable(String tableId, long tid, boolean writeLock, boolean tableMustExist, TableOperation op) throws Exception {
    if (getLock(tableId, tid, writeLock).tryLock()) {
      if (tableMustExist) {
        Instance instance = HdfsZooInstance.getInstance();
        IZooReaderWriter zk = ZooReaderWriter.getRetryingInstance();
        if (!zk.exists(ZooUtil.getRoot(instance) + Constants.ZTABLES + "/" + tableId))
          throw new ThriftTableOperationException(tableId, "", op, TableOperationExceptionType.NOTFOUND, "Table does not exists");
      }
      log.info("table " + tableId + " (" + Long.toHexString(tid) + ") locked for " + (writeLock ? "write" : "read") + " operation: " + op);
      return 0;
    } else
      return 100;
  }
  
  public static void unreserveTable(String tableId, long tid, boolean writeLock) throws Exception {
    getLock(tableId, tid, writeLock).unlock();
    log.info("table " + tableId + " (" + Long.toHexString(tid) + ") unlocked for " + (writeLock ? "write" : "read"));
  }
  
  public static long reserveHdfsDirectory(String directory, long tid) throws KeeperException, InterruptedException {
    Instance instance = HdfsZooInstance.getInstance();
    
    String resvPath = ZooUtil.getRoot(instance) + Constants.ZHDFS_RESERVATIONS + "/" + new String(Base64.encodeBase64(directory.getBytes()));
    
    IZooReaderWriter zk = ZooReaderWriter.getRetryingInstance();
    
    if (ZooReservation.attempt(zk, resvPath, String.format("%016x", tid), "")) {
      return 0;
    } else
      return 50;
  }
  
  public static void unreserveHdfsDirectory(String directory, long tid) throws KeeperException, InterruptedException {
    Instance instance = HdfsZooInstance.getInstance();
    String resvPath = ZooUtil.getRoot(instance) + Constants.ZHDFS_RESERVATIONS + "/" + new String(Base64.encodeBase64(directory.getBytes()));
    ZooReservation.release(ZooReaderWriter.getRetryingInstance(), resvPath, String.format("%016x", tid));
  }
  
  private static Lock getLock(String tableId, long tid, boolean writeLock) throws Exception {
    byte[] lockData = String.format("%016x", tid).getBytes();
    ZooQueueLock qlock = new ZooQueueLock(ZooUtil.getRoot(HdfsZooInstance.getInstance()) + Constants.ZTABLE_LOCKS + "/" + tableId, false);
    Lock lock = DistributedReadWriteLock.recoverLock(qlock, lockData);
    if (lock == null) {
      DistributedReadWriteLock locker = new DistributedReadWriteLock(qlock, lockData);
      if (writeLock)
        lock = locker.writeLock();
      else
        lock = locker.readLock();
    }
    return lock;
  }
  
  public static Lock getReadLock(String tableId, long tid) throws Exception {
    return Utils.getLock(tableId, tid, false);
  }
  
}
