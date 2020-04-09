package ohdm2mapnik;

import ohdm2mapnik.GeoObject.Line;
import ohdm2mapnik.GeoObject.Point;
import ohdm2mapnik.GeoObject.Polygon;
import util.DB;
import util.Parameter;
import util.SQLStatementQueue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OHDM2MapnikTables {

    // chunk size to import at once
    private static int chunkSize = 10000;

    public static void main(String[] args) throws SQLException, IOException {
        String sourceParameterFileName = "db_ohdm.txt";
        String targetParameterFileName = "db_mapnik.txt";

        if (args.length > 0) {
            sourceParameterFileName = args[0];
        }

        if (args.length > 1) {
            targetParameterFileName = args[1];
        }

        Parameter sourceParameter = new Parameter(sourceParameterFileName);
        Parameter targetParameter = new Parameter(targetParameterFileName);

        Connection connection = DB.createConnection(targetParameter);

        String targetSchema = targetParameter.getSchema();

        String sourceSchema = sourceParameter.getSchema();

        SQLStatementQueue sql = new SQLStatementQueue(connection);
        OHDM2MapnikTables mapnikTables = new OHDM2MapnikTables();

        mapnikTables.setupMapnikDB(sql, targetSchema);
        mapnikTables.convertDatabase(sql, targetSchema, sourceSchema);


        System.out.println("Mapnik tables creation finished");
    }

    void setupMapnikDB(SQLStatementQueue sql, String targetSchema) {
        /*
        create mapnik database schema
         */

        // points
        sql.append("DROP TABLE IF EXISTS " + targetSchema + ".planet_osm_point CASCADE;" +
                "" +
                "CREATE TABLE " + targetSchema + ".planet_osm_point" +
                "(" +
                "    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "    osm_id bigint," +
                "    version integer," +
                "    visible boolean," +
                "    geoobject bigint," +
                "    access text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housename\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housenumber\" text COLLATE pg_catalog.\"default\"," +
                "    admin_level text COLLATE pg_catalog.\"default\"," +
                "    aerialway text COLLATE pg_catalog.\"default\"," +
                "    aeroway text COLLATE pg_catalog.\"default\"," +
                "    amenity text COLLATE pg_catalog.\"default\"," +
                "    barrier text COLLATE pg_catalog.\"default\"," +
                "    boundary text COLLATE pg_catalog.\"default\"," +
                "    building text COLLATE pg_catalog.\"default\"," +
                "    highway text COLLATE pg_catalog.\"default\"," +
                "    historic text COLLATE pg_catalog.\"default\"," +
                "    junction text COLLATE pg_catalog.\"default\"," +
                "    landuse text COLLATE pg_catalog.\"default\"," +
                "    layer integer," +
                "    leisure text COLLATE pg_catalog.\"default\"," +
                "    lock text COLLATE pg_catalog.\"default\"," +
                "    man_made text COLLATE pg_catalog.\"default\"," +
                "    military text COLLATE pg_catalog.\"default\"," +
                "    name text COLLATE pg_catalog.\"default\"," +
                "    \"natural\" text COLLATE pg_catalog.\"default\"," +
                "    oneway text COLLATE pg_catalog.\"default\"," +
                "    place text COLLATE pg_catalog.\"default\"," +
                "    power text COLLATE pg_catalog.\"default\"," +
                "    railway text COLLATE pg_catalog.\"default\"," +
                "    ref text COLLATE pg_catalog.\"default\"," +
                "    religion text COLLATE pg_catalog.\"default\"," +
                "    shop text COLLATE pg_catalog.\"default\"," +
                "    tourism text COLLATE pg_catalog.\"default\"," +
                "    water text COLLATE pg_catalog.\"default\"," +
                "    waterway text COLLATE pg_catalog.\"default\"," +
                "    tags hstore," +
                "    way geometry(Point,3857)," +
                "    valid_since date," +
                "    valid_until date" +
                ");");

        // lines
        sql.append("DROP TABLE IF EXISTS " + targetSchema + ".planet_osm_line CASCADE;" +
                "" +
                "CREATE TABLE " + targetSchema + ".planet_osm_line" +
                "(" +
                "\tid bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "    osm_id bigint," +
                "    version integer," +
                "    visible boolean," +
                "    geoobject bigint," +
                "    access text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housename\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housenumber\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:interpolation\" text COLLATE pg_catalog.\"default\"," +
                "    admin_level text COLLATE pg_catalog.\"default\"," +
                "    aerialway text COLLATE pg_catalog.\"default\"," +
                "    aeroway text COLLATE pg_catalog.\"default\"," +
                "    amenity text COLLATE pg_catalog.\"default\"," +
                "    barrier text COLLATE pg_catalog.\"default\"," +
                "    bicycle text COLLATE pg_catalog.\"default\"," +
                "    bridge text COLLATE pg_catalog.\"default\"," +
                "    boundary text COLLATE pg_catalog.\"default\"," +
                "    building text COLLATE pg_catalog.\"default\"," +
                "    construction text COLLATE pg_catalog.\"default\"," +
                "    covered text COLLATE pg_catalog.\"default\"," +
                "    foot text COLLATE pg_catalog.\"default\"," +
                "    highway text COLLATE pg_catalog.\"default\"," +
                "    historic text COLLATE pg_catalog.\"default\"," +
                "    horse text COLLATE pg_catalog.\"default\"," +
                "    junction text COLLATE pg_catalog.\"default\"," +
                "    landuse text COLLATE pg_catalog.\"default\"," +
                "    layer integer," +
                "    leisure text COLLATE pg_catalog.\"default\"," +
                "    lock text COLLATE pg_catalog.\"default\"," +
                "    man_made text COLLATE pg_catalog.\"default\"," +
                "    military text COLLATE pg_catalog.\"default\"," +
                "    name text COLLATE pg_catalog.\"default\"," +
                "    \"natural\" text COLLATE pg_catalog.\"default\"," +
                "    oneway text COLLATE pg_catalog.\"default\"," +
                "    place text COLLATE pg_catalog.\"default\"," +
                "    power text COLLATE pg_catalog.\"default\"," +
                "    railway text COLLATE pg_catalog.\"default\"," +
                "    ref text COLLATE pg_catalog.\"default\"," +
                "    religion text COLLATE pg_catalog.\"default\"," +
                "    route text COLLATE pg_catalog.\"default\"," +
                "    service text COLLATE pg_catalog.\"default\"," +
                "    shop text COLLATE pg_catalog.\"default\"," +
                "    surface text COLLATE pg_catalog.\"default\"," +
                "    tourism text COLLATE pg_catalog.\"default\"," +
                "    tracktype text COLLATE pg_catalog.\"default\"," +
                "    tunnel text COLLATE pg_catalog.\"default\"," +
                "    water text COLLATE pg_catalog.\"default\"," +
                "    waterway text COLLATE pg_catalog.\"default\"," +
                "    way_area double precision," +
                "    z_order integer," +
                "    tags hstore," +
                "    way geometry(LineString,3857)," +
                "    valid_since date," +
                "    valid_until date" +
                ");");

        // roads
        sql.append("DROP TABLE IF EXISTS " + targetSchema + ".planet_osm_roads CASCADE;" +
                "" +
                "CREATE TABLE " + targetSchema + ".planet_osm_roads" +
                "(" +
                "    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "    osm_id bigint," +
                "    version integer," +
                "    visible boolean," +
                "    geoobject bigint," +
                "    access text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housename\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housenumber\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:interpolation\" text COLLATE pg_catalog.\"default\"," +
                "    admin_level text COLLATE pg_catalog.\"default\"," +
                "    aerialway text COLLATE pg_catalog.\"default\"," +
                "    aeroway text COLLATE pg_catalog.\"default\"," +
                "    amenity text COLLATE pg_catalog.\"default\"," +
                "    barrier text COLLATE pg_catalog.\"default\"," +
                "    bicycle text COLLATE pg_catalog.\"default\"," +
                "    bridge text COLLATE pg_catalog.\"default\"," +
                "    boundary text COLLATE pg_catalog.\"default\"," +
                "    building text COLLATE pg_catalog.\"default\"," +
                "    construction text COLLATE pg_catalog.\"default\"," +
                "    covered text COLLATE pg_catalog.\"default\"," +
                "    foot text COLLATE pg_catalog.\"default\"," +
                "    highway text COLLATE pg_catalog.\"default\"," +
                "    historic text COLLATE pg_catalog.\"default\"," +
                "    horse text COLLATE pg_catalog.\"default\"," +
                "    junction text COLLATE pg_catalog.\"default\"," +
                "    landuse text COLLATE pg_catalog.\"default\"," +
                "    layer integer," +
                "    leisure text COLLATE pg_catalog.\"default\"," +
                "    lock text COLLATE pg_catalog.\"default\"," +
                "    man_made text COLLATE pg_catalog.\"default\"," +
                "    military text COLLATE pg_catalog.\"default\"," +
                "    name text COLLATE pg_catalog.\"default\"," +
                "    \"natural\" text COLLATE pg_catalog.\"default\"," +
                "    oneway text COLLATE pg_catalog.\"default\"," +
                "    place text COLLATE pg_catalog.\"default\"," +
                "    power text COLLATE pg_catalog.\"default\"," +
                "    railway text COLLATE pg_catalog.\"default\"," +
                "    ref text COLLATE pg_catalog.\"default\"," +
                "    religion text COLLATE pg_catalog.\"default\"," +
                "    route text COLLATE pg_catalog.\"default\"," +
                "    service text COLLATE pg_catalog.\"default\"," +
                "    shop text COLLATE pg_catalog.\"default\"," +
                "    surface text COLLATE pg_catalog.\"default\"," +
                "    tourism text COLLATE pg_catalog.\"default\"," +
                "    tracktype text COLLATE pg_catalog.\"default\"," +
                "    tunnel text COLLATE pg_catalog.\"default\"," +
                "    water text COLLATE pg_catalog.\"default\"," +
                "    waterway text COLLATE pg_catalog.\"default\"," +
                "    way_area double precision," +
                "    z_order integer," +
                "    tags hstore," +
                "    way geometry(LineString,3857)," +
                "    valid_since date," +
                "    valid_until date" +
                ");");

        // polygons
        sql.append("DROP TABLE IF EXISTS " + targetSchema + ".planet_osm_polygon CASCADE;" +
                "" +
                "CREATE TABLE " + targetSchema + ".planet_osm_polygon" +
                "(" +
                "    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY," +
                "    osm_id bigint," +
                "    version integer," +
                "    visible boolean," +
                "    geoobject bigint," +
                "    access text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housename\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:housenumber\" text COLLATE pg_catalog.\"default\"," +
                "    \"addr:interpolation\" text COLLATE pg_catalog.\"default\"," +
                "    admin_level text COLLATE pg_catalog.\"default\"," +
                "    aerialway text COLLATE pg_catalog.\"default\"," +
                "    aeroway text COLLATE pg_catalog.\"default\"," +
                "    amenity text COLLATE pg_catalog.\"default\"," +
                "    barrier text COLLATE pg_catalog.\"default\"," +
                "    bicycle text COLLATE pg_catalog.\"default\"," +
                "    bridge text COLLATE pg_catalog.\"default\"," +
                "    boundary text COLLATE pg_catalog.\"default\"," +
                "    building text COLLATE pg_catalog.\"default\"," +
                "    construction text COLLATE pg_catalog.\"default\"," +
                "    covered text COLLATE pg_catalog.\"default\"," +
                "    foot text COLLATE pg_catalog.\"default\"," +
                "    highway text COLLATE pg_catalog.\"default\"," +
                "    historic text COLLATE pg_catalog.\"default\"," +
                "    horse text COLLATE pg_catalog.\"default\"," +
                "    junction text COLLATE pg_catalog.\"default\"," +
                "    landuse text COLLATE pg_catalog.\"default\"," +
                "    layer integer," +
                "    leisure text COLLATE pg_catalog.\"default\"," +
                "    lock text COLLATE pg_catalog.\"default\"," +
                "    man_made text COLLATE pg_catalog.\"default\"," +
                "    military text COLLATE pg_catalog.\"default\"," +
                "    name text COLLATE pg_catalog.\"default\"," +
                "    \"natural\" text COLLATE pg_catalog.\"default\"," +
                "    oneway text COLLATE pg_catalog.\"default\"," +
                "    place text COLLATE pg_catalog.\"default\"," +
                "    power text COLLATE pg_catalog.\"default\"," +
                "    railway text COLLATE pg_catalog.\"default\"," +
                "    ref text COLLATE pg_catalog.\"default\"," +
                "    religion text COLLATE pg_catalog.\"default\"," +
                "    route text COLLATE pg_catalog.\"default\"," +
                "    service text COLLATE pg_catalog.\"default\"," +
                "    shop text COLLATE pg_catalog.\"default\"," +
                "    surface text COLLATE pg_catalog.\"default\"," +
                "    tourism text COLLATE pg_catalog.\"default\"," +
                "    tracktype text COLLATE pg_catalog.\"default\"," +
                "    tunnel text COLLATE pg_catalog.\"default\"," +
                "    water text COLLATE pg_catalog.\"default\"," +
                "    waterway text COLLATE pg_catalog.\"default\"," +
                "    way_area double precision," +
                "    z_order integer," +
                "    tags hstore," +
                "    way geometry(Geometry,3857)," +
                "    valid_since date," +
                "    valid_until date" +
                ")");

        try {
            sql.forceExecute();
        } catch (SQLException e) {
            System.err.println("exception ignored: " + e);
        }

        System.out.println("Mapnik tables created!");
    }

    void convertDatabase(SQLStatementQueue sql, String targetSchema, String sourceSchema) throws SQLException {
        // count rows
        int pointsRows = this.countRows(sql, sourceSchema, "point");
        int linesRows = this.countRows(sql, sourceSchema, "line");
        int polygonsRows = this.countRows(sql, sourceSchema, "polygon");

        // start import
        this.convertPoints(sql, targetSchema, sourceSchema, pointsRows);
        this.convertLines(sql, targetSchema, sourceSchema, linesRows);
        this.convertPolygons(sql, targetSchema, sourceSchema, polygonsRows);
    }

    /**
     * Count all rows for a geometry
     *
     * @param sql
     * @param sourceSchema
     * @param geometry
     * @return rows to convert for a geometry
     */
    private int countRows(SQLStatementQueue sql, String sourceSchema, String geometry) throws SQLException {

        String whereClause = "";

        if (geometry == "point") {
            whereClause = "geoobject_geometry.type_target = 0 or geoobject_geometry.type_target = 1";
        } else if (geometry == "line") {
            whereClause = "geoobject_geometry.type_target = 2";
        } else {
            // polygon
            whereClause = "geoobject_geometry.type_target = 3";
        }

        sql.append("SELECT COUNT(" + geometry + "s.id) ");
        sql.append("FROM " + sourceSchema + "." + geometry + "s ");
        sql.append("INNER JOIN " + sourceSchema + ".geoobject_geometry ON " + geometry + "s.id = geoobject_geometry.id_target ");
        sql.append("INNER JOIN " + sourceSchema + ".geoobject ON geoobject_geometry.id_geoobject_source=geoobject.id ");
        sql.append("INNER JOIN " + sourceSchema + ".classification ON geoobject_geometry.classification_id=classification.id ");
        sql.append("WHERE " + whereClause + ";");

        ResultSet result = null;
        try {
            result = sql.executeWithResult();
        } catch (SQLException e) {
            System.err.println("exception ignored: " + e);
        }

        while (result.next()) {
            return result.getInt(1); // counted rows
        }

        return 0;
    }

    private String generateSelectQuery(String sourceSchema, String geometry, int limit, int offset) {

        /*
        SELECT
        lines.id as way_id,
        geoobject_geometry.id_geoobject_source as geoobject_id, geoobject.name,
        classification.class as classification_class, classification.subclassname as classification_subclassname,
        geoobject_geometry.tags, geoobject_geometry.valid_since,
        geoobject_geometry.valid_until, lines.line as way
        FROM lines
        INNER JOIN geoobject_geometry ON lines.id=geoobject_geometry.id_target
        INNER JOIN geoobject ON geoobject_geometry.id_geoobject_source=geoobject.id
        INNER JOIN classification ON geoobject_geometry.classification_id=classification.id
        WHERE geoobject_geometry.type_target = 2
        LIMIT {1} OFFSET {2};
         */

        String whereClause = "";

        if (geometry == "point") {
            whereClause = "geoobject_geometry.type_target = 0 or geoobject_geometry.type_target = 1";
        } else if (geometry == "line") {
            whereClause = "geoobject_geometry.type_target = 2";
        } else {
            // polygon
            whereClause = "geoobject_geometry.type_target = 3";
        }


        StringBuilder query = new StringBuilder("SELECT ");
        query.append(geometry);
        query.append("s.id as way_id, ");
        query.append("geoobject_geometry.id_geoobject_source as geoobject_id, ");
        query.append("geoobject.name, ");
        query.append("classification.class as classification_class, ");
        query.append("classification.subclassname as classification_subclassname, ");
        query.append("geoobject_geometry.tags, ");
        query.append("geoobject_geometry.valid_since, ");
        query.append("geoobject_geometry.valid_until, ");
        query.append("ST_Transform(" + geometry + "s." + geometry + ", 3857)");
        query.append(" as way ");
        query.append("FROM ");
        query.append(sourceSchema);
        query.append(".");
        query.append(geometry);
        query.append("s ");
        query.append("INNER JOIN ");
        query.append(sourceSchema);
        query.append(".geoobject_geometry ON ");
        query.append(geometry);
        query.append("s.id=geoobject_geometry.id_target ");
        query.append("INNER JOIN ");
        query.append(sourceSchema);
        query.append(".geoobject ON geoobject_geometry.id_geoobject_source=geoobject.id ");
        query.append("INNER JOIN ");
        query.append(sourceSchema);
        query.append(".classification ON geoobject_geometry.classification_id=classification.id ");
        query.append("WHERE ");
        query.append(whereClause);
        query.append(" LIMIT " + limit + " OFFSET " + offset + ";");

        return query.toString();
    }

    /**
     * Show the current import status
     *
     * @param currentRow
     * @param maxRow
     * @param geometry
     */
    void showImportStatus(int currentRow, int maxRow, String geometry) {
        if (currentRow % 10000 == 0) {
            System.out.println(geometry + ": " + currentRow + " of " + maxRow);
        }
    }

    void convertPoints(SQLStatementQueue sql, String targetSchema, String sourceSchema, int maxRows) throws SQLException {
        System.out.println("Start import points!");

        for (int i = 0; i <= maxRows; i += this.chunkSize) {
            sql.append(this.generateSelectQuery(sourceSchema, "point", this.chunkSize, i));
            ResultSet result = null;
            try {
                result = sql.executeWithResult();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }

            Point point;
            int currentRow = i;

            while (result.next()) {
                point = new Point(
                        result.getLong(1), // wayId
                        result.getLong(2), // geoobjectId
                        result.getString(3), // name
                        result.getString(4), // classificationClass
                        result.getString(5), // classificationSubclassname
                        result.getString(6), // tags
                        result.getDate(7), // validSince
                        result.getDate(8), // validUntil
                        result.getString(9) // way
                );

                sql.append(point.getMapnikQuery(targetSchema));

                currentRow++;
                this.showImportStatus(currentRow, maxRows, "point");
            }

            System.out.println("Upload to database...");
            try {
                sql.forceExecute();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }
        }
        System.out.println("Points import are completed!");
    }

    void convertLines(SQLStatementQueue sql, String targetSchema, String sourceSchema, int maxRows) throws SQLException {
        System.out.println("Start import lines!");

        for (int i = 0; i <= maxRows; i += this.chunkSize) {
            sql.append(this.generateSelectQuery(sourceSchema, "line", this.chunkSize, i));

            ResultSet result = null;
            try {
                result = sql.executeWithResult();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }

            Line line;
            int currentRow = i;

            while (result.next()) {
                line = new Line(
                        result.getLong(1), // wayId
                        result.getLong(2), // geoobjectId
                        result.getString(3), // name
                        result.getString(4), // classificationClass
                        result.getString(5), // classificationSubclassname
                        result.getString(6), // tags
                        result.getDate(7), // validSince
                        result.getDate(8), // validUntil
                        result.getString(9) // way
                );

                sql.append(line.getMapnikQuery(targetSchema));

                currentRow++;
                this.showImportStatus(currentRow, maxRows, "line");
            }

            System.out.println("Upload to database...");
            try {
                sql.forceExecute();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }
        }

        System.out.println("Lines import are completed!");
    }

    void convertPolygons(SQLStatementQueue sql, String targetSchema, String sourceSchema, int maxRows) throws SQLException {
        System.out.println("Start import polygons!");

        for (int i = 0; i <= maxRows; i += this.chunkSize) {
            sql.append(this.generateSelectQuery(sourceSchema, "polygon", this.chunkSize, i));

            ResultSet result = null;
            try {
                result = sql.executeWithResult();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }

            Polygon polygon;
            int currentRow = i;

            while (result.next()) {
                polygon = new Polygon(
                        result.getLong(1), // wayId
                        result.getLong(2), // geoobjectId
                        result.getString(3), // name
                        result.getString(4), // classificationClass
                        result.getString(5), // classificationSubclassname
                        result.getString(6), // tags
                        result.getDate(7), // validSince
                        result.getDate(8), // validUntil
                        result.getString(9) // way
                );

                sql.append(polygon.getMapnikQuery(targetSchema));

                currentRow++;
                this.showImportStatus(currentRow, maxRows, "polygon");
            }

            System.out.println("Upload to database...");
            try {
                sql.forceExecute();
            } catch (SQLException e) {
                System.err.println("exception ignored: " + e);
            }
        }


        // repair polygons
        sql.append("UPDATE " + targetSchema + ".planet_osm_polygon SET way = ST_MakeValid(way) WHERE not ST_IsValid(way);");
        try {
            sql.forceExecute();
        } catch (SQLException e) {
            System.err.println("exception ignored: " + e);
        }

        // set area
        sql.append("UPDATE " + targetSchema + ".planet_osm_polygon SET way_area = ST_Area(way);");
        try {
            sql.forceExecute();
        } catch (SQLException e) {
            System.err.println("exception ignored: " + e);
        }

        System.out.println("Polygons import are completed!");
    }

}
