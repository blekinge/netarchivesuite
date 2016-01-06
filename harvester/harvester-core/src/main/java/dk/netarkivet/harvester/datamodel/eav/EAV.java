package dk.netarkivet.harvester.datamodel.eav;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.antiaction.raptor.dao.AttributeBase;
import com.antiaction.raptor.dao.AttributeTypeBase;
import com.antiaction.raptor.sql.DBWrapper;
import com.antiaction.raptor.sql.SqlResult;
import com.antiaction.raptor.sql.mssql.MSSql;

import dk.netarkivet.harvester.datamodel.HarvestDBConnection;

/**
 * EAV wrapper for the actual EAV implementation.
 */
public class EAV {

	/** tree id for <code>SparseFullHarvest</code> attributes and types. */
	public static final int SNAPSHOT_TREE_ID = 1;
	/** tree id for <code>DomainConfiguration</code> attributes and types. */
	public static final int DOMAIN_TREE_ID = 2;

	/** Classloader used to instantiate attribute type implementations. */
	protected static ClassLoader classLoader = EAV.class.getClassLoader();

	/** Singleton instance. */
	protected static EAV instance;

	/**
     * @return an instance of this class.
     */
    public static synchronized EAV getInstance() {
        if (instance == null) {
            instance = new EAV();
        }
        return instance;
    }

    /** DB layer implementation. MSSQL is more like SQL92. */
    protected DBWrapper db = MSSql.getInstance(null, classLoader);

    /**
     * Insert attribute into database.
     * @param attribute attribute to insert into database
     */
    public void insertAttribute(AttributeBase attribute) {
        Connection connection = HarvestDBConnection.get();
        db.attribute_insert(connection, attribute);
        HarvestDBConnection.release(connection);
    }

    /**
     * Update existing attribute.
     * @param attribute attribute to update in database
     */
    public void saveAttribute(AttributeBase attribute) {
        Connection connection = HarvestDBConnection.get();
    	attribute.saveState(db, connection);
        HarvestDBConnection.release(connection);
    }

    /**
     * Returns a list of attribute types for the given tree id.
     * @param tree_id tree id to look for attribute types in
     * @return a list of attribute types for the given tree id
     */
    public List<AttributeTypeBase> getAttributeTypes(int tree_id) {
        Connection connection = HarvestDBConnection.get();
		List<AttributeTypeBase> attributeTypes = db.getAttributeTypes(connection, classLoader, tree_id);
        HarvestDBConnection.release(connection);
		return attributeTypes;
	}

    /**
     * Handy class to pair an attribute and its type.
     */
    public static class AttributeAndType {
    	public AttributeTypeBase attributeType;
    	public AttributeBase attribute;
    	public AttributeAndType(AttributeTypeBase attributeType, AttributeBase attribute) {
        	this.attributeType = attributeType;
        	this.attribute = attribute;
    	}
    }

    /**
     * Returns a list of attributes and their type for a given entity id and tree id.
     * @param tree_id tree id to look in
     * @param entity_id entity to look for
     * @return a list of attributes and their type for a given entity id and tree id.
     * @throws SQLException if an SQL exception occurs while querying the database
     */
    public List<AttributeAndType> getAttributesAndTypes(int tree_id, int entity_id) throws SQLException {
        Connection connection = HarvestDBConnection.get();
		List<AttributeTypeBase> attributeTypes = db.getAttributeTypes(connection, classLoader, tree_id);
		AttributeTypeBase attributeType;
		Map<Integer, AttributeTypeBase> attributeTypesMap = new TreeMap<Integer, AttributeTypeBase>();
		for (int i=0; i<attributeTypes.size(); ++i) {
			attributeType = attributeTypes.get(i);
			attributeTypesMap.put(attributeType.id, attributeType);
		}
        SqlResult sqlResult = db.attributes_getTypedAttributes(connection, tree_id, entity_id);
        List<AttributeAndType> attributes = new ArrayList<AttributeAndType>();
        AttributeBase attribute;
        ResultSet rs = sqlResult.rs;
		while ( rs.next() ) {
			int type_id = rs.getInt("type_id");
			attributeType = attributeTypesMap.get(type_id);
			attribute = null;
			rs.getInt("id");
			if (attributeType != null && !rs.wasNull()) {
				attribute = attributeType.instanceOf();
				attribute.attributeType = attributeType;
				attribute.loadState( db, connection, rs );
			}
			attributes.add(new AttributeAndType(attributeType, attribute));
		}
		sqlResult.close();
        HarvestDBConnection.release(connection);
		return attributes;
	}

    /**
     * Compare two lists containing attributes and their types.
     * @param antList1
     * @param antList2
     * @return the result of comparing two lists containing attributes and their types
     */
    public static int compare(List<AttributeAndType> antList1, List<AttributeAndType> antList2) {
    	int res;
        AttributeAndType ant1;
        AttributeAndType ant2;
        int idx1 = 0;
        int idx2 = 0;
        if (antList1.size() > 0 || antList2.size() > 0) {
        	if (idx1 < antList1.size()) {
        		ant1 = antList1.get( idx1++ );
        	} else {
        		ant1 = null;
        	}
        	if (idx2 < antList2.size()) {
        		ant2 = antList2.get( idx2++ );
        	} else {
        		ant2 = null;
        	}
            boolean bLoop = true;
            while (bLoop) {
            	if (ant1 != null) {
            		if (ant2 != null) {
            			res = ant2.attributeType.id - ant1.attributeType.id;
                        if (res != 0L) {
                            return res < 0L ? -1 : 1;
                        }
                        // TODO Change to support types other than integer.
                        switch (ant1.attributeType.datatype) {
                        case 1:
                        	Integer i1 = null;
                        	Integer i2 = null;
                        	if (ant1.attribute != null) {
                        		i1 = ant1.attribute.getInteger();
                        	}
                        	if (i1 == null) {
                        		i1 = ant1.attributeType.def_int;
                        	}
                        	if (i1 == null) {
                        		i1 = 0;
                        	}
                        	if (ant2.attribute != null) {
                        		i2 = ant2.attribute.getInteger();
                        	}
                        	if (i2 == null) {
                        		i2 = ant2.attributeType.def_int;
                        	}
                        	if (i2 == null) {
                        		i2 = 0;
                        	}
                        	res = i2 - i1;
                            if (res != 0L) {
                                return res < 0L ? -1 : 1;
                            }
                        	break;
                       	default:
                       		throw new UnsupportedOperationException("EAV attribute datatype compare not implemented yet.");
                        }
                    	if (idx1 < antList1.size()) {
                    		ant1 = antList1.get( idx1++ );
                    	} else {
                    		ant1 = null;
                    	}
                    	if (idx2 < antList2.size()) {
                    		ant2 = antList2.get( idx2++ );
                    	} else {
                    		ant2 = null;
                    	}
            		} else {
            			return -1;
            		}
            	} else {
            		if (ant2 != null) {
            			return 1;
            		} else {
            			bLoop = false;
            		}
            	}
            }
        }
    	return 0;
    }

}
