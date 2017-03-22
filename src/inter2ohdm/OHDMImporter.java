package inter2ohdm;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.DB;
import util.OHDM_DB;
import util.FileSQLStatementQueue;
import util.SQLStatementQueue;
import util.Parameter;
import util.Trigger;
import util.Util;

/**
 * That class imports (and updates) data from intermediate database to OHDM.
 * It changes both, ohdm data and intermediate data.
 * 
 * @author thsc
 */
public class OHDMImporter extends Importer {
    private final String sourceSchema;
    private final String targetSchema;
    private final IntermediateDB intermediateDB;
    private final SQLStatementQueue sourceUpdateQueue;
    private final SQLStatementQueue targetSelectQueue;
    private final SQLStatementQueue targetInsertQueue;
    
    private String defaultSince = "1970-01-01";
    private String defaultUntil = "2017-01-01";
    
    public OHDMImporter(IntermediateDB intermediateDB, 
            Connection sourceConnection, Connection targetConnection, 
            String sourceSchema, String targetSchema, SQLStatementQueue updateQueue) {
        
        super(sourceConnection, targetConnection);
        
        this.sourceSchema = sourceSchema;
        this.targetSchema = targetSchema;
        this.intermediateDB = intermediateDB;
        
//        this.sourceUpdateQueue = new SQLStatementQueue(sourceConnection);
        this.sourceUpdateQueue = updateQueue;
        
        this.targetSelectQueue = new SQLStatementQueue(targetConnection);
        this.targetInsertQueue = new SQLStatementQueue(targetConnection);
        
        this.defaultSince = "2016-01-01";
        this.defaultUntil = this.getTodayString();
    }
    
    void close() throws SQLException {
        this.targetInsertQueue.forceExecute();
        this.targetInsertQueue.close();
    }
    
