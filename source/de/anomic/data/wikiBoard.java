//wikiBoard.java 
//-------------------------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//last major change: 20.07.2004

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.

//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroNaturalOrder;

public class wikiBoard {

    public  static final int keyLength = 64;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    static {
        SimpleFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private kelondroMapObjects datbase = null;
    private kelondroMapObjects bkpbase = null;
    private static HashMap<String, String> authors = new HashMap<String, String>();

    public wikiBoard(File actpath, File bkppath) {
        new File(actpath.getParent()).mkdirs();
        if (datbase == null) {
            datbase = new kelondroMapObjects(new kelondroDyn(actpath, true, true, keyLength, recordSize, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        }
        new File(bkppath.getParent()).mkdirs();
        if (bkpbase == null) {
            bkpbase = new kelondroMapObjects(new kelondroDyn(bkppath, true, true, keyLength + dateFormat.length(), recordSize, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        }
    }

    public int sizeOfTwo() {
        return datbase.size() + bkpbase.size();
    }

    public int size() {
        return datbase.size();
    }

    public void close() {
        datbase.close();
        bkpbase.close();
    }

    private static String dateString() {
        return dateString(new Date());
    }

    public static String dateString(Date date) {
        synchronized (SimpleFormatter) {
            return SimpleFormatter.format(date);
        }
    }

    private static String normalize(String key) {
        if (key == null) return "null";
        return key.trim().toLowerCase();
    }

    public static String webalize(String key) {
        if (key == null) return "null";
        key = key.trim().toLowerCase();
        int p;
        while ((p = key.indexOf(" ")) >= 0)
            key = key.substring(0, p) + "%20" + key.substring(p +1);
        return key;
    }

    public static String guessAuthor(String ip) {
        String author = authors.get(ip);
        //yacyCore.log.logDebug("DEBUG: guessing author for ip = " + ip + " is '" + author + "', authors = " + authors.toString());
        return author;
    }

    public static void setAuthor(String ip, String author) {
        authors.put(ip,author);
    }

    public entry newEntry(String subject, String author, String ip, String reason, byte[] page) throws IOException {
        return new entry(normalize(subject), author, ip, reason, page);
    }

    public class entry {

        String key;
        HashMap<String, String> record;

        public entry(String subject, String author, String ip, String reason, byte[] page) throws IOException {
            record = new HashMap<String, String>();
            key = subject;
            if (key.length() > keyLength) key = key.substring(0, keyLength);
            record.put("date", dateString());
            if ((author == null) || (author.length() == 0)) author = "anonymous";
            record.put("author", kelondroBase64Order.enhancedCoder.encode(author.getBytes("UTF-8")));
            if ((ip == null) || (ip.length() == 0)) ip = "";
            record.put("ip", ip);
            if ((reason == null) || (reason.length() == 0)) reason = "";
            record.put("reason", kelondroBase64Order.enhancedCoder.encode(reason.getBytes("UTF-8")));
            if (page == null)
                record.put("page", "");
            else
                record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
            authors.put(ip, author);
            //System.out.println("DEBUG: setting author " + author + " for ip = " + ip + ", authors = " + authors.toString());
        }

        private entry(String key, HashMap<String, String> record) {
            this.key = key;
            this.record = record;
        }

        public String subject() {
            return key;
        }

        public Date date() {
            try {
                String c = record.get("date");
                if (c == null) {
                    System.out.println("DEBUG - ERROR: date field missing in wikiBoard");
                    return new Date();
                }
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(c);
                }
            } catch (ParseException e) {
                return new Date();
            }
        }

        public String author() {
            String a = record.get("author");
            if (a == null) return "anonymous";
            byte[] b = kelondroBase64Order.enhancedCoder.decode(a, "de.anomic.data.wikiBoard.author()");
            if (b == null) return "anonymous";
            return new String(b);
        }

        public String reason() {
            String r = record.get("reason");
            if (r == null) return "";
            byte[] b = kelondroBase64Order.enhancedCoder.decode(r, "de.anomic.data.wikiBoard.reason()");
            if (b == null) return "unknown";
            return new String(b);
        }

        public byte[] page() {
            String m = record.get("page");
            if (m == null) return new byte[0];
            byte[] b = kelondroBase64Order.enhancedCoder.decode(m, "de.anomic.data.wikiBoard.page()");
            if (b == null) return "".getBytes();
            return b;
        }

        private void setAncestorDate(Date date) {
            record.put("bkp", dateString(date));
        }

        private Date getAncestorDate() {
            try {
                String c = record.get("date");
                if (c == null) return null;
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(c);
                }
            } catch (ParseException e) {
                return null;
            }
        }

        /*
	public boolean hasAncestor() {
	    Date ancDate = getAncestorDate();
	    if (ancDate == null) return false;
	    try {
		return bkpbase.has(key + dateString(ancDate));
	    } catch (IOException e) {
		return false;
	    }
	}
         */

        public entry getAncestor() {
            Date ancDate = getAncestorDate();
            if (ancDate == null) return null;
            return read(key + dateString(ancDate), bkpbase);
        }

        private void setChild(String subject) {
            record.put("child", kelondroBase64Order.enhancedCoder.encode(subject.getBytes()));
        }

        private String getChildName() {
            String c = record.get("child");
            if (c == null) return null;
            byte[] subject = kelondroBase64Order.enhancedCoder.decode(c, "de.anomic.data.wikiBoard.getChildName()");
            if (subject == null) return null;
            return new String(subject);
        }

        public boolean hasChild() {
            String c = record.get("child");
            if (c == null) return false;
            byte[] subject = kelondroBase64Order.enhancedCoder.decode(c, "de.anomic.data.wikiBoard.hasChild()");
            return (subject != null);
        }

        public entry getChild() {
            String childName = getChildName();
            if (childName == null) return null;
            return read(childName, datbase);
        }
    }

    public String write(entry page) {
        // writes a new page and returns key
        try {
            // first load the old page
            entry oldEntry = read(page.key);
            // set the bkp date of the new page to the date of the old page
            Date oldDate = oldEntry.date();
            page.setAncestorDate(oldDate);
            oldEntry.setChild(page.subject());
            // write the backup
            //System.out.println("key = " + page.key);
            //System.out.println("oldDate = " + oldDate);
            //System.out.println("record = " + oldEntry.record.toString());
            bkpbase.set(page.key + dateString(oldDate), oldEntry.record);
            // write the new page
            datbase.set(page.key, page.record);
            return page.key;
        } catch (IOException e) {
            return null;
        }
    }

    public entry read(String key) {
        return read(key, datbase);
    }

    private entry read(String key, kelondroMapObjects base) {
        try {
            key = normalize(key);
            if (key.length() > keyLength) key = key.substring(0, keyLength);
            HashMap<String, String> record = base.getMap(key);
            if (record == null) return newEntry(key, "anonymous", "127.0.0.1", "New Page", "".getBytes());
            return new entry(key, record);
        } catch (IOException e) {
            return null;
        }
    }
    
    public entry readBkp(String key) {
        return read(key, bkpbase);
    }

    /*
    public boolean has(String key) {
	try {
	    return datbase.has(normalize(key));
	} catch (IOException e) {
	    return false;
	}
    }
     */

    public Iterator<String> keys(boolean up) throws IOException {
        return datbase.keys(up, false);
    }

    public Iterator<String> keysBkp(boolean up) throws IOException {
        return bkpbase.keys(up, false);
    }
}
