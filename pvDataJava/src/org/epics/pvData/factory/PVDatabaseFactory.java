/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvData.factory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.epics.pvData.misc.Executor;
import org.epics.pvData.misc.ExecutorFactory;
import org.epics.pvData.misc.MessageNode;
import org.epics.pvData.misc.MessageQueue;
import org.epics.pvData.misc.MessageQueueFactory;
import org.epics.pvData.misc.ThreadPriority;
import org.epics.pvData.pv.Field;
import org.epics.pvData.pv.MessageType;
import org.epics.pvData.pv.PVDataCreate;
import org.epics.pvData.pv.PVDatabase;
import org.epics.pvData.pv.PVRecord;
import org.epics.pvData.pv.PVStructure;
import org.epics.pvData.pv.Requester;



/**
 * Factory for PVDatabase.
 * @author mrk
 *
 */
public class PVDatabaseFactory {
private static PVDataCreate pvDataCreate = null;
    
    private static Database master;
    
    static {
        pvDataCreate = PVDataFactory.getPVDataCreate();
        master = new Database("master");
        PVStructure pvStructure = pvDataCreate.createPVStructure(null,"null", new Field[0]);
        master.addStructure(pvStructure);
    }
    
    /**
     * Create a PVDatabase.
     * @param name Name for the database.
     * @return PVDatabase interface.
     */
    public static PVDatabase create(String name) {
        if(name.equals("master")) return master;
        return new Database(name);
    }
    /**
     * Get the master database.
     * @return PVDatabase interface.
     */
    public static PVDatabase getMaster() {
        return master;
    }
    
    private static class Database implements PVDatabase,Runnable {
        private String name;
        private TreeMap<String,PVStructure> structureMap = new TreeMap<String,PVStructure>();
        private boolean isMaster = false;
        private LinkedHashMap<String,PVRecord> recordMap = new LinkedHashMap<String,PVRecord>();
        private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private ReentrantLock messageRequestListLock = new ReentrantLock();
        private ArrayList<Requester> messageRequesterList = new ArrayList<Requester>();
        // following are only used by master
        private static final int messageQueueSize = 300;
        private MessageQueue messageQueue = MessageQueueFactory.create(messageQueueSize);
        private Executor iocExecutor = null;
        
