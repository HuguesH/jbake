package org.jbake.app;

import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.*;

public class DBUtil {
    public static ContentStore createDB(final String type, String name) {
        return new ContentStore(type, name);
    }
    public static ContentStore createDataStore(final String type, String name) {
        return new ContentStore(type, name);
    }
    
    public static void updateSchema(final ContentStore db) {
        db.updateSchema();
    }

    public static Map<String, Object> documentToModel(ODocument doc) {
        Map<String, Object> result = new HashMap<String, Object>();
        Iterator<Map.Entry<String, Object>> fieldIterator = doc.iterator();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, Object> entry = fieldIterator.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static List<String> wrapFields(Iterator<ODocument> docs, String fieldName){
            List<String> list = new ArrayList<String>();
            while (docs.hasNext()) {
                ODocument next = docs.next();
                list.add((String)next.field(fieldName));
            }
            return list;
    }



    /**
     * Converts a DB list into a String array
     */
    @SuppressWarnings("unchecked")
    public static String[] toStringArray(Object entry) {
    	if (entry instanceof String[]) {
            return (String[]) entry;
        } else if (entry instanceof OTrackedList) {
            OTrackedList<String> list = (OTrackedList<String>) entry;
            return list.toArray(new String[list.size()]);
        }
    	return new String[0];
    }

}
