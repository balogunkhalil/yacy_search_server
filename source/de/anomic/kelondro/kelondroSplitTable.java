// kelondroSplitTable.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 12.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.anomic.server.serverMemory;

public class kelondroSplitTable implements kelondroIndex {

    // this is a set of kelondro tables
    // the set is divided into tables with different entry date
    // the table type can be either kelondroFlex or kelondroEco

    private static final long minimumRAM4Eco = 80 * 1024 * 1024;
    private static final int EcoFSBufferSize = 20;
    
    private HashMap<String, kelondroIndex> tables; // a map from a date string to a kelondroIndex object
    private kelondroRow rowdef;
    private File path;
    private String tablename;
    private kelondroOrder<kelondroRow.Entry> entryOrder;
    
    public kelondroSplitTable(File path, String tablename, kelondroRow rowdef, boolean resetOnFail) {
        this.path = path;
        this.tablename = tablename;
        this.rowdef = rowdef;
        this.entryOrder = new kelondroRow.EntryComparator(rowdef.objectOrder);
        init(resetOnFail);
    }
    
    public void init(boolean resetOnFail) {
        
        // initialized tables map
        this.tables = new HashMap<String, kelondroIndex>();
        if (!(path.exists())) path.mkdirs();
        String[] tablefile = path.list();
        String date;
        
        // first pass: find tables
        HashMap<String, Long> t = new HashMap<String, Long>();
        long ram, sum = 0;
        File f;
        for (int i = 0; i < tablefile.length; i++) {
            if ((tablefile[i].startsWith(tablename)) &&
                (tablefile[i].charAt(tablename.length()) == '.') &&
                (tablefile[i].length() == tablename.length() + 7)) {
                f = new File(path, tablefile[i]);
                if (f.isDirectory()) {
                    ram = kelondroFlexTable.staticRAMIndexNeed(path, tablefile[i], rowdef);
                } else {
                    ram = kelondroEcoTable.staticRAMIndexNeed(f, rowdef);
                }
                if (ram > 0) {
                    t.put(tablefile[i], new Long(ram));
                    sum += ram;
                }
            }
        }
        
        // second pass: open tables
        Iterator<Map.Entry<String, Long>> i;
        Map.Entry<String, Long> entry;
        String maxf;
        long maxram;
        kelondroIndex table;
        while (t.size() > 0) {
            // find maximum table
            maxram = 0;
            maxf = null;
            i = t.entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                ram = entry.getValue().longValue();
                if (ram > maxram) {
                    maxf = entry.getKey();
                    maxram = ram;
                }
            }
            
            // open next biggest table
            t.remove(maxf);
            date = maxf.substring(tablename.length() + 1);
            f = new File(path, maxf);
            if (f.isDirectory()) {
                // this is a kelonodroFlex table
                table = new kelondroCache(new kelondroFlexTable(path, maxf, rowdef, 0, resetOnFail));
            } else {
                table = new kelondroEcoTable(f, rowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize, 0);
            }
            tables.put(date, table);
        }
    }
    
    public void reset() throws IOException {
    	this.close();
    	String[] l = path.list();
    	for (int i = 0; i < l.length; i++) {
    		if (l[i].startsWith(tablename)) {
    		    File f = new File(path, l[i]);
    		    if (f.isDirectory()) kelondroFlexTable.delete(path, l[i]); else f.delete();
    		}
    	}
    	init(true);
    }
    
    public String filename() {
        return new File(path, tablename).toString();
    }
    
    private static final Calendar thisCalendar = Calendar.getInstance();
    public static final String dateSuffix(Date date) {
        int month, year;
        StringBuffer suffix = new StringBuffer(6);
        synchronized (thisCalendar) {
            thisCalendar.setTime(date);
            month = thisCalendar.get(Calendar.MONTH) + 1;
            year = thisCalendar.get(Calendar.YEAR);
        }
        if ((year < 1970) && (year >= 70)) suffix.append("19").append(Integer.toString(year));
        else if (year < 1970) suffix.append("20").append(Integer.toString(year));
        else if (year > 3000) return null;
        else suffix.append(Integer.toString(year));
        if (month < 10) suffix.append("0").append(Integer.toString(month)); else suffix.append(Integer.toString(month));
        return new String(suffix);    
    }
    
    public int size() {
        Iterator<kelondroIndex> i = tables.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }
    
    public synchronized kelondroProfile profile() {
        kelondroProfile[] profiles = new kelondroProfile[tables.size()];
        Iterator<kelondroIndex> i = tables.values().iterator();
        int c = 0;
        while (i.hasNext()) profiles[c++] = ((kelondroIndex) i.next()).profile();
        return kelondroProfile.consolidate(profiles);
    }
    
    public int writeBufferSize() {
        Iterator<kelondroIndex> i = tables.values().iterator();
        int s = 0;
        kelondroIndex ki;
        while (i.hasNext()) {
            ki = ((kelondroIndex) i.next());
            if (ki instanceof kelondroCache) s += ((kelondroCache) ki).writeBufferSize();
        }
        return s;
    }
    
    public kelondroRow row() {
        return this.rowdef;
    }
    
    public boolean has(byte[] key) throws IOException {
        Iterator<kelondroIndex> i = tables.values().iterator();
        kelondroIndex table;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            if (table.has(key)) return true;
        }
        return false;
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        Object[] keeper = keeperOf(key);
        if (keeper == null) return null;
        return (kelondroRow.Entry) keeper[1];
    }
    
    public synchronized void putMultiple(List<kelondroRow.Entry> rows) throws IOException {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        return put(row, null); // entry for current date
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Object[] keeper = keeperOf(row.getColBytes(0));
        if (keeper != null) return ((kelondroIndex) keeper[0]).put(row);
        if ((entryDate == null) || (entryDate.after(new Date()))) entryDate = new Date(); // fix date
        String suffix = dateSuffix(entryDate);
        if (suffix == null) return null;
        kelondroIndex table = (kelondroIndex) tables.get(suffix);
        if (table == null) {
            // open table
            File f = new File(path, tablename + "." + suffix);
            if (f.exists()) {
                if (f.isDirectory()) {
                    // open a flex table
                    table = new kelondroFlexTable(path, tablename + "." + suffix, rowdef, 0, true);
                } else {
                    // open a eco table
                    table = new kelondroEcoTable(f, rowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
                }
            } else {
                // make new table
                if (serverMemory.request(minimumRAM4Eco, true)) {
                    // enough memory for a ecoTable
                    table = new kelondroEcoTable(f, rowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
                } else {
                    // use the flex table
                    table = new kelondroFlexTable(path, tablename + "." + suffix, rowdef, 0, true);
                }
            }
            tables.put(suffix, table);
        }
        table.put(row);
        return null;
    }
    
    public synchronized Object[] keeperOf(byte[] key) throws IOException {
        Iterator<kelondroIndex> i = tables.values().iterator();
        kelondroIndex table;
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            entry = table.get(key);
            if (entry != null) return new Object[]{table, entry};
        }
        return null;
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        addUnique(row, null);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        if ((entryDate == null) || (entryDate.after(new Date()))) entryDate = new Date(); // fix date
        String suffix = dateSuffix(entryDate);
        if (suffix == null) return;
        kelondroIndex table = (kelondroIndex) tables.get(suffix);
        if (table == null) {
            // make new table
            if (serverMemory.request(minimumRAM4Eco, true)) {
                // enough memory for a ecoTable
                table = new kelondroEcoTable(new File(path, tablename + "." + suffix), rowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
            } else {
                // use the flex table
                table = new kelondroFlexTable(path, tablename + "." + suffix, rowdef, 0, true);
            }
            tables.put(suffix, table);
        }
        table.addUnique(row);
    }
    
    public synchronized void addUniqueMultiple(List<kelondroRow.Entry> rows) throws IOException {
        Iterator<kelondroRow.Entry> i = rows.iterator();
        while (i.hasNext()) addUnique(i.next());
    }
    
    public synchronized void addUniqueMultiple(List<kelondroRow.Entry> rows, Date entryDate) throws IOException {
        Iterator<kelondroRow.Entry> i = rows.iterator();
        while (i.hasNext()) addUnique(i.next(), entryDate);
    }
    
    public ArrayList<kelondroRowSet> removeDoubles() throws IOException {
        Iterator<kelondroIndex> i = tables.values().iterator();
        ArrayList<kelondroRowSet> report = new ArrayList<kelondroRowSet>();
        while (i.hasNext()) {
            report.addAll(i.next().removeDoubles());
        }
        return report;
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key, boolean keepOrder) throws IOException {
        Iterator<kelondroIndex> i = tables.values().iterator();
        kelondroIndex table;
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            table = i.next();
            entry = table.remove(key, keepOrder);
            if (entry != null) return entry;
        }
        return null;
    }
    
    public synchronized kelondroRow.Entry removeOne() throws IOException {
        Iterator<kelondroIndex> i = tables.values().iterator();
        kelondroIndex table, maxtable = null;
        int maxcount = -1;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            if (table.size() > maxcount) {
                maxtable = table;
                maxcount = table.size();
            }
        }
        if (maxtable == null) {
            return null;
        } else {
            return maxtable.removeOne();
        }
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        HashSet<kelondroCloneableIterator<byte[]>> set = new HashSet<kelondroCloneableIterator<byte[]>>();
        Iterator<kelondroIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            set.add(i.next().keys(up, firstKey));
        }
        return kelondroMergeIterator.cascade(set, rowdef.objectOrder, kelondroMergeIterator.simpleMerge, up);
    }
    
    public synchronized kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] firstKey) throws IOException {
        HashSet<kelondroCloneableIterator<kelondroRow.Entry>> set = new HashSet<kelondroCloneableIterator<kelondroRow.Entry>>();
        Iterator<kelondroIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            set.add(i.next().rows(up, firstKey));
        }
        return kelondroMergeIterator.cascade(set, entryOrder, kelondroMergeIterator.simpleMerge, up);
    }

    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }
    
    public synchronized void close() {
        if (tables == null) return;
        Iterator<kelondroIndex> i = tables.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        tables = null;
    }
    
    public static void main(String[] args) {
        System.out.println(dateSuffix(new Date()));
    }
    
}