        private Database(String name) {
            this.name = name;
            if(name.equals("master")) {
                isMaster = true;
                iocExecutor = ExecutorFactory.create(
                        "PVDatabaseMessage", ThreadPriority.lowest);
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#addRecord(org.epics.pvData.pv.PVRecord)
         */
        public boolean addRecord(PVRecord record) {
            rwLock.writeLock().lock();
            try {
                String key = record.getPVRecord().getRecordName();
                if(recordMap.containsKey(key)) {
                    message("record already exists",MessageType.warning);
                    return false;
                }
                if(this!=master && master.findRecord(key)!=null) {
                    message("record already exists in master",MessageType.warning);
                    return false;
                }
                recordMap.put(key,record);
            } finally {
                rwLock.writeLock().unlock();
            }
            if(isMaster) {
                record.getPVRecord().addRequester(this);
            }
            return true;
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#addRequester(org.epics.pvData.pv.Requester)
         */
        public void addRequester(Requester requester) {
            messageRequestListLock.lock();
            try {
                messageRequesterList.add(requester);
            } finally {
                messageRequestListLock.unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#addStructure(org.epics.pvData.pv.PVStructure)
         */
        public boolean addStructure(PVStructure pvStructure) {
            rwLock.writeLock().lock();
            try {
                String key = pvStructure.getField().getFieldName();
                if(structureMap.containsKey(key)) return false;
                if(this!=master && master.findStructure(key)!=null) return false;
                structureMap.put(key,pvStructure);
                return true;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#findRecord(java.lang.String)
         */
        public PVRecord findRecord(String recordName) {
            rwLock.readLock().lock();
            try {
                PVRecord record = null;
                record = recordMap.get(recordName);
                if(record==null && this!=master) record = master.findRecord(recordName);
                return record;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#findStructure(java.lang.String)
         */
        public PVStructure findStructure(String structureName) {
            rwLock.readLock().lock();
            try {
                PVStructure pvStructure = null;
                pvStructure = structureMap.get(structureName);
                if(pvStructure==null && this!=master) {
                    pvStructure = master.findStructure(structureName);
                }
                return pvStructure;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#getMaster()
         */
        public PVDatabase getMaster() {
            return master;
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#getName()
         */
        public String getName() {
            return name;
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#getRecords()
         */
        public PVRecord[] getRecords() {
            rwLock.readLock().lock();
            try {
                PVRecord[] array = new PVRecord[recordMap.size()];
                recordMap.values().toArray(array);
                return array;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#getStructures()
         */
        public PVStructure[] getStructures() {
            rwLock.readLock().lock();
            try {
                PVStructure[] array = new PVStructure[structureMap.size()];
                structureMap.values().toArray(array);
                return array;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#mergeIntoMaster()
         */
        public void mergeIntoMaster() {
            if(getMaster()==this) return;
            rwLock.writeLock().lock();
            try {
                master.merge(structureMap,recordMap);
                structureMap.clear();
                recordMap.clear();
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        
        // merge allows master to be locked once
        private void merge(
                TreeMap<String,PVStructure> structure,
                LinkedHashMap<String,PVRecord> from)
        {
            Set<String> keys;
            rwLock.writeLock().lock();
            try {
                keys = structure.keySet();
                for(String key: keys) {
                    structureMap.put(key,structure.get(key));
                }
                keys = from.keySet();
                for(String key: keys) {
                    PVRecord pvRecord = from.get(key);
                    pvRecord.addRequester(this);
                    recordMap.put(key,pvRecord);
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#message(java.lang.String, org.epics.pvData.pv.MessageType)
         */
        public void message(String message, MessageType messageType) {
            if(!isMaster) {
                messageRequestListLock.lock();
                try {
                    if(messageRequesterList.size()<=0) {
                        PrintStream printStream;
                        if(messageType==MessageType.info) {
                            printStream = System.out;
                        } else {
                            printStream = System.err;
                        }
                        printStream.println(messageType.toString() + " " + message);
                       
                    } else {
                        Iterator<Requester> iter = messageRequesterList.iterator();
                        while(iter.hasNext()) {
                            Requester requester = iter.next();
                            requester.message(message, messageType);
                        }
                    }
                } finally {
                    messageRequestListLock.unlock();
                }
                return;
            }
            boolean execute = false;
            messageQueue.lock();
            try {
                if(messageQueue.isEmpty()) execute = true;
                if(messageQueue.isFull()) {
                    messageQueue.replaceLast(message, messageType);
                } else {
                    messageQueue.put(message, messageType);
                }
            } finally {
                messageQueue.unlock();
            }
            if(execute) {
                iocExecutor.execute(this);
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#recordList(java.lang.String)
         */
        public String[] recordList(String regularExpression) {
            ArrayList<String> list = new ArrayList<String>();
            if(regularExpression==null) regularExpression = ".*";
            Pattern pattern;
            try {
                pattern = Pattern.compile(regularExpression);
            } catch (PatternSyntaxException e) {
                return new String[0];
            }
            rwLock.readLock().lock();
            try {
                Set<Map.Entry<String,PVRecord>> recordSet = recordMap.entrySet();
                Iterator<Map.Entry<String,PVRecord>> iter = recordSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry<String,PVRecord> entry = iter.next();
                    String name = entry.getKey();
                    if(pattern.matcher(name).matches()) {
                        list.add(name);
                    }
                }
                String[] result = new String[list.size()];
                for(int i=0; i< list.size(); i++) result[i] = list.get(i);
                return result;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#recordToString(java.lang.String)
         */
        public String recordToString(String regularExpression) {
            StringBuilder result = new StringBuilder();
            if(regularExpression==null) regularExpression = ".*";
            Pattern pattern;
            try {
                pattern = Pattern.compile(regularExpression);
            } catch (PatternSyntaxException e) {
                return "PatternSyntaxException: " + e;
            }
            rwLock.readLock().lock();
            try {
                Set<Map.Entry<String,PVRecord>> recordSet = recordMap.entrySet();
                Iterator<Map.Entry<String,PVRecord>> iter = recordSet.iterator();
                while(iter.hasNext()) {
                    Map.Entry<String,PVRecord> entry = iter.next();
                    String name = entry.getKey();
                    if(pattern.matcher(name).matches()) {
                        result.append(String.format("%nrecord %s%s",name,entry.getValue().toString()));
                    }
                }
                return result.toString();
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#removeRecord(org.epics.pvData.pv.PVRecord)
         */
        public boolean removeRecord(PVRecord record) {
            if(isMaster) {
                record.getPVRecord().removeRequester(this);
            }
            rwLock.writeLock().lock();
            try {
                String key = record.getPVRecord().getRecordName();
                if(recordMap.remove(key)!=null) return true;
                return false;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#removeRequester(org.epics.pvData.pv.Requester)
         */
        public void removeRequester(Requester requester) {
            messageRequestListLock.lock();
            try {
                messageRequesterList.remove(requester);
            } finally {
                messageRequestListLock.unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#removeStructure(org.epics.pvData.pv.PVStructure)
         */
        public boolean removeStructure(PVStructure structure) {
            rwLock.writeLock().lock();
            try {
                String key = structure.getStructure().getFieldName();
                if(structureMap.remove(key)!=null) return true;
                return false;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#structureList(java.lang.String)
         */
        public String[] structureList(String regularExpression) {
            ArrayList<String> list = new ArrayList<String>();
            if(regularExpression==null) regularExpression = ".*";
            Pattern pattern;
            try {
                pattern = Pattern.compile(regularExpression);
            } catch (PatternSyntaxException e) {
                return new String[0];
            }
            rwLock.readLock().lock();
            try {
                Set<String> keys = structureMap.keySet();
                for(String key: keys) {
                    PVStructure pvStructure = structureMap.get(key);
                    String name = pvStructure.getStructure().getFieldName();
                    if(pattern.matcher(name).matches()) {
                        list.add(name);
                    }
                }
                String[] result = new String[list.size()];
                for(int i=0; i< list.size(); i++) result[i] = list.get(i);
                return result;
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.PVDatabase#structureToString(java.lang.String)
         */
        public String structureToString(String regularExpression) {
            StringBuilder result = new StringBuilder();
            if(regularExpression==null) regularExpression = ".*";
            Pattern pattern;
            try {
                pattern = Pattern.compile(regularExpression);
            } catch (PatternSyntaxException e) {
                return "PatternSyntaxException: " + e;
            }
            rwLock.readLock().lock();
            try {
                Set<String> keys = structureMap.keySet();
                for(String key: keys) {
                    PVStructure pvStructure = structureMap.get(key);
                    String name = pvStructure.getStructure().getFieldName();
                    if(pattern.matcher(name).matches()) {
                        result.append(" " + pvStructure.toString());
                    }
                }
                return result.toString();
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvData.pv.Requester#getRequesterName()
         */
        public String getRequesterName() {
            return name;
        }
        /* (non-Javadoc)
         * @see java.lang.Runnable#run()
         */
        public void run() { // handles messages
            while(true) {
                String message = null;
                MessageType messageType = null;
                int numOverrun = 0;
                messageQueue.lock();
                try {
                    MessageNode messageNode = messageQueue.get();
                    numOverrun = messageQueue.getClearOverrun();
                    if(messageNode==null) break;
                    message = messageNode.message;
                    messageType = messageNode.messageType;
                    messageNode.message = null;
                } finally {
                    messageQueue.unlock();
                }
                messageRequestListLock.lock();
                try {
                    
                    if(messageRequesterList.size()<=0) {
                        PrintStream printStream;
                        if(messageType==MessageType.info) {
                            printStream = System.out;
                        } else {
                            printStream = System.err;
                        }
                        if(numOverrun>0) {
                            System.err.println(MessageType.error.toString() + " " + numOverrun + " dropped messages ");
                        }
                        if(message!=null) {
                            printStream.println(messageType.toString() + " " + message);
                        }
                    } else {
                        Iterator<Requester> iter = messageRequesterList.iterator();
                        while(iter.hasNext()) {
                            Requester requester = iter.next();
                            requester.message(message, messageType);
                            if(numOverrun>0) {
                                requester.message(numOverrun + " dropped messages",MessageType.error);
                            }
                        }
                    }
                } finally {
                    messageRequestListLock.unlock();
                }
            }
        }
    }
}