    private String getTodayString() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 0);
        
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");

        String formatted = format1.format(cal.getTime());
        return formatted;
    }

    @Override
    public boolean importNode(OSMNode node, boolean importUnnamedEntities) {
        if(!this.elementHasIdentity(node) && !importUnnamedEntities) {
            // nodes without an identity are not imported.
            return false;
        }

        return (this.importOSMElement(node, importUnnamedEntities, false) != null);
    }
     
    @Override
    public boolean importWay(OSMWay way, boolean importUnnamedEntities) {
        if(this.importOSMElement(way, importUnnamedEntities, false) == null) {
            return false; // failure
        }
        
        List<OSMElement> iNodesList = way.getNodesWithIdentity();
        if(iNodesList == null || iNodesList.isEmpty()) return true; // ready
        
        // imported.. fill subsequent table
//        SQLStatementQueue sql = new SQLStatementQueue(this.targetConnection);

        try {
            this.saveSubsequentObjects(this.targetInsertQueue, way.getOHDMObjectID(), 
                    OHDM_DB.POINT, iNodesList.iterator());
        
            this.targetInsertQueue.couldExecute();
        }
        catch(SQLException se) {
            Util.printExceptionMessage(se, this.targetInsertQueue, "writing to subsequent table failed", false);
        }
        
        return true;
    }
    
    private void saveSubsequentObjects(SQLStatementQueue sql, String targetObjectID, int sourceType, 
            Iterator<OSMElement> eIter) throws SQLException {
        
        if(sourceType != OHDM_DB.POINT && sourceType != OHDM_DB.LINESTRING) {
            throw new SQLException("subsequent table only keeps point and ways");
        }
        
        if(targetObjectID == null || targetObjectID.isEmpty()) {
            throw new SQLException("targetObjectID must not be null or empty when inserting into subsequent table");
        }
        
        /*
        INSERT INTO ohdm.subsequent_geom_user(target_id, point_id, line_id, polygon_id)
        */
        sql.append("INSERT INTO ");
        sql.append(DB.getFullTableName(this.targetSchema, OHDM_DB.SUBSEQUENT_GEOM_USER));
        sql.append(" (target_id, ");
        switch(sourceType) {
            case OHDM_DB.POINT:
                sql.append("point_id");
                break;
            case OHDM_DB.LINESTRING:
                sql.append("line_id");
                break;
        }
        sql.append(") VALUES ");
        
        boolean first = true;
        while(eIter.hasNext()) {
            OSMElement e = eIter.next();
            
            if(first) {
                first = false;
            } else {
                sql.append(",");
            }
            
            sql.append("(");
            sql.append(targetObjectID);
            sql.append(",");
            sql.append(e.getOHDMObjectID());
            sql.append(")");
        }
        sql.append("; ");
    }

    /**
     * TODO handle boundary attribute admin-level!!http://wiki.openstreetmap.org/wiki/Tag:boundary%3Dadministrative#admin_level
     * @param relation
     * @param importUnnamedEntities
     * @return
     * @throws SQLException 
     */
    @Override
    public boolean importRelation(OSMRelation relation, boolean importUnnamedEntities) throws SQLException {
        // debug stop
        if(relation.getOSMIDString().equalsIgnoreCase("4451529")) {
            int i = 42;
        }
        
        String ohdmIDString = this.importOSMElement(relation, importUnnamedEntities, true);
        
        if(ohdmIDString == null) return false;
        
        // remember it ohdm incarnation...
        relation.setOHDMObjectID(this.sourceUpdateQueue, ohdmIDString);
        
        /* now there are two options:
        a) that relation represents a multigeometry (in most cases)
        b) it represents a polygon with one or more hole(s)
         */
        
        /* status:
        Object is stored but geometry not.
        
        1) Relation is stored as geometry only in two cases:
        
        a) that relation is a polygon but made up by several ways
        b) is a multipolygone with holes
        
        2) Otherwise, relation is stored as geoobject wit relations to 
        other geoobjects.
        */
        
        // handle option 2)
        if(!relation.isPolygon()) {
            return this.saveRelationAsRelatedObjects(relation, ohdmIDString);
        } else {
            if(relation.isMultipolygon()) {
                return this.saveRelationAsMultipolygon(relation);
            } 
        }

        return false;
    }
    
    int getTargetTypeInt(OSMElement ohdmElement) {
        int targetType = 0;
        switch(ohdmElement.getGeometryType()) {
            case OHDM_DB.POINT: 
                targetType = OHDM_DB.OHDM_POINT_GEOMTYPE;
                break;
            case OHDM_DB.LINESTRING: 
                targetType = OHDM_DB.OHDM_LINESTRING_GEOMTYPE;
                break;
            case OHDM_DB.POLYGON: 
                targetType = OHDM_DB.OHDM_POLYGON_GEOMTYPE;
                break;
        }
        
        return targetType;
    }
    
    @Override
    public boolean importPostProcessing(OSMElement element, boolean importUnnamedEntities) throws SQLException {
        // are there historic names?
        List<OldName> oldNames = element.getOldNames();
        if(oldNames == null) return false;
        
        /* we have a list of pairs like
        yyyy-yyyy oldName1
        yyyy-yyyy oldName2
        yyyy-yyyy oldName3
        whereas oldName1 can be identical to oldName3 or 2 or all can be 
        the same.. in that case, time spans must be combined...
        */

        int targetType = this.getTargetTypeInt(element);
        String classCodeString = element.getClassCodeString(); // just a guess, might have changed over time.
        int externalUserID = this.getOHDM_ID_ExternalUser(element);

        HashMap<String,String> newOldObject_Name_ID = new HashMap<>();
        
        Iterator<OldName> oldNamesIter = oldNames.iterator();        
        while(oldNamesIter.hasNext()) {
            OldName oldName = oldNamesIter.next();
            
            // each new old name is a new object
            
            // have we already created a new old object?
            String newOldOHDMID = newOldObject_Name_ID.get(oldName.oldname);
            
            if(newOldOHDMID == null) {
                // not yet created.. do it
                newOldOHDMID = this.addOHDMObject(oldName.oldname, 
                        this.getOHDM_ID_ExternalUser(element));
                
                // remember
                newOldObject_Name_ID.put(oldName.oldname, newOldOHDMID);
            }
            
            String targetIDString;
            if(element instanceof OSMRelation) {
                // relations can have more than one associated geometry
                OSMRelation relation = (OSMRelation) element;
                for(int i = 0; i < relation.getMemberSize(); i++) {
                    OSMElement member = relation.getMember(i);
                    
                    targetIDString = member.getOHDMGeomID();
                    this.addValidity(this.targetSelectQueue, targetType, 
                            classCodeString, newOldOHDMID, targetIDString, 
                            externalUserID, oldName.fromYear, oldName.toYear);
                }
            } else {
                targetIDString = element.getOHDMGeomID();
                if(targetIDString != null && targetIDString.length() > 0) {
                    this.addValidity(this.targetSelectQueue, targetType, 
                            classCodeString, newOldOHDMID, targetIDString, 
                            externalUserID, oldName.fromYear, oldName.toYear);
                }
            }
        }
        
        if(newOldObject_Name_ID.isEmpty()) return false;
        
        this.targetSelectQueue.forceExecute();
        
        return true;
    }
    
    private int idExternalSystemOSM = -1;
    private int getOHDM_ID_externalSystemOSM() {
        if(this.idExternalSystemOSM == -1) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("SELECT id FROM ");
                sb.append(DB.getFullTableName(targetSchema, OHDM_DB.EXTERNAL_SYSTEMS));
                sb.append(" where name = 'OSM' OR name = 'osm';");
                ResultSet result = 
                        this.executeQueryOnTarget(sb.toString());
                
                result.next();
                this.idExternalSystemOSM = result.getInt(1);

            } catch (SQLException ex) {
                Logger.getLogger(OHDMImporter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return this.idExternalSystemOSM;
    }

    boolean validUserID(String userID) {
        if(userID.equalsIgnoreCase("-1")) { 
            return false; 
        }
        
        return true;
    }
    
    
    private final HashMap<String, Integer> idExternalUsers = new HashMap<>();
    
    private int getOHDM_ID_ExternalUser(OSMElement ohdmElement) {
        // create user entry or find user primary key
        String externalUserID = ohdmElement.getUserID();
        String externalUsername = ohdmElement.getUsername();

        return this.getOHDM_ID_ExternalUser(externalUserID, 
                externalUsername);
    }
    
    private int getOHDM_ID_ExternalUser(String externalUserID, String externalUserName) {
        if(!this.validUserID(externalUserID)) return OHDM_DB.UNKNOWN_USER_ID;
        
        Integer idInteger = this.idExternalUsers.get(externalUserID);
        if(idInteger != null) { // already in memory
            return idInteger;
        }
        
        int osm_id = this.getOHDM_ID_externalSystemOSM();
        
        int ohdmID = -1; // -1 means failure
        try {
            // search in db
            // SELECT id from external_users where userid = '43566';
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT id from ");
            sb.append(DB.getFullTableName(this.targetSchema, OHDM_DB.EXTERNAL_USERS));
            sb.append(" where userid = '");
            sb.append(externalUserID);
            sb.append("' AND external_system_id = '");
            sb.append(osm_id);
            sb.append("';");
            
            ResultSet result = this.executeQueryOnTarget(sb.toString());
            
            if(result.next()) {
                // there is an entry
                ohdmID = result.getInt(1);

                // keep it
                this.idExternalUsers.put(externalUserID, ohdmID);
            } else {
                // there is no entry
                StringBuilder s = new StringBuilder();
                //SQLStatementQueue s = new SQLStatementQueue(this.targetConnection);
                s.append("INSERT INTO ");
                s.append(DB.getFullTableName(this.targetSchema, OHDM_DB.EXTERNAL_USERS));
                s.append(" (userid, username, external_system_id) VALUES ('");
                s.append(externalUserID);
                s.append("', '");
                s.append(externalUserName);
                s.append("', ");
                s.append(this.getOHDM_ID_externalSystemOSM());
                s.append(") RETURNING id;");
                //s.flush();
                
                ResultSet insertResult = this.executeQueryOnTarget(s.toString());
                insertResult.next();
                ohdmID = insertResult.getInt(1);
            }
        } catch (SQLException ex) {
            // TODO serious probleme
            System.err.println("thats a serious problem, cannot insert/select external user id: " + ex.getMessage());
        }
        
        return ohdmID;
        
    }
    
    String getOHDMObject(OSMElement osmElement, boolean namedEntitiesOnly) throws SQLException {
        // already in OHDM DB?
        String ohdmIDString = osmElement.getOHDMObjectID();
        if(ohdmIDString != null) return ohdmIDString;
        
        // add entry in object table
        try {
            // osm elements don't have necessarily a name. Does this one?
            if(!this.elementHasIdentity(osmElement)) {
                // no name - to be imported anyway?
                if(!namedEntitiesOnly) {
                    // yes - fetch osm dummy object
                    ohdmIDString = this.getOSMDummyObject_OHDM_ID();
                    osmElement.setOHDMObjectID(this.sourceUpdateQueue, ohdmIDString);
                    
                    return ohdmIDString;
                } else {
                    // no identity and we only accept entities with an identity.. null
                    return null;
                }
            } else {
                // object has an identity
                
                // create user entry or find user primary key
                String externalUserID = osmElement.getUserID();
                String externalUsername = osmElement.getUsername();

                int id_ExternalUser = this.getOHDM_ID_ExternalUser(externalUserID, 
                        externalUsername);

                ohdmIDString =  this.addOHDMObject(osmElement, id_ExternalUser);
            }
        
            return ohdmIDString;
        }
        catch(Exception e) {
            System.err.println("failure during node import: " + e.getClass().getName() + ":" + e.getMessage());
        }
        
        return null;
    }
    
    String addOHDMObject(OSMElement osmElement, int externalUserID) throws SQLException {
        // already in OHDM DB?
        String ohdmIDString = osmElement.getOHDMObjectID();
        if(ohdmIDString != null) return ohdmIDString;
        
//        SQLStatementQueue targetQueue = new SQLStatementQueue(this.targetConnection);
        
        String name = osmElement.getName();
        
        ohdmIDString = this.addOHDMObject(name, externalUserID);
        
        // remember in element
        osmElement.setOHDMObjectID(this.sourceUpdateQueue, ohdmIDString);
        
        return ohdmIDString;
    }
    
    String addOHDMObject(String name, int externalUserID) throws SQLException {
        SQLStatementQueue sql = new SQLStatementQueue(this.targetConnection);
        sql.append("INSERT INTO ");
        sql.append(DB.getFullTableName(this.targetSchema, OHDM_DB.GEOOBJECT));
        sql.append(" (name, source_user_id) VALUES ('");
        sql.append(name);
        sql.append("', ");
        sql.append(externalUserID);
        sql.append(") RETURNING id;");
        
        ResultSet result = sql.executeWithResult();
        result.next();
        return result.getBigDecimal(1).toString();
    }
    
    String addGeometry(OSMElement osmElement, int externalUserID) throws SQLException {
        String wkt = osmElement.getWKTGeometry();
        if(wkt == null || wkt.length() < 1) return null;
        
//        SQLStatementQueue targetQueue = new SQLStatementQueue(this.targetConnection);
        
        targetSelectQueue.append("INSERT INTO ");
        
        String fullTableName;
        
        switch(osmElement.getGeometryType()) {
            case OHDM_DB.POINT: 
                fullTableName = DB.getFullTableName(this.targetSchema, OHDM_DB.POINTS);
                targetSelectQueue.append(fullTableName);
                targetSelectQueue.append(" (point, ");
                break;
            case OHDM_DB.LINESTRING: 
                fullTableName = DB.getFullTableName(this.targetSchema, OHDM_DB.LINES);
                targetSelectQueue.append(fullTableName);
                targetSelectQueue.append(" (line, ");
                break;
            case OHDM_DB.POLYGON: 
                fullTableName = DB.getFullTableName(this.targetSchema, OHDM_DB.POLYGONS);
                targetSelectQueue.append(fullTableName);
                targetSelectQueue.append(" (polygon, ");
                break;
        }
        
        targetSelectQueue.append(" source_user_id) VALUES ('");
        
        targetSelectQueue.append(wkt);
        targetSelectQueue.append("', ");
        targetSelectQueue.append(externalUserID);
        targetSelectQueue.append(") RETURNING id;");

        try {
            ResultSet result = targetSelectQueue.executeWithResult();
            result.next();
            String geomIDString = result.getBigDecimal(1).toString();
            osmElement.setOHDMGeometryID(this.sourceUpdateQueue, geomIDString);
            return geomIDString;
        }
        catch(SQLException e) {
            System.err.println("failure when inserting geometry, wkt:\n" + wkt + "\nosm_id: " + osmElement.getOSMIDString());
            throw e;
        }
    }
    
    

    void addValidity(OSMElement osmElement, String ohdmIDString, String ohdmGeomIDString, int externalUserID) throws SQLException {
        // what table is reference by id_geometry
        int targetType = 0;
        switch(osmElement.getGeometryType()) {
            case OHDM_DB.POINT: 
                targetType = OHDM_DB.OHDM_POINT_GEOMTYPE;
                break;
            case OHDM_DB.LINESTRING: 
                targetType = OHDM_DB.OHDM_LINESTRING_GEOMTYPE;
                break;
            case OHDM_DB.POLYGON: 
                targetType = OHDM_DB.OHDM_POLYGON_GEOMTYPE;
                break;
        }
        
        this.addValidity(this.targetInsertQueue, osmElement, targetType, 
                osmElement.getClassCodeString(), ohdmIDString, 
                ohdmGeomIDString, externalUserID);
        
//        this.targetSelectQueue.forceExecute();
        this.targetInsertQueue.couldExecute();
    }
    
    private String formatDateString(String sinceValue) {
        // assume we have only got the year
        if(sinceValue.length() == 4) {
            return sinceValue + "-01-01";
        } 
        
        // TODO more..
        
        return null;
    }
    
    void addValidity(SQLStatementQueue sq, OSMElement osmElement, int targetType, 
            String classCodeString, String sourceIDString, 
            String targetIDString, int externalUserID) throws SQLException {
        
        String sinceString = null;
        /* 
        is there since tag in osm origin?
        probably not .. haven't not yet proposed that tag 
        */
        String sinceValue = osmElement.getValue("since");
        if(sinceValue != null) {
            sinceString = this.formatDateString(sinceValue);
        } 

        // take osm timestamp
        if(sinceString == null) {
            sinceString = osmElement.getTimeStampString();
        }
        
        // finally take default
        if(sinceString == null) {
            sinceString = this.defaultSince;
        }
        
        this.addValidity(sq, targetType, classCodeString, sourceIDString, targetIDString, externalUserID, sinceString, this.defaultUntil);
    }
    
    void addValidity(SQLStatementQueue sq, int targetType, 
            String classCodeString, String sourceIDString, 
            String targetIDString, int externalUserID, String sinceString, 
            String untilString) throws SQLException {
        
        if(sourceIDString == null) {
            // failure
            System.err.println("source id must not be null");
        }
        
        sq.append("INSERT INTO ");
        sq.append(DB.getFullTableName(this.targetSchema, OHDM_DB.GEOOBJECT_GEOMETRY));
        sq.append(" (type_target, classification_id, id_geoobject_source, id_target, valid_since, valid_until, source_user_id) VALUES (");

        sq.append(targetType);
        sq.append(", ");
        sq.append(classCodeString);
        sq.append(", ");
        sq.append(sourceIDString);
        sq.append(", ");
        sq.append(targetIDString);
        sq.append(", '");
        sq.append(sinceString);
        sq.append("', '"); 
        sq.append(untilString);
        sq.append("', "); // until
        sq.append(externalUserID);
        sq.append(");");
    }
    
    void addContentAndURL(OSMElement osmElement, String ohdmIDString) {
        SQLStatementQueue sql = this.targetInsertQueue;
        
        String description = osmElement.getValue("description");
        
        /*
        INSERT INTO ohdm.content(id, name, value, mimetype)
            VALUES (42, 'description', 'huhu', 'text/plain');
        */
//        if(description != null) {
//            sql.append("INSERT INTO ");
//            sql.append(DB.getFullTableName(this.targetSchema, OHDM_DB.CONTENT));
//            sql.append(" (name, source_user_id) VALUES ('");
//            sql.append(name);
//            sql.append("', ");
//            sql.append(externalUserID);
//            sql.append(") RETURNING id;");
//        }
        
        /*
         * URLs
        image
        url
        website
         */
//        SQLStatementQueue sq = new SQLStatementQueue(this.targetConnection);
    }
    
    private boolean elementHasIdentity(OSMElement ohdmElement) {
        String name = ohdmElement.getName();
        
        // must have a name
        if(name == null || name.length() < 1) return false;
        
        // name must not be a single number
        try {
            Integer.parseInt(name);
            
            // it's a number and only a number
            return false;
        }
        catch(NumberFormatException e) {
            // that's ok - no number.. go ahead
        }
        
        return true;
    }
    
    /**
     * 
     * @param osmElement
     * @return ohdm_id as string
     */
    public String importOSMElement(OSMElement osmElement, boolean namedEntitiesOnly, boolean importWithoutGeometry) {
        String osmID = osmElement.getOSMIDString();
        if(osmID.equalsIgnoreCase("188276804") || osmID.equalsIgnoreCase("301835391")) {
            // debug break
            int i = 42;
        }
        
        // except relations, entities without a geometry are not to be imported
        if(!osmElement.hasGeometry() && !importWithoutGeometry) {
            return null;
        }
        
        try {
            // get external user id from ohdm
            int id_ExternalUser = this.getOHDM_ID_ExternalUser(osmElement);

            /*
              get ohdm object. That call returns null only if that object has no
            identity and importing of unnamed entities is not yet wished in that call
            null indicates a failure
            */
            String ohdmObjectIDString = this.getOHDMObject(osmElement, namedEntitiesOnly);

            if(ohdmObjectIDString == null) {
                /*
                System.err.println("cannot create or find ohdm object id (not even dummy osm) and importing of unnamed entites allowed");
                System.err.println(osmElement);
                */

                // try to add a geometry
                String ohdmGeomIDString = this.addGeometry(osmElement, id_ExternalUser);
                
                if(ohdmGeomIDString != null) {
                    // remeber geometry in inter db
                    this.intermediateDB.setOHDM_IDs(this.sourceUpdateQueue, osmElement, null, ohdmGeomIDString);
                }

                // geometry added but no object.. we are finished here
                return null;
            }

            // we have an object.. try to add geometry
            
            // try to add a geometry
            String ohdmGeomIDString = this.addGeometry(osmElement, id_ExternalUser);
            
            // combine object and geometry if there is a geometry
            if(ohdmGeomIDString != null) {
                // create entry in object_geometry table
                this.addValidity(osmElement, ohdmObjectIDString, ohdmGeomIDString, 
                        id_ExternalUser);
                
                /* now make both object and geom id persistent to intermediate db
                */
                this.intermediateDB.setOHDM_IDs(this.sourceUpdateQueue, osmElement, ohdmObjectIDString, ohdmGeomIDString);
            }

            // keep some special tags (url etc, see wiki) for the object
            this.addContentAndURL(osmElement, ohdmObjectIDString);
            
            return ohdmObjectIDString;
        }
        catch(Exception e) {
            System.err.println("failure during import of intermediate object: " + e.getMessage());
            System.err.println(osmElement);
        }
        
        return null;
    }
    
    void forgetPreviousNodesImport() throws SQLException {
        SQLStatementQueue sql = new SQLStatementQueue(this.sourceConnection);

        System.out.println("reset nodes entries in intermediate db");
        sql.append("UPDATE ");
        sql.append(DB.getFullTableName(this.sourceSchema, "nodes"));
        sql.append(" SET ohdm_geom_id=null, ohdm_object_id=null;");
        sql.forceExecute(true);
        
        sql.join();
    }
      
    void forgetPreviousWaysImport() throws SQLException {
        SQLStatementQueue sql = new SQLStatementQueue(this.sourceConnection);

        System.out.println("reset ways entries in intermediate db");
        sql.append("UPDATE ");
        sql.append(DB.getFullTableName(this.sourceSchema, "ways"));
        sql.append(" SET ohdm_geom_id=null, ohdm_object_id=null;");
        sql.forceExecute(true);
        
        sql.join();
    }
      
    void forgetPreviousRelationsImport() throws SQLException {
        SQLStatementQueue sql = new SQLStatementQueue(this.sourceConnection);

        System.out.println("reset relations entries in intermediate db");
        sql.append("UPDATE ");
        sql.append(DB.getFullTableName(this.sourceSchema, "relations"));
        sql.append(" SET ohdm_geom_id=null, ohdm_object_id=null;");
        sql.forceExecute();
        
        sql.join();
    }
    
    public static void main(String args[]) throws IOException, SQLException {
        // let's fill OHDM database
        System.out.println("Start importing ODHM data from intermediate DB");
        SQLStatementQueue sourceQueue = null;
        
        Trigger trigger = null;
        OSMExtractor extractor = null;
        OHDMImporter ohdmImporter = null;
        SQLStatementQueue targetQueue = null;
        
        try {
            String sourceParameterFileName = "db_inter.txt";
            String targetParameterFileName = "db_ohdm.txt";
            
            if(args.length > 0) {
                sourceParameterFileName = args[0];
            }
            
            if(args.length > 1) {
                targetParameterFileName = args[1];
            }
            
            Parameter sourceParameter = new Parameter(sourceParameterFileName);
            Parameter targetParameter = new Parameter(targetParameterFileName);
            
            Connection sourceConnection = DB.createConnection(sourceParameter);
            Connection targetConnection = DB.createConnection(targetParameter);
            
            IntermediateDB iDB = new IntermediateDB(sourceConnection, sourceParameter.getSchema());
            
            String sourceSchema = sourceParameter.getSchema();
            String targetSchema = targetParameter.getSchema();
            
            SQLStatementQueue updateQueue = null;
            FileSQLStatementQueue fileUpdateQueue = null;
            
            String currentUpdateFileName = "updateNodes.sql";
            File updateCommmands = new File(currentUpdateFileName);
            
            if(sourceParameter.usePSQL()) {
                // create File to keep update commands
                System.out.println("intermediate update queue uses psql and sql files.");
                fileUpdateQueue = new FileSQLStatementQueue(updateCommmands);
                updateQueue = fileUpdateQueue;
            } else {
                System.out.println("intermediate update queue uses jdbc");
                updateQueue = new SQLStatementQueue(sourceParameter);
            }
            
            ohdmImporter = new OHDMImporter(iDB, sourceConnection, 
                    targetConnection, sourceSchema, targetSchema, updateQueue);

            /* TODO: remove those separation between nodes, way and relations.
            does not work in practice
            */
            try {
                if(targetParameter.forgetPreviousImport()) {
                    System.out.println("remove ohdm entries in intermediate database");            
                    
                    if(targetParameter.importNodes()) {
                        ohdmImporter.forgetPreviousNodesImport();
                    }
                    if(targetParameter.importWays()) {
                        ohdmImporter.forgetPreviousWaysImport();
                    }
                    if(targetParameter.importRelations()) {
                        ohdmImporter.forgetPreviousRelationsImport();
                    }
                }
                
                if(targetParameter.importNodes() && 
                        targetParameter.importWays() &&
                        targetParameter.importRelations()) {
                    // remove all
                    OHDM_DB.dropOHDMTables(targetConnection, targetSchema);
                } else {
                    // drop only parts
                    if(targetParameter.importNodes()) {
                        OHDM_DB.dropNodeTables(targetConnection, targetSchema);
                    }
                    if(targetParameter.importWays()) {
                        OHDM_DB.dropWayTables(targetConnection, targetSchema);
                    }
                    if(targetParameter.importRelations()) {
                        OHDM_DB.dropRelationTables(targetConnection, targetSchema);
                    }
                }
            }
            catch(Exception e) {
                System.err.println("problems during setting old data (non-fatal): " + e.getLocalizedMessage());
            }
            
            OHDM_DB.createOHDMTables(targetConnection, targetSchema);
            
            String stepLenString = sourceParameter.getReadStepLen();
            int stepLen = 10000;
            try {
                if(stepLenString != null) {
                    stepLen = Integer.parseInt(stepLenString);
                }
            }
            catch(NumberFormatException e) {
                    // ignore and work with default
            }

            extractor = new OSMExtractor(sourceConnection, sourceSchema, ohdmImporter, stepLen);
            
            System.out.println("intermediate select queue uses jdbc");
            sourceQueue = DB.createSQLStatementQueue(sourceConnection, sourceParameter);
            
            System.out.println("ohdm insert queue uses jdbc");
            targetQueue = new SQLStatementQueue(targetParameter);
            
            System.out.println("start insert data into ohdm DB from intermediate DB");
        
            // start stats trigger each [interval] minutes (default 5)
            int logMessageInterval = targetParameter.getLogMessageInterval();
            trigger = new Trigger(extractor, 1000 * 60 * logMessageInterval);
            trigger.start();
            
            if(targetParameter.importNodes()) {
                extractor.processNodes(sourceQueue, true);
                
                if(fileUpdateQueue != null) {
                    // cut off update stream and let psql process that stuff
                    String nextFileName = "updateWays.sql";
                    updateCommmands = new File(nextFileName);
                    fileUpdateQueue.switchFile(updateCommmands);

                    trigger.setMilliseconds(1000 * 60 * 30); // 30 minutes
                    // now process stored updates... that process must be performed before processing ways or relations
                    System.out.println("psql is executing node update commands..");
                    Util.feedPSQL(sourceParameter, currentUpdateFileName, false, true);
                    System.out.println("..done");
                    trigger.setMilliseconds(1000 * 60 * 5); // 5 minutes again
                    trigger.interrupt();

                    // remember new update filename
                    currentUpdateFileName = nextFileName;
                }
            } else {
                System.out.println("skip nodes import.. see importNodes in ohdm parameter file");
            }
            
            if(targetParameter.importWays()) {
                extractor.processWays(sourceQueue, false);
                
                if(fileUpdateQueue != null) {
                    // cut off update stream and let psql process that stuff
                    String nextFileName = "updateRelations.sql";
                    updateCommmands = new File(nextFileName);
                    fileUpdateQueue.switchFile(updateCommmands);

                    trigger.setMilliseconds(1000 * 60 * 30); // 30 minutes
                    // now process stored updates... that process must be performed before processing ways or relations
                    System.out.println("psql is executing way update commands..");
                    Util.feedPSQL(sourceParameter, currentUpdateFileName, false, true);
                    System.out.println("..done");
                    trigger.setMilliseconds(1000 * 60 * 5); // 5 minutes
                    trigger.interrupt();

                    // remember new update filename
                    currentUpdateFileName = nextFileName;
                }
            } else {
                System.out.println("skip ways import.. see importWays in ohdm parameter file");
            }

            if(targetParameter.importRelations()) {
                extractor.processRelations(sourceQueue, false);
                
                if(fileUpdateQueue != null) {
                    // close update stream and let psql process that stuff
                    fileUpdateQueue.close();

                    // now process stored updates... that process must be performed before processing ways or relations
                    trigger.setMilliseconds(1000 * 60 * 30); // 30 minutes
                    System.out.println("psql is executing relation update commands..");
                    Util.feedPSQL(sourceParameter, currentUpdateFileName, false, true);
                    System.out.println("..done");
                    trigger.setMilliseconds(1000 * 60 * 5); // 5 minutes
                    trigger.interrupt();
                }
            } else {
                System.out.println("skip relations import.. see importRelations in ohdm parameter file");
            }

        } catch (IOException | SQLException e) {
            Util.printExceptionMessage(e, sourceQueue, "main method in Inter2OHDM", false);
        }
        finally {
            if(trigger != null) {
                trigger.end();
                trigger.interrupt();
            }
            
            if(targetQueue != null) {
                targetQueue.forceExecute();
                targetQueue.join();
            }
            if(ohdmImporter != null) ohdmImporter.close();
            System.out.println("done importing from intermediate DB to ohdm DB");
            System.out.println(extractor.getStatistics());
        }
    }

    private final String osmDummyObjectOHDM_ID = "0";
    
    private String getOSMDummyObject_OHDM_ID() {
        return this.osmDummyObjectOHDM_ID;
    }

    private boolean saveRelationAsRelatedObjects(OSMRelation relation, 
            String ohdmIDString) throws SQLException {
        
        // get all ohdm ids and store it
        StringBuilder sq = new StringBuilder();

        /**
         * INSERT INTO [geoobject_geometry] 
         * (id_geoobject_source, id_target, type_target, valid_since, 
         * valid_until VALUES (..)
         */

        sq.append("INSERT INTO ");
        sq.append(OHDM_DB.GEOOBJECT_GEOMETRY);
        sq.append("(id_geoobject_source, id_target, type_target, role,");
        sq.append(" classification_id, valid_since, valid_until) VALUES ");

        boolean notFirstSet = false;
        for(int i = 0; i < relation.getMemberSize(); i++) {
            OSMElement member = relation.getMember(i);
            String memberOHDMObjectIDString = this.getOHDMObject(member, true);
            if(memberOHDMObjectIDString == null) continue; // no identity

            // get role of that member in that relation
            String roleName = relation.getRoleName(i);

            if(notFirstSet) {
                sq.append(", ");
            } else {
                notFirstSet = true;
            }

            sq.append("(");
            sq.append(ohdmIDString); // id source
            sq.append(", ");
            sq.append(memberOHDMObjectIDString); // id target
            sq.append(", ");
            if(member instanceof OSMNode) { // type_target
                sq.append(OHDM_DB.OHDM_POINT_GEOMTYPE);
            } else if(member instanceof OSMWay) {
                sq.append(OHDM_DB.OHDM_LINESTRING_GEOMTYPE);
            } else {
                sq.append(OHDM_DB.OHDM_GEOOBJECT_GEOMTYPE);
            }
            sq.append(", '");
            sq.append(roleName); // role
            sq.append("', ");
            sq.append(relation.getClassCodeString()); // classification
            sq.append(", '");
            sq.append(this.defaultSince); // since
            sq.append("', '");
            sq.append(this.defaultUntil); // until
            sq.append("')"); // end that value set
        }
        sq.append(";"); // end that value set

        if(notFirstSet) {
            // there is at least one value set - excecute
            SQLStatementQueue sql = new SQLStatementQueue(this.targetConnection);
            sql.append(sq.toString());
            sql.forceExecute();
            return true;
        }
        return false;
    }

    private boolean saveRelationAsMultipolygon(OSMRelation relation) throws SQLException {
        /* sometimes (actually quite often) relations contain only an inner
        and outer member but inner comes first which is not compatible with
        definition of a multipolygon.. We correct that problem here
        */
        
        /* 
            if a multipolygon relation has only two member, inner and outer,
            bring them into right order.
        */
        if(!relation.checkMultipolygonMemberOrder()) return false;
        
        // debugging stop
        if(relation.getOSMIDString().equalsIgnoreCase("3323433")) {
            int i = 42;
        }
        
        // option b) it is a polygone or probably a multipolygon
        ArrayList<String> polygonIDs = new ArrayList<>();
        ArrayList<String> polygonWKT = new ArrayList<>();
        
        ArrayList<OSMElement> waysWithIdentity = new ArrayList<>();
        ArrayList<OSMElement> nodesWithIdentity = new ArrayList<>();
        
        if(!relation.fillRelatedGeometries(polygonIDs, polygonWKT, 
                waysWithIdentity, nodesWithIdentity)) return false;
        
        /* we have two list with either references to existing
         geometries or to string representing geometries which are 
        to be stored and referenced.
        */
//        SQLStatementQueue targetQueue = new SQLStatementQueue(this.targetConnection);
        for(int i = 0; i < polygonIDs.size(); i++) {
            String pID = polygonIDs.get(i);
            if(pID.equalsIgnoreCase("-1")) {
                // this geometry is not yet in the database.. insert that polygon
                targetSelectQueue.append("INSERT INTO ");
                targetSelectQueue.append(DB.getFullTableName(this.targetSchema, OHDM_DB.POLYGONS));
                targetSelectQueue.append(" (polygon, source_user_id) VALUES ('");
                targetSelectQueue.append("SRID=4326;"); // make it an ewkt
                targetSelectQueue.append(polygonWKT.get(i));
                targetSelectQueue.append("', ");
                int ohdmUserID = this.getOHDM_ID_ExternalUser(relation);
                targetSelectQueue.append(ohdmUserID);
                targetSelectQueue.append(") RETURNING ID;");
                
//                sq.print("saving polygon wkt");
                try {
                    ResultSet polygonInsertResult = targetSelectQueue.executeWithResult();
                    polygonInsertResult.next();
                    String geomIDString = polygonInsertResult.getBigDecimal(1).toString();
                    polygonIDs.set(i, geomIDString);
                }
                catch(SQLException e) {
                    System.err.println("sql failed: " + targetSelectQueue.toString());
                    throw e;
                }
            }
        }

        if(polygonIDs.size() < 1) return false;
        
        // add relations
        int targetType = OHDM_DB.OHDM_POLYGON_GEOMTYPE; // all targets are polygons
        String classCodeString = relation.getClassCodeString();
        String sourceIDString = relation.getOHDMObjectID();
        
        if(sourceIDString == null) {
            // debug stop
            int i = 42;
        }
        int externalUserID = this.getOHDM_ID_ExternalUser(relation);
        
        // void addValidity(int targetType, String classCodeString, String sourceIDString, String targetIDString, int externalUserID) throws SQLException {
        for(String targetIDString : polygonIDs) {
            this.addValidity(targetSelectQueue, relation, targetType, classCodeString, sourceIDString, targetIDString, externalUserID);
        }
        
        // fill subsequent table if necessary
        if(!waysWithIdentity.isEmpty()) {
            this.saveSubsequentObjects(targetSelectQueue, relation.getOHDMObjectID(), 
                    OHDM_DB.LINESTRING, waysWithIdentity.iterator());
        }
        
        if(!nodesWithIdentity.isEmpty()) {
            this.saveSubsequentObjects(targetSelectQueue, relation.getOHDMObjectID(), 
                    OHDM_DB.POINT, nodesWithIdentity.iterator());
        }
        
        try {
            targetSelectQueue.forceExecute(true);
        }
        catch(SQLException se) {
            Util.printExceptionMessage(se, targetSelectQueue, "when writing relation tables", false);
        }
        return true;
    }

    void forgetPreviousImport() throws SQLException {
        this.forgetPreviousNodesImport();
        this.forgetPreviousWaysImport();
        this.forgetPreviousWaysImport();
    }
}