package exportfromintermediate;

import java.math.BigDecimal;

/**
 *
 * @author thsc
 */
public class OHDMNode extends OHDMElement {
    private String longitude;
    private String latitude;

    OHDMNode(IntermediateDB intermediateDB, String osmIDString, String classCodeString, String sTags, String ohdmObjectIDString, String ohdmGeomIDString, boolean valid) {
        super(intermediateDB, osmIDString, classCodeString, null, sTags, ohdmObjectIDString, ohdmGeomIDString, valid);
    }

    OHDMNode(IntermediateDB intermediateDB, String osmIDString, String classCodeString, String sTags, String longitude, String latitude, String ohdmObjectIDString, String ohdmGeomIDString, boolean valid) {
        this(intermediateDB, osmIDString, classCodeString, sTags, ohdmObjectIDString, ohdmGeomIDString, valid);
        
        this.longitude = longitude;
        this.latitude = latitude;
    }
    
    @Override
    String getWKTGeometry() {
        StringBuilder sb = new StringBuilder("POINT(");
        sb.append(this.getLongitude());
        sb.append(" ");
        sb.append(this.getLatitude());
        sb.append(")");
        
        return sb.toString();
    }
    
    String getLongitude() {
        return this.longitude;
    }
    
    String getLatitude() {
        return this.latitude;
    }

    @Override
    GeometryType getGeometryType() {
        return GeometryType.POINT;
    }
}